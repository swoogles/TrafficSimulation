# Multi-stage build: first stage compiles the Scala.js code
FROM eclipse-temurin:8-jdk AS builder

WORKDIR /build

# Install SBT - create wrapper script that downloads launcher jar
RUN apt-get update && \
    apt-get install -y curl && \
    mkdir -p /usr/local/sbt/bin && \
    printf '#!/bin/bash\nset -e\nSBT_VERSION=$(grep sbt.version project/build.properties 2>/dev/null | cut -d= -f2 || echo "1.3.3")\nSBT_LAUNCHER_JAR="$HOME/.sbt/launchers/$SBT_VERSION/sbt-launch.jar"\nmkdir -p "$(dirname "$SBT_LAUNCHER_JAR")"\nif [ ! -f "$SBT_LAUNCHER_JAR" ]; then\n  echo "Downloading sbt launcher $SBT_VERSION..."\n  curl -L "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/$SBT_VERSION/sbt-launch-$SBT_VERSION.jar" -o "$SBT_LAUNCHER_JAR"\nfi\njava -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -jar "$SBT_LAUNCHER_JAR" "$@"\n' > /usr/local/sbt/bin/sbt && \
    chmod +x /usr/local/sbt/bin/sbt && \
    ln -sf /usr/local/sbt/bin/sbt /usr/local/bin/sbt && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copy build configuration files first (for better layer caching)
COPY project/ ./project/
COPY build.sbt ./

# Pre-download dependencies (this layer will be cached if dependencies don't change)
# Note: SBT will download its launcher jar on first run
ENV CI=true
RUN sbt update

# Copy source code
COPY src/ ./src/

# Build the Scala.js project (non-interactive mode)
RUN sbt fastOptJS

# Second stage: serve the static files with nginx
FROM nginx:alpine

# Create the target directory structure
RUN mkdir -p /usr/share/nginx/html/target/scala-2.12

# Copy the compiled JavaScript files
COPY --from=builder /build/target/scala-2.12/traffic-jsdeps.js /usr/share/nginx/html/target/scala-2.12/
COPY --from=builder /build/target/scala-2.12/traffic-fastopt.js /usr/share/nginx/html/target/scala-2.12/

# Copy static assets (HTML, CSS, images)
COPY index.html /usr/share/nginx/html/
COPY css/ /usr/share/nginx/html/css/
COPY images/ /usr/share/nginx/html/images/

# Expose port 80
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]

