// nashorn usage:
//   /usr/jdk/jdk1.8.0_25/bin/jjs -J-Xbootclasspath/p:lib/nashorn.jar -cp classes:lib/asm-debug-all-5.0.3.jar jsjs.js -- jsjs.js
//
// jsjs usage:
//   /usr/jdk/jdk1.9.0/bin/java -Xbootclasspath/p:lib/nashorn.jar -cp .:classes:lib/asm-debug-all-5.0.3.jar jsjs jsjs.js

// Java classes used
var String = Java.type("java.lang.String");
var StringBuilder = Java.type("java.lang.StringBuilder");
var System = Java.type("java.lang.System");
var PrintWriter = Java.type("java.io.PrintWriter");
var List = Java.type("java.util.List");
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var AtomicInteger = Java.type("java.util.concurrent.atomic.AtomicInteger");

// bootstrap
var Hack = Java.type("com.github.forax.jsjs.Hack");

var CompilationUnitTreeImpl = Java.type("jdk.nashorn.api.tree.CompilationUnitTreeImpl");
var FunctionDeclarationTreeImpl = Java.type("jdk.nashorn.api.tree.FunctionDeclarationTreeImpl");
var BlockTreeImpl = Java.type("jdk.nashorn.api.tree.BlockTreeImpl");
var ExpressionStatementTreeImpl = Java.type("jdk.nashorn.api.tree.ExpressionStatementTreeImpl");
var AssignmentTreeImpl = Java.type("jdk.nashorn.api.tree.AssignmentTreeImpl");
var FunctionCallTreeImpl = Java.type("jdk.nashorn.api.tree.FunctionCallTreeImpl");
var MemberSelectTreeImpl = Java.type("jdk.nashorn.api.tree.MemberSelectTreeImpl");
var IdentifierTreeImpl = Java.type("jdk.nashorn.api.tree.IdentifierTreeImpl");
var ObjectLiteralTreeImpl = Java.type("jdk.nashorn.api.tree.ObjectLiteralTreeImpl");
var VariableTreeImpl = Java.type("jdk.nashorn.api.tree.VariableTreeImpl");
var ForInLoopTreeImpl = Java.type("jdk.nashorn.api.tree.ForInLoopTreeImpl");
var ArrayAccessTreeImpl = Java.type("jdk.nashorn.api.tree.ArrayAccessTreeImpl");
var NewTreeImpl = Java.type("jdk.nashorn.api.tree.NewTreeImpl");
var LiteralTreeImpl = Java.type("jdk.nashorn.api.tree.LiteralTreeImpl");
var ConditionalExpressionTreeImpl = Java.type("jdk.nashorn.api.tree.ConditionalExpressionTreeImpl");
var BinaryTreeImpl = Java.type("jdk.nashorn.api.tree.BinaryTreeImpl");
var FunctionExpressionTreeImpl = Java.type("jdk.nashorn.api.tree.FunctionExpressionTreeImpl");
var ReturnTreeImpl = Java.type("jdk.nashorn.api.tree.ReturnTreeImpl");
var IfTreeImpl = Java.type("jdk.nashorn.api.tree.IfTreeImpl");
var ThrowTreeImpl = Java.type("jdk.nashorn.api.tree.ThrowTreeImpl");
var InstanceOfTreeImpl = Java.type("jdk.nashorn.api.tree.InstanceOfTreeImpl");

var Handle = Java.type("org.objectweb.asm.Handle");
var Label = Java.type("org.objectweb.asm.Label");
var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var ClassReader = Java.type("org.objectweb.asm.ClassReader");
var ClassWriter = Java.type("org.objectweb.asm.ClassWriter");
var CheckClassAdapter = Java.type("org.objectweb.asm.util.CheckClassAdapter");
var TraceClassVisitor = Java.type("org.objectweb.asm.util.TraceClassVisitor");

var MethodType = Java.type("java.lang.invoke.MethodType");

// do we have a script file passed? if not, use current script
//var sourceName = this.rguments.length == 0? __FILE__ : this.arguments[0];
var sourceName = this.arguments[0];

