package com.github.forax.jsjs;

import static com.github.forax.jsjs.RT.mh;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class JSArray extends JSObject {
  private final Object[] array;
  
  JSArray(JSObject proto, int capacity) {
    this(proto, new Object[capacity]);
  }
  
  JSArray(JSObject proto, Object[] array) {
    super(proto);
    this.array = array;
    set(LENGTH_KEY, array.length);
  }
  
  Object get(int index) {
    return array[index];
  }
  
  void set(int index, Object value) {
    Objects.requireNonNull(value);
    if (array[index] != null) {
      throw new Error("array at index " + index + " is already defined");
    }
    array[index] = value;
  }
  
  // used by boot/boot.js
  final Iterator<Object> iterator() {
    Object[] array = this.array;
    int length = array.length;
    return new Iterator<Object>() {
      private int i;
      
      @Override
      public boolean hasNext() {
        return i < length;
      }
      @Override
      public Object next() {
        try {
          return array[i++];
        } catch(ArrayIndexOutOfBoundsException e) {
          throw new NoSuchElementException();
        }
      }
    };
  }
  
  final void sort() {
    Arrays.sort(array);
  }
  
  @Override
  final MethodHandle getArrayGetter() {
    return GET_INDEX.asType(methodType(Object.class, JSObject.class, Object.class));
  }
  
  @Override
  final MethodHandle getArraySetter() {
    return SET_INDEX.asType(methodType(void.class, JSObject.class, Object.class, Object.class));
  }
  
  private static final String LENGTH_KEY = "length";
  private static final MethodHandle GET_INDEX, SET_INDEX;
  static {
    Lookup lookup = lookup();
    GET_INDEX = mh(lookup, Lookup::findVirtual, JSArray.class, "get", methodType(Object.class, int.class));
    SET_INDEX = mh(lookup, Lookup::findVirtual, JSArray.class, "set", methodType(void.class, int.class, Object.class));
  }
}
