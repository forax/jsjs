package com.github.forax.jsjs;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

public class Hack {
  //FIXME remove these methods once RT will be able to disambiguate among overloads
   
  public static Object parseAST(Object path) throws jdk.nashorn.api.scripting.NashornException, java.io.IOException {
   jdk.nashorn.api.tree.Parser parser = jdk.nashorn.api.tree.Parser.create("-strict");
   return parser.parse(java.nio.file.Paths.get(path.toString()), null);
  }
  
  public static StringBuilder appendString(StringBuilder builder, String text) {
    return builder.append(text);
  }
  
  public static Object toInternalClassName(Object className) {
    return className.toString().replace('.', '/');
  }
  
  public static ClassReader classReader(Object array) {
    return new ClassReader((byte[])array);
  }
  
  public static Object dumpBytecode(Object array) {
    ClassReader reader = new ClassReader((byte[])array);
    CheckClassAdapter.verify(reader, false, new PrintWriter(System.out));
    return null;
  }
  
  public static Object write(Object path, Object array) throws IOException {
    Files.write((Path)path, (byte[])array);
    return null;
  }
}