// parse AST
var ast = Hack.parseAST(sourceName);

var className = sourceName.substring(0, sourceName.length() - 3);  // remove '.js'
var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
//var writer = new ClassWriter(0); // DEBUG
//var cv = new TraceClassVisitor(writer, new PrintWriter(System.out)); // DEBUG
var cv = writer;

var internalClassName = Hack.toInternalClassName(className);
cv.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER, internalClassName, null, "java/lang/Object", null);
cv.visitSource("script", null);


// --- line support

function line(tree) {
  return ast.getLineMap().getLineNumber(tree.getStartPosition())|0;
}

// --- visitor

function Visitor() {
	this.map = new Object();
}
Visitor.prototype.when = function(type, fun) {
	this.map[type.class.toString()] = fun;
	return this;
};
Visitor.prototype.call = function(receiver, env) {
	if (receiver == undefined) {
		throw "receiver is undefined !";
	}
	var fun = this.map[receiver.getClass().toString()];
	if (fun == undefined) {
		throw "no mapping for " + receiver.getClass();
	}
	return fun(receiver, this, env);
};

// --- free vars env

function FreeVarEnv(parent, locals, freevars) {
	this.parent = parent;
	this.locals = locals;
	this.freevars = freevars;
}
FreeVarEnv.prototype.addLocal = function(name) {
	if (this.locals[name] == undefined) {
	    this.locals[name] = name;
	}
};
FreeVarEnv.prototype.isLocal = function(name) {
	if (this.locals[name] != undefined) {
	    return true;
	}
	if (this.parent == null) {
		return false;
	}
	return this.parent.isLocal(name);
};
FreeVarEnv.prototype.addFreeVar = function(name) {
	if (this.isLocal(name) == false) {
		if (this.freevars[name] == undefined) {
		    this.freevars[name] = name;
		}
	}
};


// --- free vars visitor

