# Build stage: compile and package the Spring Boot app (Maven + Java 8)
FROM maven:3.8-eclipse-temurin-8 AS builder
WORKDIR /build

COPY pom.xml .
# Download dependencies (cached unless pom changes)
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B -q

# Run stage: only the JAR and JRE
FROM eclipse-temurin:8-jre
WORKDIR /app

COPY --from=builder /build/target/ebook-chat-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
