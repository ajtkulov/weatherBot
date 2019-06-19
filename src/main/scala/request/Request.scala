package request

import model.{Cloud, ModelReader, SkyTimeLine}
import org.joda.time.Instant
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, JsString, JsValue, Json}

import scala.util.Try


case class MetaData(time: Instant, prefixUrl: String) {}

case class MetaDataObject(values: List[MetaData])

object MetaDataObject {
  def readJson(jsValue: JsValue): MetaDataObject = {

    val obj: JsObject = jsValue.as[JsObject]

    MetaDataObject(obj.fields.collect { case (timeStamp, url: JsString) => MetaData(new Instant(timeStamp.toLong * 1000), url.value) }.toList)
  }
}

object Request {
  lazy val logger = LoggerFactory.getLogger(getClass)

  def getMeta(): MetaDataObject = {
    val url = s"https://yandex.ru/pogoda/front/maps/amanifest?type=nowcast"
    val json = scala.io.Source.fromURL(url).getLines().mkString("")
    MetaDataObject.readJson(Json.parse(json))
  }

  private def get(metaData: MetaData): List[Cloud] = {
    List(
      "1/0_-40_40.json",
      "1/40_-40_40.json",
      "1/0_40_40.json",
      "1/0_0_40.json",
      "1/40_0_40.json",
      "1/40_40_40.json",
      "1/40_80_40.json",
      "1/0_80_40.json"
    ).toParArray.flatMap { suffix =>
      Try {
8        val url = s"${metaData.prefixUrl}/$suffix"
        val json = scala.io.Source.fromURL(url).getLines().mkString("")
        ModelReader.readJson(metaData.time, Json.parse(json))
      }.getOrElse(List())
    }.toList
  }

  def getModels(): SkyTimeLine = {
    val meta = getMeta()
    SkyTimeLine(meta.values.flatMap(get))
  }
}
