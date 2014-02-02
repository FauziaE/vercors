package vct.antlr4.parser;


import vct.util.Syntax;
import static vct.col.ast.StandardOperator.*;
import static vct.col.ast.PrimitiveType.Sort.*;

/**
 * Defines the syntax of common types and operations of  
 * the Program Verification Language (PVL).
 * 
 * @see Syntax
 * 
 */
public class PVLSyntax {

  private static Syntax syntax;
  
  public static Syntax get(){
    if(syntax==null){
      syntax=new Syntax();

/*
      syntax.addInfix(SubType,"<:",90);
      syntax.addInfix(SuperType,":>",90);
      syntax.addInfix(Implies,"==>",30);
      syntax.addInfix(IFF,"<==>",30);
      syntax.addLeftFix(Wand,"-*",30);
      syntax.addFunction(Perm,"Perm");
      syntax.addFunction(Head,"head");
      syntax.addFunction(Tail,"tail");
      syntax.addFunction(Value,"Value");
      syntax.addFunction(PointsTo,"PointsTo");
      syntax.addFunction(ArrayPerm,"ArrayPerm");
      syntax.addFunction(Old,"\\old");
      syntax.addOperator(Size,999,"|","|");
      syntax.addOperator(Nil,999,"nil<",">");
      syntax.addOperator(Subscript,145,"","[","]"); // TODO: check if relative order to Select is OK!
      syntax.addOperator(Cast,145,"((",")",")");
      syntax.addLeftFix(Append,"+++",110);
    
      // Java Operators  Precedence
      // 14 postfix  expr++ expr--
      syntax.addPostfix(PostIncr,"++",140);
      syntax.addPostfix(PostDecr,"--",140);
      // 13 unary   ++expr --expr +expr -expr ~ !
      syntax.addPrefix(BitNot, "~", 130);
      syntax.addPrefix(Not, "!", 130);
      syntax.addPrefix(UMinus, "-", 130);
      syntax.addPrefix(UPlus, "+", 130);
      syntax.addPrefix(PreIncr, "++", 130);
      syntax.addPrefix(PreIncr, "--", 130);
*/
      // 12 multiplicative  * / %
      syntax.addLeftFix(Mult,"mul",120);
      syntax.addLeftFix(Div,"div",120);
      syntax.addLeftFix(Mod,"mod",120);
      // 11 additive  + -
      syntax.addLeftFix(Plus,"+",110);
      syntax.addLeftFix(Minus,"-",110);
/*
      // 10 shift   << >> >>>
      syntax.addInfix(LeftShift,"<<", 100);
      syntax.addInfix(RightShift,">>", 100);
      syntax.addInfix(UnsignedRightShift,">>", 100);
      //  9 relational  < > <= >= instanceof
      syntax.addInfix(LT,"<",90);
      syntax.addInfix(LTE,"<=",90);
      syntax.addInfix(GT,">",90);
      syntax.addInfix(GTE,">=",90);
      syntax.addInfix(Instance," instanceof ",90);
      //  8 equality  == !=
      syntax.addInfix(EQ,"==",80);
      syntax.addInfix(NEQ,"!=",80);
      //  7 bitwise AND   &
      syntax.addInfix(BitAnd,"&",70);
      //  6 bitwise exclusive OR  ^
      syntax.addInfix(BitXor,"^",60);
      //  5 bitwise inclusive OR  |
      syntax.addInfix(BitOr,"|",50);
      //  4 logical AND   &&
      syntax.addLeftFix(And,"&&",40);
      syntax.addLeftFix(Star,"**",40);
      //  3 logical OR  ||
      syntax.addLeftFix(Or,"||",30);
      //  2 ternary   ? :
       * */
      syntax.addOperator(ITE,20,"","?",":","");
      syntax.addPrefix(BindOutput,"?",666);
      /*
      //  1 assignment  = += -= *= /= %= &= ^= |= <<= >>= >>>=
      syntax.addRightFix(Assign,"=",10);
      syntax.addRightFix(AddAssign,"+=",10);
      syntax.addRightFix(SubAssign,"-=",10);
      syntax.addRightFix(MulAssign,"*= ",10);
      syntax.addRightFix(DivAssign,"/=",10);
      syntax.addRightFix(RemAssign,"%=",10);
      syntax.addRightFix(AndAssign,"&=",10);
      syntax.addRightFix(XorAssign,"^=",10);
      syntax.addRightFix(OrAssign,"|=",10);
      syntax.addRightFix(ShlAssign,"<<=",10);
      syntax.addRightFix(ShrAssign,">>=",10);
      syntax.addRightFix(SShrAssign,">>>=",10);
      
      syntax.addPrimitiveType(Double,"double");
      syntax.addPrimitiveType(Integer,"int");
      syntax.addPrimitiveType(ZFraction,"zfrac");
      syntax.addPrimitiveType(Fraction,"frac");
      syntax.addPrimitiveType(Long,"long");
      syntax.addPrimitiveType(Void,"void");
      syntax.addPrimitiveType(Resource,"resource");
      syntax.addPrimitiveType(Boolean,"boolean");
      syntax.addPrimitiveType(Class,"classtype");
      syntax.addPrimitiveType(Char,"char");
      syntax.addPrimitiveType(Float,"float");
      */
      //syntax.addPrimitiveType(UInteger,"/*unsigned*/ int");
      //syntax.addPrimitiveType(ULong,"/*unsigned*/ long");
      //syntax.addPrimitiveType(UShort,"/*unsigned*/ short");
      //syntax.addPrimitiveType(Short,"short");
    }
    return syntax;
  } 
}
