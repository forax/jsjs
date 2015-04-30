package com.github.forax.jsjs;

import static com.github.forax.jsjs.JSObject.setPrototypeOf;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.exactInvoker;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.genericMethodType;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import com.github.forax.jsjs.JSObject.HiddenClass;

public class RT {
  private static <T> ClassValue<T> classValue(Function<Class<?>, T> initializer) {
    return new ClassValue<T>() {
      @Override
      protected T computeValue(Class<?> type) {
        return initializer.apply(type);
      }
    };
  }
  
  private static JSFunction createJavaType(Class<?> clazz) {
    return new JSFunction("JavaClass " +  clazz.getName(), __ -> { throw new Error("no function defined !"); }, (function, mop) -> {
      MethodType type = mop.type();
      for(Constructor<?> constructor: clazz.getConstructors()) {
        if (constructor.getParameterCount() == type.parameterCount()) {
          try {
            return publicLookup().unreflectConstructor(constructor).asType(type);
          } catch (IllegalAccessException e) {
            throw new AssertionError(e);
          }
        }
      }
      throw new Error("no constructor found for class" + clazz);
    });
  }
  
  private static Predicate<Method> selectInstanceMethod(boolean instanceMethod) {
    return method -> !Modifier.isStatic(method.getModifiers()) == instanceMethod;
  }
  
  private static void populateWithMethods(JSObject metadata, Class<?> type, Predicate<Method> filter, UnaryOperator<MethodHandle> mapper) {
    HashMap<String, ArrayList<Method>> map = new HashMap<>();
    for(Method method: type.getMethods()) {
      if (!filter.test(method)) {
        continue;
      }
      method.setAccessible(true);  // FIXME HACK
      String name = method.getName();
      map.computeIfAbsent(name, key -> new ArrayList<>(2)).add(method);
    }
    
    map.forEach((name, methods) -> {
      metadata.set(name, new JSFunction(type.getName() + "::" + name, methodType -> {
        //FIXME, implement overload resolution instead of choosing the first overload 
        Optional<Method> methodOpt = methods.stream().filter(m -> 1 + m.getParameterCount() == methodType.parameterCount()).findFirst();
        Method method;
        if (!methodOpt.isPresent()) {
          if (methods.size() != 1) {
            throw new Error("no method matching " + methodType + " among " + methods);
          }
          method = methods.get(0);
        } else {
          method = methodOpt.get();
        }
        MethodHandle target;
        try {
          target = mapper.apply(publicLookup().unreflect(method));
        } catch (IllegalAccessException e) {
          throw new AssertionError(e);
        }
        if (method.isVarArgs()) {
          MethodType targetType = target.type();
          int targetTypeCount = targetType.parameterCount();
          target = target.asCollector(targetType.parameterType(targetTypeCount - 1), methodType.parameterCount() - targetTypeCount + 1); //FIXME if array is empty
        }
        return target.asType(methodType);
      }, (__1, __2) -> { throw new Error("no constructor defined !"); }));
    });
  }
  
