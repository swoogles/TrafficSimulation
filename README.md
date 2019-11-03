# Embouteillage #

The project is a traffic simulator that runs in your browser, powered by ScalaJS.
It is now 100% client code, meaning it can be hosted simply as a static site.
Features so far:

    * Single-lane, 1-dimensional traffic.
    * Intelligent Driving Model to determine vehicle behavior.
    * Pause and reset Scene.
    * Adjustable Parameters
        * Timing in between vehicles
        * Initial vehicle velocity

    * Disrupt traffic via 2 means:
        * Bring an existing vehicle to a dead stop.
        * Plop a new vehicle down in the middle of the road. (Unreliable behavior if dropped on an existing vehicle.)

It uses powerful scala tools to construct a fully typed and reactive Web applications. Among them:

- [scalajs](https://github.com/scala-js/scala-js)
- [scalatags](https://github.com/lihaoyi/scalatags) for UI.
- [scala.rx](https://github.com/lihaoyi/scala.rx) for tracking changes in the UI.
- [scaladget](https://github.com/mathieuleclaire/scaladget) to draw some svg.

## Build & Run ##
First, build the javascript:
```
sbt fastOptJS
```

## Play with the Simulation ##

    firefox ./index.html


![](https://i.imgur.com/Cw1YIO7.png)

## Testing Requirements ##
Install NodeJS (and PhantomJS?) to run tests in ScalaJS. Still missing something here though...

    npm install jsdom
