# Obraz finance-service. Kontekstem builda jest KATALOG GŁÓWNY repozytorium, nie
# ten katalog: moduł jest częścią reaktora i nie zbuduje się bez root POM-u ani
# bez startera z libs/java.
#
# Dwa etapy, żeby w obrazie końcowym nie było Mavena.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Najpierw same POM-y: warstwa z zależnościami przeżywa zmianę kodu.
COPY pom.xml ./
COPY libs/java/logging-starter/pom.xml libs/java/logging-starter/
COPY services/finance-service/pom.xml services/finance-service/
RUN mvn -B -q -pl services/finance-service -am dependency:go-offline

COPY libs/java/logging-starter/src libs/java/logging-starter/src
COPY services/finance-service/src services/finance-service/src
# -am buduje starter razem z serwisem; testy jadą w CI, nie w obrazie
RUN mvn -B -q -pl services/finance-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
# aplikacja nie potrzebuje roota — kontener startuje jako zwykły użytkownik
RUN addgroup -S app && adduser -S -G app app
COPY --from=build /build/services/finance-service/target/finance-service-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
