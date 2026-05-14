# Multi-stage build: Gradle installDist on Temurin JDK 21, then a slim JRE runtime as non-root.
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache bash
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew \
    && ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache netcat-openbsd \
    && addgroup -g 10001 app \
    && adduser -u 10001 -G app -D -h /app app
WORKDIR /app
COPY --from=build /workspace/build/install/many-faces-mailer/ ./
RUN chown -R app:app /app
USER app
ENV JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0
EXPOSE 50054
# installDist shell wrapper invokes java with full classpath; keep working directory on /app.
CMD ["./bin/many-faces-mailer"]
