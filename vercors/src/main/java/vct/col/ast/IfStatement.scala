package vct.col.ast

import hre.ast.MessageOrigin;
import scala.collection.mutable.ArrayBuffer
import vct.col.util.VisitorHelper

object IfStatement {
  val elseGuard = new ConstantExpression(true, new MessageOrigin("else guard"))
}

case class IfStatementCase(var guard:ASTNode, var effect:ASTNode)

class IfStatement extends ASTNode with VisitorHelper  {
  private[this] val cases = new ArrayBuffer[IfStatementCase]()
  
  def getCount = cases.size
  def getGuard(i:Int) = cases.apply(i).guard
  def getStatement(i:Int) = cases.apply(i).effect
  
  def this(cond:ASTNode, truebranch:ASTNode, falsebranch:ASTNode) = {
    this()
    addClause(cond, truebranch)
    if (falsebranch != null) addClause(IfStatement.elseGuard, falsebranch)
  }
  
  def addClause(guard:ASTNode, stmt:ASTNode) : Unit = {
    stmt.setParent(this)
    if (guard != IfStatement.elseGuard) guard.setParent(this)
    cases += new IfStatementCase(guard, stmt)
  }
  
  override def accept_simple[T,A](m:ASTMapping1[T,A], arg:A) = m.map(this, arg)
  override def accept_simple[T](v:ASTVisitor[T]) = handle_standard(() => v.visit(this))
  override def accept_simple[T](m:ASTMapping[T]) = handle_standard(() => m.map(this))
}
