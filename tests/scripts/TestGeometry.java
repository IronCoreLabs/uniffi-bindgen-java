import uniffi.geometry.*;

public class TestGeometry {
  public static void main(String[] args) throws Exception {
    var ln1 = new Line(new Point(0.0,0.0), new Point(1.0,2.0));
    var ln2 = new Line(new Point(1.0,1.0), new Point(2.0,2.0));

    assert Geometry.gradient(ln1) == 2.0;
    assert Geometry.gradient(ln2) == 1.0;

    assert Geometry.intersection(ln1, ln2).equals(new Point(0.0, 0.0));
    assert Geometry.intersection(ln1, ln1) == null;
  }
}
