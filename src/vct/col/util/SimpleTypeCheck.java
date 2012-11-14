package vct.col.util;

import vct.col.ast.*;
import vct.col.ast.NameExpression.Kind;
import vct.col.ast.PrimitiveType.Sort;
import static hre.System.Abort;
import static hre.System.Debug;
import static hre.System.Fail;
import static hre.System.Warning;

public class SimpleTypeCheck extends RecursiveVisitor<Type> {

  public void check(){
    for(ASTClass cl:source().classes()){
      cl.accept(this);
    }
  }

  public SimpleTypeCheck(ProgramUnit arg){
    super(arg);
  }

  public void visit(ConstantExpression e){
    Debug("constant %s",e);
    super.visit(e);
    if (e.getType()==null) Abort("untyped constant %s",e);
  }
  public void visit(ClassType t){
    super.visit(t);
    ASTClass cl=source().find(t.getNameFull());
    if (cl==null) {
      Method m=source().find_predicate(t.getNameFull());
      if (m==null){
        Fail("type error: class (or predicate) "+t.getFullName()+" not found");
      }
    }
    t.setType(t);
  }
 
  public void visit(MethodInvokation e){
    super.visit(e);
    if (e.object==null) Abort("unresolved method invokation at "+e.getOrigin());
    if (e.object.getType()==null) Abort("object has no type at %s",e.object.getOrigin());
    if (!(e.object.getType() instanceof ClassType)) Abort("invokation on non-class");
    ClassType object_type=(ClassType)e.object.getType();
    int N=e.getArity();
    for(int i=0;i<N;i++){
      if (e.getArg(i).labels()>0) {
        for(int j=i+1;j<N;j++){
          if (e.getArg(j).labels()==0) Fail("positional argument following named argument");
        }
        N=i;
        break;
      }
    }
    Type type[]=new Type[N];
    for(int i=0;i<N;i++){
      type[i]=e.getArg(i).getType();
      if (type[i]==null) Abort("argument %d has no type.",i);
    }
    ASTClass cl=source().find(object_type.getNameFull());
    if (cl==null) Fail("could not find class %s",object_type.getFullName());
    Method m=cl.find(e.method,type);
    while(m==null && cl.super_classes.length>0){
      cl=source().find(cl.super_classes[0].getNameFull());
      m=cl.find(e.method,type);
    }
    if (m==null) {
      String parts[]=e.method.split("_");
      if (parts.length==3 && parts[1].equals("get")){
        // TODO: check if parts[0] is a predicate.
        DeclarationStatement field=cl.find_field(parts[2]);
        if (field!=null) {
          Warning("assuming %s is implicit getter function",e.method);
          e.setType(field.getType());
        }
        return;
      }
      String tmp="";
      if (N>0){
        tmp=type[0].toString();
        for(int i=1;i<N;i++){
          tmp=tmp+","+type[i].toString();
        }
      }
      Fail("could not find method %s(%s) in class %s at %s",e.method,tmp,object_type.getFullName(),e.getOrigin());
    }
    switch(m.kind){
    case Constructor:
      e.setType((Type)e.object);
      break;
    default:
      e.setType(m.getReturnType());
      break;
    }
    e.setDefinition(m);
  }
  
  public void visit(AssignmentStatement s){
    super.visit(s);
    ASTNode loc=s.getLocation();
    ASTNode val=s.getExpression();
    Type loc_type=loc.getType();
    if (loc_type==null) Abort("Location has no type.");
    Type val_type=val.getType();
    if (val_type==null) Abort("Value has no type has no type.");
    if (!(loc_type.equals(val_type) || loc_type.supertypeof(val_type))) {
      Abort("Types of location (%s) and value (%s) do not match at %s.",loc_type,val_type,s.getOrigin());
    }
  }
  
  public void visit(DeclarationStatement s){
    super.visit(s);
    String name=s.getName();
    Type t=s.getType();
    ASTNode e=s.getInit();
    if (e!=null && !t.equals(e.getType())) {
      Abort("type of %s (%s) does not match its initialization (%s)",name,t,e.getType());
    }
  }
  
