package main

import model._
import request.Request

object Main extends App {
  override def main(args: Array[String]): Unit = {
    val model: SkyTimeLine = Request.getModels()

    println(model.forecast(Coor(53.229994, 56.846230)))
    println(model.forecast(Coor(31.191044, 58.247710)))

  }
}