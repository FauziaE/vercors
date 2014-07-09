/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package semper
package silicon
package heap

import interfaces.{VerificationResult, Failure}
import interfaces.state.{ChunkIdentifier, Chunk, Store, Heap, PathConditions, State, StateFactory}
import interfaces.reporting.Context
import interfaces.decider.Decider
import state.terms._
import silicon.state.terms.utils.BigPermSum
import state.{SymbolConvert, QuantifiedChunk, FieldChunkIdentifier, DirectFieldChunk}
import ast.Field
import sil.ast.LocationAccess
import sil.verifier.PartialVerificationError
import sil.verifier.reasons.{InsufficientPermission, ReceiverNull}

/**
 * Helper functions to handle quantified chunks
 */
trait QuantifiedChunkHelper[ST <: Store[ST], H <: Heap[H], PC <: PathConditions[PC], S <: State[ST, H, S], C <: Context[C]] {
  def getQuantifiedChunk(h: H, field: String): Option[QuantifiedChunk]

  def isQuantifiedFor(h: H, field: String): Boolean

  def value(σ: S,
            h: H,
            ofReceiver: Term,
            withField: Field,
            quantifiedVars: Seq[Term],
            pve:PartialVerificationError,
            locacc:LocationAccess,
            c: C)
           (Q: Term => VerificationResult)
           : VerificationResult

  /**
   * Converts all field chunks for the given field to their quantified equivalents
   */
  def quantifyChunksForField(h: H, f: String/*, quantifiedVars: Seq[Var]*/): H

  def rewriteGuard(guard: Term): Term

  /**
   * Transform a single element (without a guard) to its axiomatization equivalent
   */
  def transformElement(rcvr: Term,
                       field: String,
                       value: Term,
                       perm:DefaultFractionalPermissions/*,
                       quantifiedVars: Seq[Var]*/)
                      : (QuantifiedChunk, Option[Term])

  /**
   * Transform permissions under quantifiers to their axiomatization equivalents
   */
  def transform(rcvr: Term,
                f: Field,
                value: Term,
                talpha: DefaultFractionalPermissions,
                cond: Term,
                quantifiedVars: Seq[Term])
               : QuantifiedChunk

  /**
   * Returns a symbolic sum which is equivalent to the permissions for the given receiver/field combination
   */
  def permission(h: H, id: ChunkIdentifier, quantifiedVars: Seq[Term]): Term

  /**
   * Consumes the given chunk in the heap
   */
  def consume(σ: S,
              h: H,
//              ch: QuantifiedChunk,
              rcvr: Term,
              f: Field,
              perms: DefaultFractionalPermissions,
              quantifiedVars: Seq[Term],
              pve:PartialVerificationError,
              locacc: LocationAccess,
              c: C)
             (Q: H => VerificationResult)
             : VerificationResult
}

