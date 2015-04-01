var ProcessBuilder = Java.type("java.lang.ProcessBuilder");
var Collectors = Java.type("java.util.stream.Collectors");

var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");

function artifact(path, extension) {
	var pathname = path.toString();
	return Paths.get("", pathname.substring(0, pathname.length() - 3) + extension);
}
function exec(cmd, arg) {
	return new ProcessBuilder((cmd + arg.toString()).split(" ")).redirectOutput(devnull).redirectErrorStream(true).start();
}

var jsjs = Paths.get("", "jsjs.js");
var bootjs = Paths.get("", "boot/boot.js");

var java_home = "/usr/jdk/jdk1.8.0_40";
//var java_home = "/usr/jdk/jdk1.9.0";
var compiler_jjs = java_home + "/bin/jjs -J-Xbootclasspath/p:lib/nashorn.jar -cp classes:lib/asm-debug-all-5.0.3.jar " + jsjs + " -- ";
var compiler_jsjs = java_home + "/bin/java -Xbootclasspath/p:lib/nashorn.jar -cp .:classes:lib/asm-debug-all-5.0.3.jar jsjs "
var runner = java_home + "/bin/java -cp .:classes ";

var devnull = Paths.get("", "/dev/null").toFile();

function compile(compiler, path, force) {
	var classfile = artifact(path, ".class");
		
	var jsjs_time = Files.getLastModifiedTime(jsjs).toMillis();
	var source_time = Files.getLastModifiedTime(path).toMillis();
	var class_time = Files.exists(classfile)? Files.getLastModifiedTime(classfile).toMillis(): 0;
	
	if (!force) {
		if (jsjs_time < class_time) {
			if (source_time < class_time) {
				print("skip compilation of " + path);
				return;
			}
		}
	}
	
	var process = exec(compiler, path);
	var exitCode = process.waitFor();
	print("compilation of " + path + " = " + exitCode);
}

function run(path) {
	var classname = artifact(path, "");
	var process = exec(runner, classname);
	var exitCode = process.waitFor();
	print("run of " + path + " = " + exitCode);
}

var tests = Files.list(Paths.get("", "test"))
  .filter(function(path) { return path.toString().endsWith(".js"); })
  .collect(Collectors.toList());


print("use JDK from " + java_home);

// first compile jsjs.js with nashorn
compile(compiler_jjs, jsjs, false);

// then compile boot.js with jsjs if available
var compiler = Files.exists(artifact(jsjs, ".class"))? compiler_jsjs: compiler_jjs;
compile(compiler, bootjs, false);

// re-compile jsjs with jsjs if bootstrap option is set
if (arguments.indexOf("bootstrap") != -1) {
	compile(compiler_jsjs, bootjs, true);
	compile(compiler_jsjs, jsjs, true);
}
	
// then compile and run all tests if skiptest is not set
if (arguments.indexOf("skiptest") == -1) {
    tests.parallelStream().forEach(function(path) { return compile(compiler, path, false); });
    tests.parallelStream().forEach(function(path) { return run(path); });
}