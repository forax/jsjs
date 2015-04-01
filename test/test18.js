function Point(x, y) {
	this.x = x;
	this.y = y;
}

function translate(dx, dy) {
  return new Point(this.x + dx, this.y + dy);
}

Point.prototype.translate = translate;

var p = new Point(2, 3);
var p2 = p.translate(1, 1);
print(p2.x);
print(p2.y);
