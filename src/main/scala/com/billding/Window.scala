package com.billding

import com.billding.svgRendering.{
  DividerRing,
  LaneChangeSignal,
  Motion,
  Projection,
  RenderedVehicle,
  RoadRing,
  RoadShape,
  RoadStrip
}
import com.billding.traffic.Scene
import org.scalajs.dom
import org.scalajs.dom.svg.{G, SVG}
import scalatags.JsDom
import scalatags.JsDom.all._
import scalatags.JsDom.{svgAttrs, svgTags}
import squants.space.Length

/*
 * TODO It might make more sense for this to accept a List[JsDom.TypedTag[G]]
 * and canvas dimensions to not muck around with anything specific to the scene.
 */
object Window {

  /*
  These live out here rather than in the class body on purpose. svgNode is a val that renders
  during construction, so any field declared below it is still null by the time it is read.
   */
  private val Tarmac = "#23282f"
  private val EdgeLine = "#ffffff"

  /** Dashed lane markings are yellower and dimmer than the solid white at the kerb. */
  private val DividerLine = "#d8c56a"

  /*
  The sprite is a light silver car, so multiplying it by a colour keeps its shading and its
  outline while changing what colour it is - a green car rather than a green rectangle. The
  alternative, tinting with an overlay, would paint the sprite's transparent corners too.
   */
  private val AcceleratingTint = "#35c46a"
  private val BrakingTint = "#e8483c"

  private val AcceleratingFilter = "acceleratingCar"
  private val BrakingFilter = "brakingCar"

  /** Which tint, if any, a car in this state is drawn through. */
  def filterFor(motion: Motion): Option[String] = motion match {
    case Motion.Accelerating => Some(AcceleratingFilter)
    case Motion.Braking      => Some(BrakingFilter)
    case Motion.Steady       => None
  }

  /**
    * The two tints every car shares, defined once and referenced by the cars that want them.
    *
    * Flooding the filter region with a colour and compositing it against the sprite's own
    * alpha gives a car-shaped patch of that colour; multiplying the sprite by that patch
    * recolours the paintwork while leaving the windows and the shadow under the car dark.
    */
  def tintDefinitions: JsDom.TypedTag[dom.svg.Element] =
    svgTags.defs(
      tintFilter(AcceleratingFilter, AcceleratingTint),
      tintFilter(BrakingFilter, BrakingTint)
    )

  private def tintFilter(name: String, colour: String): JsDom.TypedTag[dom.svg.Element] =
    svgTags.filter(id := name)(
      svgTags.feFlood(attr("flood-color") := colour, attr("result") := "paint"),
      svgTags.feComposite(
        attr("in") := "paint",
        attr("in2") := "SourceAlpha",
        attr("operator") := "in",
        attr("result") := "carShapedPaint"
      ),
      svgTags.feBlend(
        attr("in") := "SourceGraphic",
        attr("in2") := "carShapedPaint",
        attr("mode") := "multiply"
      )
    )

  /**
    * The indicator colour. Amber because nothing else on the road is - the tarmac is dark,
    * the paint is white and yellow-grey, and the two tints a car can wear are green and red.
    */
  private val SignalTint = "#ffc233"

  /** The paler amber the car itself is washed with, so a flash reads as light on paintwork. */
  private val SignalGlow = "#fff2c4"

  /** How many chevrons are in the air at once. */
  private val SignalChevrons = 3

  /** How many times the run of them repeats over the course of one warning. */
  private val SignalRepeats = 3

  /** How far the last chevron gets from the car's flank, in car widths. */
  private val SignalReach = 2.4

  /** How many ping rings are expanding at once, spaced evenly round the same clock. */
  private val SignalPings = 2

  /**
    * How far a ping gets from the car before it goes out, in car lengths.
    *
    * Kept close in. The first try at this sent rings out nearly three car lengths on the
    * grounds that bigger is easier to see, and on a road where the cars are one length apart
    * that draws a haze of overlapping hoops across the whole loop: every car is inside
    * somebody's ping, so a ping stops meaning anything. A pulse that stays within reach of
    * the car it belongs to is the one that still points at a car.
    */
  private val PingReach = 1.0