var freevarVisitor = new Visitor();
freevarVisitor
  .when(CompilationUnitTreeImpl, function(compilationUnit, visitor, env) {
	for each(var element in compilationUnit.getSourceElements()) {
		visitor.call(element, env);
	}
  })
  .when(FunctionDeclarationTreeImpl, function(functionDeclaration, visitor, env) {
	// empty
  })
  .when(BlockTreeImpl, function(block, visitor, env) {
	var newEnv = new FreeVarEnv(env, new Object(), env.freevars);
	for each(var statement in block.getStatements()) {
		visitor.call(statement, newEnv);
	}
  })
  .when(ExpressionStatementTreeImpl, function(exprStatement, visitor, env) {
	visitor.call(exprStatement.getExpression(), env);
  })
  .when(AssignmentTreeImpl, function(assignment, visitor, env) {
	var variable = assignment.getVariable();
	if (variable instanceof IdentifierTreeImpl) { // local var re-assignment
          throw "variable " + variable.getName() + " can only be initialized once !";	
	}  
	if (variable instanceof MemberSelectTreeImpl) {  // field assignment
	    visitor.call(variable.getExpression(), env);
	    visitor.call(assignment.getExpression(), env);
	} else {  // array assignment
	    visitor.call(variable.getExpression(), env);
	    visitor.call(variable.getIndex(), env);
	    visitor.call(assignment.getExpression(), env);
	}
  })
  .when(FunctionCallTreeImpl, function(funcall, visitor, env) {
	var args = funcall.getArguments();
	var selector = funcall.getFunctionSelect();
	if (selector instanceof IdentifierTreeImpl) {  // function call
	  for each(var arg in args) {
	    visitor.call(arg, env);	
	  }
	  // it's maybe a local variable access
	  env.addFreeVar(selector.getName());
	} else {  // method call or any expression + funcall
	  visitor.call(selector, env);
	  for each(var arg in args) {
	    visitor.call(arg, env);	
	  }
	}
  })
  .when(MemberSelectTreeImpl, function(memberSelect, visitor, env) {
	visitor.call(memberSelect.getExpression(), env);
  })
  .when(IdentifierTreeImpl, function(identifier, visitor, env) {
	// local variable access
	env.addFreeVar(identifier.getName());
  })
  .when(ObjectLiteralTreeImpl, function(objectLiteral, visitor, env) {
	var properties = objectLiteral.getProperties();
	for each(var property in properties) {
		visitor.call(property.getValue(), env);
	}
  })
  .when(VariableTreeImpl, function(variable, visitor, env) {
	var name = variable.getName();
	var initializer = variable.getInitializer();
	if (initializer != null) {
		visitor.call(initializer, env);
	}
	env.addLocal(name);
  })
  .when(ForInLoopTreeImpl, function(forInLoop, visitor, env) {
	visitor.call(forInLoop.getExpression(), env);
	var newEnv = new FreeVarEnv(env, new Object(), env.freevars);
	newEnv.addLocal(forInLoop.getVariable().getName());
	visitor.call(forInLoop.getStatement(), newEnv);
  })
  .when(ArrayAccessTreeImpl, function(arrayAccess, visitor, env) {
	visitor.call(arrayAccess.getExpression(), env);
	visitor.call(arrayAccess.getIndex(), env);
  })
  .when(NewTreeImpl, function(newTree, visitor, env) {
	var init = newTree.getConstructorExpression();
	if (init instanceof FunctionCallTreeImpl) {
	  var args = init.getArguments();
	  var selector = init.getFunctionSelect();
          if (selector instanceof IdentifierTreeImpl) {
	    for each(var arg in args) {
	      visitor.call(arg, env);	
	    }
	    
	    // it's maybe a local variable access
    	    env.addFreeVar(selector.getName());
	    return;
	  }
	}
	throw "unsupported new operation";
  })
  .when(LiteralTreeImpl, function(literal, visitor, env) {
	// do nothing
  })
  .when(ConditionalExpressionTreeImpl, function(conditional, visitor, env) {
	visitor.call(conditional.getCondition(), env);
	visitor.call(conditional.getTrueExpression(), env);
	visitor.call(conditional.getFalseExpression(), env);
  })
  .when(BinaryTreeImpl, function(binary, visitor, env) {
	visitor.call(binary.getLeftOperand(), env);
	visitor.call(binary.getRightOperand(), env);
  })
  .when(FunctionExpressionTreeImpl, function(lambda, visitor, env) {
	var newEnv = new FreeVarEnv(null, new Object(), env.freevars);
	for each(var parameter in lambda.getParameters()) {
		newEnv.addLocal(parameter.getName());
	}
	visitor.call(lambda.getBody(), newEnv);
  })
  .when(ReturnTreeImpl, function(returnTree, visitor, env) {
	var expr = returnTree.getExpression();
	if (expr != null) {
	  visitor.call(expr, env);
	}
  })
  .when(IfTreeImpl, function(ifTree, visitor, env) {
	visitor.call(ifTree.getCondition(), env);
	visitor.call(ifTree.getThenStatement(), env);
	var elseStatement = ifTree.getElseStatement();
	if (elseStatement != null) {
	  visitor.call(elseStatement, env);
	}
  })
  .when(ThrowTreeImpl, function(throwTree, visitor, env) {
	visitor.call(throwTree.getExpression(), env);
  })
  .when(InstanceOfTreeImpl, function(instanceofTree, visitor, env) {
	visitor.call(instanceofTree.getExpression(), env);
	visitor.call(instanceofTree.getType(), env);
  });


// --- generator env

function Env(parent, mv) {
	this.map = new Object();
	this.parent = parent;
	this.size = (parent == null)? new AtomicInteger(0): new AtomicInteger(parent.size.get());
	this.mv = mv;
}
Env.prototype.lookup = function(name) {
	var slot = this.map[name];
	if (slot != undefined) {
		return slot;
	}
	if (this.parent == null) {
		return undefined;
	}
	return this.parent.lookup(name);
};
Env.prototype.register = function(name) {
	var slot = this.newSlot();
	this.map[name] = slot;
	return slot;
};
Env.prototype.newSlot = function() {
	return this.size.getAndIncrement();
};
Env.prototype.desc = function(parameterCount) {
	return MethodType.genericMethodType(parameterCount).toMethodDescriptorString();
};



