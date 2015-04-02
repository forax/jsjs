// boot script, initialize 


function __wrapJavaArray__(javaArray) {
	return __createArray__(Array.prototype, javaArray);
}

this.__proto__.parseInt = function (object) {
	return Java.type("java.lang.Integer").parseInt(object.toString());
};
this.__proto__.parseFloat = function (object) {
	return Java.type("java.lang.Double").parseDouble(object.toString());
};

Object.keys = function(object) {
	return __createArray__(Array.prototype, __keys__(object));
}

Array.prototype.iterator = function() {
	return __arrayIterator__(this);
}
Array.prototype.sort = function() {
	return __arraySort__(this);
}