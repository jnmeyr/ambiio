package de.jnmeyr.ambiio

case class Pixel(red: Byte,
                 green: Byte,
                 blue: Byte) {

  lazy val toBytes: Array[Byte] = Array(red, green, blue)

  lazy val toInt: Int = red << 16 + green << 8 + blue

  def *(value: Double): Pixel = Pixel(
    ((red.toInt & 0XFF) * value).toByte,
    ((green.toInt & 0XFF) * value).toByte,
    ((blue.toInt & 0XFF) * value).toByte)

}

object Pixel {

  def apply(red: Int, green: Int, blue: Int): Pixel = Pixel(red.toByte, green.toByte, blue.toByte)

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

  implicit def orderingByBrightness: Ordering[Pixel] = (left: Pixel, right: Pixel) => (left.red + left.green + left.blue) - (right.red + right.green + right.blue)

}
