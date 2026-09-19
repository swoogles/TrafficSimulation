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

### Development with automatic browser reload

Install the development server once (requires Node.js and npm):

```sh
npm ci
```

In one terminal, keep sbt running in watch mode:

```sh
sbt '~fastLinkJS'
```

After the first successful build, start the development server in another terminal:

```sh
npm run dev
```

Open <http://127.0.0.1:8777> (or the URL printed by BrowserSync if the port is
already occupied). Saving Scala code triggers incremental compilation and linking;
BrowserSync reloads the page as soon as the generated JavaScript finishes writing.
A failed compilation leaves the current page running until a successful build.
Reloading resets the simulation. HTML and images are also watched, and CSS changes
are injected without resetting the simulation. Stop both commands with Ctrl+C.

No additional sbt plugin or linker settings are needed: the existing `sbt-scalajs`
plugin provides `fastLinkJS`, and sbt's `~` prefix watches its source inputs.
[BrowserSync](https://browsersync.io/docs/options) supplies the HTTP server and
browser reload connection. Its development configuration serves
`target/scala-2.13/traffic-fastopt/` at the page's `traffic-opt/` URL (including
source maps). It watches generated JavaScript, so it reloads after linking rather
than immediately on a Scala edit. Open the HTTP URL above to get live reload.

The [official Scala.js Vite workflow](https://www.scala-js.org/doc/tutorial/scalajs-vite.html)
is another option for applications using ES modules and npm imports. This app
currently uses a plain script and the exported `Client.run()` entry point, so
BrowserSync works with its existing output format.

### Static / production build

```sh
sbt fullLinkJS
python3 -m http.server 8777
```

Open <http://localhost:8777>. A normal static server uses the fully optimized
`target/scala-2.13/traffic-opt/main.js` referenced by `index.html`.

## Play with the most recent version of the project  ##

[Live Demo](https://www.whywestopped.com)

![](https://i.imgur.com/Cw1YIO7.png)

## Testing Requirements ##
Install NodeJS (and PhantomJS?) to run tests in ScalaJS. Still missing something here though...

    npm install jsdom
