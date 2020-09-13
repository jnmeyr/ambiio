package de.jnmeyr.ambiio

import org.scalatest.wordspec.AnyWordSpec

class PixelSpec
  extends AnyWordSpec {

  "A pixel" when {

    "as bytes" should {

      "be readable" in {
        assert {
          val pixels = Pixel.unapply(Seq[Byte]()).get
          pixels.isEmpty
        }
        assert {
          val pixels = Pixel.unapply(Seq(0, 0, 0).map(_.toByte)).get
          pixels.size == 1 && pixels.contains(Pixel(0, 0, 0))
        }
        assert {
          val pixels = Pixel.unapply(Seq(131, 62, 200).map(_.toByte)).get
          pixels.size == 1 && pixels.contains(Pixel(-125, 62, -56))
        }
        assert {
          val pixels = Pixel.unapply(Seq(255, 255, 255, 12, 0, 133).map(_.toByte)).get
          pixels.size == 2 && pixels.contains(Pixel(-1, -1, -1)) && pixels.contains(Pixel(12, 0, -123))
        }
      }

      "be writable" in {
        assert(Pixel(0, 0, 0).toBytes sameElements Array[Byte](0, 0, 0))
        assert(Pixel(131, 62, 200).toBytes sameElements Array[Byte](-125, 62, -56))
        assert(Pixel(255, 255, 255).toBytes sameElements Array[Byte](-1, -1, -1))
        assert(Pixel(2131212, 0, -123).toBytes sameElements Array[Byte](12, 0, -123))
      }

    }

    "as string" should {

      "be readable" in {
        assert(Pixel.unapply("#000000").contains(Pixel(0, 0, 0)))
        assert(Pixel.unapply("#833ec8").contains(Pixel(131, 62, 200)))
        assert(Pixel.unapply("#ffffff").contains(Pixel(255, 255, 255)))
        assert(Pixel.unapply("#0c0085").contains(Pixel(12, 0, 133)))
      }

      "be writeable" in {
        assert(Pixel(0, 0, 0).toHexString == "#000000")
        assert(Pixel(131, 62, 200).toHexString == "#833ec8")
        assert(Pixel(255, 255, 255).toHexString == "#ffffff")
        assert(Pixel(2131212, 0, -123).toHexString == "#0c0085")
      }

    }

  }

}
