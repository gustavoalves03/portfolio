# --- Build stage ---
FROM maven:3.9.9-eclipse-temurin-21-jammy AS build
ENV MAVEN_HOME=/usr/share/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"
WORKDIR /app

COPY pom.xml .

# Diag verbeux
RUN echo "PATH=$PATH" \
 && ls -al /usr/share/maven/bin || true \
 && /usr/share/maven/bin/mvn -v \
 && mvn -v \
 && /usr/share/maven/bin/mvn -q -e -DskipTests dependency:go-offline

COPY src ./src
RUN /usr/share/maven/bin/mvn -q -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