  /**
    * One chevron, in the car's own frame: an apex out to the side, and two arms trailing it.
    *
    * Kept as geometry rather than going straight to markup because which side of the car
    * these end up on is the one thing about the whole effect that can be silently wrong - a
    * sign the wrong way round draws a perfectly convincing arrow at the lane the driver is
    * leaving.
    */
  case class Chevron(apexX: Double,
                     apexY: Double,
                     halfSpan: Double,
                     sweep: Double,
                     opacity: Double) {

    /** Arms swept back from the apex, against the way it is travelling. */
    def points: String =
      s"${apexX - halfSpan},${apexY - sweep} $apexX,$apexY ${apexX + halfSpan},${apexY - sweep}"
  }

  /**
    * A car announcing where it is about to go: chevrons breaking off its flank and running
    * out across the line it means to cross, brightening as the moment approaches.
    *
    * Motion is what the eye picks up in the corner of it, so the point of drawing this rather
    * than a static arrow is that the chevrons travel. Everything here is a function of how far
    * through the warning the driver is, which is what makes them move: the canvas is redrawn
    * from the simulation every tick, so a progress that keeps climbing is an animation.
    *
    * These are laid out in the car's own frame, where x runs the way it is pointing and y runs
    * to its left - the same sense the signal's direction is given in, so a left change is
    * simply a positive one.
    */
  def chevronsFor(
    signal: LaneChangeSignal,
    carLength: Double,
    carWidth: Double
  ): Seq[Chevron] = {
    val flank = carWidth / 2.0

    (0 until SignalChevrons).map { chevron =>
      // Evenly spaced round the same clock, so one leaves as the one before it fades out.
      val phase = wrapped(signal.progress * SignalRepeats + chevron.toDouble / SignalChevrons)

      Chevron(
        apexX = carLength / 2.0,
        apexY = flank + signal.direction * (flank + phase * carWidth * SignalReach),
        halfSpan = carLength * (0.16 + 0.18 * phase), // Spreading as it goes, like a wake.
        sweep = carWidth * 0.4 * signal.direction,
        // Holding its brightness most of the way out and then going, so a chevron reads as a
        // bright thing travelling: amber at half strength over dark tarmac looks like dirt.
        opacity = math.pow(1.0 - phase, 0.5) * intensityOf(signal)
      )
    }
  }

  /** Quieter while the driver has only just made its mind up, full strength as it goes. */
  private def intensityOf(signal: LaneChangeSignal): Double =
    0.65 + 0.35 * clamped(signal.progress)

  /**
    * On, off, on: an indicator rather than a shimmer.
    *
    * A sine spends most of its life somewhere in the middle, and at the size a car is drawn
    * here that reads as a car which is slightly the wrong colour rather than as a car that is
    * flashing. What the eye catches across a crowded ring is the transition, so what to draw
    * is the squarest wave the tick rate will carry - held on a little longer than off, which
    * is how a real indicator looks and stops the effect flickering into nothing.
    */
  private def flash(signal: LaneChangeSignal): Double =
    if (wrapped(signal.progress * SignalRepeats) < 0.6) 1.0 else 0.15

  def laneChangeSignal(
    signal: LaneChangeSignal,
    carLength: Double,
    carWidth: Double
  ): Seq[JsDom.TypedTag[dom.svg.Element]] = {
    val chevrons = chevronsFor(signal, carLength, carWidth).map { chevron =>
      svgTags.polyline(
        svgAttrs.points := chevron.points,
        svgAttrs.fill := "none",
        svgAttrs.stroke := SignalTint,
        svgAttrs.strokeWidth := math.max(2.0, carWidth * 0.3).toString,
        svgAttrs.strokeLinecap := "round",
        svgAttrs.strokeLinejoin := "round",
        svgAttrs.opacity := chevron.opacity.toString
      )
    }

    // Outermost first, so the car ends up sitting on top of its own warning rather than under it.
    (pings(signal, carLength, carWidth) :+ halo(signal, carLength, carWidth)) ++ chevrons
  }