  public void visit(Method m){
    super.visit(m);
    String name=m.getName();
    ASTNode body=m.getBody();
    Contract contract=m.getContract();
    if (contract!=null){
      if (contract.pre_condition.getType()==null) Abort("untyped pre condition"); // TODO check boolean.
      if (contract.post_condition.getType()==null) Abort("untyped post condition"); // TODO check boolean.
    }
    if (body!=null && (body instanceof BlockStatement)) {
      //TODO: determine type of block
      return;
    }
    if (body!=null) {
      Type bt=body.getType();
      if (bt==null) Abort("untyped body of %s has class %s",name,body.getClass());
      if (!bt.equals(m.getReturnType()))
      Abort("body of %s does not match result type",name);
    }
  }
  public void visit(NameExpression e){
    super.visit(e);
    Debug("%s name %s",e.getKind(),e.getName());
    Kind kind = e.getKind();
    String name=e.getName();
    switch(kind){
      case Argument:
      case Local:
      case Field:{
        VariableInfo info=variables.lookup(name);
        if (info==null) {
          Abort("%s name %s is undefined",kind,name);
        }
        if (info.kind!=kind){
          if (kind==NameExpression.Kind.Local){
            Warning("mismatch of kinds %s/%s for name %s",kind,info.kind,name);
          } else {
            Abort("mismatch of kinds %s/%s for name %s",kind,info.kind,name);
          }
        }
        DeclarationStatement decl=(DeclarationStatement)info.reference;
        e.setType(decl.getType());
        break;
      }
      case Method:
        if (e.getType()!=null){
          Abort("type of member %s has been set illegally",name);
        }
        break;
      case Reserved:
        if (name.equals("this")){
          ASTClass cl=current_class();
          if (cl==null){
            Abort("use of keyword this outside of class context");
          }
          e.setType(new ClassType(cl.getFullName()));
          break;
        } else if (name.equals("null")){
          e.setType(new ClassType("<<null>>"));
          break;
        } else if (name.equals("\\result")||name.equals("result")){
          Method m=current_method();
          if (m==null){
            Abort("Use of result keyword outside of a method context.");
          }
          e.setType(m.getReturnType());
          break;
        }
        Abort("missing case for reserved name %s",name);
        break;
      case Unresolved:{
        VariableInfo info=variables.lookup(name);
        if (info!=null) {
          Warning("unresolved %s name %s found during type check",info.kind,name);
          //TODO: fix for label case!
          DeclarationStatement decl=(DeclarationStatement)info.reference;
          e.setType(decl.getType());
          break;
        }
        Abort("unresolved name %s found during type check at %s",name,e.getOrigin());
        break;
      }
      case Label:
        e.setType(new ClassType("<<label>>"));
        break;
      default:
        Abort("missing case for kind %s",kind);
        break;
    }
  }
  public void visit(OperatorExpression e){
    super.visit(e);
    Debug("operator %s",e.getOperator());
    StandardOperator op=e.getOperator();
    switch(op){
    case And:
    case Star:
    case Or:
    case Implies:
    case IFF:
    {
      Type t1=e.getArg(0).getType();
      if (t1==null || !t1.isBoolean()) Fail("type of left argument unknown at "+e.getOrigin());
      Type t2=e.getArg(1).getType();
      if (t2==null) Fail("type of right argument unknown at %s",e.getOrigin());
      if (!t2.isBoolean()) Fail("type of right argument is %s rather than boolean at %s",t2,e.getOrigin());
      e.setType(new PrimitiveType(Sort.Boolean));
      break;
    }
    case PointsTo:
    case Perm:
    case Value:
      // TODO: check arguments
      e.setType(new PrimitiveType(Sort.Boolean));
      break;
    case Fork:
    case Join:
      // TODO: check arguments
      e.setType(new PrimitiveType(Sort.Void));
      break;
    case Assign:
    {
      if (e.getArg(0) instanceof NameExpression){
        NameExpression name=(NameExpression)e.getArg(0);
        if (name.getKind()==NameExpression.Kind.Label) break;
      }
      Type t1=e.getArg(0).getType();
      if (t1==null) Fail("type of left argument unknown at "+e.getOrigin());
      Type t2=e.getArg(1).getType();
      if (t2==null) Fail("type of right argument unknown at "+e.getOrigin());
      if (t1.getClass()!=t2.getClass()) {
        Fail("Types of left and right-hand side arguments in assignment are incomparable at "+e.getOrigin());
      }
      e.setType(t1);
      break;
    }    
    case EQ:
    case NEQ:
    {
      Type t1=e.getArg(0).getType();
      if (t1==null) Fail("type of left argument unknown at "+e.getOrigin());
      Type t2=e.getArg(1).getType();
      if (t2==null) Fail("type of right argument unknown at "+e.getOrigin());
      if (t1.getClass()!=t2.getClass()) {
        Fail("Types of left and right-hand side argument are uncomparable at "+e.getOrigin());
      }
      e.setType(new PrimitiveType(Sort.Boolean));
      break;
    }
    case ITE:
    {
      Type t=e.getArg(0).getType();
      if (!t.isBoolean()){
        Abort("First argument of if-the-else must be boolean at "+e.getOrigin());
      }
      Type t1=e.getArg(1).getType();
      if (t1==null) Fail("type of left argument unknown at "+e.getOrigin());
      Type t2=e.getArg(2).getType();
      if (t2==null) Fail("type of right argument unknown at "+e.getOrigin());
      if (t1.getClass()!=t2.getClass()) {
        Fail("Types of left and right-hand side argument are uncomparable at "+e.getOrigin());
      }
      e.setType(t1);      
      break;
    }
    case Not:
    {
      Type t=e.getArg(0).getType();
      if (!t.isBoolean()){
        Abort("Argument of negation must be boolean at "+e.getOrigin());
      }
      e.setType(t);
      break;
    }
    case PreIncr:
    case PreDecr:
    case PostIncr:
    case PostDecr:
    case UMinus:
    case UPlus:
    {
      Type t=e.getArg(0).getType();
      if (!t.isInteger()){
        Fail("Argument of %s must be boolean at %s",op,e.getOrigin());
      }
      e.setType(t);
      break;
    }
    case Plus:
    case Minus:
    case Mult:
    case Div:
    case Mod:
    {
      Type t1=e.getArg(0).getType();
      if (t1==null) Fail("type of left argument unknown at %s",e.getOrigin());
      Type t2=e.getArg(1).getType();
      if (t2==null) Fail("type of right argument unknown at %s",e.getOrigin());
      if (t1.supertypeof(t2)){
        e.setType(t1);
      } else if (t2.supertypeof(t1)){
        e.setType(t1);
      } else {
        Fail("Types of left and right-hand side argument are uncomparable at %s",e.getOrigin());
      }
      break;
    }
    case GTE:
    case LTE:
    case LT:
    case GT:
    {
      Type res=new PrimitiveType(Sort.Byte);
      Type t1=e.getArg(0).getType();
      if (t1==null) Fail("type of left argument unknown at %s",e.getOrigin());
      if (!t1.supertypeof(res)) Fail("type of first argument of %s is wrong at %s",op,e.getOrigin());
      Type t2=e.getArg(1).getType();
      if (t2==null) Fail("type of right argument unknown at %s",e.getOrigin());
      if (!t2.supertypeof(res)) Fail("type of second argument of %s is wrong at %s",op,e.getOrigin());
      if (t1.getClass()!=t2.getClass()) {
        Fail("Types of left and right-hand side argument are uncomparable at %s",e.getOrigin());
      }
      e.setType(new PrimitiveType(Sort.Boolean));      
      break;
    }
    case DirectProof:{
      e.setType(new PrimitiveType(Sort.Void));
      break;
    }
    case Fold:
    case Unfold:
    {
      ASTNode arg=e.getArg(0);
      if (!(arg instanceof MethodInvokation)){
        Fail("At %s: argument of (un)fold must be a predicate invokation.",arg.getOrigin());
      }
      MethodInvokation prop=(MethodInvokation)arg;
      if (prop.getDefinition().kind != Method.Kind.Predicate){
        Fail("At %s: argument of (un)fold must be predicate and not %s",arg.getOrigin(),prop.getDefinition().kind);
      }
      e.setType(new PrimitiveType(Sort.Void));      
      break;
    }
    case Assert:
    case HoarePredicate:
    case Assume:
    {
      Type t=e.getArg(0).getType();
      if (t==null) Fail("type of argument is unknown at %s",e.getOrigin());
      if (!t.isBoolean()){
        Fail("Argument of %s must be boolean at %s",op,e.getOrigin());
      }
      e.setType(new PrimitiveType(Sort.Void));      
      break;
    }
    case Old:
    {
      Type t=e.getArg(0).getType();
      if (t==null) Fail("type of argument is unknown at %s",e.getOrigin());
      e.setType(t);      
      break;
    }
    case Continue:
    {
      Type t=e.getArg(0).getType();
      if (t!=null) Fail("argument of %s should not have type %s",op,t);
      e.setType(new PrimitiveType(Sort.Void));  
      break;
    }
    case New:
    {
      ASTNode t=e.getArg(0);
      if (!(t instanceof ClassType)) Fail("argument to new is not a class type");
      e.setType((Type)t);
      break;
    }
    default:
      Abort("missing case of operator %s",op);
      break;
    }
  }
  
