# Overlays and tiles

A living design note, trimmed to what still holds.

[creativity_plan.md](creativity_plan.md) supersedes parts of what this document
originally said. Everything below either agrees with that plan or is a workstream
the plan treats as separate and still live. What was cut, and what replaced it,
is listed at the end rather than left in place to be rediscovered.

Original decision numbers are kept, so the gaps are where the cuts were and
`creativity_plan.md`'s reference to "question 8" still resolves.

Two things the page is being asked for:

1. **Readings inside the road.** The counter and the rate currently sit in a
   panel below the canvas. On a ring there is a large empty disc in the middle
   of the drawing doing nothing, and that is where they belong - and the way to
   put them there should generalise, so any scene can say where its own open
   space is rather than every overlay hard-coding "the middle of the circle".

2. **Roads as pieces.** Road elements as connected pieces with inputs and
   outputs on their sides, so scenes are arrangements of pieces rather than one
   bespoke shape each. A ring becomes four curves; a street becomes straights
   and an exit; junctions and merges become possible for the first time.

These meet at one idea: **a scene should be able to describe its own geometry** -
where the tarmac is, where the gaps are, which way traffic enters and leaves.
The overlay needs the "where are the gaps" half. Pieces need all of it.

The two are independent workstreams. Neither blocks the other.

## What exists today

- `Path` (`physics/Path.scala`) - `StraightPath` and `RingPath`. A position is
  an arc length; points, headings and normals are derived. `extent` gives the
  world rectangle a path occupies.
- `TrackLane` / `TrackRoad` (`traffic/`) - lanes along paths, with lane
  changing between them. Closed paths wrap; open ones clamp.
- `Lane` / `Street` - the older straight-road model, with sources that make
  cars and a destination that removes them.
- `Scene` (`traffic/Scene.scala`) - `StreetScene` and `RingScene`. Each one
  answers `renderables`, `roadShapes`, `project(width, height)`, and now
  `completed`. `project` is where each scene already decides how its world maps
  onto pixels, and `RingScene.project` already knows the road cannot fill a
  tall box.
- `RoadShape` (`svgRendering/`) - `RoadRing`, `RoadStrip`, `DividerRing`,
  `CountingLine`. Drawn by `Window`, which is the only place that knows SVG.
- `CompletionTally` (`traffic/`) - the total and the recent rate, held by
  `Model` because the rate is a reading rather than a fact about the traffic.

## The overlay workstream

`creativity_plan.md` calls this "a related workstream, not an assumed
prerequisite for the new road system". It is not a step toward the road work and
the road work does not wait on it. The decisions below stand on their own.

1. **Readings inside the geometry.** Put the readings inside the ring, through a
   scene-declared description of its own open space, rather than hard-coding a
   position per scene.

2. **Free regions, largest wins.** A scene returns a list of empty world
   rectangles - no names, no roles. An overlay sorts them by area and takes the
   biggest one its content will fit in.

   ```
   scene.freeRegions: List[WorldBox]
   overlay picks maxBy(area) that fits
   ```

   The appeal is that no scene has to anticipate what an overlay wants, and no
   overlay has to know what shape of road it is on: a ring reports its middle,
   a street reports the band above the tarmac, and a future arrangement of
   pieces reports whatever holes the arrangement happens to leave. Placement
   stays a property of the geometry rather than of anybody's intent.

   Worth watching: with nothing named, the largest box is not always the one a
   person would have chosen, and the choice can change as a scene changes
   shape. If that bites, roles can be layered on later as a filter over the
   same list rather than as a replacement for it.

   **Open, per the plan.** `creativity_plan.md` proposes a compact bottom sheet
   for the editor's palette and inspector, and asks that it be compared with
   this free-space approach - an overlay must not end up covering a merge or the
   target a finger is reaching for. Free regions may be right for readings and
   wrong for controls. Not yet settled either way.

3. **SVG inside the canvas.** The readings become part of the drawing rather
   than a div floated over it.

   ```
   Scene.overlays -> List(Readout(box, lines))
   Window draws svgTags.text inside the <svg>
   ```

   One coordinate system end to end: free regions are world boxes and so is the
   text's placement, so nothing has to be converted or kept in step with a
   separate DOM layer. It also means the numbers travel with the picture - zoom,
   resize or ever export it and they are still where they belong.

   The cost is that typography becomes arithmetic. Font size has to be derived
   from the box and the projection rather than set in CSS, and none of the
   panel's existing `.reading` styling carries over, so the look has to be
   rebuilt in SVG attributes - including its own answer for dark mode, since the
   canvas paints its own colours already.

4. **The readings move in.** The tally row leaves the panel rather than being
   duplicated in it, and the panel row is what a scene with no room falls back
   to. So a ring shows its numbers inside the loop and a street shows them in
   the row underneath, and neither shows them twice.

   That also gives the road back the row's worth of height, so
   `Client.RoomForTheControls` goes back to what it was before the counter
   arrived.

   The thing to keep an eye on: the numbers now move when you change scene, and
   a reading that changes places is a reading you have to look for. It is
   accepted because two copies of one figure is the worse fault - but if the
   hunting turns out to be annoying, the fallback rule in decision three (panel
   only when nothing fits) is the next thing to try, not going back to both.

