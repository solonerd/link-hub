# Runtime image for the already-built Spring Boot jar (multi-stage build not used).
# Build locally first: mvn -DskipTests package  →  then: docker build -t link-hub .
FROM eclipse-temurin:21-jre

# Align container clock with the deployment region
ENV TZ=Asia/Shanghai

WORKDIR /app
# Copy the fat jar produced by Maven (requires target/*.jar to exist on the host)
COPY target/*.jar app.jar
# App listens on server.port 18080 (must match application.yml / compose mapping)
EXPOSE 18080
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]