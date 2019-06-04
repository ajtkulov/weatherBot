package model

import model.Forecast.{SimpleTimeLineForecast, TimeLineForecase}
import org.joda.time.{DateTime, Instant}
import play.api.libs.json.{JsArray, JsObject, JsValue, Writes}
import play.api.libs.json._
import play.api.libs.json.Json._

import scala.util.{Random, Try}

case class Coor(x: BigDecimal, y: BigDecimal) {
  def -->(dest: Coor): Coor = {
    Coor(dest.x - x, dest.y - y)
  }

}

case class Poly(values: List[Coor]) {
  def pairs: List[(Coor, Coor)] = {
    values.zip(values.drop(1) :+ values.head)
  }
}

case class Cloud(timeStamp: Instant, poly: Poly, precipitationStrength: Double, precipitationType: Int) {}

case class SkyTimeLine(clouds: List[Cloud]) {
  def forecast(pos: Coor): Map[Instant, Forecast] = {
    clouds.groupBy(_.timeStamp).mapValues(Sky.apply).mapValues(_.forecast(pos))
  }
}

case class Sky(clouds: List[Cloud]) {
  require(clouds.map(_.timeStamp).distinct.size == 1)

  def forecast(pos: Coor): Forecast = {
    val inside = clouds.filter(cloud => Geometry.inside(cloud.poly, pos)).sortBy(x => x.precipitationStrength)(Ordering[Double].reverse).headOption

    val nearest = clouds.map(cloud => (cloud, Geometry.nearest(cloud.poly, pos))).minBy(x => x._2)._1

    Forecast(inside, nearest)
  }
}

case class Forecast(inside: Option[Cloud], nearest: Cloud) {
  def toSimple(coor: Coor): SimpleForecast = {
    SimpleForecast(inside.map(cloud => (cloud.precipitationStrength, cloud.precipitationType)), Geometry.nearest(nearest.poly, coor))
  }
}

case class SimpleForecast(inside: Option[(Double, Int)], distance: BigDecimal)

object Serialization {
  implicit val writer: Writes[SimpleForecast] = new Writes[SimpleForecast] {
    override def writes(o: SimpleForecast): JsValue = {
      if (o.inside.isDefined) {
        obj("strength" -> o.inside.get._1, "type" -> o.inside.get._2)
      }
      else {
        obj("nearestKm" -> o.distance)
      }
    }
  }

  implicit val writerComplex: Writes[SimpleTimeLineForecast] = new Writes[SimpleTimeLineForecast] {
    override def writes(o: SimpleTimeLineForecast): JsValue = {
      val sorted = o.toList.sortBy(_._1.getMillis)

      val json: List[JsObject] = sorted.map(x => {
        writer.writes(x._2).as[JsObject] + ("time" -> JsNumber(x._1.getMillis)) + ("local_time" -> JsString(new DateTime(x._1.getMillis).toString()))
      })
      arr(json)
    }
  }
}

object Forecast {
  type TimeLineForecase = Map[Instant, Forecast]
  type SimpleTimeLineForecast = Map[Instant, SimpleForecast]

  def toSimple(value: TimeLineForecase)(coor: Coor): SimpleTimeLineForecast = value.mapValues(_.toSimple(coor))
}

trait Show[T] {
  def show(value: T): String
}

object Shows {

  implicit val showSimpleTimeLineForecase: Show[SimpleTimeLineForecast] = new Show[SimpleTimeLineForecast] {
    override def show(value: SimpleTimeLineForecast): String = {
      val sorted = value.toList.sortBy(_._1.getMillis)
      sorted.map(x => showSimpleForecast.show(x._2)).mkString(" ")
    }
  }

  implicit val showSimpleForecast: Show[SimpleForecast] = new Show[SimpleForecast] {
    override def show(value: SimpleForecast): String = {
      value match {
        case SimpleForecast(Some((0.25, 1)), _) => """🌦️"""
        case SimpleForecast(Some((0.5, 1)), _) => "🌧"
        case SimpleForecast(Some((0.75, 1)), _) => "⛈"
        case SimpleForecast(Some((1.0, 1)), _) => "⚡"
        case SimpleForecast(None, d) if d < 2 => "☁️"
        case SimpleForecast(None, d) if d <= 5.1 => "❔"
        case SimpleForecast(None, d) if d > 50 => """☀"""
        case SimpleForecast(None, d) if d > 10 => "🌤"
        case SimpleForecast(None, d) if d > 5 => "⛅"
        case SimpleForecast(Some((_, 2)), _) => "❄️"

        case _ => "[???]"
      }
    }
  }
}