  /**
    * Rings breaking off the car and running out across the tarmac, well past its own footprint.
    *
    * This is the part that has to work at a glance across the whole ring, and nothing drawn
    * inside a car's own outline can do that: at this scale a car is a smudge a few pixels
    * across, and decorating a smudge gets you a slightly different smudge. Something several
    * car lengths wide, expanding, is a different kind of event on the canvas - it is the only
    * thing on screen that grows, and it is visible from the far side of the loop.
    *
    * Centred on the car and drawn round rather than along it, so which way the car happens to
    * be pointing makes no difference to how findable it is.
    */
  private def pings(
    signal: LaneChangeSignal,
    carLength: Double,
    carWidth: Double
  ): Seq[JsDom.TypedTag[dom.svg.Element]] =
    (0 until SignalPings).map { ring =>
      // Spaced round the same clock as the chevrons, so one leaves as the one before it goes.
      val phase = wrapped(signal.progress * SignalRepeats + ring.toDouble / SignalPings)

      svgTags.circle(
        svgAttrs.cx := (carLength / 2.0).toString,
        svgAttrs.cy := (carWidth / 2.0).toString,
        svgAttrs.r := (carLength * (0.5 + phase * PingReach)).toString,
        svgAttrs.fill := "none",
        svgAttrs.stroke := SignalTint,
        // Thinning as it spreads, the way a ring of anything travelling outwards does.
        svgAttrs.strokeWidth := math.max(1.5, carWidth * 0.5 * (1.0 - phase)).toString,
        // Gone before it reaches its full radius, so the road isn't permanently hooped.
        svgAttrs.opacity := (math.pow(1.0 - phase, 1.3) * intensityOf(signal)).toString
      )
    }

  /**
    * The car itself lit up, on the same clock as everything else.
    *
    * The pings say where to look and the chevrons say which way; this says which car, which is
    * the question a ring of traffic makes hard - a ping centred between two cars a few pixels
    * apart is a ping on both of them. It used to be an outline only, on the grounds that a
    * wash of colour muddies the tarmac, but an outline two pixels wide loses to the green and
    * red the cars are already wearing. Filling it and flashing it hard wins that argument: for
    * the two thirds of each cycle it is lit, the car is plainly a different colour from every
    * other car on the road.
    */
  private def halo(
    signal: LaneChangeSignal,
    carLength: Double,
    carWidth: Double
  ): JsDom.TypedTag[dom.svg.Element] = {
    val spill = carWidth * 0.5
    val strength = flash(signal) * intensityOf(signal)

    svgTags.rect(
      svgAttrs.x := (-spill).toString,
      svgAttrs.y := (-spill).toString,
      svgAttrs.width := (carLength + 2 * spill).toString,
      svgAttrs.height := (carWidth + 2 * spill).toString,
      svgAttrs.rx := (carWidth / 2.0 + spill).toString, // A pill, so it hugs a car rather than boxing it.
      svgAttrs.fill := SignalGlow,
      svgAttrs.fillOpacity := (0.55 * strength).toString,
      svgAttrs.stroke := SignalTint,
      svgAttrs.strokeWidth := math.max(2.0, carWidth * 0.28).toString,
      // Never quite out, so between flashes it still says which car this is about.
      svgAttrs.opacity := (0.3 + 0.7 * strength).toString
    )
  }

  private def clamped(fraction: Double): Double = math.max(0.0, math.min(1.0, fraction))

  /** Where in its own cycle a repeating thing is, given how many cycles have gone by. */
  private def wrapped(cycles: Double): Double = {
    val remainder = cycles % 1.0
    if (remainder < 0) remainder + 1.0 else remainder
  }

  /** How far in from the kerb the painted lines sit, as a fraction of the road's width. */
  private val EdgeLineInset = 0.42

  /**
    * Painted lines are a share of the road's own width, so they stay in proportion as the
    * canvas grows, with a floor that keeps them from vanishing on a small one.
    */
  private def edgeLineWidth(roadWidthInPixels: Double): Double =
    math.max(1.0, roadWidthInPixels * 0.12)
}

class Window(scene: Scene, canvasWidth: Int, availableHeight: Int) {
  import Window.{edgeLineWidth, DividerLine, EdgeLine, EdgeLineInset, Tarmac}

  private val projection: Projection = scene.project(canvasWidth, availableHeight)

  val svgNode: JsDom.TypedTag[SVG] =
    svgTags
      .svg(
        attr("viewBox") := projection.viewBox,
        onwheel := { wheelEvent: dom.MouseEvent =>
          println("we want to zoom in/out here." + wheelEvent)
        }
      )(
        Window.tintDefinitions,
        svgTags.g(
          createSvgReps(scene.roadShapes.map(createRoadSvgRepresentation)),
          createSvgReps(scene.renderables.map(createCarSvgRepresentation))
        )
      )

  private def createSvgReps(
    drawables: Seq[JsDom.TypedTag[G]]
  ): JsDom.TypedTag[G] =
    svgTags.g(
      for {
        t <- drawables
      } yield {
        t
      }
    )