  public void visit(Dereference e){
    super.visit(e);
    Type object_type=e.object.getType();
    if (object_type==null) Fail("type of object unknown at "+e.getOrigin());
    if (!(object_type instanceof ClassType)) Abort("cannot select members of non-object type.");
    if (((ClassType)object_type).getFullName().equals("<<label>>")){
      //TODO: avoid this kludge to not typing labeled arguments
      e.setType(object_type);
    } else {
      Debug("resolving class "+((ClassType)object_type).getFullName()+" "+((ClassType)object_type).getNameFull().length);
      ASTClass cl=source().find(((ClassType)object_type).getNameFull());
      if (cl==null) Fail("could not find class %s",((ClassType)object_type).getFullName());
      Debug("looking in class "+cl.getName());
      DeclarationStatement decl=cl.find_field(e.field);
      if (decl==null) Fail("Field %s not found in class %s",e.field,((ClassType)object_type).getFullName());
      e.setType(decl.getType());
    }
  }

  public void visit(BlockStatement s){
    super.visit(s);
    // TODO: consider if type should be type of last statement. 
  }
  public void visit(IfStatement s){
    super.visit(s);
    int N=s.getCount();
    for(int i=0;i<N;i++){
      Type t=s.getGuard(i).getType();
      if (t==null || !(t instanceof PrimitiveType) || (((PrimitiveType)t).sort!=Sort.Boolean)){
        Fail("Guard %d of if statement is not a boolean at %s",i,s.getOrigin());
      }
    }
    // TODO: consider if this can be an if expression.... 
  }
  public void visit(ReturnStatement s){
    super.visit(s);
    // TODO: check expression against method type.
  }
  public void visit(ASTClass c){
    super.visit(c);
    // TODO: type checks on class.
  }
  
  public void visit(LoopStatement s) {
    super.visit(s);
    for(ASTNode inv:s.getInvariants()){
      Type t=inv.getType();
      if (t==null || !(t instanceof PrimitiveType) || (((PrimitiveType)t).sort!=Sort.Boolean)){
        Abort("loop invariant is not a boolean");
      }
    }
    ASTNode tmp;
    tmp=s.getEntryGuard();
    if (tmp!=null) {
      Type t=tmp.getType();
      if (t==null || !(t instanceof PrimitiveType) || (((PrimitiveType)t).sort!=Sort.Boolean)){
        Abort("loop entry guard is not a boolean");
      }
    }
    tmp=s.getExitGuard();
    if (tmp!=null) {
      Type t=tmp.getType();
      if (t==null || !(t instanceof PrimitiveType) || (((PrimitiveType)t).sort!=Sort.Boolean)){
        Abort("loop exit guard is not a boolean");
      }      
    }
  }

}
