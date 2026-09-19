# Road-building sandbox: creativity plan

Status: first discussion draft, 2026-09-18. This is a design conversation and
implementation checklist, not approval to implement every proposal below.
Record answers here as they arrive; distinguish requirements, prior decisions,
recommendations, and unresolved choices.

## What we want

- Assemble and rearrange road systems from primitive pieces, like 2D Lego.
- Start with highways, including merges and splits.
- Leave a credible path toward neighborhood streets and intersections.
- Track each piece's connection points, lane directions, and vehicle input/output.
- Keep traffic and controls understandable on approximately five-inch screens.
- Build and watch on phones in the first version (confirmed by the user).
- Prioritize plausible individual drivers and believable congestion when realism
  and ease of experimentation conflict (confirmed by the user).
- Support quick experiments: change a layout or traffic demand and see the result.
- Make creating and arranging roads fun and engaging, not merely possible.
- Keep traffic running during geometry edits and preserve vehicles wherever
  possible (confirmed by the user).

The user requested planning and brainstorming first. No road-model or UI changes
are part of this draft.

## Existing work and the architectural hinge

[TILES.md](TILES.md) contains earlier decisions: grid placement, multiple lane
ports on each side, and tiles that describe geometry rather than owning cars.
It also proposes overlays in free space; that is a related workstream, not an
assumed prerequisite for the new road system. Keep that document intact while
reconciling its decisions here.

Its unresolved question 8 is fundamental: can lanes remain single joined paths,
or must they become a graph? A branch cannot be represented by one unambiguous
path. Prebuilding a separate whole path per route duplicates shared road unless
vehicle occupancy and interactions are still indexed on common lane sections.

Relevant implementation findings:

| Current code | Useful foundation | Change required for networks |
| --- | --- | --- |
| `physics/Path.scala` | Distance along a path; position, heading, normal, bounds | Partial arcs, composite paths, transforms; preserve distance when crossing boundaries instead of clamping it away |
| `traffic/TrackLane.scala` | Individual vehicles, IDM following, ordering along a lane | Find leaders across connected sections; handle a vehicle whose body spans a boundary |
| `traffic/TrackRoad.scala` | MOBIL choices, gap checks, lane-change animations | Explicit adjacent-lane intervals and position mapping; normalized whole-lane progress is insufficient at ramps and lane drops |
| `traffic/Scene.scala` | Common rendering and scene interface | Add a network scene alongside existing street/ring scenes |
| `svgRendering/Projection.scala` | World-to-screen projection and uniform fitting | Persistent camera, inverse mapping for touch, zoom and pan |
| `Window.scala`, `Client.scala` | SVG rendering and reactive controls | General curves; retain static road elements instead of replacing the complete SVG on updates |
| `uimodules/Model.scala` | Scenario controls and state | Separate editable network, runtime traffic, and camera state; decouple simulation advancement from display frames |

Do not make `TrackRoad.update` traverse each tile independently: downstream
queues and competing merges need a view of the connected network.

## Approaches and trade-offs

### How pieces are placed

| Approach | Good at | Costs |
| --- | --- | --- |
| Equal square tiles, fixed lane slots | Easy snapping, rotation, touch placement and validation | Large curves and ramps need many tiles; catalog grows with lane counts and orientations |
| Grid with parameterized pieces spanning several cells | Lego feel plus long tapers, broad bends and reusable interchange assemblies | Footprint/overlap checking and port compatibility are more involved |
| Rigid pieces freely positioned, snapping compatible ports | Less grid restriction; automatic alignment to an existing road | Closing loops and joining two existing branches may require a different piece or moving an assembly |
| Adjustable curves with snapping endpoints | Natural geometry and arbitrary junction angles | Harder touch editing, curve generation, and geometry validation |

**Working recommendation, awaiting confirmation:** make ports authoritative and
compare grid assembly with growing roads from ports before choosing the default
placement interaction. Optional grid/angle guides could organize space without
constraining every road. Parameterized pieces spanning several cells remain a
useful grid candidate. Mirroring must regenerate lane directions and mappings,
not just flip the artwork.

