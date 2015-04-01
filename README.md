# jsjs
jsjs is a JavaScript compiler + engine written in JavaScript on top of the Java Virtual Machine

## how to build it ?
First, you need a Java 8 compatible JDK, then you have to edit
the line in the script run_build.js that define where the JDK is
```
  var java_home = "PATH_TO_YOUR_JDK";
```

then you can build it with the following command
```
  jjs run_build.js -- bootstrap skiptest
```

The script will use the Nashorn JavaScript Engine to compile itselef (jsjs.js) to a .class file and
then use this class to recompile itself, bootstraping itself.


## How to run the test ?
```
  jjs run_build.js
```

## Why do you need to put a home built version of Nashorn in the boot class path when running jsjs ?
jsjs uses the Nashorn parser API defined by the [JEP 236](http://openjdk.java.net/jeps/236)
which is integrated in JDK 9 but not available in JDK 8 (yet ?).

## How to use jsjs on a simple JavaScript file ?
jsjs acts as a compiler that takes a JavaScript file as input and generate a corresponding class file.
By example, the folloxing command will compile fun.js to bytecode
```
  /usr/jdk/jdk1.8.0_40/bin/java -Xbootclasspath/p:lib/nashorn.jar -cp .:classes:lib/asm-debug-all-5.0.3.jar jsjs fun.js
```

then the generated file, fun.class, can be run using the classical java command
```
  /usr/jdk/jdk1.8.0_40/bin/java -cp classes:. fun
```

In order to run, you need to put the class files of the 5 Java files (Builtins, JSObject, JSFunction, JSArray and RT)
in the classpath, these classes implement the JavaScript semantics (in fact, a JavaScript-like, see below).


## Does jsjs implements any ECMAScript standard ?
No, jsjs semantics is a reduced subset of the ECMASCript 5 (strict mode) semantics with the guarantee to be stable.

## What do you mean by stable ?
The stable semantics is a semantics in between the full mutable and full immutable semantics.
Stable means that once a value has been observed as defined (as not undefined for JavaScript),
then the value can not changed anymore. So local variables can be assigned once, object properties
can be assigned once, prototypes can be assigned once, captured variables from enclosing scope are immutable
(already assigned), etc.

Given that being able to add a new property or a new method to any object is in the DNA of JavaScript,
it seems to be, in my opinion, the only sane semantics. That's said, i may be wrong.
