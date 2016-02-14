package kalang.compiler;

import kalang.util.AstUtil;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import jast.ast.AssignExpr;
import jast.ast.AstNode;
import jast.ast.AstVisitor;
import jast.ast.BinaryExpr;
import jast.ast.CastExpr;
import jast.ast.CatchStmt;
import jast.ast.ClassExpr;
import jast.ast.ClassNode;
import jast.ast.ConstExpr;
import jast.ast.ElementExpr;
import jast.ast.ExprNode;
import jast.ast.FieldExpr;
import jast.ast.IfStmt;
import jast.ast.InvocationExpr;
import jast.ast.KeyExpr;
import jast.ast.LoopStmt;
import jast.ast.MethodNode;
import jast.ast.MultiStmtExpr;
import jast.ast.NewArrayExpr;
import jast.ast.NewExpr;
import jast.ast.ParameterExpr;
import jast.ast.ReturnStmt;
import jast.ast.Statement;
import jast.ast.TryStmt;
import jast.ast.UnaryExpr;
import jast.ast.VarDeclStmt;
import jast.ast.VarExpr;
import jast.ast.VarObject;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import kalang.core.ClassType;
import kalang.core.PrimitiveType;
import kalang.core.Type;
import kalang.core.Types;
import static kalang.util.AstUtil.getMethodsByName;
import static kalang.util.AstUtil.getParameterTypes;
import static kalang.util.AstUtil.matchTypes;

public class SemanticAnalyzer extends AstVisitor<Type> {
   
    HashMap<String, VarObject> fields;

    AstLoader astLoader;

    ClassNode clazz;


    //AstMetaParser astParser;

    MethodNode method;

    List<String> methodDeclared;

    boolean returned;

    private AstSemanticErrorReporter err;

    private AstSemanticErrorHandler errHandler;

    private Stack<Map<Type,AstNode>> exceptionStack = new Stack();

    private Map<AstNode, Type> types = new HashMap<>();

    SemanticAnalyzer(AstLoader astLoader) {
        this.astLoader = astLoader;
        //this.typeSystem = new TypeSystem(astLoader);
        //this.astParser = new AstUtil();
        errHandler = new AstSemanticErrorHandler() {
            @Override
            public void handleAstSemanticError(AstSemanticError error) {
                System.err.println(error.toString());
            }
        };
    }
    
    private Type getDefaultType(){
        return Types.ROOT_TYPE;
    }

    public void setAstSemanticErrorHandler(AstSemanticErrorHandler handler) {
        errHandler = handler;
    }

    private ExprNode cast(ExprNode expr, Type from, Type to, AstNode node) {
            expr = from.cast(to, expr);
        if (expr == null) {
            err.failedToCast(node, from.getName(), to.getName());
            return null;
        }
        return expr;
    }

    public ClassNode loadAst(String name, AstNode node) {
        ClassNode ast = this.astLoader.getAst(name);
        if (ast == null) {
            err.classNotFound(node, name);
            return null;
        }
        return ast;
    }

    public void check(ClassNode clz) {
        err = new AstSemanticErrorReporter(clz, new AstSemanticErrorReporter.AstSemanticReporterCallback() {
            @Override
            public void handleAstSemanticError(AstSemanticError error) {
                errHandler.handleAstSemanticError(error);
            }
        });
        this.fields = new HashMap();
        this.methodDeclared = new LinkedList();
        for (VarObject f : clz.fields) {
            this.fields.put(f.name, f);
        }
        this.clazz = clz;
        visit(clazz);
        if (clazz.interfaces.size() > 0) {
            for (ClassNode itf : clazz.interfaces) {
                //assert itf != null;
                ClassNode itfNode = itf;
                if (itfNode == null) {
                    continue;
                }
                List<MethodNode> unImps = AstUtil.getUnimplementedMethod(clazz, itfNode);
                if (unImps.size() > 0) {
                    err.notImplementedMethods(clazz, itfNode, unImps);
                    //fail(CE"unimplemented method:${mStr}",clazz);
                }
            }
        }
    }

