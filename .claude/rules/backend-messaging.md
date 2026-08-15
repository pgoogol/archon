---
paths:
  - "services/**"
  - "libs/java/**"
---

# Messaging

Applies to a service that uses Kafka or RabbitMQ.

- Set `enable.auto.commit=false` — commit offsets manually after successful
  processing.
- Route messages that failed after max retries to a **dead-letter topic**. Never
  discard them silently.
- Consumer group IDs must be unique per service and environment.
- Idempotent producer: `enable.idempotence=true`, `acks=all`.
- Keep inter-service message contracts in a schema registry (Avro/Protobuf).
  Never raw JSON without a schema.
- Unit-test consumers with `EmbeddedKafkaBroker`, integration-test with
  Testcontainers.