object ModelReader {
  def readJson(instant: Instant, jsValue: JsValue): List[Cloud] = {

    val obj: JsObject = (jsValue \ "polygons" \ "strength").as[JsObject]

    obj.fields.toList.flatMap {
      case (strength, js) => readByStrength(instant, strength.toInt, js)
    }
  }

  def readByStrength(instant: Instant, strength: Int, js: JsValue): List[Cloud] = {
    js.as[JsArray].value.toList.flatMap(x => readCloud(instant, strength, x))
  }

  def readCloud(instant: Instant, strength: Int, js: JsValue): List[Cloud] = {
    val polies = (js \ "coords").as[List[List[List[BigDecimal]]]]

    List(Cloud(instant, toPoly(polies.head), strength, 1))
  }

  def toCoor(values: List[BigDecimal]): Coor = {
    Coor(values(0) / 100000, values(1) / 100000)
  }

  def toPoly(values: List[List[BigDecimal]]): Poly = {
    Poly(values.map(toCoor))
  }
}

object Geometry {
  lazy val rand = new Random(1)

  def vectorProd(x: Coor, y: Coor): BigDecimal = {
    x.x * y.y - x.y * y.x
  }

  def sq(x: Coor, vect: (Coor, Coor)): BigDecimal = {
    vectorProd(x --> vect._1, x --> vect._2)
  }

  def sq(c: Coor, p: Poly): BigDecimal = {
    p.pairs.map(x => sq(c, x)).sum
  }

  def det(x: Coor, y: Coor): BigDecimal = {
    x.x * y.y - x.y * y.x
  }

  /**
    *
    * @param p1 line
    * @param p2 line
    * @param b  beam start
    * @param d  beam direction
    * @return does line (p1-p2) intersect with beam (b, d)
    */
  def intersect(p1: Coor, p2: Coor, b: Coor, d: Coor): Boolean = {
    Try {
      val dd = det(Coor(p2.x - p1.x, -d.x), Coor(p2.y - p1.y, -d.y))
      val d1 = det(Coor(b.x - p1.x, -d.x), Coor(b.y - p1.y, -d.y))
      val d2 = det(Coor(p2.x - p1.x, b.x - p1.x), Coor(p2.y - p1.y, b.y - p1.y))

      val t1 = d1 / dd
      val t2 = d2 / dd

      t1 >= 0 && t1 <= 1 && t2 >= 0
    }.getOrElse(false)
  }

  def inside(poly: Poly, s: Coor): Boolean = {
    val d = Coor(rand.nextDouble(), rand.nextDouble())

    poly.pairs.count(x => intersect(x._1, x._2, s, d)) % 2 == 1
  }

  def distance(a: Coor, b: Coor): BigDecimal = {
    val sq = (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
    Math.sqrt(sq.toDouble)
  }

  def sphereDistance(a: Coor, b: Coor): BigDecimal = {
    def angleToRadian(value: BigDecimal): BigDecimal = value * Math.PI / 180

    val r: BigDecimal = 6371

    val f1 = angleToRadian(a.x)
    val f2 = angleToRadian(b.x)
    val df = angleToRadian(b.x - a.x)
    val dl = angleToRadian(b.y - a.y)
    val aa = Math.sin(df.toDouble / 2.0) * Math.sin(df.toDouble / 2.0) + Math.cos(f1.toDouble) * Math.cos(f2.toDouble) * Math.sin(dl.toDouble / 2) * Math.sin(dl.toDouble / 2)
    val cc = 2 * Math.atan2(Math.sqrt(aa), Math.sqrt(1 - aa))
    r * cc
  }

  def nearest(poly: Poly, d: Coor): BigDecimal = {
    poly.values.map(sphereDistance(_, d)).min
  }
}
