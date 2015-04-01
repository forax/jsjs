function A() {
}
function foo() {
}

function check() {
  print(A.prototype.foo == foo);
}

A.prototype.foo = foo;
check();
check();
check();