  private static void populateWithStaticFields(JSObject metadata, Class<?> type) {
    for(Field field: type.getFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      try {
        metadata.set(field.getName(), field.get(null));
      } catch (IllegalArgumentException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }
  }
  
  static final ClassValue<JSFunction> JAVA_TYPE_MAP = classValue(type -> {
        JSObject prototype = new JSObject();
        populateWithMethods(prototype, type, selectInstanceMethod(true), UnaryOperator.identity());
    
        JSFunction function = createJavaType(type);
        //setPrototypeOf(function, prototype);  // FIXME
        function.set(JSObject.PROTOTYPE_KEY, prototype);
        populateWithStaticFields(function, type);
        populateWithMethods(function, type, selectInstanceMethod(false), mh -> dropArguments(mh, 0, Object.class));
        function.set("class", type);  // interrop with Java
        return function;
      });
  
  private static final ThreadLocal<JSObject> GLOBAL_MAP_TL_INIT = new ThreadLocal<>();
  static final ClassValue<JSObject> GLOBAL_MAP = classValue(type -> {
        if (type.getName().equals("boot.boot")) {
          return GLOBAL_MAP_TL_INIT.get();   // ugly hack, global is already initialized, just return it
        }
    
        JSObject global = new JSObject();
        global.set("global", global);
        
        JSFunction builtins = JAVA_TYPE_MAP.get(Builtins.class);
        setPrototypeOf(global, builtins);
        
        // add Object, Function and Java
        MethodHandle nop = dropArguments(constant(Object.class, null), 0, Object.class);
        JSFunction object = createJSFunction("Object", methodType -> nop);
        populateWithMethods(object, JSObject.class, selectInstanceMethod(false), mh -> dropArguments(mh, 0, Object.class));
        global.set("Object", object);
        
        JSFunction function = createJSFunction("Function", methodType -> nop); // FIXME
        setPrototypeOf(function, new JSObject(object));
        global.set("Function", function);
        
        JSFunction array = createJSFunction("Array", methodType -> nop); // FIXME
        JSObject arrayPrototype = new JSObject(object);
        setPrototypeOf(array, arrayPrototype);
        global.set("Array", array);
        
        JSFunction java = createJSFunction("Java", methodType -> nop);  // FIXME
        JSObject javaPrototype = new JSObject();
        javaPrototype.set("type", builtins.get("javaTypeOf"));
        setPrototypeOf(java, javaPrototype);
        global.set("Java", java);        
        
        //FIXME, revisit
        global.set("undefined", null);
        
        // initialize global object by running boot/boot.js 
        // warning, this will ask for the global object even if it is not fully initialized
        GLOBAL_MAP_TL_INIT.set(global);
        try {
          mh(publicLookup(), Lookup::findStatic, Class.forName("boot.boot"), "__script__", methodType(Object.class, Object.class)).invoke(global);
        } catch(RuntimeException | Error e) {
          throw e;
        } catch(Throwable e) {
          throw new Error(e);
        } finally {
          GLOBAL_MAP_TL_INIT.set(null);
        }
        return global;
      });
  
  private static JSFunction createJSFunction(String name, Function<MethodType, MethodHandle> functionFactory) {
    JSFunction newFunction = new JSFunction(name, functionFactory, (function, mop) -> {
      //System.out.println("create constructor " + mop.type() + " for function " + function);
      
      MethodHandle prototypeGetter = function.getGetterIfConstant(JSObject.PROTOTYPE_KEY, mop::invalidate).asType(methodType(JSObject.class));
      MethodHandle newObject = MethodHandles.filterReturnValue(prototypeGetter, JSOBJECT_NEW);
      
      MethodType methodType = mop.type();
      MethodHandle mh = dropArguments(identity(JSObject.class), 1, methodType.parameterList());
      
      MethodHandle constructor = functionFactory.apply(methodType);
      MethodType constructorType = methodType.changeReturnType(void.class).insertParameterTypes(0, JSObject.class);
      MethodHandle mh2 = foldArguments(mh, constructor.asType(constructorType));
      return foldArguments(mh2, newObject).asType(methodType);
    });
    newFunction.set(JSObject.PROTOTYPE_KEY, new JSObject());
    //FIXME also set function "constructor"
    return newFunction;
  }
  
  // called by a method handle too
  private static Object def(JSObject global, boolean isLocal, String name, MethodHandle impl, Object... capturedValues) {
    MethodHandle target = (capturedValues.length == 0)? impl: insertArguments(impl, 0, capturedValues);
    JSFunction function = createJSFunction(name + target.type(), __ -> target);
    setPrototypeOf(function, (JSObject)global.get("Function"));
    if (!isLocal) {
      global.set(name, function);
    }
    return function;
  }
  
  public static CallSite bsm_def(Lookup lookup, String name, MethodType methodType, int parameterCount, String kind) throws NoSuchMethodException, IllegalAccessException {
    Class<?> lookupClass = lookup.lookupClass();
    MethodHandle target = lookup.findStatic(lookupClass, name, genericMethodType(parameterCount));
    JSObject global = GLOBAL_MAP.get(lookupClass);
    boolean isLocal = kind.equals("local");
    if (methodType.parameterCount() == 0) {
      return new ConstantCallSite(constant(Object.class, def(global, isLocal, name, target)));
    }
    return new ConstantCallSite(insertArguments(DEF, 0, global, isLocal, name, target).asCollector(Object[].class, methodType.parameterCount()));
  }
  
  // called by a method handle
  private static Object[] newObjectArray(int size) {
    return new Object[size];
  }
  
  public static CallSite bsm_object_literal(Lookup lookup, String name, MethodType methodType, String properties) {
    String[] keys = (properties.isEmpty())? new String[0]: properties.split(":");
    MethodHandle target = JSObject.getLiteralObject(keys);
    return new ConstantCallSite(target.asType(methodType));
  }
  
  public static CallSite bsm_const(Lookup lookup, String name, MethodType methodType, Object constant) {
    switch(name) {  // booleans have no encoding in the constant pool, use the string representation instead
    case "true":
    case "false":
      constant = Boolean.valueOf(name);
      break;
    default:
      // do nothing, the constant is already encoded in the constant pool
    }
    return new ConstantCallSite(constant(Object.class, constant));
  }
  
  public static CallSite bsm_global(Lookup lookup, String name, MethodType methodType) {
    Object globalVar = GLOBAL_MAP.get(lookup.lookupClass()).get(name);
    if (globalVar == null && !name.equals("undefined")) {  //FIXME remove "undefined"
      throw new Error("no variable " + name + " found");
    }
    return new ConstantCallSite(constant(Object.class, globalVar));
  }
  
  public static CallSite bsm(Lookup lookup, String name, MethodType methodType) {
    int index = name.indexOf(':');
    String protocol = name.substring(0, index);
    String op = name.substring(index + 1);
    
    MethodHandle target;
    switch(protocol) {
    case "call":         // function call
    case "binary":       // binary op
    case "instanceof":   // instanceof op
    case "asthrowable":  // wrap into an error if needed
      JSFunction function = (JSFunction)GLOBAL_MAP.get(lookup.lookupClass()).get(op);
      if (function == null) {  // add an error message if the object is not a function
        throw new Error("fail to do " + protocol + " on " + op);
      }
      target = function.getFunctionTarget(genericMethodType(methodType.parameterCount())).asType(methodType);
      break;
    case "truth":  // give me the truth
      target = identity(Object.class).asType(methodType(boolean.class, Object.class));
      break;
    default:
      throw new Error("unknown protocol " + protocol + ":" + op);
    }
    return new ConstantCallSite(target.asType(methodType));
  }
  
  public static CallSite bsm_get(Lookup lookup, String name, MethodType methodType) {
    return accessor(methodType, (object, invalidate) -> object.getGetter(name, invalidate));
  }
  public static CallSite bsm_set(Lookup lookup, String name, MethodType methodType) {
    return accessor(methodType, (object, invalidate) -> object.getSetter(name, invalidate));
  }
  public static CallSite bsm_array_get(Lookup lookup, String name, MethodType methodType) {
    return accessor(methodType, (object, invalidate) -> object.getArrayGetter());
  }
  public static CallSite bsm_array_set(Lookup lookup, String name, MethodType methodType) {
    return accessor(methodType, (object, invalidate) -> object.getArraySetter());
  }
  
  interface Accessor {
    MethodHandle apply(JSObject object, Function<MethodHandle, MethodHandle> invalidate);
  }
  
  private static CallSite accessor(MethodType methodType, Accessor accessor) {
    return new PolymorphicInliningCacheCallSite(methodType, (receiver, mop) -> {
      if (!(receiver instanceof JSObject)) {
        throw new Error("can not " + ((methodType.returnType() == void.class)? "set": "get") + " a value of a non javascript object (" + receiver + ")");
      }
      JSObject object = (JSObject)receiver;
      MethodHandle target = accessor.apply(object, mop::invalidate);
      return new CacheEntry(RT.TYPE_CHECK.bindTo(object.getClass()), target.asType(methodType));
    });
  }
  
  public static CallSite bsm_lambda_call(Lookup lookup, String name, MethodType methodType) {
    return new PolymorphicInliningCacheCallSite(methodType, (receiver, mop) -> {
      if (!(receiver instanceof JSFunction)) {
        throw new Error(name + " is not a function");
      }
      JSFunction function = (JSFunction)receiver;
      MethodHandle target = dropArguments(function.getFunctionTarget(mop.type().dropParameterTypes(0, 1)), 0, Object.class);
      return new CacheEntry(RT.POINTER_CHECK.bindTo(receiver), target);
    });
  }
  
  public static CallSite bsm_fun_new(Lookup lookup, String name, MethodType methodType) {
    JSFunction function = (JSFunction)GLOBAL_MAP.get(lookup.lookupClass()).get(name);
    if (function == null) {  // FIXME, add an error message if the value is not a function
      throw new Error("fail to find function " + name);
    }
    return new InvalidableInliningCacheCallSite(methodType, mop -> {
      return function.getConstructorTarget(mop);
    });
  }
  
  public static CallSite bsm_lambda_new(Lookup lookup, String name, MethodType methodType) {
    return new PolymorphicInliningCacheCallSite(methodType, (receiver, mop) -> {
      if (!(receiver instanceof JSFunction)) {
        throw new Error(name + " is not a function");
      }
      JSFunction function = (JSFunction)receiver;
      MethodHandle target =  dropArguments(function.getConstructorTarget(mop.withType(mop.type().dropParameterTypes(0, 1))), 0, Object.class);
      return new CacheEntry(RT.POINTER_CHECK.bindTo(receiver), target);
    });
  }
  
  public static CallSite bsm_meth(Lookup lookup, String name, MethodType methodType) {
    //System.out.println("bsm_meth " + name+methodType);
    return new PolymorphicInliningCacheCallSite(methodType, (receiver, mop) -> {
      //System.out.println(" found receiver " + receiver);
      //System.out.println(" found receiver class " + receiver.getClass());
      
      Class<?> type = receiver.getClass();
      if (receiver instanceof JSObject) {
        JSObject object = (JSObject)receiver;
        MethodHandle getter = object.getGetter(name, mop::invalidate).asType(methodType(Object.class, Object.class));
        MethodHandle asMH = new PolymorphicInliningCacheCallSite(methodType(MethodHandle.class, Object.class), (fun, mop2) -> {
          if (!(fun instanceof JSFunction)) {
            throw new Error("value (" + fun + ") of key " + name + " is not a function !");
          }
          
          if (mop2.counter() > 0) {  // one shot
            // FIXME, the error message will be a CCE :(
            MethodHandle fallback = insertArguments(JSFunction.GET_FUNCTION_TARGET, 1, mop.type()).asType(methodType(MethodHandle.class, Object.class));
            return new CacheEntry(null /* clear cache */, fallback);  
          }
          JSFunction function = (JSFunction)fun;
          return new CacheEntry(
              POINTER_CHECK.bindTo(function),
              dropArguments(constant(MethodHandle.class, function.getFunctionTarget(mop.type())), 0, Object.class));
        }).dynamicInvoker();
        
        MethodHandle target = foldReceiverInvoker(mop.type(), filterReturnValue(getter, asMH));
        return new CacheEntry(RT.TYPE_CHECK.bindTo(type), target);
      }
      
      
      // Java interop
      JSObject metadata = (JSObject)JAVA_TYPE_MAP.get(type).get(JSObject.PROTOTYPE_KEY);
      JSFunction function = (JSFunction)metadata.get(name);
      if (function == null) {
        throw new Error("no function " + name + " found");
      }
      
      //System.out.println(" found function " + function);
      MethodHandle target =  function.getFunctionTarget(mop.type());
      return new CacheEntry(RT.TYPE_CHECK.bindTo(type), target);
    });
  }
  
  static final class CacheEntry {
    final MethodHandle test;
    final MethodHandle target;
    
    CacheEntry(MethodHandle test, MethodHandle target) {
      this.test = test;
      this.target = target;
    }
  }
  
  static interface MOP {
    MethodType type();
    int counter();
    MethodHandle invalidate(MethodHandle target);
    
    default MOP withType(MethodType methodType) {
      return new MOP() {
        @Override
        public MethodType type() {
          return methodType;
        }
        @Override
        public int counter() {
          return MOP.this.counter();
        }
        @Override
        public MethodHandle invalidate(MethodHandle target) {
          return MOP.this.invalidate(target);
        }
      };
    }
  }
  
  /*private*/ static final class InvalidableInliningCacheCallSite extends MutableCallSite implements MOP {
    private final Function<MOP, MethodHandle> entryFinder;
    private int counter;
    
    InvalidableInliningCacheCallSite(MethodType methodType, Function<MOP, MethodHandle> entryFinder) {
      super(methodType);
      this.entryFinder = entryFinder;
      setTarget(entryFinder.apply(this));
    }
    
    @Override
    public int counter() {
      return counter;
    }
    
    @Override
    public MethodHandle invalidate(MethodHandle target) {
      counter++;
      return foldArguments(target, insertArguments(SET_TARGET, 0, this, foldArguments(exactInvoker(type()), insertArguments(APPLY, 0, entryFinder, this))));
    }
    
    private static final MethodHandle APPLY = mh(publicLookup(), Lookup::findVirtual, Function.class, "apply", methodType(Object.class, Object.class))
        .asType(methodType(MethodHandle.class, Function.class, MOP.class));
  }
  
  static MethodHandle foldReceiverInvoker(MethodType methodType, MethodHandle mh) {
    return foldArguments(
        exactInvoker(methodType),
        dropArguments(mh, 1, genericMethodType(methodType.parameterCount() - 1).parameterList()));
  }
  
  /*private*/ static final class PolymorphicInliningCacheCallSite extends MutableCallSite implements MOP {
    private final BiFunction<Object, MOP, CacheEntry> entryFinder;
    private int counter;
    
    PolymorphicInliningCacheCallSite(MethodType methodType, BiFunction<Object, MOP, CacheEntry> entryFinder) {
      super(methodType);
      this.entryFinder = entryFinder;
      MethodHandle fallback = FALLBACK.bindTo(this).asType(methodType(MethodHandle.class, methodType.parameterType(0)));
      setTarget(foldReceiverInvoker(methodType, fallback));
    }
    
    @Override
    public int counter() {
      return counter;
    }
    
    @Override
    public MethodHandle invalidate(MethodHandle target) {
      return foldArguments(target, insertArguments(SET_TARGET, 0, this, foldReceiverInvoker(type(), FALLBACK.bindTo(this))));
    }
    
    private static MethodHandle fallback(PolymorphicInliningCacheCallSite callsite, Object receiver) {
      CacheEntry entry = callsite.entryFinder.apply(receiver, callsite);
      callsite.counter++;
      MethodHandle target = entry.target;
      MethodHandle test = entry.test;
      if (test != null) {  // install a new guard ?
        callsite.setTarget(guardWithTest(test, target, callsite.getTarget()));
      } else {             // stop a use a more general algorithm
        callsite.setTarget(target);
      }
      return target;
    }
    
    private static final MethodHandle FALLBACK = mh(lookup(), Lookup::findStatic, PolymorphicInliningCacheCallSite.class, "fallback",
        methodType(MethodHandle.class, PolymorphicInliningCacheCallSite.class, Object.class));
  }
  
  private static boolean typeCheck(Class<?> type, Object receiver) {
    return receiver.getClass() == type;
  }
  
  private static boolean pointerCheck(Object instance, Object receiver) {
    return instance == receiver;
  }
  
  
  interface MHFactory<T> {
    MethodHandle create(Lookup lookup, Class<?> declaringClass, String name, T type) throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException;
  }
  static <T> MethodHandle mh(Lookup lookup, MHFactory<T> factory, Class<?> declaringClass, String name, T type) {
    try {
      return factory.create(lookup, declaringClass, name, type);
    } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }
  
  private static final MethodHandle JSOBJECT_NEW, DEF;
  static final MethodHandle TYPE_CHECK, POINTER_CHECK, SET_TARGET, NEW_OBJECT_ARRAY, JSOBJECT_LITERAL_NEW;
  static {
    Lookup lookup = MethodHandles.lookup();
    MHFactory<MethodType> findStatic = Lookup::findStatic;
    JSOBJECT_NEW = mh(lookup, findStatic, JSObject.class, "newJSObject", methodType(JSObject.class, JSObject.class));
    NEW_OBJECT_ARRAY = mh(lookup, findStatic, RT.class, "newObjectArray", methodType(Object[].class, int.class));
    JSOBJECT_LITERAL_NEW = mh(lookup, findStatic, JSObject.class, "newJSLiteralObject", methodType(JSObject.class, HiddenClass.class, JSObject.class, Object[].class));
    DEF = mh(lookup, findStatic, RT.class, "def", methodType(Object.class, JSObject.class, boolean.class, String.class, MethodHandle.class, Object[].class));
    TYPE_CHECK = mh(lookup, findStatic, RT.class, "typeCheck", methodType(boolean.class, Class.class, Object.class));
    POINTER_CHECK = mh(lookup, findStatic, RT.class, "pointerCheck", methodType(boolean.class, Object.class, Object.class));
    SET_TARGET = mh(lookup, Lookup::findVirtual, MutableCallSite.class, "setTarget", methodType(void.class, MethodHandle.class));
  }
}
