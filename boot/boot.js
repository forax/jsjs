// boot script, initialize 


function __wrapJavaArray__(javaArray) {
	return __createArray__(Array.prototype, javaArray);
}

this.__proto__.parseInt = Java.type("java.lang.Integer").parseInt;
this.__proto__.parseFloat = Java.type("java.lang.Double").parseDouble;

Object.keys = function(object) {
	return __createArray__(Array.prototype, __keys__(object));
}

Array.prototype.iterator = function() {
	return __arrayIterator__(this);
}
Array.prototype.sort = function() {
	return __arraySort__(this);
}