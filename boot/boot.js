// boot script, initialize 

//var AtomicInteger = Java.type("java.util.concurrence.atomic.AtomicInteger");

function __wrapJavaArray__(javaArray) {
	return __createArray__(Array.prototype, javaArray);
}

Object.keys = function(object) {
	return __createArray__(Array.prototype, __keys__(object));
}

Array.prototype.iterator = function() {
	return __arrayIterator__(this);
}
Array.prototype.sort = function() {
	return __arraySort__(this);
}