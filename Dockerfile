# Stage 1: Build the Java application
FROM maven:3.8.6-openjdk-11 AS build

# Set the working directory
WORKDIR /app

# Copy the pom.xml and download dependencies
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the Tomcat image
FROM tomcat:9.0

# Remove default web apps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the WAR file from the build stage to the Tomcat webapps directory
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/

# Optionally, expose ports if not using the default
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]
