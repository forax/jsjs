package com.github.forax.jsjs;

import static com.github.forax.jsjs.RT.mh;
import static java.lang.invoke.MethodHandles.arrayElementGetter;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.SwitchPoint;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;

import com.github.forax.jsjs.RT.CacheEntry;
import com.github.forax.jsjs.RT.InvalidableInliningCacheCallSite;
import com.github.forax.jsjs.RT.PolymorphicInliningCacheCallSite;

public class JSObject {
  private HiddenClass hiddenClass;
  private /* stable */ JSObject proto;
  private SwitchPoint switchPoint;
  /* stable */ Object[] values;
  
  public JSObject(JSObject proto) {
    hiddenClass = HIDDEN_MAP_ROOT;
    if (proto != null) {
      setPrototypeOf(this, proto);
    }
    values = new Object[4];
  }
  
  public JSObject() {
    hiddenClass = HIDDEN_MAP_ROOT;
    values = new Object[4];
  }
  
  // called by a method handle
  static JSObject newJSObject(JSObject proto) {
    return new JSObject(proto);
  }
  
  final Object get(String key) {
    Objects.requireNonNull(key);
    if (PROTO_KEY.equals(key)) {
      return proto;
    }
    Integer slot = hiddenClass.slot(key);
    if (slot != null) {
      return values[slot];  
    }
    if (proto == null) {
      return null;
    }
    return slowGet(key, proto);
  }
  
  private static Object slowGet(String key, JSObject proto) {
    for(JSObject p = proto; p != null; p = p.proto) {
      Integer slot = p.hiddenClass.slot(key);
      if (slot != null) {
        return p.values[slot];  
      } 
    }
    return null;
  }
  
  final void set(String key, Object value) {
    Objects.requireNonNull(key);
    //Objects.requireNonNull(value);  //TODO revisit
    if (PROTO_KEY.equals(key)) {
      setPrototypeOf(this, (JSObject)value);
      return;
    }
    HiddenClass hiddenClass = this.hiddenClass;
    Object[] array = this.values;
    Integer slot = hiddenClass.slot(key);
    if (slot != null) {
      throw new Error(key + " already set");
    }
    
    this.hiddenClass = hiddenClass = hiddenClass.forward(key);
    if (hiddenClass.slotMap.size() > array.length) {
      array = this.values = Arrays.copyOf(array, array.length << 1);
    }
    int newSlot = hiddenClass.slot(key);
    array[newSlot] = value; 
    invalidate();  // all codes that use objects that defines the current object as prototype must be changed 
  }
  
  
  // needed by boot.js
  final Object[] keys() {
   return hiddenClass.slotMap.keySet().stream().filter(s -> !s.equals(PROTO_KEY)).toArray();
  }
  
  
  
  @Override
  public String toString() {  //DEBUG
    return "0x" + Integer.toHexString(hashCode()) + " of " + hiddenClass.toString();
  }
  
  // called by a method handle
  static JSObject getPrototypeOf(JSObject object) {
    return object.proto;
  }
  
  static void setPrototypeOf(JSObject object, JSObject proto) {
    if (object.proto != null) {
      throw new Error("prototype already set");
    }
    object.hiddenClass = object.hiddenClass.forwardProto();
    object.proto = proto;
    object.invalidate();
  }
  
  private void invalidate() {
    SwitchPoint switchPoint = this.switchPoint;
    if (this.switchPoint != null) {
      SwitchPoint.invalidateAll(new SwitchPoint[] { switchPoint });
    }
    this.switchPoint = null;
  }
  
  private SwitchPoint switchPoint() {
    SwitchPoint switchPoint = this.switchPoint;
    if (switchPoint != null) {
      return switchPoint;
    }
    return this.switchPoint = new SwitchPoint();
  }
  
  static final class HiddenClass {
    final HashMap<String, Integer> slotMap;
    final HashMap<String, HiddenClass> forwardMap = new HashMap<>();
    
    HiddenClass(HashMap<String, Integer> slotMap) {
      this.slotMap = slotMap;
    }
    
    Integer slot(String key) {
      return slotMap.get(key);
    }

    HiddenClass forward(String key) {
      return forwardMap.computeIfAbsent(key, k -> {
        HashMap<String, Integer> slotMap = new HashMap<>();
        slotMap.putAll(this.slotMap);
        slotMap.put(k, slotMap.size());
        return new HiddenClass(slotMap);
      });
    }
    
    HiddenClass forwardProto() {
      return forwardMap.computeIfAbsent(PROTO_KEY, k -> {
        return new HiddenClass(slotMap);
      });
    }
    
    @Override
    public String toString() {  // DEBUG
      return slotMap.keySet().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }
  }
  
