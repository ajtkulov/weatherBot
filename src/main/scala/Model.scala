package model

import model.Forecast.{SimpleTimeLineForecase, TimeLineForecase}
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

case class Model(coordinates: Array[Array[Array[BigDecimal]]])

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

  implicit val writerComplex: Writes[SimpleTimeLineForecase] = new Writes[SimpleTimeLineForecase] {
    override def writes(o: SimpleTimeLineForecase): JsValue = {
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
  type SimpleTimeLineForecase = Map[Instant, SimpleForecast]

  def toSimple(value: TimeLineForecase)(coor: Coor): SimpleTimeLineForecase = value.mapValues(_.toSimple(coor))
}

trait Show[T] {
  def show(value: T): String
}

object Shows {

  implicit val showSimpleTimeLineForecase: Show[SimpleTimeLineForecase] = new Show[SimpleTimeLineForecase] {
    override def show(value: SimpleTimeLineForecase): String = {
      val sorted = value.toList.sortBy(_._1.getMillis)
      sorted.map(x => showSimpleForecast.show(x._2)).mkString(" ")
    }
  }

  implicit val showSimpleForecast: Show[SimpleForecast] = new Show[SimpleForecast] {
    override def show(value: SimpleForecast): String = {
      value match {
        case SimpleForecast(Some((0.25, 1)), _) => "[light rain]"
        case SimpleForecast(Some((0.5, 1)), _) => "[rain]"
        case SimpleForecast(Some((0.75, 1)), _) => "[strengthened rain]"
        case SimpleForecast(Some((1.0, 1)), _) => "[heavy rain]"
        case SimpleForecast(None, d) if d < 2 => "[near, <2km]"
        case SimpleForecast(None, d) if d <= 5.1 => "[near, <5km]"
        case SimpleForecast(None, d) if d > 100 => "[far away, >100km]"
        case SimpleForecast(None, d) if d > 50 => "[far away, >50km]"
        case SimpleForecast(None, d) if d > 25 => "[somewhere, >25km]"
        case SimpleForecast(None, d) if d > 10 => "[somewhere, >10km]"
        case SimpleForecast(None, d) if d > 5 => "[somewhere, >5km]"
        case SimpleForecast(Some((_, 2)), _) => "[snow]"

        case _ => "[???]"
      }
    }
  }
}

object ModelReader {
  def readJson(jsValue: JsValue): List[Cloud] = {

    val obj: JsObject = jsValue.as[JsObject]

    obj.fields.flatMap(x => readCloud(new Instant(x._1.toLong * 1000), x._2)).toList
  }

  def readCloud(timestamp: Instant, jsValue: JsValue): List[Cloud] = {
    val obj = jsValue.as[JsObject]
    (obj \ "features").as[JsArray].value.flatMap(x => readGeo(timestamp, x)).toList
  }

  def toCoor(values: List[BigDecimal]): Coor = {
    Coor(values(0), values(1))
  }

  def toPoly(values: List[List[BigDecimal]]): Poly = {
    Poly(values.map(toCoor))
  }

  def readGeo(instant: Instant, jsValue: JsValue): List[Cloud] = {
    val polies = (jsValue \ "geometry" \ "coordinates").as[List[List[List[BigDecimal]]]]

    polies.map(x => Cloud(instant, toPoly(x), (jsValue \ "properties" \ "prec_strength").as[Double], (jsValue \ "properties" \ "prec_type").as[Int]))
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
