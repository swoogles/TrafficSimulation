# Working on this repo

## Finish means committed and pushed

When a piece of work is done, commit it and push it to `master`. Don't stop at
"the code is written" and don't ask whether to push - the asking is the part
that wastes a turn. Branch first only if the work is speculative or the user
says so.

Done includes the build. See below.

## The built JavaScript has to go with it

`index.html` loads `target/scala-2.13/traffic-opt/main.js`. That file is tracked
in git, but `target/` is also in `.gitignore`, so:

    git add target/scala-2.13/traffic-opt/main.js   # silently does nothing
    git add src                                     # leaves the build behind

Plain `git add` skips it without failing, which is how four commits of source
once went up while the page served from the repo still ran the old simulator.
Any change to `src/` that the page should show needs:

    sbt fullLinkJS
    git add -f target/scala-2.13/traffic-opt/main.js target/scala-2.13/traffic-opt/main.js.map

Check it landed with `git show HEAD:target/scala-2.13/traffic-opt/main.js | grep -c <something-new>`
rather than assuming.

## Six tests already fail

`sbt test` fails six tests on a clean checkout: five in `SquantsJsonSpec`
(serialization round-trips) and one in `PilotedVehicleSpec` ("should hold steady
when pacing the target car"). They predate this work and are unrelated to it.

So a red suite is not automatically your fault, and it is not a reason to hold
back a commit. What matters is the count: 6 failing and everything else green is
the baseline. Anything above 6, or a new name in the list, is yours.

## Seeing it run

The simulation is driven by `requestAnimationFrame`, which Chrome throttles to
nothing in a backgrounded tab - the page freezes on whatever frame it was on.
A screenshot of a hidden tab is a still photograph of one instant, not the
traffic. Take several in a row (each one wakes the tab) or check
`document.visibilityState` before believing what you are looking at.

Serve it over http rather than `file://`:

    python3 -m http.server 8777
