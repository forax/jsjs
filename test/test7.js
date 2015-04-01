function test1(a, b) {
	if (a == b) {
		print("wrong !");
	}
	if (a != b) {
		print("OK");
	}	
}

function test2(a, b) {
	if (a == b) {
		print("wrong !");
	} else {
		print("OK");
	}
	if (a != b) {
		print("OK");
	} else {
		print("wrong !");
	}	
}

test1(4, 7);
test2(4, 7);