    @Override
    public Type visit(AstNode node) {
        if (node instanceof Statement && returned) {
            err.fail("unabled to reach statement", AstSemanticError.LACKS_OF_STATEMENT, node);
            return null;
        }
        Object ret = super.visit(node);
        if (ret instanceof Type) {
            types.put(node,(Type) ret);
            return  (Type) ret;
        }
        return null;
    }

    @Override
    public Type visitCastExpr(CastExpr node) {
        return node.type;
    }

    @Override
    public Type visitAssignExpr(AssignExpr node) {
        Type ft = visit(node.from);
        Type tt = visit(node.to);
        if(!requireNoneVoid(ft, node)) return getDefaultType();
        if(!requireNoneVoid(tt, node)) return getDefaultType();
        if(!ft.equals(tt)){
            if(!checkCastable(ft, tt, node)){
                return getDefaultType();
            }
            node.from = cast(node.from, ft, tt, node);
        }
        return tt;
    }
    
    private PrimitiveType getPrimitiveType(Type t){
        if(t instanceof PrimitiveType){
            return (PrimitiveType) t;
        }else if(t instanceof ClassType){
            return Types.getPrimitiveType((ClassType)t);
        }else{
            return null;
        }
    }

    private Type getMathType(Type t1, Type t2, String op) {
        PrimitiveType pt1= getPrimitiveType(t1);
        PrimitiveType pt2 = getPrimitiveType(t2);
        if(pt1==null){
            throw new IllegalArgumentException(t1.getName());
        }
        if(pt2==null) throw new IllegalArgumentException(t2.getName());
        String ret = MathType.getType(pt1.getName(), pt2.getName(), op);
        return Types.getPrimitiveType(ret);
    }

    /*  private ExprNode checkAndCastToBoolean(ExprNode expr){
     String type = visit(expr);
     if(!castSys.isBoolean(type)){
     def be = new BinaryExpr();
     be.expr1 = expr;
     be.operation = "!="
     def zero = new ConstExpr();
     if(castSys.isNumber(type)){
     zero.type = INT_CLASS_NAME;
     zero.value = 0;
     }else{
     zero.type = NULL_CLASS;
     }
     be.expr2 = zero;
     return be;
     }
     //TODO cast string to boolean
     return expr;
     }*/
    @Override
    public Type visitBinaryExpr(BinaryExpr node) {
        Type t1 = visit(node.expr1);
        Type t2 = visit(node.expr2);
        String op = node.operation;
        Type t;
        switch (op) {
            case "==":
                if (Types.isNumber(t1)) {
                    if (!Types.isNumber(t2)) {
                        err.failedToCast(node, t2.getName(), Types.INT_CLASS_TYPE.getName());
                        return getDefaultType();
                    }
                    //fail("Number required",node);
                } else {
                    //TODO pass anything.may be Object needed?
                }
                t = Types.BOOLEAN_TYPE;
                break;
            case "+":
                if(isNumber(t1) && isNumber(t2)){
                    t = getMathType(t1, t2, op);
                }else{
                    node.expr1 = cast(node.expr1,t1,Types.STRING_CLASS_TYPE, node);
                    node.expr2 = cast(node.expr2, t2, Types.STRING_CLASS_TYPE, node);
                    t =Types.STRING_CLASS_TYPE;
                }
                break;
            case "-":
            case "*":
            case "/":
            case "%":
                if(!requireNumber(node, t1)) return getDefaultType();
                if(!requireNumber(node, t2)) return getDefaultType();
                t = (getMathType(t1, t2, op));
                break;
            case ">=":
            case "<=":
            case ">":
            case "<":
                if(!requireNumber(node, t1)) return getDefaultType();
                if(!requireNumber(node, t2)) return getDefaultType();
                t = Types.BOOLEAN_TYPE;
                break;
            case "&&":
            case "||":
                if(!requireBoolean(node, t1)) return getDefaultType();
                if(!requireBoolean(node, t2)) return getDefaultType();
                t = Types.BOOLEAN_TYPE;
                break;
            case "&":
            case "|":
            case "^":
                if(!requireNumber(node, t1)) return getDefaultType();
                if(!requireNumber(node, t2)) return getDefaultType();
                t = getPrimitiveType(Types.getHigherType(t1, t2));
                break;
            default:
                err.fail("unsupport operation:" + op, AstSemanticError.UNSUPPORTED, node);
                return getDefaultType();
        }
        return t;
    }