### Discussion: which placement system is more fun?

The user explicitly values fun and engagement. Grid placement is still open;
the initial grid recommendation has been broadened to compare port-first placement.
The predictions below are design judgments to test on a phone.

Three independent choices are easy to confuse:

1. **Position:** on a grid or anywhere in the world.
2. **Shape:** fixed piece, adjustable length/radius, or freely drawn curve.
3. **Connection:** explicit compatible lane endpoints in all cases.

A freely positioned fixed bend cannot necessarily join two already placed ends.
Snapping one end determines its position and orientation; the other end may
still miss. Connecting both ends needs suitable geometry, adjustment of other
pieces, or an explicit generated connector. This is the largest hidden cost of
free placement, particularly when closing loops or completing an interchange.

| Experience | Grid snapping | Free placement with port snapping |
| --- | --- | --- |
| Source of enjoyment | Quick assembly, clear choices, a satisfying construction puzzle | Shaping a place, sweeping curves, more personal-looking layouts |
| First successful road | A small compatible kit can make this very quick | Growing from an existing port can also be quick if rotation aligns automatically |
| Building neighborhoods | Repeated blocks and parallel streets are straightforward | Irregular streets feel natural; parallelism and equal spacing need optional guides |
| Building highways | Predictable modules, but ramps may feel constrained by the grid | Broad bends and oblique ramps are easier to express with adjustable geometry |
| Common frustration | The layout wanted is between cells, or the catalog lacks a fitting piece | A nearly connected join, ambiguous snap target, or a loop that will not close |
| Phone interaction | Coarse cells reduce positioning precision, though a finger can still obscure the preview | Large magnetic targets and alignment previews reduce precision; raw rotate/drag handles would be cumbersome |
| Supporting complexity | Compatible footprints, lane slots and a useful piece catalog | Snap-target selection, rotation, collision checking, and potentially a curve/constraint solver |

Neither placement system by itself solves merges, lane-count mismatches or
intersection conflicts. Both produce the same lane graph. A grid only helps
connections fit when the kit's dimensions and ports were designed to fit it.

**Candidate interaction A: assemble pieces.** Choose a straight, bend or ramp;
preview it at a compatible grid location; tap to place; rotate using a button.
Make common pieces adjustable in bounded increments so the fun is not interrupted
by hunting through dozens of nearly identical tiles.

**Candidate interaction B: grow from a connection.** Tap an open road end, choose
straight/bend/ramp, and drag or tap the desired extent. The starting end stays
attached and its heading aligns automatically. Nearby compatible ends attract
the preview. Length/radius adjustments create geometry within defined constraints.
This is more guided than placing arbitrary floating pieces, and could be the more
engaging highway interaction if it remains easy to control on a phone.

**Possible combination:** use ports as the connection authority, offer grid and
angle guides for organizing space, and let a compatible port alignment override
a guide. Show a stable preview before committing. Do not switch snap targets
with tiny finger movements; retain the selected target until the user moves away.
Expose a simple way to choose a different nearby target. A combination is useful
only if it feels like one predictable interaction, not two competing snap systems.

The confirmed simulation priority is plausible drivers and believable congestion.
The editor may propose a valid curve or longer taper automatically; it must show
the resulting shape before accepting it. Do not silently shrink physical queue
storage, remove vehicles, or create an instantaneous merge to make a join appear
successful. Curve speeds and acceleration-lane lengths need coherent world units,
even if the displayed cars or selection handles are enlarged for readability.

Before settling placement, compare A and B on the same touch tasks: extend a
highway, attach an on-ramp, close a loop, build a block, then change a road already
connected at both ends. Assess time to first working traffic, accidental edits,
undo/correction frequency, and whether the user wants to keep experimenting.
Prototypes can initially use static roads and a preview vehicle; a full simulation
rewrite is not needed to discover which editor interaction feels better.

