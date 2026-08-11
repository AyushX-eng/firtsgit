# Build stage
FROM maven:3.8.1-openjdk-17 as builder
WORKDIR /app
COPY . .
RUN mvn -DskipTests clean package

# Runtime stage
FROM openjdk:17-slim
WORKDIR /app
COPY --from=builder /app/target/api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
