/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package viper.silicon

import org.slf4s.Logging
import viper.silver.ast
import viper.silver.components.StatefulComponent
import viper.silicon.DefaultVerifier._
import viper.silicon.interfaces._
import viper.silicon.decider.{DeciderProvider, SMTLib2PreambleEmitter}
import viper.silicon.state._
import viper.silicon.state.terms.{AxiomRewriter, sorts, Sort}
import viper.silicon.supporters._
import viper.silicon.supporters.functions.FunctionSupporterProvider
import viper.silicon.supporters.qps._
import viper.silicon.reporting.{DefaultStateFormatter, Bookkeeper}
import viper.silicon.utils.NoOpStatefulComponent
import viper.silver.verifier.Success
import viper.api.OriginInfo

object DefaultVerifier {
  type ST = MapBackedStore
  type H = ListBackedHeap
  type S = DefaultState[ST, H]
  type C = DefaultContext[H]
}

class DefaultVerifier(val config: Config)
    extends NoOpStatefulComponent
       with DeciderProvider[ST, H, S]
       with DefaultEvaluator[ST, H, S]
       with DefaultProducer[ST, H, S]
       with DefaultConsumer[ST, H, S]
       with DefaultExecutor[ST, H, S]
       with FunctionSupporterProvider[ST, H, S]
       with ChunkSupporterProvider[ST, H, S]
       with PredicateSupporterProvider[ST, H, S]
       with DefaultBrancher[ST, H, S]
       with DefaultJoiner[ST, H, S]
       with DefaultLetHandler[ST, H, S, C]
       with MagicWandSupporter[ST, H, S]
       with HeuristicsSupporter[ST, H, S]
       with HeapCompressorProvider[ST, H, S, C]
       with QuantifiedChunkSupporterProvider[ST, H, S]
       with QuantifiedPredicateChunkSupporterProvider[ST, H, S]
       with MethodSupporterProvider[ST, H, S]
       with Logging {

  protected implicit val manifestH: Manifest[H] = manifest[H]

  val bookkeeper = new Bookkeeper(config)
  val stateFormatter = new DefaultStateFormatter[ST, H, S](config)
  val symbolConverter = new DefaultSymbolConvert()
  val domainTranslator = new DefaultDomainsTranslator(symbolConverter)
  val stateFactory = new DefaultStateFactory()
  val identifierFactory = new DefaultIdentifierFactory
  val axiomRewriter = new AxiomRewriter(new utils.Counter(), bookkeeper.logfiles("axiomRewriter"))
  val preambleEmitter = new SMTLib2PreambleEmitter(decider.prover)
  val sequencesEmitter = new DefaultSequencesEmitter(decider.prover, symbolConverter, preambleEmitter)
  val setsEmitter = new DefaultSetsEmitter(decider.prover, symbolConverter, preambleEmitter)
  val multisetsEmitter = new DefaultMultisetsEmitter(decider.prover, symbolConverter, preambleEmitter)
  val domainsEmitter = new DefaultDomainsEmitter(decider.prover, domainTranslator, symbolConverter)
  val fieldValueFunctionsEmitter = new DefaultFieldValueFunctionsEmitter(decider.prover, symbolConverter, preambleEmitter, config)
  val predSnapGenerator = new PredicateSnapGenerator(symbolConverter)
  val predicateSnapFunctionsEmitter = new DefaultPredicateSnapFunctionsEmitter(decider.prover, symbolConverter, predSnapGenerator, preambleEmitter, config)


  private val statefulSubcomponents = List[StatefulComponent](
    bookkeeper,
    preambleEmitter, sequencesEmitter, setsEmitter, multisetsEmitter, domainsEmitter,
    fieldValueFunctionsEmitter,
    predicateSnapFunctionsEmitter,
    decider, identifierFactory,
    functionsSupporter, predicateSupporter, methodSupporter,
    quantifiedChunkSupporter, quantifiedPredicateChunkSupporter )

  /* Lifetime */

  override def start() {
    super.start()
    statefulSubcomponents foreach (_.start())
  }

  override def reset() {
    super.reset()
    statefulSubcomponents foreach (_.reset())
  }

  override def stop() {
    super.stop()
    statefulSubcomponents foreach (_.stop())
  }

  /* Program verification */
  
  def verify(program: ast.Program): List[VerificationResult] = {
    predSnapGenerator.setup(program)

    emitPreamble(program)
    SymbExLogger.resetMemberList()
    SymbExLogger.setConfig(config)

    //    ev.predicateSupporter.handlePredicates(program)

    /* FIXME: A workaround for Silver issue #94.
     * toList must be before flatMap. Otherwise Set will be used internally and some
     * error messages will be lost.
     */
    val functionVerificationResults = functionsSupporter.units.toList flatMap (function =>
      if (viper.api.VControl.gonogo(function)) {
        val l = functionsSupporter.verify(function, createInitialContext(function, program))
        val ok=l match {
          case Seq(viper.silicon.interfaces.Success()) => true
          case _ => false
        }
        bookkeeper.functionVerified(function.name)
        viper.api.VControl.report(function,ok)
        l
      } else {
        List()
      })

    val predicateVerificationResults = predicateSupporter.units.toList flatMap (predicate =>
      if (viper.api.VControl.gonogo(predicate)) {
        val l = predicateSupporter.verify(predicate, createInitialContext(predicate, program))
        val ok=l match {
          case Seq(viper.silicon.interfaces.Success()) => true
          // TODO: why can we ignore unreachable?
          case Seq(Unreachable()) => true
          case _ => false
        }
        bookkeeper.predicateVerified(predicate.name)
        viper.api.VControl.report(predicate,ok)
        l
      } else {
        List()
      })

    val methodVerificationResults =
      methodSupporter.units.toList
        .filterNot(excludeMethod)
        .flatMap(method => {
          if (viper.api.VControl.gonogo(method)) {
            val c = createInitialContext(method, program)
            //      ev.quantifiedChunkSupporter.initLastFVF(c.qpFields) /* TODO: Implement properly */
            val l = methodSupporter.verify(method, c)
            val ok=l match {
              case Seq(viper.silicon.interfaces.Success()) => true
              case Seq(viper.silicon.interfaces.Unreachable()) => true
              case _ => false
            }
            bookkeeper.methodVerified(method.name)
            viper.api.VControl.report(method,ok)
            l
          } else {
            List()
          }
        })
        
    /** Write JavaScript-Representation of the log if the SymbExLogger is enabled */
    SymbExLogger.writeJSFile()
    /** Write DOT-Representation of the log if the SymbExLogger is enabled */
    SymbExLogger.writeDotFile()

    (functionVerificationResults
      ++ predicateVerificationResults
      ++ methodVerificationResults)
  }

  private def createInitialContext(member: ast.Member, program: ast.Program): C = {
    val quantifiedFields = toSet(ast.utility.QuantifiedPermissions.quantifiedFields(member, program))
    val quantifiedPredicates = toSet(ast.utility.QuantifiedPermissions.quantifiedPredicates(member, program))
    val applyHeuristics = program.fields.exists(_.name.equalsIgnoreCase("__CONFIG_HEURISTICS"))

    DefaultContext[H](program = program,
                      qpFields = quantifiedFields,
                      qpPredicates = quantifiedPredicates,
                      applyHeuristics = applyHeuristics,
                      predicateSnapMap = predSnapGenerator.snapMap,
                      predicateFormalVarMap = predSnapGenerator.formalVarMap)
  }

  private def excludeMethod(method: ast.Method) = (
       !method.name.matches(config.includeMethods())
    || method.name.matches(config.excludeMethods()))

  /* Prover preamble */

  private def emitPreamble(program: ast.Program) {
    decider.prover.logComment("Started: " + bookkeeper.formattedStartTime)
    decider.prover.logComment("Silicon.buildVersion: " + Silicon.buildVersion)

    decider.prover.logComment("-" * 60)
    decider.prover.logComment("Preamble start")

    sequencesEmitter.analyze(program)
    setsEmitter.analyze(program)
    multisetsEmitter.analyze(program)
    domainsEmitter.analyze(program)
    fieldValueFunctionsEmitter.analyze(program)
    predicateSnapFunctionsEmitter.analyze(program)
    functionsSupporter.analyze(program)
    predicateSupporter.analyze(program)
    methodSupporter.analyze(program)

    emitStaticPreamble()

    sequencesEmitter.declareSorts()
    setsEmitter.declareSorts()
    multisetsEmitter.declareSorts()
    domainsEmitter.declareSorts()
    fieldValueFunctionsEmitter.declareSorts()
    predicateSnapFunctionsEmitter.declareSorts()
    functionsSupporter.declareSorts()
    predicateSupporter.declareSorts()
    methodSupporter.declareSorts()

    /* Sequences depend on multisets ($Multiset.fromSeq, which is
     * additionally axiomatised in the sequences axioms).
     * Multisets depend on sets ($Multiset.fromSet).
     */
    setsEmitter.declareSymbols()
    multisetsEmitter.declareSymbols()
    sequencesEmitter.declareSymbols()
    domainsEmitter.declareSymbols()
    domainsEmitter.emitUniquenessAssumptions()
    fieldValueFunctionsEmitter.declareSymbols()
    predicateSnapFunctionsEmitter.declareSymbols()
    functionsSupporter.declareSymbols()
    predicateSupporter.declareSymbols()
    methodSupporter.declareSymbols()

    sequencesEmitter.emitAxioms()
    setsEmitter.emitAxioms()
    multisetsEmitter.emitAxioms()
    domainsEmitter.emitAxioms()
    functionsSupporter.emitAxioms()
    predicateSupporter.emitAxioms()
    methodSupporter.emitAxioms()

    emitSortWrappers(Set(sorts.Int, sorts.Bool, sorts.Ref, sorts.Perm))
    emitSortWrappers(sequencesEmitter.sorts)
    emitSortWrappers(setsEmitter.sorts)
    emitSortWrappers(multisetsEmitter.sorts)
    emitSortWrappers(domainsEmitter.sorts)
    emitSortWrappers(fieldValueFunctionsEmitter.sorts)
    emitSortWrappers(predicateSnapFunctionsEmitter.sorts)
    emitSortWrappers(functionsSupporter.sorts)
    emitSortWrappers(predicateSupporter.sorts)
    emitSortWrappers(methodSupporter.sorts)

    /* ATTENTION: The triggers mention the sort wrappers introduced for FVFs.
     * The axiom therefore needs to be emitted after the sort wrappers have
     * been emitted.
     */
    fieldValueFunctionsEmitter.emitAxioms()
    predicateSnapFunctionsEmitter.emitAxioms()

    decider.prover.logComment("Preamble end")
    decider.prover.logComment("-" * 60)
  }

  private def emitSortWrappers(ss: Set[Sort]) {
    if (ss.nonEmpty) {
      decider.prover.logComment("Declaring additional sort wrappers")

      ss.foreach(sort => {
        val toSnapWrapper = terms.SortWrapperDecl(sort, sorts.Snap)
        val fromSnapWrapper = terms.SortWrapperDecl(sorts.Snap, sort)

        decider.prover.declare(toSnapWrapper)
        decider.prover.declare(fromSnapWrapper)

        preambleEmitter.emitParametricAssertions("/sortwrappers.smt2",
                                                 Map("$S$" -> decider.prover.termConverter.convert(sort)))
      })
    }
  }

  private def emitStaticPreamble() {
    decider.prover.logComment("\n; /z3config.smt2")
    preambleEmitter.emitPreamble("/z3config.smt2")

    val smt2ConfigOptions =
      config.z3ConfigArgs().map { case (k, v) => s"(set-option :$k $v)" }

    if (smt2ConfigOptions.nonEmpty) {
      log.info(s"Additional Z3 configuration options are '${config.z3ConfigArgs()}'")
      preambleEmitter.emitPreamble(smt2ConfigOptions)
    }

    decider.prover.logComment("\n; /preamble.smt2")
    preambleEmitter.emitPreamble("/preamble.smt2")
  }
}