### Live edits while traffic keeps running

Confirmed: keep traffic running and preserve vehicles wherever possible. This
rules out resetting the whole simulation as the normal geometry-edit workflow.

Proposed mechanics, still to validate:

- Manipulate a preview while the current network runs. Commit a valid graph and
  its vehicle-state migration together at a simulation-step boundary; traffic
  must never observe a half-connected network.
- Preserve stable lane/vehicle IDs, current traffic and queues on unaffected roads.
  Recompute affected route continuations and adjacency when topology changes.
- Adding an empty branch is the simplest case. Shortening an occupied road,
  changing lane count, or moving a connected endpoint needs an explicit policy
  for cars, rear-body occupancy, in-progress lane changes and insufficient space.
- Candidate policy for disruptive edits: prevent new entry to affected sections,
  let them clear while the rest of the network runs, then apply the change. Show
  that an edit is pending and allow cancellation. Blocked traffic might never
  clear, so this cannot be the only policy.
- Decide alternatives for edits that cannot preserve all cars: reject the edit
  with an explanation, allow explicit removal with accounting, or another user-
  chosen behavior. Do not silently teleport cars into a safe-looking gap.
- Undoing geometry is also a live network change. It cannot restore old vehicle
  positions without rewinding traffic; distinguish geometry undo from replay.

This increases the value of bounded local edits. Grid pieces help constrain an
edit's footprint, but deleting an occupied grid tile still needs migration rules.
With free placement, moving a road can also reshape its neighbors; show exactly
which sections will change rather than silently adjusting a large connected area.
Growing a new branch from an open port is a promising common interaction for both.

### How traffic moves through them

| Approach | Good at | Costs / limits |
| --- | --- | --- |
| Stitch tiles into whole continuous lanes | Smallest change for rings and unbranched roads | Merges/splits still require shared occupancy and route logic; poor network foundation alone |
| Directed lane graph with continuous distance on each section | Individual cars, shared queues, branches, ramps, future intersections | Requires boundary lookahead, transfer arbitration and route-aware lane changes |
| Discrete cells or aggregate queues | Fast experiments with many vehicles; explicit flow/storage accounting | Coarser driving and animation; substantial change to current IDM/MOBIL behavior |
| External simulator, e.g. SUMO | Existing network and intersection behavior; potential calibration/export path | Integration and deployment work; keeping a client-only phone app would need separate investigation |

**Recommendation, awaiting confirmation:** a directed lane graph, reusing the
current driver equations where appropriate. Join simple consecutive geometry
pieces internally if useful, but retain graph branches and stable editor IDs.
Road-piece boundaries need not create artificial driving decisions.

