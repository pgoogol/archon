# Obraz music-service. Kontekstem builda jest KATALOG GŁÓWNY repozytorium, nie
# ten katalog: moduł jest częścią reaktora i nie zbuduje się bez root POM-u ani
# bez startera z libs/java.
#
# Dwa etapy, żeby w obrazie końcowym nie było Mavena.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Najpierw same POM-y: warstwa z zależnościami przeżywa zmianę kodu.
COPY pom.xml ./
COPY libs/java/logging-starter/pom.xml libs/java/logging-starter/
COPY services/music-service/pom.xml services/music-service/
# POM-y pozostałych modułów reaktora: Maven czyta całą listę <module> z roota,
# zanim zawęzi build do -pl, i przerywa, gdy któregokolwiek brakuje. Same POM-y,
# bez źródeł — te moduły się tutaj nie budują.
COPY libs/java/llm-starter/pom.xml libs/java/llm-starter/
COPY services/finance-service/pom.xml services/finance-service/
COPY services/kitchen-service/pom.xml services/kitchen-service/
RUN mvn -B -q -pl services/music-service -am dependency:go-offline

COPY libs/java/logging-starter/src libs/java/logging-starter/src
COPY services/music-service/src services/music-service/src
# -am buduje starter razem z serwisem; testy jadą w CI, nie w obrazie.
# Spotless też jest robotą CI: kontekst builda nie zawiera .git, więc wtyczka
# nie zna ustawień końca linii z repozytorium i na źródłach z CRLF (klon
# windowsowy) zgłasza każdy plik.
RUN mvn -B -q -pl services/music-service -am -DskipTests -Dspotless.check.skip=true package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
# aplikacja nie potrzebuje roota — kontener startuje jako zwykły użytkownik
RUN addgroup -S app && adduser -S -G app app
COPY --from=build /build/services/music-service/target/music-service-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
