module.exports = {
  server: ".",
  listen: "127.0.0.1",
  port: 8777,
  open: false,
  ui: false,
  notify: false,
  ghostMode: false,
  // Keep the static site's fullLinkJS URL, but serve fastLinkJS output in dev.
  // Rewrite before serving so a missing dev build cannot fall back to stale JS.
  middleware: [function (req, res, next) {
    req.url = req.url.replace(
      /^\/target\/scala-2\.13\/traffic-opt\//,
      "/target/scala-2.13/traffic-fastopt/"
    );
    res.setHeader("Cache-Control", "no-store");
    next();
  }],
  files: [
    "target/scala-2.13/traffic-fastopt/*.js",
    "index.html",
    "css/**/*.css",
    "images/**/*"
  ],
  watchEvents: ["add", "change"],
  watchOptions: {
    ignoreInitial: true,
    awaitWriteFinish: { stabilityThreshold: 100, pollInterval: 25 }
  }
};
