package de.jnmeyr.ambiio

import scala.annotation.tailrec
import scala.util.Try

case class Pixel(red: Int,
                 green: Int,
                 blue: Int) {

  def *(value: Double): Pixel = Pixel(
    ((red.toInt & 0XFF) * value).toByte,
    ((green.toInt & 0XFF) * value).toByte,
    ((blue.toInt & 0XFF) * value).toByte
  )

  def toBytes: Array[Byte] = Array(red.toByte, green.toByte, blue.toByte)

  def toHexString: String = f"#$red%02x$green%02x$blue%02x"

}

object Pixel {

  def grey(value: Double): Pixel = Pixel((255 * value).toInt, (255 * value).toInt, (255 * value).toInt)

  val black: Pixel = grey(0.0)

  val white: Pixel = grey(1.0)

  def red(red: Double): Pixel = Pixel((255 * red).toInt, 0, 0)

  val red: Pixel = red(1.0)

  def green(green: Double): Pixel = Pixel(0, (255 * green).toInt, 0)

  val green: Pixel = green(1.0)

  def blue(blue: Double): Pixel = Pixel(0, 0, (255 * blue).toInt)

  val blue: Pixel = blue(1.0)

  def yellow(yellow: Double): Pixel = Pixel((255 * yellow).toInt, (255 * yellow).toInt, 0)

  val yellow: Pixel = yellow(1.0)

  def purple(purple: Double): Pixel = Pixel((255 * purple).toInt, 0, (255 * purple).toInt)

  val purple: Pixel = purple(1.0)

  def unapply(bytes: Seq[Byte]): Option[Vector[Pixel]] = Try {
    @tailrec
    def fromBytes(bytes: Seq[Byte], pixels: Vector[Pixel] = Vector.empty): Vector[Pixel] = bytes match {
      case Seq(red, green, blue) => pixels :+ Pixel(red, green, blue)
      case Seq(red, green, blue, bytes@_*) => fromBytes(bytes, pixels :+ Pixel(red, green, blue))
    }

    fromBytes(bytes, Vector.empty)
  }.toOption

  private val Regex = "^#([a-fA-F0-9]{2})([a-fA-F0-9]{2})([a-fA-F0-9]{2})$".r

  def unapply(string: String): Option[Pixel] = Try(
    string.toLowerCase match {
      case "black" => black
      case "white" => white
      case "red" => red
      case "green" => green
      case "blue" => blue
      case "yellow" => yellow
      case "purple" => purple
      case Regex(red, green, blue) => Pixel(Integer.parseInt(red, 16), Integer.parseInt(green, 16), Integer.parseInt(blue, 16))
    }
  ).toOption

  object Implicits {

    implicit val BrightnessOrdering: Ordering[Pixel] = (left: Pixel, right: Pixel) => (left.red + left.green + left.blue) - (right.red + right.green + right.blue)

  }

}