  //returned method handle is typed ()JSObject
  private MethodHandle getPrototypeGetterIfConstant(Function<MethodHandle, MethodHandle> invalidate) {
    JSObject proto = this.proto;
    MethodHandle target = constant(JSObject.class, proto);
    if (proto != null) {  // prototype is stable !
      return target;
    }
    MethodHandle fallback = GET_PROTOTYPE_OF.bindTo(this);
    return switchPoint().guardWithTest(target, invalidate.apply(fallback));
  }
  
  //returned method handle is typed (JSObject)Object
  final MethodHandle getGetter(String key, Function<MethodHandle, MethodHandle> invalidate) {
    if (PROTO_KEY.equals(key)) {
      // prototype tend to be constant
      return new InvalidableInliningCacheCallSite(
          methodType(Object.class, JSObject.class), mop -> {
            MethodHandle fallback = GET_PROTOTYPE_OF.asType(methodType(Object.class, JSObject.class));
            if (mop.counter() > 0) {  // one shot
              return fallback;
            }
            MethodHandle test = RT.POINTER_CHECK.bindTo(this).asType(methodType(boolean.class, JSObject.class));
            MethodHandle target = dropArguments(constant(Object.class, proto), 0, JSObject.class);
            return guardWithTest(test, target, mop.invalidate(fallback));
          }).dynamicInvoker();
    }
    
    return new PolymorphicInliningCacheCallSite(
        methodType(Object.class, JSObject.class), (receiver, mop) -> {
          if (mop.counter() > 5) {  // too many hidden classes, revert to a call to the generic get()
            return new CacheEntry(null, insertArguments(GET_KEY, 1, key));
          }
          
          HiddenClass hiddenClass = this.hiddenClass;
          MethodHandle target;
          MethodHandle test = filterReturnValue(GET_HIDDEN_CLASS, RT.POINTER_CHECK.bindTo(hiddenClass));
          
          Integer slot = hiddenClass.slot(key);
          if (slot != null) {  // the value is stable
            target =  filterReturnValue(GET_VALUES, insertArguments(ARRAY_GETTER, 1, slot));
          } else {
            target = (proto == null)?
                dropArguments(constant(Object.class, null), 0, JSObject.class):  // if the prototype changed, the hidden class will change too
                dropArguments(proto.getGetterIfConstant(key, invalidate), 0, JSObject.class);
          }
          return new CacheEntry(test, target);
        }).dynamicInvoker();
  }
  
  // returned method handle is typed ()Object
  final MethodHandle getGetterIfConstant(String key, Function<MethodHandle, MethodHandle> invalidate) {
    if (PROTO_KEY.equals(key)) {
      return getPrototypeGetterIfConstant(invalidate);
    }
    
    Integer slot = hiddenClass.slot(key);
    if (slot != null) {  // the value is stable
      return constant(Object.class, values[slot]);
    } 
    
    JSObject proto = this.proto;
    if (proto != null) {  // the prototype is stable
      return proto.getGetterIfConstant(key, invalidate);
    }
    return switchPoint().guardWithTest(
        constant(Object.class, null),
        invalidate.apply(insertArguments(GET_KEY, 0, this, key)));
  }
  
  final MethodHandle getSetter(String key, Function<MethodHandle, MethodHandle> invalidate) {
    return insertArguments(SET_KEY, 1, key);  //TODO should be optimized !
  }

  MethodHandle getArrayGetter() {
    return GET_KEY.asType(methodType(Object.class, JSObject.class, Object.class));
  }
  
  MethodHandle getArraySetter() {
    return SET_KEY.asType(methodType(void.class, JSObject.class, Object.class, Object.class));
  }
  
  private static final MethodHandle ARRAY_GETTER = arrayElementGetter(Object[].class);
  static final MethodHandle GET_PROTOTYPE_OF;
  private static final MethodHandle GET_VALUES, GET_HIDDEN_CLASS, GET_KEY, SET_KEY;
  static {
    Lookup lookup = lookup();
    GET_VALUES = mh(lookup, Lookup::findGetter, JSObject.class, "values", Object[].class);
    GET_HIDDEN_CLASS = mh(lookup, Lookup::findGetter, JSObject.class, "hiddenClass", HiddenClass.class)
        .asType(methodType(Object.class, JSObject.class));
    GET_PROTOTYPE_OF = mh(lookup, Lookup::findStatic, JSObject.class, "getPrototypeOf", methodType(JSObject.class, JSObject.class));
    GET_KEY = mh(lookup, Lookup::findVirtual, JSObject.class, "get", methodType(Object.class, String.class));
    SET_KEY = mh(lookup, Lookup::findVirtual, JSObject.class, "set", methodType(void.class, String.class, Object.class));
  }
  
  private static final String PROTO_KEY = "__proto__";
  static final String PROTOTYPE_KEY = "prototype";
  private static final HiddenClass HIDDEN_MAP_ROOT = new HiddenClass(new HashMap<>());
}