class DefaultQuantifiedChunkHelper[ST <: Store[ST],
                                   H <: Heap[H],
                                   PC <: PathConditions[PC],
                                   S <: State[ST, H, S],
                                   C <: Context[C]]
                                  (decider: Decider[DefaultFractionalPermissions, ST, H, PC, S, C],
                                   symbolConverter: SymbolConvert,
                                   stateFactory: StateFactory[ST, H, S])
    extends QuantifiedChunkHelper[ST, H, PC, S, C] {

  import symbolConverter.toSort
  import stateFactory._
  import decider._

  def getQuantifiedChunk(h: H, field: String) =
    h.values.find{
      case ch: QuantifiedChunk => ch.name == field
      case _ => false
    }.asInstanceOf[Option[QuantifiedChunk]]
//    h.values.filter(_.name == field)
//            .exists{case ch: QuantifiedChunk => true case _ => false}

  def isQuantifiedFor(h: H, field: String) = getQuantifiedChunk(h, field).nonEmpty

  def transformElement(rcvr: Term,
                       field: String,
                       value: Term,
                       perm: DefaultFractionalPermissions/*,
                       quantifiedVars: Seq[Var]*/) =

    rcvr match {
      case SeqAt(s, i) =>
//        Predef.assert(quantifiedVars.length == 1,
//                      s"Expected a single quantified variable only, but found $quantifiedVars")

//        val tPermInstantiated = perm.replace(quantifiedVars(0), i).asInstanceOf[DefaultFractionalPermissions]

        val tTotalPerm =
          IntPermTimes(
//            tPermInstantiated,
            MultisetCount(*(), MultisetFromSeq(SeqDrop(SeqTake(s, Plus(IntLiteral(1), i)),i))),
            perm)

        println(s"  rcvr = $rcvr")
        println(s"  i = $i")
//        println(s"  quantifiedVars = $quantifiedVars")
//        println(s"  tPermInstantiated = $tPermInstantiated")
        println(s"  tTotalPerm = $tTotalPerm")

        (QuantifiedChunk(field, value, tTotalPerm, Nil/*quantifiedVars*/), Some(i))

      case _ =>
        val p = TermPerm(Ite(*() === rcvr, perm, NoPerm()))

        (QuantifiedChunk(field, value, p, Nil/*quantifiedVars*/), None)
    }

  /**
    * Gives the permissions in the heap for the given receiver
    */
  def permission(h: H, id: ChunkIdentifier, quantifiedVars: Seq[Term]): Term = {
    val chunks = h.values.toSeq.collect {
      case permChunk: QuantifiedChunk if permChunk.name == id.name =>
        permChunk.perm.replace(*(), id.args.last)
                      .replace(permChunk.quantifiedVars, quantifiedVars)
    }.asInstanceOf[Iterable[DefaultFractionalPermissions]]

    BigPermSum(chunks, Predef.identity)
  }

  def rewriteGuard(guard: Term): Term = {
    guard match {
      case SeqIn(SeqRanged(a,b),c) => /*SeqIn(SeqRanged(a,b),c)*/ And(AtLeast(c,a), Less(c,b))
      case t => t /* Sets */
    }
  }

  def value(σ: S,
            h: H,
            rcvr: Term,
            f: Field,
            quantifiedVars: Seq[Term],
            pve: PartialVerificationError,
            locacc: LocationAccess,
            c: C)
           (Q: Term => VerificationResult)
           : VerificationResult = {

    decider.assert(σ, Or(NullTrigger(rcvr),rcvr !== Null())) {
      case false =>
        Failure[ST, H, S](pve dueTo ReceiverNull(locacc))
      case true =>
        decider.assert(σ, Less(NoPerm(), permission(h, FieldChunkIdentifier(rcvr, f.name), quantifiedVars))) {
          case false =>
            decider.prover.logComment("cannot read " + rcvr + "." + f.name + " in heap: " + h.values.filter(ch => ch.name == f.name))
            Failure[ST, H, S](pve dueTo InsufficientPermission(locacc))
          case true =>
            decider.prover.logComment("creating function to represent " + f + " relevant heap portion: " + h.values.filter(ch => ch.name == f.name))
            val valueT = decider.fresh(f.name, sorts.Arrow(sorts.Ref, toSort(f.typ)))
            val fApp = DomainFApp(Function(valueT.id, sorts.Arrow(sorts.Ref, toSort(f.typ))), List(*()))
            val x = Var("x", sorts.Ref)

            h.values.foreach {
              case ch: QuantifiedChunk if ch.name == f.name =>
                /* TODO: Commenting the triggers is (probably) just a temporary work-around to cope with problems related to quantified permissions. */
                //                val valtrigger = ch.value match {
                //                  case _: DomainFApp => Trigger(List(ch.value.replace(*(), x)))
                //                  case _ => Trigger(List())}

                val qvsMap = toMap(quantifiedVars zip ch.quantifiedVars)
                val tInstantiatedPerms = ch.perm.replace(qvsMap).replace(*(), x).asInstanceOf[DefaultFractionalPermissions]
                val tInstantiatedValue = ch.value.replace(qvsMap).replace(*(), x)

                decider.assume(
                  Quantification(
                    Forall,
                    List(x),
                    Implies(tInstantiatedPerms > NoPerm(), fApp.replace(*(), x) === tInstantiatedValue)
                    /*, List(Trigger(List(fApp.replace(*(), x))), valtrigger)*/))

              case ch if ch.name == f.name =>
                sys.error(s"I did not expect non-quantified chunks on the heap for field $ch")

              case _ =>
            }

            Q(DomainFApp(Function(valueT.id, sorts.Arrow(sorts.Ref, toSort(f.typ))), List(rcvr)))}}
  }

  def quantifyChunksForField(h: H, f: String/*, quantifiedVars: Seq[Var]*/) =
    H(h.values.map {
      case ch: DirectFieldChunk if ch.name == f =>
        transformElement(ch.id.rcvr, f, ch.value, ch.perm/*, quantifiedVars*/)._1

      case ch =>
        ch
    })

  /* TODO: Don't emit the Seq[Int] axiomatisation just because there's a ranged in forall */
  def transform(rcvr: Term,
                f: Field,
                value: Term,
                talpha: DefaultFractionalPermissions,
                cond: Term,
                quantifiedVars: Seq[Term]) = {

    val count = rcvr match {
      case SeqAt(s, i) =>
        cond match {
          case SeqIn(SeqRanged(a, b), c) if c == i => MultisetCount(*(), MultisetFromSeq(SeqDrop(SeqTake(s, b), a)))
          case a => sys.error("Silicon cannot handle conditions of this form when quantifying over a sequence. Try 'forall i:Int :: i in [x..y] ==>' ...")
        }
      case v: Var =>
        Ite(cond.replace(rcvr, *()), IntLiteral(1), IntLiteral(0))
      case _ =>
        sys.error("Unknown type of receiver, cannot rewrite.")
    }

    QuantifiedChunk(f.name, value, IntPermTimes(count, talpha), quantifiedVars)
  }

  def isWildcard(perm: Term):Boolean = perm match {
    case TermPerm(t) => isWildcard(t)
    case _: WildcardPerm => true
    case PermPlus(t0, t1) => isWildcard(t0) || isWildcard(t1)
    case PermMinus(t0, t1) => isWildcard(t0) || isWildcard(t1)
    case PermTimes(t0, t1) => isWildcard(t0) || isWildcard(t1)
    case IntPermTimes(_, t1) => isWildcard(t1)
    case Ite(a,b,c) => isWildcard(b) || isWildcard(c)
    case FullPerm() => false
    case NoPerm() => false
    case PermMin(a,b) => isWildcard(a) || isWildcard(b)
    case MultisetCount(_) => false
    case FractionPerm(_,_) => false
    case _ => false
  }

  /* TODO: Implement an optimized order for exhale.
   *       One heuristic could be to take chunks first that
   *       Mention the same sets/sequences (syntactically modulo equality).
   */
//  private def exhalePermissions2(σ: S, h: H, ch: QuantifiedChunk): (Chunk, H, Boolean) = {
//    val skolem = fresh(sorts.Ref)
//    val opt = h.values /* optimizedOrder(h.values, ch) */
//    decider.prover.logComment("" + opt)
//    opt.foldLeft[(Chunk,H,Boolean)]((ch,h.empty,false)){
//      case ((ch1:QuantifiedChunk, h, true), ch2) => (ch1, h+ch2, true)
//      case ((ch1:QuantifiedChunk, h, false), ch2) =>
//        ch2 match {
//          case quant:QuantifiedChunk if quant.name == ch1.name =>
//            if(isWildcard(ch1.perm)) assume(ch1.perm.replace(*(), skolem).asInstanceOf[DefaultFractionalPermissions] < quant.perm.replace(*(), skolem).asInstanceOf[DefaultFractionalPermissions])
//            val r = PermMin(ch1.perm, quant.perm)
//            val d = check(σ, (ch1.perm-r).replace(*(), skolem) === NoPerm())
//            if (check(σ, (quant.perm - r).replace(*(), skolem) === NoPerm()))
//              (QuantifiedChunk(ch1.name, null, ch1.perm - r, Nil), h, d)
//            else
//              (QuantifiedChunk(ch1.name, null, ch1.perm-r, Nil), h+QuantifiedChunk(quant.name, quant.value, quant.perm - r, Nil), d)
//
//          case ch =>
//            (ch1, h + ch, false)
//        }
//    }
//  }

  private def exhalePermissions2(σ: S,
                                 h: H,
                                 /*ch: QuantifiedChunk*/
                                 rcvr: Term,
                                 f: Field,
                                 perms: DefaultFractionalPermissions,
                                 quantifiedVars: Seq[Term])
                                : (DefaultFractionalPermissions, H, Boolean) = {

    val vSkolem = rcvr // fresh(sorts.Ref)
    val opt = h.values /* optimizedOrder(h.values, ch) */
    decider.prover.logComment("" + opt)

    def skol(perms: DefaultFractionalPermissions) =
      perms.replace(*(), vSkolem).asInstanceOf[DefaultFractionalPermissions]

    println("\n[exhalePermissions2]")
//    println(s"  ch = $ch")
    println(s"  rcvr = $rcvr")
    println(s"  f = $f")
    println(s"  perms = $perms")
    println(s"  quantifiedVars = $quantifiedVars")
//    println(s"  vSkolem = $vSkolem")

    opt.foldLeft((perms, h.empty, false)) {
      case ((perms1, h1, true), ch2) =>
        /* No further permissions needed */
        (perms1, h1 + ch2, true)

      case ((perms1, h1, false), ch2: QuantifiedChunk) if ch2.name == f.name =>
        /* More permissions needed and ch2 is a chunk that provides permissions */

        /* In these permission terms the Vars chosen for the explicitly quantified variables
         * at the time the quantified chunk ch2 was created have been replaced by the Vars
         * that were chosen when ch1 was created.
         */
        val tInitializedPerm1 = perms1 //ch1.perm
        val tInitializedPerm2 = ch2.perm.replace(ch2.quantifiedVars, /*ch1.*/quantifiedVars).asInstanceOf[DefaultFractionalPermissions]

//        println(s"\n  ch1 = $ch1")
        println(s"\n  perms1 = $perms1")
        println(s"  ch2 = $ch2")
//        println(s"  ch1.quantifiedVars = ${ch1.quantifiedVars}")
        println(s"  ch2.quantifiedVars = ${ch2.quantifiedVars}")
        println(s"  tInitializedPerm1 = $tInitializedPerm1")
        println(s"  tInitializedPerm2 = $tInitializedPerm2")

        if (isWildcard(tInitializedPerm1)) // TODO: Unsound! Constrains all wildcards, regardless of whether or not they are currently constrainable
          assume(skol(tInitializedPerm1) < skol(tInitializedPerm2))

        val r = PermMin(tInitializedPerm1, tInitializedPerm2)
        val d = check(σ, skol(tInitializedPerm1 - r) === NoPerm())

        println(s"  r = $r")
        println(s"  d = $d")

        /* IMPORTANT: The updated version of ch2 must still use the placeholder (*), not vSkolem!
         *            Hence, the updated version of ch1 must also still use the placeholder.
         */

        if (check(σ, skol(tInitializedPerm2 - r) === NoPerm()))
          (/*ch1*/perms1 - r, h1, d)
        else
          (/*ch1*/perms1 - r, h1 + ch2.copy(perm = tInitializedPerm2 - r, quantifiedVars = /*ch1.*/quantifiedVars), d)

      case ((ch1, h1, false), ch2) =>
        /* More permissions needed, but ch2 is not a chunk that provides permissions */
        (ch1, h1 + ch2, false)
    }
  }

  def consume(σ: S,
              h: H,
//              ch: QuantifiedChunk,
              rcvr: Term,
              f: Field,
              perms: DefaultFractionalPermissions,
              quantifiedVars: Seq[Term],
              pve:PartialVerificationError,
              locacc: LocationAccess,
              c: C)
             (Q: H => VerificationResult)
             : VerificationResult = {


    val k = exhalePermissions2(σ, h, rcvr, f, perms, quantifiedVars)
    if(!k._3)
      Failure[ST, H, S](pve dueTo InsufficientPermission(locacc))
    else
      Q(k._2)
  }
}
