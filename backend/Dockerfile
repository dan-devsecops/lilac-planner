# build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
# Retry up to 3 times - Maven Central occasionally drops connections mid-download
# inside the buildx container, causing a transient premature-EOF failure.
RUN for i in 1 2 3; do \
      mvn -B -q -e dependency:go-offline && break; \
      echo "dependency:go-offline attempt $i failed, retrying..."; \
      rm -rf /root/.m2/repository; \
    done

COPY src ./src
RUN mvn -B -q -DskipTests package \
 && mv target/$(ls target | grep -E '^planner-.*\.jar$' | grep -v original) app.jar

# runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Explicit numeric UID/GID so Kubernetes can verify runAsNonRoot on any cluster
# (named users fail Pod Security Standards' "must run as non-root" check).
RUN addgroup -g 1000 app && adduser -S -u 1000 -G app app
COPY --from=build /app/app.jar /app/app.jar
USER 1000

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