// --- generator visitor

var BSM = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_METH = new Handle(Opcodes.H_INVOKESTATIC,
    "com/github/forax/jsjs/RT",
	"bsm_meth",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_CONST = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_const",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;");
var BSM_GLOBAL = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_global",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_DEF = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_def",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;ILjava/lang/String;)Ljava/lang/invoke/CallSite;");
var BSM_GET = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_get",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_SET = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_set",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_ARRAY_GET = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_array_get",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_ARRAY_SET = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_array_set",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_LAMBDA_CALL = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_lambda_call",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_LAMBDA_NEW = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_lambda_new",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_FUN_NEW = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_fun_new",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
var BSM_OBJECT_LITERAL = new Handle(Opcodes.H_INVOKESTATIC,
	"com/github/forax/jsjs/RT",
	"bsm_object_literal",
	"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;");


var lambdaId = new AtomicInteger(0);

function genFun(fun, isLocal, visitor, env) {
  var freeVarEnv = new FreeVarEnv(null, new Object(), new Object());
  freeVarEnv.addLocal("this");
  var parameters = fun.getParameters();
  for each(var parameter in parameters) {
    freeVarEnv.addLocal(parameter.getName());
  }
  freevarVisitor.call(fun.getBody(), freeVarEnv);
  var freevars = Object.keys(freeVarEnv.freevars);
  freevars.sort();
  //print("freevars " + freevars);
	
  // put all free vars on stack if available
  // delay error reporting, do the check when seeing a local var access
  var captureds = new Object();
  for each(var freevar in freevars) {
    var slot = env.lookup(freevar);
    if (slot != undefined) {
      captureds[freevar] = freevar;
      env.mv.visitVarInsn(Opcodes.ALOAD, slot);
    }
  }
  var capturedvars = Object.keys(captureds);
  capturedvars.sort();
  //print("capturedvars " + capturedvars);
	
  var name = isLocal? "lambda$" + lambdaId.getAndIncrement(): fun.getName();
  var mv = cv.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, name, env.desc((capturedvars.length|0) + 1 + parameters.size()), null, null); 
  
  //print("generate " + name);
  
  var newEnv = new Env(null, mv);
  for each(var capturedvar in capturedvars) {
    newEnv.register(capturedvar);
  }
  newEnv.register("this");
  for each(var parameter in fun.getParameters()) {
    newEnv.register(parameter.getName());
  }
	
  mv.visitCode();
  visitor.call(fun.getBody(), newEnv);
  mv.visitInsn(Opcodes.ACONST_NULL);
  mv.visitInsn(Opcodes.ARETURN);
  mv.visitMaxs(0, 0);
  //mv.visitMaxs(10, 10);
  mv.visitEnd();
  
  env.mv.visitInvokeDynamicInsn(name, env.desc(capturedvars.length), BSM_DEF, (capturedvars.length|0) + 1 + parameters.size(), isLocal? "local": "global");
}

