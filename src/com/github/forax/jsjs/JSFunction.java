package com.github.forax.jsjs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.forax.jsjs.RT.MOP;

public final class JSFunction extends JSObject {
  private final String name;
  private final Function<MethodType, MethodHandle> functionFactory;
  private final BiFunction<JSFunction, MOP, MethodHandle> constructorFactory;
  
  JSFunction(String name, Function<MethodType, MethodHandle> functionFactory, BiFunction<JSFunction, MOP, MethodHandle> constructorFactory) {
    this.name = name;
    this.functionFactory = functionFactory;
    this.constructorFactory = constructorFactory;
  }
  
  MethodHandle getFunctionTarget(MethodType methodType) {
    return check(functionFactory.apply(methodType), methodType);
  }
  
  MethodHandle getConstructorTarget(MOP mop) {
    return check(constructorFactory.apply(this, mop), mop.type());
  }
  
  private static MethodHandle check(MethodHandle mh, MethodType type) {
    if (!mh.type().equals(type)) {
      throw new Error("wrong signature, ask for " + type + " but found " + mh.type());
    }
    return mh;
  }

  @Override
  public String toString() {
    //return name + " " + super.toString();  //DEBUG
    return name;
  }
  
  static final MethodHandle GET_FUNCTION_TARGET;
  static {
    try {
      GET_FUNCTION_TARGET = MethodHandles.lookup().findVirtual(JSFunction.class, "getFunctionTarget",
          MethodType.methodType(MethodHandle.class, MethodType.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }
}