5. **Two equal readings, stacked.** The same shape as the panel row it
   replaces - name above value, twice, both the same size - rather than a big
   headline number with the rate demoted under it.

   ```
   COMPLETED        RATE
      142          22/min
   ```

   Which is the quieter choice, and the point of it is that the reading is the
   same reading wherever it has been put. A figure that is a centrepiece inside
   the ring and a small pill under a street is two different-feeling numbers,
   and moving between scenes would read as a change in what is being measured.
   Keeping the shape means only the position moves.

   It also keeps the door open: two equal readings is a list, so a third - mean
   speed, density, whatever the road turns out to want to say - is another entry
   rather than a redesign.

### Phase 1: the overlay

Everything above is settled, so this can be picked up cold.

- **`Scene.freeRegions: List[WorldBox]`** - a new member on the sealed trait,
  answered by each scene in world coordinates. `RingScene` reports the disc
  inside its innermost lane, less the lane width. `StreetScene` reports
  nothing at first, so it keeps the panel row.
- **`WorldBox`** - centre, width, height, in `physics`, beside `PathExtent`
  which is already exactly this shape. Likely the same type, renamed or reused.
- **A readout the canvas can draw** - a list of name/value pairs plus the box it
  was placed in. Composed outside the scene, because the rate lives in `Model`
  by an earlier decision and the scene has no business knowing it. So `Window`
  takes the readings alongside the scene, picks the largest region that fits,
  and lays the text out in it.
- **Fitting** - the text has a natural size in world metres; a region fits if
  the text is inside it with margin. Nothing fits, nothing is drawn, and the
  panel row appears instead.
- **`ControlElements`** - the tally row becomes conditional on the scene having
  nowhere to draw it. `Client.RoomForTheControls` returns to 140.
- **Colour** - the canvas paints its own, so the readings need their own light
  and dark values rather than the panel's CSS variables. The blue already used
  for the counting line is the value's colour; the names want a grey that reads
  on tarmac and on white.

## What survives of the tile proposal

The piece contract now lives in `creativity_plan.md` under "Proposed contract for
a road piece", which is more detailed than anything here and is where new work
should go. Two of this document's decisions survived into it.

6. **A piece does not hold cars.** Geometry, ports and parameters live on the
   piece; vehicles live in the runtime and are located on lane sections. This is
   the part of the original decision 6 that the plan kept, and it is the reason
   a piece can be edited under running traffic at all.

   What did *not* survive is the rest of that decision - see the ledger below.

7. **A side carries several connection points, not one.** A side is however many
   the road needs there, so a four-way intersection, a merge and a branch are
   all expressible.

   ```
   Piece
     sides: N, E, S, W
     each side: List[Port]      (a side may also have none)
   ```

   The plan refines the unit of connection: a **port** carries an ordered list of
   **lane endpoints**, and a connection maps one outgoing endpoint to one
   incoming endpoint. That avoids the ambiguity in the original wording, where a
   port was both a single lane and a list of them.

   The original decision also claimed this settled grid-versus-free-ports as
   "both", with the grid deriving port positions. The plan leaves grid placement
   open (D1) and makes ports the connection authority on their own. The
   ports-are-the-contract half stands; the grid half is open.

8. **Answered.** _(originally: branching breaks "one continuous path per lane" -
   is a path a whole route through the network, or are segments joined into a
   graph?)_ `creativity_plan.md` answers it: a **directed lane graph**, with
   continuous distance along each section and explicit movements between them.

   The diagnosis that raised the question was correct and is worth keeping,
   because it is why the answer costs what it does. A car in a lane approaching a
   four-way intersection has three ways to leave it, so "the path this lane is"
   is no longer a single thing - and `TrackLane`'s whole model, an arc length
   along one path with the leader at index - 1, depends on it being single.
   Everything the graph needs that a single path did not - leader lookup across a
   seam, residual distance carried over a boundary, a body occupying two sections
   at once - follows from that.

## Ledger: what creativity_plan.md superseded

| This document said | What replaced it | Where |
| --- | --- | --- |
| Overlay first; pieces come after and fill in the same description | Overlays are a related workstream, not a prerequisite for the road system, and do not sequence it | plan, "Existing work and the architectural hinge" |
| Free-space overlays are the way to place things on the canvas | Still open for editor controls; compare against a compact bottom sheet that cannot cover a merge or a finger's target | plan, "Geometry and five-inch screens" |
| An arrangement compiles to one joined `Path` per lane, and `TrackLane` drives on it unchanged | A directed lane graph. Joining consecutive geometry internally is allowed, but branches and stable editor IDs are retained | plan, P2 and "How traffic moves through them" |
| A tile is geometry only and can carry no rules of its own | Local speed rules and junction controls may be authored on a piece and compiled into the network. Keeping cars out of pieces does not require pieces to be geometry alone | plan, "Proposed contract for a road piece" |
| The grid arranges pieces and derives port positions, settling grid-versus-ports as "both" | Ports are the connection authority. Grid placement remains open (D1); grid and angle guides are optional aids, not the contract | plan, D1 and "How pieces are placed" |
| A port is a lane, and also a list of lanes | A port carries ordered lane endpoints; connections map endpoint to endpoint | plan, "Proposed contract for a road piece" |
| Question 8 is open and the tile design is blocked on it | Answered: directed lane graph | plan, "How traffic moves through them" |