    @Override
    public Type visitConstExpr(ConstExpr node) {
        return node.type;
    }

    @Override
    public Type visitElementExpr(ElementExpr node) {
        Type type = visit(node.target);
        //if(!type.endsWith("[]")){
        if(!requireArray(node, type)) return getDefaultType();
        //fail("Array type required",node)
        //}
        return type.getComponentType();
    }

    @Override
    public Type visitFieldExpr(FieldExpr node) {
        if (null == node.target) {
            VarObject field = fields.get(node.fieldName);
            if (isStatic(method.modifier)) {
                if(!requireStatic(field.modifier, node)) return getDefaultType();
            }
            return field.type;
        }
        Type t = visit(node.target);
        
//        ClassNode target = ((ClassType) t).getClassNode();
//        if (target == null) {
//            err.fieldNotFound(node, t.getName());
//            return getDefaultType();
//        }
        String fname = node.fieldName;
        //VarObject field = this.astParser.getField(target, fname);
        VarObject field = t.getField(fname);
        if (field == null) {
            err.fieldNotFound(node, fname);
            return getDefaultType();
        }
        if (node.target instanceof ClassExpr) {
            if(!requireStatic(field.modifier, node)) return getDefaultType();
        }
        return field.type;
    }

    @Override
    public Type visitInvocationExpr(InvocationExpr node) {
        List<Type> types = visitAll(node.arguments);
        ClassType target = node.target != null ?(ClassType) visit(node.target) : Types.getClassType(this.clazz);
        String methodName = node.methodName;
        ClassNode ast = target.getClassNode();
        if (ast == null) {
            return Types.ROOT_TYPE;
        }
        boolean matched = applyMethod(ast,node,types.toArray(new Type[0]));
        if (!matched) {
            return getDefaultType();
        }
        boolean inStaticMethod = node.target == null && Modifier.isStatic(this.method.modifier);
        boolean isClassExpr = node.target instanceof ClassExpr;
        if (inStaticMethod || isClassExpr) {
            if(!requireStatic(method.modifier, node)) return getDefaultType();
        }
        castInvocationParams(node, method);
        //TODO here could be optim
        for(Type et:method.exceptionTypes){
            this.exceptionStack.peek().put(et,node);
        }
        return method.type;
    }

    @Override
    public Type visitParameterExpr(ParameterExpr node) {
        return node.parameter.type;
    }

    @Override
    public Type visitUnaryExpr(UnaryExpr node) {
        String preOp = node.preOperation;
        Type et = visit(node.expr);
        if (preOp != null && preOp.equals("!")) {
            if(!requireBoolean(node, et)) return getDefaultType();
        } else {
            //TODO unary type check
            //if(!requireNumber(node,et)) return getDefaultType()
        }
        return et;
    }

    @Override
    public Type visitVarExpr(VarExpr node) {
        //Integer vid = node.varId;
        //def declStmt = this.varDeclStmts.get(vid);
        //declStmt.type
        return node.var.type;
    }

    private void caughException(Type type, AstNode node) {
        Map<Type, AstNode> exceptions = this.exceptionStack.peek();
        Type[] exTypes = exceptions.keySet().toArray(new Type[0]);
        for (Type e : exTypes) {
                if (
                        e.equals(type)
                        || e.isSubclassTypeOf(type)
                        ) {
                    exceptions.remove(e);
                }
        }
    }

    @Override
    public Type visitTryStmt(TryStmt node) {
        this.exceptionStack.add(new HashMap<>());
        visit(node.execStmt);
        visitAll(node.catchStmts);
        Map<Type, AstNode> uncaught = this.exceptionStack.pop();
        if (uncaught.size() > 0) {
            this.exceptionStack.peek().putAll(uncaught);
        }
        visit(node.finallyStmt);
        return null;
    }

    @Override
    public Type visitCatchStmt(CatchStmt node) {
        this.caughException(node.catchVarDecl.var.type, node);
        return null;
    }

    @Override
    public Type visitClassExpr(ClassExpr node) {
        return Types.getClassType(loadAst(node.name, node));
    }

    @Override
    public Type visitNewExpr(NewExpr node) {
        return node.type;
    }

