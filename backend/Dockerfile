# --- Build stage ---
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copier d'abord les fichiers de build pour profiter du cache Maven
COPY pom.xml .
# Si tu as un settings.xml privé, dé-commente la ligne suivante :
# COPY .mvn/ .mvn/
# COPY settings.xml /root/.m2/settings.xml

# Pré-télécharger les deps (optionnel mais optimise le cache)
RUN mvn -q -e -DskipTests dependency:go-offline

# Copier le code
COPY src ./src

# Build du jar
RUN mvn -q -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
