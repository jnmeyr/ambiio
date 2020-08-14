package de.jnmeyr.ambiio

sealed trait Values {

  def toPixels(pixels: Int): Vector[Pixel]

}

object Values {

  case class Single(pixel: Pixel)
    extends Values {

    override def toPixels(pixels: Int): Vector[Pixel] = {
      Vector.range(0, pixels).map(_ => pixel)
    }

  }

  object Single {

    def apply(value: Double,
              inOpt: Option[Pixel]): Single = new Single(inOpt.getOrElse(Pixel.white) * value)

  }

  case class Pair(left: Pixel,
                  right: Pixel)
    extends Values {

    override def toPixels(pixels: Int): Vector[Pixel] = {
      Vector.range(0, pixels / 2).map(_ => left) ++ Vector.range(0, pixels / 2).map(_ => right)
    }

  }

  object Pair {

    def apply(left: Double,
              right: Double,
              inOpt: Option[Pixel]): Pair = new Pair(inOpt.getOrElse(Pixel.white) * left, inOpt.getOrElse(Pixel.white) * right)

  }

  case class Multiple(pixels: Vector[Pixel])
    extends Values {

    override def toPixels(pixels: Int): Vector[Pixel] = {
      def decreased: Vector[Pixel] = {
        import Pixel.Implicits.BrightnessOrdering

        val of = (this.pixels.size / pixels.toFloat).toInt
        val by = this.pixels.size - (of * pixels)
        this.pixels.slice(by / 2, this.pixels.size - by / 2).sliding(of, of).map(_.max).toVector
      }

      def increased: Vector[Pixel] = {
        val by = pixels - this.pixels.size
        val (start, end) = Vector.range(0, by).map(_ => Pixel.black).splitAt(by / 2)
        start ++ this.pixels ++ end
      }

      if (this.pixels.size > pixels) decreased
      else if (this.pixels.size < pixels) increased
      else this.pixels
    }

  }

  object Multiple {

    def apply(values: Vector[Double],
              inOpt: Option[Pixel]): Multiple = new Multiple(values.map(inOpt.getOrElse(Pixel.white) * _))

  }

}
