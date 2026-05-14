# Placeholder runtime until the Gradle application JAR is packaged here.
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -g 10001 app && adduser -u 10001 -G app -D -h /app app
WORKDIR /app
USER app
# Keeps `docker compose up` useful for port/network checks; replace with java -jar when the worker exists.
CMD ["sh", "-c", "echo many_faces_mailer: gRPC worker skeleton — replace this CMD with the real server; exec sleep infinity"]
