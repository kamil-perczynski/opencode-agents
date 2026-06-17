FROM eclipse-temurin:25-jdk-alpine

RUN apk add curl

WORKDIR /opt
RUN curl -L -O "https://github.com/anomalyco/opencode/releases/download/v1.17.7/opencode-linux-$(uname -m | sed 's/x86_64/x64/;s/aarch64/arm64/')$(ldd --version 2>&1 | grep -qi musl && echo -musl).tar.gz" \
    && tar -xzf opencode-linux-*.tar.gz \
    && rm opencode-linux-*.tar.gz

RUN find /opt -name "opencode" -type f -exec ln -sf {} /usr/local/bin/opencode \;

WORKDIR /app

COPY build/libs/* opencode-agents-1.0.jar

COPY build/distributions/*.tar .
RUN ls -la
RUN tar -xf *.tar --strip-components=1 && rm *.tar

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -XX:MaxRAMPercentage=80 -XX:+UseCompactObjectHeaders"
CMD ["./bin/opencode-agents"]
