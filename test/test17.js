function A() {
}
function hello() {
  print("hello");
}
A.prototype.hello = hello;

var a = new A();
a.hello();
