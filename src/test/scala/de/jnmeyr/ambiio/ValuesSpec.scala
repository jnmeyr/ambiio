package de.jnmeyr.ambiio

import org.scalatest.wordspec.AnyWordSpec

class ValuesSpec
  extends AnyWordSpec {

  "A single value" should {

    def value = Values.Single(Pixel.red)

    "become pixels" in {
      assert {
        val pixels = value.toPixels(0)
        pixels.isEmpty
      }
      assert {
        val pixels = value.toPixels(2)
        pixels.size == 2 &&
          pixels.forall(_ == Pixel.red)
      }
      assert {
        val pixels = value.toPixels(60)
        pixels.size == 60 &&
          pixels.forall(_ == Pixel.red)
      }
    }

  }

  "A pair value" should {

    def value = Values.Pair(Pixel.red, Pixel.green)

    "become pixels" in {
      assert {
        val pixels = value.toPixels(0)
        pixels.isEmpty
      }
      assert {
        val pixels = value.toPixels(2)
        pixels.size == 2 &&
          pixels.take(1).forall(_ == Pixel.red) &&
          pixels.drop(1).forall(_ == Pixel.green)
      }
      assert {
        val pixels = value.toPixels(60)
        pixels.size == 60 &&
          pixels.take(30).forall(_ == Pixel.red) &&
          pixels.drop(30).forall(_ == Pixel.green)
      }
    }

  }

  "A multiple value" when {

    "being empty" should {

      def value = Values.Multiple(Vector.empty)

      "become pixels" in {
        assert {
          val pixels = value.toPixels(0)
          pixels.isEmpty
        }
        assert {
          val pixels = value.toPixels(1)
          pixels.size == 1 &&
            pixels.forall(_ == Pixel.black)
        }
        assert {
          val pixels = value.toPixels(2)
          pixels.size == 2 &&
            pixels.forall(_ == Pixel.black)
        }
        assert {
          val pixels = value.toPixels(3)
          pixels.size == 3 &&
            pixels.forall(_ == Pixel.black)
        }
        assert {
          val pixels = value.toPixels(60)
          pixels.size == 60 &&
            pixels.forall(_ == Pixel.black)
        }
      }

    }

    "having one value" should {

      def value = Values.Multiple(Vector(Pixel.red))

      "become pixels" in {
        assert {
          val pixels = value.toPixels(0)
          pixels.isEmpty
        }
        assert {
          val pixels = value.toPixels(1)
          pixels.size == 1 &&
            pixels.forall(_ == Pixel.red)
        }
        assert {
          val pixels = value.toPixels(2)
          pixels.size == 2 &&
            pixels.take(1).forall(_ == Pixel.red) &&
            pixels.drop(1).forall(_ == Pixel.black)
        }
        assert {
          val pixels = value.toPixels(3)
          pixels.size == 3 &&
            pixels.take(1).forall(_ == Pixel.black) &&
            pixels.slice(1, 2).forall(_ == Pixel.red) &&
            pixels.drop(2).forall(_ == Pixel.black)
        }
        assert {
          val pixels = value.toPixels(60)
          pixels.size == 60 &&
            pixels.take(29).forall(_ == Pixel.black) &&
            pixels.slice(29, 30).forall(_ == Pixel.red) &&
            pixels.drop(30).forall(_ == Pixel.black)
        }
      }

    }

    "having many values" should {

      def value = Values.Multiple(Vector(Pixel.red * 0.9, Pixel.green * 1.0, Pixel.blue * 0.8))

      "become pixels" in {
        assert {
          val pixels = value.toPixels(0)
          pixels.isEmpty
        }
        assert {
          val pixels = value.toPixels(1)
          pixels.size == 1 &&
            pixels.forall(_ == Pixel.green)
        }
        assert {
          val pixels = value.toPixels(2)
          pixels.size == 2 &&
            pixels(0) == Pixel.red * 0.9 &&
            pixels(1) == Pixel.green * 1.0
        }
        assert {
          val pixels = value.toPixels(3)
          pixels.size == 3 &&
            pixels(0) == Pixel.red * 0.9 &&
            pixels(1) == Pixel.green * 1.0 &&
            pixels(2) == Pixel.blue * 0.8
        }
        assert {
          val pixels = value.toPixels(60)
          pixels.size == 60 &&
            pixels.take(28).forall(_ == Pixel.black) &&
            pixels(28) == Pixel.red * 0.9 &&
            pixels(29) == Pixel.green * 1.0 &&
            pixels(30) == Pixel.blue * 0.8 &&
            pixels.drop(31).forall(_ == Pixel.black)
        }
      }

    }

  }

}
