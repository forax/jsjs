function test() {
	var foo = function() {
		return 3;
	}
	return foo;
}

var fun = test();
if (fun() != 3) {
	throw "error !";
}

