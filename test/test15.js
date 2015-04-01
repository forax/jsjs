function A(foo) {
  this.foo = foo;
}

function foo(self) {
	self.foo = "hello";
}

function display(self) {
	print(self.foo);
}

var a = new A("hello");
display(a);
display(a);

