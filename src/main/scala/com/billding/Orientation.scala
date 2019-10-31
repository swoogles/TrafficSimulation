package com.billding

import squants.DoubleVector

sealed trait Orientation {
  val vec: DoubleVector
}
object Orientation {

  case object North extends Orientation {
    val vec = DoubleVector(0.0, -1.0, 0.0)
  }

  case object South extends Orientation {
    val vec = DoubleVector(0.0, 1.0, 0.0)
  }

  case object East extends Orientation {
    val vec = DoubleVector(1.0, 0.0, 0.0)
  }

  case object West extends Orientation {
    val vec = DoubleVector(-1.0, 0.0, 0.0)
  }

}
