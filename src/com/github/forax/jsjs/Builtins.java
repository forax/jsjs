package com.github.forax.jsjs;

import java.util.Objects;

//TODO, try to write most of them in JS
public class Builtins {
  // --- default JavaScript functions
  
  public static Object print(Object o) {
    System.out.println(o);
    return null;
  }
  
  // --- Java interrop
  
  public static Object javaTypeOf(String javaClassName) {
    try {
      return RT.JAVA_TYPE_MAP.get(Class.forName(javaClassName));
    } catch (ClassNotFoundException e) {
      throw new Error(e);
    }
  }
  
  
  // --- methods needed by boot/boot.js
  
  public static Object __keys__(Object object) {
    return ((JSObject)object).keys();
  }
  
  public static Object __createArray__(Object proto, Object javaArray) {
    return new JSArray((JSObject)proto, (Object[])javaArray);
  }
  public static Object __arrayIterator__(Object array) {
    return ((JSArray)array).iterator();
  }
  public static Object __arraySort__(Object array) {
    ((JSArray)array).sort();
    return array;
  }
  
  
  // ---
  
  public static Object plus(Object o1, Object o2) {
    if (o1 instanceof Integer && o2 instanceof Integer) {
      try {
        return Math.addExact((Integer)o1, (Integer)o2);
      } catch(ArithmeticException e) {
        return (double)(Integer)o1 + (double)(Integer)o2;
      }
    }
    if (o1 instanceof String || o2 instanceof String) {
      return String.valueOf(o1).concat(String.valueOf(o2));
    }
    return ((Number)o1).doubleValue() + ((Number)o2).doubleValue();
  }
  public static Object minus(Object o1, Object o2) {
    if (o1 instanceof Integer && o2 instanceof Integer) {
      try {
        return Math.subtractExact((Integer)o1, (Integer)o2);
      } catch(ArithmeticException e) {
        // do nothing
      }
    }
    return ((Number)o1).doubleValue() - ((Number)o2).doubleValue();
  }
  public static Object multiply(Object o1, Object o2) {
    if (o1 instanceof Integer && o2 instanceof Integer) {
      try {
        return Math.multiplyExact((Integer)o1, (Integer)o2);
      } catch(ArithmeticException e) {
        // do nothing
      }
    }
    return ((Number)o1).doubleValue() * ((Number)o2).doubleValue();
  }
  public static Object divide(Object o1, Object o2) {
    return ((Number)o1).doubleValue() / ((Number)o2).doubleValue();
  }
  public static Object remainder(Object o1, Object o2) {
    return ((Number)o1).doubleValue() % ((Number)o2).doubleValue();
  }
  public static Object or(Object o1, Object o2) {
    return ((Number)o1).intValue() | ((Number)o2).intValue();
  }
  public static Object and(Object o1, Object o2) {
    return ((Number)o1).intValue() & ((Number)o2).intValue();
  }
  public static Object left_shift(Object o1, Object o2) {
    return ((Number)o1).intValue() << ((Number)o2).intValue();
  }
  public static Object right_shift(Object o1, Object o2) {
    return ((Number)o1).intValue() >> ((Number)o2).intValue();
  }
  
  public static Object equal_to(Object o1, Object o2) {
    return Objects.equals(o1, o2);
  }
  public static Object not_equal_to(Object o1, Object o2) {
    return Objects.equals(o1, o2) == false;
  }
  
  @SuppressWarnings("unchecked") public static Object less_than(Object o1, Object o2) {
    return ((Comparable<Object>)o1).compareTo(o2) < 0;
  }
  @SuppressWarnings("unchecked") public static Object less_than_equal(Object o1, Object o2) {
    return ((Comparable<Object>)o1).compareTo(o2) <= 0;
  }
  @SuppressWarnings("unchecked") public static Object greater_than(Object o1, Object o2) {
    return ((Comparable<Object>)o1).compareTo(o2) > 0;
  }
  @SuppressWarnings("unchecked") public static Object greater_than_equal(Object o1, Object o2) {
    return ((Comparable<Object>)o1).compareTo(o2) >= 0;
  }
  
  public static Object asthrowable(Object value) {
    if (value instanceof Throwable) {
      return value;
    }
    return new Error(value.toString());
  }
  
  public static Object isinstance(Object value, Object type) {
    //FIXME, optimize
    if (value instanceof JSObject) {
      throw new Error("NYI");
    }
    
    Class<?> javaType = (Class<?>)((JSObject)type).get("class");
    return javaType.isAssignableFrom(value.getClass());
  }
}
