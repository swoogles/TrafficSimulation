package com.billding

import collection.mutable.Stack
import org.scalatest._
import cats.data.{NonEmptyList, Validated}
import com.billding.behavior.{IntelligentDriverImpl, IntelligentDriverModel}
import squants.{Mass, Time, Velocity}
import squants.motion.{Distance, KilometersPerHour, MetersPerSecond}
import squants.space.Meters

class TrafficSpec extends FlatSpec {

  "A Stack" should "pop values in last-in-first-out order" in {
    val stack = new Stack[Int]
    stack.push(1)
    stack.push(2)
    assert(stack.pop() === 2)
    assert(stack.pop() === 1)
  }

  it should "throw NoSuchElementException if an empty stack is popped" in {
    TestValues.run()
  }
}

object TestValues {
  def run() = {
    val spatial1: Spatial = Spatial(
      (0, 0, 0, Meters),
      (120, 0, 0, KilometersPerHour)
    )

    val spatial2: Spatial = Spatial(
      (50, 0, 0, Meters),
      (100, 0, 0, KilometersPerHour)
    )

    val drivenVehicle1 =
      new PilotedVehicleImpl(
        Commuter(spatial1), Car(spatial1))

    val drivenVehicle2 =
      new PilotedVehicleImpl(
        Commuter(spatial2), Car(spatial2))

    val idm = new IntelligentDriverImpl
    val res = idm.reactTo(drivenVehicle1, drivenVehicle2, MetersPerSecond(20))
    println("res: " + res)
  }
}