var gen = new Visitor();
gen.when(CompilationUnitTreeImpl, function(compilationUnit, visitor, env) {
	for each(var element in compilationUnit.getSourceElements()) {
          var lineLabel = new Label();
	  env.mv.visitLabel(lineLabel);
	  env.mv.visitLineNumber(line(element), lineLabel);
	  visitor.call(element, env);
	}
  })
  .when(FunctionDeclarationTreeImpl, function(functionDeclaration, visitor, env) {
	//print("function " + functionDeclaration.getName());
	genFun(functionDeclaration, false, visitor, env);
	env.mv.visitInsn(Opcodes.POP);
  })
  .when(BlockTreeImpl, function(block, visitor, env) {
	var newEnv = new Env(env, env.mv);
	for each(var statement in block.getStatements()) {
	  var lineLabel = new Label();
	  env.mv.visitLabel(lineLabel);
	  env.mv.visitLineNumber(line(statement), lineLabel);
	  visitor.call(statement, newEnv);
	}
  })
  .when(ExpressionStatementTreeImpl, function(exprStatement, visitor, env) {
	visitor.call(exprStatement.getExpression(), env);
	env.mv.visitInsn(Opcodes.POP);
  })
  .when(AssignmentTreeImpl, function(assignment, visitor, env) {
	var variable = assignment.getVariable();
	if (variable instanceof IdentifierTreeImpl) { // local var re-assignment
          throw "variable " + variable.getName() + " can only be initialized once !";	
	} 
	if (variable instanceof MemberSelectTreeImpl) {  // field assignment
	  visitor.call(variable.getExpression(), env);
	  visitor.call(assignment.getExpression(), env);
	  env.mv.visitInsn(Opcodes.DUP_X1);
	  env.mv.visitInvokeDynamicInsn(variable.getIdentifier(), "(Ljava/lang/Object;Ljava/lang/Object;)V", BSM_SET);
	} else {  // array assignment
	  visitor.call(variable.getExpression(), env);
	  visitor.call(variable.getIndex(), env);
	  visitor.call(assignment.getExpression(), env);
	  env.mv.visitInsn(Opcodes.DUP_X2);
	  env.mv.visitInvokeDynamicInsn("set", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", BSM_ARRAY_SET);
	}
  })
  .when(FunctionCallTreeImpl, function(funcall, visitor, env) {
	var args = funcall.getArguments();
	var selector = funcall.getFunctionSelect();
	if (selector instanceof IdentifierTreeImpl) {  // function call or lambda call
	  var name = selector.getName();
	  var slot = env.lookup(name);
	  if (slot != undefined) {  // it's lambda call
	    env.mv.visitVarInsn(Opcodes.ALOAD, slot);
	  }
	  env.mv.visitInsn(Opcodes.ACONST_NULL);  // FIXME this should be undefined, not null
	  for each(var arg in args) {
	    visitor.call(arg, env);	
	  }
	  if (slot != undefined) {
	    env.mv.visitInvokeDynamicInsn(name, env.desc(2 + args.size()), BSM_LAMBDA_CALL);
	  } else {
	    env.mv.visitInvokeDynamicInsn("call:" + name, env.desc(1 + args.size()), BSM);
	  }
	} else {  // method call
	  visitor.call(selector.getExpression(), env);
	  for each(var arg in args) {
	    visitor.call(arg, env);	
	  }
	  env.mv.visitInvokeDynamicInsn(selector.getIdentifier(), env.desc(1 + args.size()), BSM_METH);
	}
  })
  .when(MemberSelectTreeImpl, function(memberSelect, visitor, env) {
	// expression.identifier
	visitor.call(memberSelect.getExpression(), env);
	env.mv.visitInvokeDynamicInsn(memberSelect.getIdentifier(), "(Ljava/lang/Object;)Ljava/lang/Object;", BSM_GET);
  })
  .when(IdentifierTreeImpl, function(identifier, visitor, env) {
	// local variable access
	var name = identifier.getName();
	var slot = env.lookup(name);
	if (slot != undefined) {
          //print("lookup local variable " + name + " " + slot);
          env.mv.visitVarInsn(Opcodes.ALOAD, slot);
	} else {
	  env.mv.visitInvokeDynamicInsn(name, "()Ljava/lang/Object;", BSM_GLOBAL);
	}
  })
  .when(ObjectLiteralTreeImpl, function(objectLiteral, visitor, env) {
	var properties = objectLiteral.getProperties();
	var keys = new StringBuilder();
	for each(var property in properties) {
	  visitor.call(property.getValue(), env);
	  if (keys.length() != 0) {
	    Hack.appendString(keys, ":");
	  }
	  var key = property.getKey();
	  if (key instanceof IdentifierTreeImpl) {
	      Hack.appendString(keys, key.getName());
	  } else {
	      throw "property name must be an identifier";
	  }
	}
	env.mv.visitInvokeDynamicInsn("new", env.desc(properties.size()), BSM_OBJECT_LITERAL, keys.toString());
  })
  .when(VariableTreeImpl, function(variable, visitor, env) {
	var name = variable.getName();
	var initializer = variable.getInitializer();
	if (initializer != null) {
	  visitor.call(initializer, env);
	  var slot = env.register(name);
	  env.mv.visitVarInsn(Opcodes.ASTORE, slot);  
	} else {
	  //throw "variable " + name + " should be initialized at line " + line(variable);
	  print("WEIRD: variable " + name + " should be initialized at line " + line(variable));
	}
  })
  .when(ForInLoopTreeImpl, function(forInLoop, visitor, env) {
	visitor.call(forInLoop.getExpression(), env);
	env.mv.visitInvokeDynamicInsn("iterator", "(Ljava/lang/Object;)Ljava/lang/Object;", BSM_METH);
	var itSlot = env.newSlot();
	env.mv.visitVarInsn(Opcodes.ASTORE, itSlot);
	
	var endLabel = new Label();
	var conditionLabel = new Label();
	
	var newEnv = new Env(env, env.mv);
	var variableSlot = newEnv.register(forInLoop.getVariable().getName());
	
	env.mv.visitLabel(conditionLabel);
	env.mv.visitVarInsn(Opcodes.ALOAD, itSlot);
	env.mv.visitInvokeDynamicInsn("hasNext", "(Ljava/lang/Object;)Z", BSM_METH);
	env.mv.visitJumpInsn(Opcodes.IFEQ, endLabel);
	
	env.mv.visitVarInsn(Opcodes.ALOAD, itSlot);
	env.mv.visitInvokeDynamicInsn("next", "(Ljava/lang/Object;)Ljava/lang/Object;", BSM_METH);
	env.mv.visitVarInsn(Opcodes.ASTORE, variableSlot);
	
	visitor.call(forInLoop.getStatement(), newEnv);
	
	env.mv.visitJumpInsn(Opcodes.GOTO, conditionLabel);
	env.mv.visitLabel(endLabel);
  })
  .when(ArrayAccessTreeImpl, function(arrayAccess, visitor, env) {
	visitor.call(arrayAccess.getExpression(), env);
	visitor.call(arrayAccess.getIndex(), env);
	env.mv.visitInvokeDynamicInsn("get", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", BSM_ARRAY_GET);
  })
  .when(NewTreeImpl, function(newTree, visitor, env) {
	var init = newTree.getConstructorExpression();
	if (init instanceof FunctionCallTreeImpl) {
	  var args = init.getArguments();
	  var selector = init.getFunctionSelect();
          if (selector instanceof IdentifierTreeImpl) {  // function call
    	    var name = selector.getName();
    	    var slot = env.lookup(name);
    	    if (slot != undefined) {  // it's a lambda creation
    	      env.mv.visitVarInsn(Opcodes.ALOAD, slot);
    	    }  
	    for each(var arg in args) {
	      visitor.call(arg, env);	
	    }
	    if (slot != undefined) {
	      env.mv.visitInvokeDynamicInsn(selector.getName(), env.desc(1 + args.size()), BSM_LAMBDA_NEW);	
	    } else {
	      env.mv.visitInvokeDynamicInsn(selector.getName(), env.desc(args.size()), BSM_FUN_NEW);
	    }
	    return;
	  }
	}
	throw "unsupported new operation";
  })
  .when(LiteralTreeImpl, function(literal, visitor, env) {
	//print("literal " + literal.getValue());
	var value = literal.getValue();
	if (value == null) {
	  env.mv.visitInsn(Opcodes.ACONST_NULL);
	  return;
	}
	if (value instanceof String) {
	  env.mv.visitLdcInsn(value);
	  return;
	}
	env.mv.visitInvokeDynamicInsn(value.toString(), "()Ljava/lang/Object;", BSM_CONST, value);
  })
  .when(ConditionalExpressionTreeImpl, function(conditional, visitor, env) {
	var elseLabel = new Label(); 
        var endLabel = new Label();
	visitor.call(conditional.getCondition(), env);
	env.mv.visitInvokeDynamicInsn("truth:", "(Ljava/lang/Object;)Z", BSM);
	env.mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
	visitor.call(conditional.getTrueExpression(), env);
	env.mv.visitJumpInsn(Opcodes.GOTO, endLabel);
	env.mv.visitLabel(elseLabel);
	visitor.call(conditional.getFalseExpression(), env);
	env.mv.visitLabel(endLabel);
  })
  .when(BinaryTreeImpl, function(binary, visitor, env) {
	env.mv.visitInsn(Opcodes.ACONST_NULL);  //FIXME this should be undefined
	visitor.call(binary.getLeftOperand(), env);
	visitor.call(binary.getRightOperand(), env);
	env.mv.visitInvokeDynamicInsn("binary:" + binary.getKind().name().toLowerCase(), env.desc(3), BSM); //FIXME
  })
  .when(FunctionExpressionTreeImpl, function(lambda, visitor, env) {
	//print("lambda " + lambda.getName() + " at " + line(lambda));
	genFun(lambda, true, visitor, env);
  })
  .when(ReturnTreeImpl, function(returnTree, visitor, env) {
	var expr = returnTree.getExpression();
	if (expr != null) {
	  visitor.call(expr, env);
	} else {
	  env.mv.visitInsn(Opcodes.ACONST_NULL);
	}
	env.mv.visitInsn(Opcodes.ARETURN);
  })
  .when(IfTreeImpl, function(ifTree, visitor, env) {
	var elseLabel = new Label(); 
	var endLabel = new Label();
	visitor.call(ifTree.getCondition(), env);
	env.mv.visitInvokeDynamicInsn("truth:", "(Ljava/lang/Object;)Z", BSM);
	env.mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
	visitor.call(ifTree.getThenStatement(), env);
	var elseStatement = ifTree.getElseStatement();
	if (elseStatement != null) {
	  env.mv.visitJumpInsn(Opcodes.GOTO, endLabel);
	  env.mv.visitLabel(elseLabel);
	  visitor.call(elseStatement, env);
	  env.mv.visitLabel(endLabel);
	} else {
	  env.mv.visitLabel(elseLabel);
	}
  })
  .when(ThrowTreeImpl, function(throwTree, visitor, env) {
	env.mv.visitInsn(Opcodes.ACONST_NULL);
	visitor.call(throwTree.getExpression(), env);
	env.mv.visitInvokeDynamicInsn("asthrowable:asthrowable", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Throwable;", BSM);
	env.mv.visitInsn(Opcodes.ATHROW);
  })
  .when(InstanceOfTreeImpl, function(instanceofTree, visitor, env) {
	env.mv.visitInsn(Opcodes.ACONST_NULL);
	visitor.call(instanceofTree.getExpression(), env);
	visitor.call(instanceofTree.getType(), env);
	env.mv.visitInvokeDynamicInsn("instanceof:isinstance", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", BSM);
  });


function dumpBytecode(array) {
  Hack.dumpBytecode(array);
}


var mv = cv.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "__script__", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
var env = new Env(null, mv);
env.register("this");
mv.visitCode();
gen.call(ast, env);
mv.visitInsn(Opcodes.ACONST_NULL);
mv.visitInsn(Opcodes.ARETURN);
mv.visitMaxs(0, 0);
mv.visitEnd();

var main = cv.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
main.visitInvokeDynamicInsn("global", "()Ljava/lang/Object;", BSM_GLOBAL);
main.visitVarInsn(Opcodes.ASTORE, 1);
main.visitVarInsn(Opcodes.ALOAD, 1);
main.visitInsn(Opcodes.ACONST_NULL);
main.visitVarInsn(Opcodes.ALOAD, 0);
main.visitInvokeDynamicInsn("call:__wrapJavaArray__", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", BSM);
main.visitInvokeDynamicInsn("arguments", "(Ljava/lang/Object;Ljava/lang/Object;)V", BSM_SET);
main.visitVarInsn(Opcodes.ALOAD, 1);
main.visitMethodInsn(Opcodes.INVOKESTATIC, internalClassName, "__script__", "(Ljava/lang/Object;)Ljava/lang/Object;");
main.visitInsn(Opcodes.POP);
main.visitInsn(Opcodes.RETURN);
main.visitMaxs(0, 0);
main.visitEnd();

cv.visitEnd();
var array = cv.toByteArray();

// DEBUG
dumpBytecode(array);

Hack.write(Paths.get("", className + ".class"), array);

