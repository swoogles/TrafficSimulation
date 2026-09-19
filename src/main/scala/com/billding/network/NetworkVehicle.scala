package com.billding.network

import squants.motion.{Acceleration, MetersPerSecondSquared}
import squants.space.{Length, Meters}
import squants.Velocity

import com.billding.traffic.PilotedVehicle

/**
  * A vehicle located on the lane graph: which [[LaneSection]] it is on and how
  * far along that section's path it has driven, rather than a point in space.
  *
  * The wrapped `piloted` still owns everything that isn't where-on-the-road -
  * the driver's parameters, the car's abilities, its dimensions and uuid - the
  * same way [[com.billding.traffic.TrackVehicle]] keeps a `PilotedVehicle`
  * intact so driver behaviour, rendering and serialization keep reading the
  * fields they already read.
  *
  * `next`, `route` and `destination` are unused by phase C. They exist now so
  * that C2's lookahead and C3's tick can be written and tested before phase E
  * has any routing to fill them properly - that seam is what keeps phase C
  * independent of phase E. [[NetworkVehicle.enteringAt]] gives `next` a
  * placeholder value (the section's first outgoing movement) so a car dropped
  * onto the network without a route still has somewhere to go; E3 will
  * overwrite it from an actual route.
  */
final case class NetworkVehicle(
  piloted: PilotedVehicle,
  section: SectionId,
  s: Length,
  speed: Velocity,
  lateral: Length = Meters(0),
  acceleration: Acceleration = MetersPerSecondSquared(0),
  next: Option[MovementId] = None,
  route: List[MovementId] = Nil,
  destination: Option[SectionId] = None
)

object NetworkVehicle {

  /**
    * A vehicle placed onto `section`, with `next` defaulted to the section's
    * first declared outgoing movement.
    *
    * Declared order, not any notion of "the right one" - phase C has no
    * routing yet, so the first outgoing movement is only ever a placeholder
    * that keeps a car moving somewhere instead of nowhere. A section with no
    * outgoing movement (a dangling end, W5's implicit sink) leaves `next` as
    * [[None]].
    */
  def enteringAt(
    piloted: PilotedVehicle,
    section: SectionId,
    s: Length,
    speed: Velocity,
    index: NetworkIndex,
    lateral: Length = Meters(0),
    route: List[MovementId] = Nil,
    destination: Option[SectionId] = None
  ): NetworkVehicle =
    NetworkVehicle(
      piloted = piloted,
      section = section,
      s = s,
      speed = speed,
      lateral = lateral,
      next = index.outgoing.getOrElse(section, Nil).headOption.map(_.id),
      route = route,
      destination = destination
    )
}