    @Override
    public Type visitVarDeclStmt(VarDeclStmt node) {
        VarObject var = node.var;
        Type retType = null;
        if(var.initExpr!=null){
            retType = visit(var.initExpr);
            if(!requireNoneVoid(retType, node)) return getDefaultType();
        }
        if (var.type == null) {
            if(retType!=null){
                var.type = retType;
            }else{
                var.type = Types.ROOT_TYPE;
            }
        }
        if(retType!=null){
            checkCastable(retType, var.type, node);
        }
        return null;
    }

    @Override
    public Type visitNewArrayExpr(NewArrayExpr node) {
        return Types.getArrayType(node.type);
    }

    @Override
    public Type visitIfStmt(IfStmt node) {
        //node.conditionExpr = this.checkAndCastToBoolean(node.conditionExpr);
        if(!requireBoolean(node, visit(node.conditionExpr))) return getDefaultType();
        if (node.trueBody != null) {
            visit(node.trueBody);
        }
        boolean returnedOld = returned;
        returned = false;
        if (node.falseBody != null) {
            visit(node.falseBody);
        } else {
            returned = false;
        }
        returned = returnedOld && returned;
        return null;
    }

    @Override
    public Type visitLoopStmt(LoopStmt node) {
        if (node.preConditionExpr != null) {
            requireBoolean(node.preConditionExpr);
        }
        if (node.initStmts != null) {
            visitAll(node.initStmts);
        }
        if (node.loopBody != null) {
            visit(node.loopBody);
        }
        if (node.postConditionExpr != null) {
            requireBoolean(node.postConditionExpr);
        }
        return null;
    }

    @Override
    public Type visitMethodNode(MethodNode node) {
        String mStr = AstUtil.getMethodDescriptor(node, this.clazz.name);
        if (methodDeclared.contains(mStr)) {
            err.unsupported("declare method duplicately", node);
            return getDefaultType();
        }
        methodDeclared.add(mStr);
        method = node;
        returned = false;
        this.exceptionStack.push(new HashMap<>());
        super.visitMethodNode(node);
        if (method.exceptionTypes != null) {
            for (Type e : method.exceptionTypes) {
                this.caughException(e, node);
            }
        }
        Map<Type, AstNode> uncaught = this.exceptionStack.pop();
        for(Type k:uncaught.keySet()){
            err.uncaughtException(uncaught.get(k),k.getName());
        }
        boolean needReturn;
        if(isSpecialMethod(node)){
            needReturn = isSpecialMethodNeedReturn(node);
        }else{
            needReturn = (
                    node.type != null
                    && !node.type.equals(Types.VOID_TYPE)
                    );
        }
       
        if (node.body != null && needReturn && !returned) {
            err.fail("Missing return statement in method:" + mStr, AstSemanticError.LACKS_OF_STATEMENT, node);
        }
        return null;
    }

    @Override
    public Type visitReturnStmt(ReturnStmt node) {
        Type retType = method.type;
        //this.checkCastable(visit(node.expr),retType,node)
        if (node.expr != null) {
            Type exType = visit(node.expr);
            node.expr = this.cast(node.expr, exType, retType, node);
        }
        returned = true;
        return null;
    }

    boolean requireNumber(AstNode node, Type t) {
        if (!isNumber(t)) {
            err.failedToCast(node, t.getName(),Types.INT_CLASS_TYPE.getName() );
            return false;
        }
        return true;
    }

    boolean requireBoolean(AstNode node) {
        Type t = visit(node);
        return requireBoolean(node, t);
    }

    boolean requireBoolean(AstNode node, Type t) {
        if (!Types.isBoolean(t)) {
            err.failedToCast(node, t.getName(), Types.BOOLEAN_CLASS_TYPE.getName());
            return false;
        }
        return true;
    }

    boolean isArray(Type t) {
        return t.isArray();
    }

    boolean requireArray(AstNode node, Type t) {
        if (!isArray(t)) {
            err.failedToCast(node, t.getName(), "array");
            return false;
        }
        return true;
    }

    public boolean isStatic(Integer modifier) {
        return modifier != null ? Modifier.isStatic(modifier) : false;
    }

