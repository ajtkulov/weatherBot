import model.{Coor, Geometry, Poly}
import org.scalatest.FunSuite

class GeometryTests extends FunSuite {
  val r = new scala.util.Random()

  def randomCell(w: Double, h: Double): Coor = {
    Coor(r.nextDouble() * w, r.nextDouble() * h)
  }

  test("Inside test - square") {
    val p = Poly(List(
      Coor(0, 0),
      Coor(1, 0),
      Coor(1, 1),
      Coor(0, 1)
    ))

    val res = (1 to 100000).map(x => randomCell(2, 2)).map(Geometry.inside(p, _)).count(identity)

    assert(res >= 24700 && res <= 25300)
  }

  test("Inside test - concave") {
    val p = Poly(List(
      Coor(0, 0),
      Coor(1, 0),
      Coor(0.25, 0.25),
      Coor(0, 1)
    ))

    val res = (1 to 100000).map(x => randomCell(1, 1)).map(Geometry.inside(p, _)).count(identity)

    assert(res >= 24700 && res <= 25300)
  }

  test("Inside - simple - 1") {
    val p = Poly(List(
      Coor(0, 0),
      Coor(1, 0),
      Coor(0.25, 0.25),
      Coor(0, 1)
    ))

    assert(Geometry.inside(p, Coor(0.1, 0.1)))
    assert(!Geometry.inside(p, Coor(0.3, 0.3)))
    assert(!Geometry.inside(p, Coor(0.9, 0.9)))
    assert(!Geometry.inside(p, Coor(0.25, 0.3)))
    assert(!Geometry.inside(p, Coor(0.3, 0.25)))
    assert(Geometry.inside(p, Coor(0.23, 0.25)))
    assert(Geometry.inside(p, Coor(0.25, 0.23)))
  }
}
