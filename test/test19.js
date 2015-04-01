function Value() {
}

function checkX(expected) {
  var found = this.x;
  if (found != expected) {
	  throw "error: excepted " + expected  + " found " + found;
  }
}
Value.prototype.checkX = checkX;

var v = new Value();

// not defined
v.checkX(null);
v.checkX(null);

// in prototype
v.__proto__.x = 23;
v.checkX(23);
v.checkX(23);

// overriden
v.x = 34;
v.checkX(34);
v.checkX(34);