    boolean requireStatic(Integer modifier, AstNode node) {
        boolean isStatic = isStatic(modifier);
        if (!isStatic) {
            err.fail("couldn't refer non-static member in static context", AstSemanticError.UNSUPPORTED, node);
            return false;
        }
        return true;
    }

    boolean requireNoneVoid(Type type, AstNode node) {
        if (type == null
                || type == Types.VOID_TYPE
                || type == Types.VOID_CLASS_TYPE
                ){
            err.unsupported("use void type as value", node);
            return false;
        }
        return true;
    }

    private void castInvocationParams(InvocationExpr expr, MethodNode method) {
        List<Type> mTypes = AstUtil.getParameterTypes(method);
        int i = 0;
        for ( Type mt : mTypes) {
            Type pt = visit(expr.arguments.get(i));
                expr.arguments.set(i,
                        pt.cast(mt, expr.arguments.get(i))
                        //this.typeSystem.cast(expr.arguments.get(i), pt, mt)
                );
            i++;
        }
    }

    @Override
    public Type visitKeyExpr(KeyExpr node) {
        String key = node.key;
        if (key.equals("this")) {
            return Types.getClassType(this.clazz);
        } else if (key.equals("super")) {
            if (clazz.parent == null) {
                return getDefaultType();
            }
            return Types.getClassType(clazz.parent);
        } else {
            System.err.println("Unknown key:" + key);
            return getDefaultType();
        }
    }

    @Override
    public Type visitMultiStmtExpr(MultiStmtExpr node) {
        visitAll(node.stmts);
        return visit(node.reference);
    }
    
    public Type getType(AstNode node){
        return types.get(node);
    }

    public Map<AstNode, Type> getTypes() {
        return types;
    }

    private boolean isNumber(Type t1) {
        return Types.isNumber(t1);
    }

    public boolean isSpecialMethod(MethodNode node) {
        return node.name.startsWith("<");
    }

    public boolean isSpecialMethodNeedReturn(MethodNode node) {
        if(node.name.equals("<init>")) return false;
        else{
            System.err.println("unknown method:" + node.name);
            return false;
        }
    }

    public AstLoader getAstLoader() {
        return astLoader;
    }

    private boolean checkCastable(Type ft, Type tt,AstNode ast) {
        if(ft.equals(tt)) return true;
            if(!ft.isCastableTo(tt)){
                err.failedToCast(ast, ft.getName(), tt.getName());
                return false;
            }
        return true;
    }
    
    public static List<ExprNode[]> matchMethodByType(ExprNode[] args,MethodNode[] methods, Type[] types) {
        List<ExprNode[]> list = new LinkedList();
        for (MethodNode m : methods) {
            Type[] mTypes = getParameterTypes(m).toArray(new Type[0]);
            ExprNode[] matchedArgs = matchTypes(args,types, mTypes);
            if(matchedArgs!=null) list.add(args);
        }
        return list;
    }    
    
    
    public boolean applyMethod(ClassNode cls, InvocationExpr invocationExpr, Type[] types) {
        String methodName = invocationExpr.methodName;
        MethodNode md = AstUtil.getMethod(cls, methodName, types);
        if (md != null) {
            return false;
        } else {
            MethodNode[] methods = getMethodsByName(cls, methodName);
            ExprNode[] args = invocationExpr.arguments.toArray(new ExprNode[0]);
            int matchedCount = 0;
            ExprNode[] matchedParams=null;
            for (MethodNode m : methods) {
                Type[] mTypes = AstUtil.getParameterTypes(m).toArray(new Type[0]);
                ExprNode[] mp = AstUtil.matchTypes(args, types, mTypes);
                if (mp != null) {
                    matchedCount++;
                    matchedParams = mp;
                }
            }
            if (matchedCount < 1) {
                err.methodNotFound(invocationExpr, cls.name, methodName, Arrays.asList(types));
                return false;
            } else if (matchedCount > 1) {
                err.fail("the method " + methodName + " is ambiguous", AstSemanticError.METHOD_NOT_FOUND, invocationExpr);
                return false;
            }
            invocationExpr.arguments.clear();
            invocationExpr.arguments.addAll(Arrays.asList(matchedParams));
            return true;
        }
    }

}