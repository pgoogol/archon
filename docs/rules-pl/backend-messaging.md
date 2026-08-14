---
paths:
  - "services/**"
  - "libs/java/**"
---

# Messaging

Dotyczy serwisu, który używa Kafki albo RabbitMQ.

- Ustaw `enable.auto.commit=false` — commituj offsety ręcznie po udanym
  przetworzeniu.
- Kieruj wiadomości, które padły po wyczerpaniu retry, na **dead-letter topic**.
  Nigdy nie porzucaj ich po cichu.
- ID grup konsumenckich muszą być unikalne na serwis i środowisko.
- Producent idempotentny: `enable.idempotence=true`, `acks=all`.
- Trzymaj kontrakty wiadomości między serwisami w schema registry (Avro/Protobuf).
  Nigdy surowy JSON bez schematu.
- Testuj konsumentów jednostkowo `EmbeddedKafkaBroker`-em, integracyjnie
  Testcontainers.
