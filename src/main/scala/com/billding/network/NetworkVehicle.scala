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
  * `next`, `route` and `destination` were left by phase C for phase E to fill
  * in properly - see [[NetworkVehicle.chooseNext]], which both
  * [[NetworkVehicle.enteringAt]] (a car's very first section) and
  * [[NetworkTick.settle]] (every section it crosses afterwards) now call to
  * decide `next`: from the head of `route` when there is one, replanned from
  * `destination` when a route ran out before reaching it, or the section's
  * first declared outgoing movement when there is no destination to route
  * toward at all (unrouted phase-C traffic, or a vehicle whose replan
  * failed).
  *
  * `routeFailed` is that last case's mark: once a replan from `destination`
  * comes back with no legal route, the plan calls for continuing legally
  * rather than teleporting or removing the vehicle, but also for counting it
  * rather than letting it pass as an ordinary, on-plan trip. The flag is
  * sticky - set once, it is never cleared back to `false` - so it stays a
  * record of "this trip missed its exit and could not replan," not a live
  * read of whether a route currently resolves.
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
  destination: Option[SectionId] = None,
  routeFailed: Boolean = false
)

object NetworkVehicle {

  /**
    * A vehicle placed onto `section`, with `next` (and `route`) resolved by
    * [[chooseNext]] from `route`/`destination` exactly as any later section
    * crossing resolves them - see that method for the policy. A car entering
    * with neither a route nor a destination gets the old phase-C placeholder:
    * the section's first declared outgoing movement, or [[None]] at a
    * dangling end (W5's implicit sink).
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
  ): NetworkVehicle = {
    val (next, remainingRoute, routeFailed) = chooseNext(index, section, route, destination, routeFailed = false)
    NetworkVehicle(
      piloted = piloted,
      section = section,
      s = s,
      speed = speed,
      lateral = lateral,
      next = next,
      route = remainingRoute,
      destination = destination,
      routeFailed = routeFailed
    )
  }

  /**
    * What a vehicle does about `next` on entering `section` - the single
    * policy shared by [[enteringAt]] and [[NetworkTick.settle]], so a route's
    * branch is chosen in exactly one place no matter whether this is the
    * vehicle's first section or its fifth.
    *
    * With a nonempty `route`, its head is the choice and its tail is what's
    * left - the plan's stability requirement falls out of this for free,
    * since `next` is only ever touched here, on landing, and never redrawn
    * while a vehicle waits at a seam or in a queue.
    *
    * With an empty `route`, there is nothing left to take. That is the
    * expected, successful end of routing when `section` has no outgoing
    * movement at all (a dangling end - the ordinary way a trip ends) or is
    * already `destination` - in either case this falls back to the section's
    * first declared outgoing movement, the same placeholder phase C used,
    * with no route left to resume and no failure to mark.
    *
    * Otherwise the route ran out before `destination` did: a missed exit.
    * The plan's default applies - continue legally, replan from here, never
    * teleport or cut across. [[Routing.planRoute]] from `section` is tried;
    * success resumes as if that had been the route all along. A vehicle with
    * no `destination` to replan toward is not "missing an exit" at all - it
    * was never being routed - so it takes the same placeholder silently.
    * Only an actual failed replan (a `destination` that no longer resolves a
    * route from here) sets `routeFailed`.
    */
  private[network] def chooseNext(
    index: NetworkIndex,
    section: SectionId,
    route: List[MovementId],
    destination: Option[SectionId],
    routeFailed: Boolean
  ): (Option[MovementId], List[MovementId], Boolean) = {
    def placeholder: Option[MovementId] = index.outgoing.getOrElse(section, Nil).headOption.map(_.id)

    route match {
      case head :: tail => (Some(head), tail, routeFailed)
      case Nil =>
        val outgoing = index.outgoing.getOrElse(section, Nil)
        if (outgoing.isEmpty || destination.contains(section)) (placeholder, Nil, routeFailed)
        else
          destination match {
            case None => (placeholder, Nil, routeFailed)
            case Some(there) =>
              Routing.planRoute(index, section, there) match {
                case Some(head :: tail) => (Some(head), tail, routeFailed)
                case Some(Nil)          => (placeholder, Nil, routeFailed)
                case None               => (placeholder, Nil, true)
              }
          }
    }
  }
}
