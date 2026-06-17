FROM eclipse-temurin:25-jre-noble

# Standard Ubuntu uses apt-get
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
RUN curl -L -O "https://github.com/anomalyco/opencode/releases/download/v1.17.7/opencode-linux-$(uname -m | sed 's/x86_64/x64/;s/aarch64/arm64/').tar.gz" \
    && tar -xzf opencode-linux-*.tar.gz \
    && rm opencode-linux-*.tar.gz

RUN find /opt -name "opencode" -type f -exec ln -sf {} /usr/local/bin/opencode \;

WORKDIR /app
COPY build/distributions/*.tar .
RUN tar -xf *.tar --strip-components=1 && rm *.tar

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -XX:MaxRAMPercentage=80 -XX:+UseCompactObjectHeaders"
ENV JSON_LOG_FORMAT=true
CMD ["./bin/opencode-agents"]