  /**
    * The road under the traffic: a band of tarmac with a line down either edge.
    *
    * A ring is drawn as an actual circle rather than a many-sided polygon, so it stays
    * smooth however far in you zoom.
    */
  private def createRoadSvgRepresentation(road: RoadShape): JsDom.TypedTag[G] =
    road match {
      case RoadRing(center, radius, width) =>
        val cx = projection.xOf(center)
        val cy = projection.yOf(center)

        def ring(atRadius: Length, colour: String, thickness: Double) =
          svgTags.circle(
            svgAttrs.cx := cx.toString,
            svgAttrs.cy := cy.toString,
            svgAttrs.r := projection.across(atRadius).toString,
            svgAttrs.fill := "none",
            svgAttrs.stroke := colour,
            svgAttrs.strokeWidth := thickness.toString
          )

        val tarmacWidth = projection.across(width)
        val lineWidth = edgeLineWidth(tarmacWidth)

        svgTags.g(cls := "roadway")(
          ring(radius, Tarmac, tarmacWidth),
          ring(radius + width * EdgeLineInset, EdgeLine, lineWidth),
          ring(radius - width * EdgeLineInset, EdgeLine, lineWidth)
        )

      case DividerRing(center, radius, width) =>
        val renderedRadius = projection.across(radius)
        val lineWidth = edgeLineWidth(projection.across(width))
        // A dash and a gap of the same length, sized off the road rather than the screen, so
        // the line reads as painted markings at any zoom.
        val dash = math.max(4.0, renderedRadius * 0.05)

        svgTags.g(cls := "lane-divider")(
          svgTags.circle(
            svgAttrs.cx := projection.xOf(center).toString,
            svgAttrs.cy := projection.yOf(center).toString,
            svgAttrs.r := renderedRadius.toString,
            svgAttrs.fill := "none",
            svgAttrs.stroke := DividerLine,
            svgAttrs.strokeWidth := lineWidth.toString,
            svgAttrs.strokeDasharray := s"$dash $dash"
          )
        )

      case RoadStrip(from, to, width) =>
        val toEdgeLine = projection.down(width) * EdgeLineInset

        def stripe(offset: Double, colour: String, thickness: Double) =
          svgTags.line(
            svgAttrs.x1 := projection.xOf(from).toString,
            svgAttrs.y1 := (projection.yOf(from) + offset).toString,
            svgAttrs.x2 := projection.xOf(to).toString,
            svgAttrs.y2 := (projection.yOf(to) + offset).toString,
            svgAttrs.stroke := colour,
            svgAttrs.strokeWidth := thickness.toString
          )

        val tarmacWidth = projection.down(width)
        val lineWidth = edgeLineWidth(tarmacWidth)

        svgTags.g(cls := "roadway")(
          stripe(0, Tarmac, tarmacWidth),
          stripe(-toEdgeLine, EdgeLine, lineWidth),
          stripe(toEdgeLine, EdgeLine, lineWidth)
        )
    }

  // TODO This should go somewhere else, on its own.
  private def createCarSvgRepresentation(vehicle: RenderedVehicle): JsDom.TypedTag[G] = {
    val CIRCLE: String = "conceptG"

    val renderedWidth = projection.across(vehicle.width)
    val renderedHeight = projection.down(vehicle.height)

    // A car's position is its centre, so the sprite hangs half of itself back and up from
    // there, and turns about that same centre to face along the road.
    val x = projection.xOf(vehicle.position) - renderedWidth / 2
    val y = projection.yOf(vehicle.position) - renderedHeight / 2
    val turn =
      s"rotate(${vehicle.headingInDegrees}, ${renderedWidth / 2}, ${renderedHeight / 2})"

    // A steady car is left exactly as it was drawn before, so colour reads as something
    // happening rather than as a permanent property of the traffic.
    val tint = Window.filterFor(vehicle.motion).map(name => svgAttrs.filter := s"url(#$name)")

    // Drawn inside the car's own group, so a chevron turns with the car it belongs to.
    val signal = vehicle.signal
      .map(Window.laneChangeSignal(_, renderedWidth, renderedHeight))
      .getOrElse(Seq.empty)

    svgTags.g(
      cls := CIRCLE
    )(
      svgAttrs.transform := s"translate($x, $y) $turn"
    )(
      signal,
      svgTags.image(
        href := "images/sedan.svg",
        width := renderedWidth.px,
        height := renderedHeight.px,
        tint,
        onclick := { _: dom.MouseEvent =>
          println(vehicle.uuid)
        }
      )
    )
  }
}
