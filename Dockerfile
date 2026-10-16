# Downloads the musl build of opencode v2 on the build platform (no QEMU needed for multi-arch builds)
FROM --platform=$BUILDPLATFORM alpine:3.24 AS opencode

ARG TARGETARCH
ARG OPENCODE_VERSION=2.0.31

RUN ARCH=$([ "$TARGETARCH" = "arm64" ] && echo arm64 || echo x64) \
    && wget -qO- "https://registry.npmjs.org/@opencode/cli-linux-${ARCH}-musl/-/cli-linux-${ARCH}-musl-${OPENCODE_VERSION}.tgz" \
     | tar -xz -C /tmp package/bin/opencode \
    && install -m 0755 /tmp/package/bin/opencode /opencode

FROM eclipse-temurin:25-jre-alpine

# opencode musl binary links against libstdc++ (and libgcc)
RUN apk add --no-cache libstdc++

COPY --from=opencode /opencode /usr/local/bin/opencode

WORKDIR /app
COPY build/docker-dist/lib lib
COPY build/docker-dist/app .

EXPOSE 8080

ENV OPENCODE_DISABLE_AUTOUPDATE=1
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -XX:MaxRAMPercentage=70 -XX:+UseCompactObjectHeaders"
ENV JSON_LOG_FORMAT=true
CMD ["java", "-cp", "*:lib/*", "io.github.torvehammok.KtorMainKt"]