SUMO's network design distinguishes roads, lanes, junction rules and lane
connections. This is a useful precedent for that separation, not a recommendation
to adopt its complete data format or runtime.
[Reference: SUMO road networks](https://sumo.dlr.de/docs/Networks/SUMO_Road_Networks.html).

## Proposed contract for a road piece

Keep three layers distinct:

```mermaid
flowchart TD
    A[Editable pieces: placement, ports, parameters] --> B[Validate and build lane graph]
    B --> C[Runtime traffic: vehicles, routes, queues, controls]
    A --> D[Road geometry]
    C --> E[Vehicle poses and traffic readings]
    D --> F[Camera and screen renderer]
    E --> F
```

Suggested vocabulary, not a final Scala API:

- **Piece definition:** parameterized straight, bend, merge, split, etc.; footprint,
  local lane geometry, connection points and allowed movements. Contains no live cars.
- **Piece instance:** stable ID, definition/version, placement, rotation, parameters.
- **Port:** stable ID, boundary side, local position/offset, travel tangent and
  ordered lane endpoints. Each endpoint has an ID, in/out direction relative to
  the piece, width and elevation layer. A side can have several ports or none.
- **Connection:** explicit mapping from an outgoing lane endpoint to an incoming
  endpoint on another piece. Ports carry lane lists; endpoints identify individual
  lanes, avoiding ambiguous claims that one port is both one lane and many lanes.
- **Lane section:** directed physical path with length, speed profile and allowed
  neighboring lanes over specified intervals.
- **Movement:** permitted continuation from an incoming section to an outgoing
  section, including a real traversable curve where needed. May carry a yield,
  signal or conflict rule. A tile seam can be a simple continuation.
- **Runtime vehicle:** one owner section, longitudinal distance, speed, driver
  state, intended continuation/route and lane-change state. Body occupancy can
  extend into neighboring sections without duplicating the vehicle.
- **Scenario:** network document plus demand sources, destinations, control settings
  and random seed. Camera and editor selection are separate from traffic state.

Connection validation must check position and tangent continuity, direction,
lane correspondence, width compatibility and layer. Opposing boundary normals
face each other; the vehicle's travel tangent remains continuous. A lane-count
change requires an explicit taper/merge/split, not implicit disappearance of a lane.

Snapping proposes connections; validation makes them real. Crossing artwork never
automatically means connected roads. Overpasses need explicit layer separation;
same-level crossings need junction geometry and conflict rules.

Local speed rules and junction controls may be authored on a piece and compiled
into the network. Keeping live vehicles outside pieces does not require pieces
to be restricted to geometry alone. This is a proposed revision of TILES.md's
stronger geometry-only restriction.

## Capacity: four different quantities

Do not assign a single number called capacity and use it for everything.

| Quantity | Meaning | Example |
| --- | --- | --- |
| Connectivity | Which lanes can accept or send traffic | Two incoming lanes connect to one outgoing lane |
| Storage | How much stopped traffic fits physically | Queue length depends on section length, vehicle lengths and gaps |
| Demand / admission limit | Traffic wanting to enter, or a deliberate metering rule | Source requests 20 vehicles/minute; ramp meter limits admission |
| Observed throughput | Vehicles actually crossing a detector per unit simulation time | Congestion reduces completed departures despite unchanged demand |

Physical throughput should emerge from following distances, speeds, merging and
downstream space. An optional numeric cap can represent deliberate metering; it
does not override the need for a safe gap. Incoming capacity is shared when
several approaches compete for the same downstream lane.

Unadmitted demand needs an explicit policy: queue outside the modeled network,
discard it and count it, or model the upstream approach. Proposed default: retain
and display a virtual source queue, with a documented bound and overflow policy.
Internal congestion must back up onto upstream roads; it must not vanish at a seam.

## Merges and splits: behavior, not just connectors

A highway on-ramp usually needs an acceleration lane and a region where drivers
seek a gap. A two-to-one lane drop is a different primitive. A Y split must give
drivers enough advance knowledge to reach a permitted exit lane.

Proposed first kit:

1. Source and sink boundaries with configurable demand and destinations.
2. One-direction straight with a lane-count parameter; opposing directions can be
   composed into a two-way road assembly later.
3. Broad bend with supported radius and lane count.
4. Lane addition and lane drop with a taper of nonzero length.
5. On-ramp assembly: approach, acceleration lane, merge region.
6. Off-ramp assembly: approach, diverge region, exit branch.
7. Optional simple overpass, depending on the first-map requirements.

Offer convenient ramp pieces in the palette, but allow them to be made from the
same underlying sections and rules. Later, users can save groups as reusable
assemblies with exposed external ports.

Required simulation behavior:

- Look ahead through several downstream sections far enough to brake safely.
  A stopped leader just past a seam is still a leader.
- Carry leftover travel distance across boundaries, including more than one short
  section in a step. Reject zero-length traversable cycles.
- Keep each vehicle's identity and account for its rear clearing a seam/conflict.
- Decide competing merge entries from a consistent snapshot, resolve them in a
  deterministic order, then commit accepted moves together. Permit safe simultaneous
  movements; never advance a vehicle twice because of section iteration order.
- Check downstream receiving space and conflicts before admission. A denied
  movement creates a stopping constraint far enough upstream for braking.
- Treat ordinary continuation differently from yielding: road seams do not add stops.
- Define merge priority and starvation behavior. Compare mainline priority, zipper
  alternation and gap acceptance as explicit experiment choices.
- Choose a split continuation before the last moment. Keep that choice stable;
  repeatedly drawing a random branch while waiting creates erratic behavior.
- Define what happens when a driver misses an exit. Proposed default: continue
  legally and reroute where possible; never teleport or make an unsafe last-second cut.
- Support mandatory lane changes for an ending lane or planned exit, in addition
  to MOBIL's discretionary incentive to change lane.

For future neighborhood intersections, a movement also needs crossing conflicts,
right-of-way and downstream-clearance rules. These are distinct from endpoint
connectivity. SUMO documents this distinction through internal junction links and
intersection behavior.
[Reference: intersections](https://eclipse.dev/sumo/docs/Simulation/Intersections.html).

## Geometry and five-inch screens

**Unresolved product choice:** must the whole map and every car remain visible at
once, or may people zoom into detail? Arbitrarily large maps cannot satisfy both
on a small screen. Physical diagonal size also does not specify CSS viewport size.

Recommended starting direction, awaiting confirmation:

- Keep distances and speeds in physical world units. Changing screen size must
  never change demand, capacity, journey time or driver behavior.
- Use uniform map scaling; do not stretch individual axes to squeeze curved roads
  into a viewport. Start with lines and circular arcs; add spline curves when a
  concrete piece needs them. Splines need distance-based sampling, not uniform
  parameter increments that make speed vary visually.
- Large highway curves and acceleration lanes span multiple grid cells. Tight toy
  geometry at highway speeds needs either speed constraints or an explicitly
  schematic display mode; decide this deliberately rather than hiding it in scale.
- Provide overview, pinch zoom, pan, fit-map and focus-selection. Preserve camera
  position between ticks. A follow-vehicle mode is an optional experiment.
- At detail scale, show individual cars, lanes and merge signals. At overview
  scale, simplify cars to markers or show congestion/flow on roads. If markers
  need to be enlarged for visibility, their display size must not affect collision
  dimensions. Avoid enlarged markers covering adjacent lanes.
- Keep text and control sizes in screen units. Prototype 44–48 CSS-pixel touch
  targets, separate from road geometry; exact targets remain a usability decision.
- For editing: select a piece, show compatible ports and placement preview, rotate
  with a visible button, then confirm. Offer tap-to-place as well as dragging.
  Distinguish building gestures from camera gestures; undo should be prominent.
- Phone editing is required from the first release. Validate the complete touch
  sequence: choose, place, rotate, connect, select, adjust, delete and undo. No
  essential operation may depend on hover, right-click or a hardware keyboard.
  At dense junctions, select the piece first and expose its lane ports in a larger
  inspector rather than requiring a finger to hit each tiny endpoint on the map.
- Use a compact bottom sheet for the palette/inspector. Traffic remains visible
  while settings change. Overlay placement must not obscure a merge or a finger's
  target; compare this with TILES.md's free-space approach.
- Prototype portrait at 320, 360 and 390 CSS pixels wide, landscape, and a real
  target phone. Viewport emulation alone cannot validate touch comfort or speed.

Renderer choices: retained SVG is the lowest-disruption prototype and supports
selection naturally. Canvas may suit a larger moving fleet but needs manual hit
testing/accessibility support; a static SVG road layer plus Canvas vehicles is
another option. Choose after measuring an agreed map and vehicle count. WebGL is
not a prerequisite for this design.

Use a fixed simulation timestep with elapsed-time accumulation, a bounded catch-up
policy and rendering interpolation. Rendering fewer frames must not silently
reduce traffic throughput or change merge decisions. Pause on hidden tabs is a
possible explicit policy, to be decided.

## Experiments worth building

| Experiment | What it teaches us |
| --- | --- |
| Merge laboratory: two-lane highway, ramp, downstream sink | Whether queues propagate through joins, yielding looks believable, and the phone view explains who is waiting |
| Same layout, lane-drop versus acceleration-lane merge | Whether primitive composition captures different bottlenecks rather than only different artwork |
| Split, bypass, rejoin | Whether branch choices remain stable and shared downstream capacity is accounted for correctly |
| Short weaving section between entry and exit | Whether route-driven lane changes interact sensibly with discretionary passing |
| Highway ramp ending at a neighborhood T-junction | Whether the architecture extends to priority and crossing conflicts without a second traffic engine |

Creative possibilities after the basics: tap a port to offer compatible next
pieces; preview where a queue would spill when placing a bottleneck; paint a route;
save a compact interchange as a reusable piece; compare two layouts with identical
demand and random seed. These are ideas, not commitments.

## Decision register

| ID | Status | Decision / question |
| --- | --- | --- |
| R1 | Required | Composable roads with explicit connections, merges and splits |
| R2 | Required | Highway-first, extensible toward neighborhood streets |
| R3 | Required | Small-screen legibility is an architectural concern |
| P1 | Prior decision in TILES.md; reconfirm | Grid placement with lane ports on each side |
| P2 | Prior decision needs revision | Geometry-only joined paths cannot alone represent branching traffic |
| D1 | Recommended; pending | Port-connected parameterized pieces compile to a directed lane graph; compare placement interactions before choosing a default |
| D2 | Recommended; pending | Separate definitions/geometry, runtime traffic and viewport state |
| D3 | Recommended; pending | Physical following and receiving space determine flow; meters are explicit optional rules |
| D4 | Confirmed by user | Build and watch on phones in the first version |
| D5 | Confirmed by user; migration policy open | Keep traffic running and preserve vehicles wherever possible during geometry edits |
| D6 | Open | Fit-everything bounded play area versus zoomable overview/detail |
| D7 | Open | Split probabilities versus destinations and route planning |
| D8 | Open | Overpasses in the first highway kit |
| D9 | Open | First useful scenario, network size, vehicle count and target phone |
| D10 | Confirmed by user | Plausible individual drivers and believable congestion |
| D11 | Confirmed priority; interaction open | Road creation should be fun and engaging; compare piece assembly with growing roads from ports |

## Questions for our next passes

First batch has been asked: phone editing, placement style, fidelity, editing live
traffic, first map/scale, and what capacity means. Second batch: mobile viewing,
branch choices and overpasses. Phone building/viewing and plausible individual
drivers with believable congestion, and live editing with vehicle preservation
are confirmed. Placement is under active
discussion with fun and engagement as explicit priorities; other answers remain
pending. Recommendations are not recorded as user decisions.

Further questions to work through after those:

- What should we see within the first minute that makes the new editor worthwhile?
- Which merging behavior should be the default: mainline priority, zipper, or
  deliberately imperfect human gap acceptance?
- Is right-hand traffic sufficient initially, and are opposite directions needed
  in the first kit?
- Do neighborhood streets eventually include signals, stop signs, roundabouts,
  parked cars, pedestrians and bicycles? Which is the first necessary addition?
- Should each piece have a fixed physical size, or should users stretch lengths
  while preserving valid curves and taper geometry?
- Should disconnected pieces be allowed in drafts, with only runnable connected
  components simulated, or should Run require every port to be resolved?
- How should demand change: a steady rate, bursts, daily patterns, or user-triggered cars?
- Is local save/load enough first, or is sharing a map essential to tinkering?
- Which measurements matter most: throughput, travel time, queue length, safety
  interventions, or simply the visible traffic motion?

## Implementation checklist and gates

### 0. Agree on a small, testable product slice

- [x] Inspect current path, lane, scene and rendering assumptions.
- [x] Read the earlier tile proposal and identify the branching conflict.
- [x] Record architectural alternatives and ask the first design questions.
- [ ] Record the user's answers and resolve D1–D11 before treating them as settled.
- [ ] Pick one demo map and explicit phone/network/vehicle performance targets.
- [ ] Reconcile decisions with TILES.md without silently discarding earlier work.

### 1. Prove assembly and mobile readability

- [ ] Compare grid assembly and growing from ports on the same phone editing tasks;
  record which is more enjoyable and where each causes correction or confusion.
- [ ] Define stable piece/port/lane IDs, units, transforms and connection rules.
- [ ] Build a small static straight/bend/ramp arrangement with port visualization.
- [ ] Validate rotated port positions, direction, lane mapping and curve continuity.
- [ ] Prototype camera and selected-piece controls at the agreed phone sizes.
- [ ] Decide physical scale, minimum readable detail and overview representation.
- [ ] Gate: using touch alone, a person can place and rotate a ramp, connect it,
  inspect its lanes, move the camera without accidental edits, and undo a mistake.
  They can then follow traffic through it without the inspector hiding the merge.

### 2. Prove connected traffic before a full editor

- [ ] Add NetworkScene beside existing scenes; reuse driver equations, not invalid
  assumptions about whole-lane alignment or wrapping.
- [ ] Implement cross-section leader lookup, body occupancy and residual-distance transfer.
- [ ] Implement fixed-step advancement and deterministic conflict resolution.
- [ ] Validate an empty continuation, stopped queue across a seam, curved join,
  several short sections per step and a closed loop made from pieces.
- [ ] Gate: splitting one physical road into more pieces does not materially change
  its traffic behavior, and each vehicle advances once per tick.

### 3. Prove merges, splits and demand

- [ ] Implement explicit source demand, blocked-entry queues and sink accounting.
- [ ] Implement acceleration-lane merging, lane drops and downstream blocking.
- [ ] Implement stable route/split intent and mandatory lane preparation for exits.
- [ ] Test simultaneous arrivals, yield fairness, queues reaching upstream forks,
  blocked sinks, missed exits and repeatability with the same seed.
- [ ] Check conservation: admitted = active + departed + explicitly removed;
  requested demand is separately accounted for as admitted, pending or dropped.
- [ ] Gate: the merge laboratory and split/rejoin experiments work without vehicle
  duplication, disappearance, unsafe seam admission or hidden teleports.

### 4. Make experimentation comfortable

- [ ] Add palette, snapping preview, rotate, remove, undo/redo and useful invalid-join feedback.
- [ ] Apply network edits as a validated transaction using the chosen traffic policy.
- [ ] Keep simulation running through edit previews and atomic commits; test
  occupied-road shortening/removal, in-progress lane changes, changed routes,
  pending edits that cannot drain, and geometry undo while traffic advances.
- [ ] Save/load versioned network documents, demand settings and seeds; keep camera
  preferences separate and runtime snapshots optional.
- [ ] Offer simple measurements and repeatable before/after scenario resets.
- [ ] Retain static rendering, cull offscreen artwork and profile on the target phone.
- [ ] Gate: the agreed map remains readable and meets an agreed frame-time budget
  under a crowded-merge workload, with reproducible simulation results.

### 5. Extend toward streets after the highway contract holds

- [ ] Add opposite directions, priority T-junctions and explicit turning movements.
- [ ] Add conflict zones, stop/yield rules and signals according to selected scope.
- [ ] Add overpasses if deferred, then reusable multi-piece assemblies.
- [ ] Revisit pedestrians, bicycles, parking and import/export only against an actual
  scenario; avoid promising these from lane connectivity alone.

## Research boundaries

ASAM OpenDRIVE provides another reference for road linkage and explicit junction
relationships. We can learn from its separation of geometry and connectivity
without implementing the standard in the first editor.
[Reference: OpenDRIVE road linkage](https://publications.pages.asam.net/standards/ASAM_OpenDRIVE/ASAM_OpenDRIVE_Specification/v1.8.1/specification/10_roads/10_03_road_linkage.html).

Performance targets, a safe numerical timestep, calibrated highway capacities,
and the best phone interaction have not been established by this review. They
need agreed scenarios and measurements, not assumptions embedded in the API.
