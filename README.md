# 🏦 Midas Core — JPMC Advanced Software Engineering Forage

> A financial transaction processing system built as part of the **JPMorgan Chase & Co. Advanced Software Engineering Virtual Experience Program** on Forage.

---

## 📌 Overview

**Midas Core** is a backend microservice that simulates a real-world financial transaction processing pipeline. It consumes transaction events from a **Kafka** topic, validates them against business rules (sender/recipient existence, sufficient balance), applies external incentives, persists valid transactions to a database, and exposes a REST API to query user balances.

This project spans multiple progressive tasks — from application bootstrapping to Kafka integration, database persistence, REST API development, and external service communication.

---

## 🗂️ Project Structure

```
forage-midas/
├── src/
│   ├── main/
│   │   ├── java/com/jpmc/midascore/
│   │   │   ├── MidasCoreApplication.java       # Spring Boot entry point
│   │   │   ├── component/
│   │   │   │   ├── TransactionListener.java    # Kafka consumer listener
│   │   │   │   ├── TransactionService.java     # Core business logic & transaction processing
│   │   │   │   ├── IncentiveService.java       # HTTP client for external incentive API
│   │   │   │   └── DatabaseConduit.java        # Helper wrapper for UserRepository
│   │   │   ├── config/
│   │   │   │   └── KafkaConfig.java            # Kafka producer & consumer configuration
│   │   │   ├── controller/
│   │   │   │   └── BalanceController.java      # REST endpoint: GET /balance
│   │   │   ├── entity/
│   │   │   │   ├── UserRecord.java             # JPA entity: User with balance
│   │   │   │   └── TransactionRecord.java      # JPA entity: Persisted transaction record
│   │   │   ├── foundation/
│   │   │   │   ├── Transaction.java            # Kafka message model (senderId, recipientId, amount)
│   │   │   │   ├── Balance.java                # REST response model for balance queries
│   │   │   │   └── Incentive.java              # Response model from external incentive API
│   │   │   └── repository/
│   │   │       ├── UserRepository.java         # Spring Data JPA repo for UserRecord
│   │   │       └── TransactionRepository.java  # Spring Data JPA repo for TransactionRecord
│   │   └── resources/
│   │       └── application.yml                 # App config: server, Kafka, H2 datasource
│   └── test/
│       └── java/com/jpmc/midascore/
│           ├── TaskOneTests.java               # Task 1: Application boot verification
│           ├── TaskTwoTests.java               # Task 2: Kafka consumer verification
│           ├── TaskThreeTests.java             # Task 3: Database persistence with debugger
│           ├── TaskFourTests.java              # Task 4: Validation logic tests
│           ├── TaskFiveTests.java              # Task 5: Full pipeline + balance query test
│           ├── KafkaProducer.java              # Test helper: publishes transactions to Kafka
│           ├── UserPopulator.java              # Test helper: seeds users into the database
│           ├── FileLoader.java                 # Test helper: loads transaction test data files
│           └── BalanceQuerier.java             # Test helper: queries balance via REST
├── services/
│   └── transaction-incentive-api.jar          # External incentive service (pre-built JAR)
├── pom.xml                                    # Maven build configuration
└── application.yml                            # Root-level config (overridden by src/main)
```

---

## ⚙️ Technology Stack

| Layer | Technology | Version |
|---|---|---|
| **Language** | Java | 17 |
| **Framework** | Spring Boot | 3.2.5 |
| **Message Broker** | Apache Kafka (via Spring Kafka) | 3.1.4 |
| **Database** | H2 In-Memory Database | 2.2.224 |
| **ORM** | Spring Data JPA (Hibernate) | 3.2.5 |
| **REST** | Spring Boot Web (Embedded Tomcat) | 3.2.5 |
| **JSON** | Jackson Databind | (managed by Spring Boot) |
| **Build Tool** | Apache Maven | 3.x (via Maven Wrapper) |
| **Testing** | JUnit 5, Spring Boot Test | 3.2.5 |
| **Integration Testing** | Spring Kafka Test (Embedded Kafka) | 3.1.4 |
| **Container Testing** | Testcontainers Kafka | 1.19.1 |
| **Logging** | SLF4J + Logback | (managed by Spring Boot) |

---

## 🔑 Key Features

### 1. Kafka Transaction Consumption
- Subscribes to the `trader-updates` Kafka topic via `@KafkaListener`
- Deserializes JSON payloads into `Transaction` objects (Jackson)
- Uses `JsonDeserializer` with trusted packages configured for flexibility

### 2. Business Rule Validation
All incoming transactions are validated before processing:
- ✅ **Sender exists** — verified against the database by sender ID
- ✅ **Recipient exists** — verified against the database by recipient ID
- ✅ **Sufficient balance** — sender's balance must be ≥ transaction amount
- ❌ Invalid transactions are silently rejected with a warning log

### 3. External Incentive Service Integration
- For each valid transaction, `IncentiveService` makes an HTTP `POST` to an external incentive API (`http://localhost:8080/incentive`)
- The returned incentive amount is **credited to the recipient only** (not deducted from sender)
- Failures are handled gracefully — defaults to `0` incentive on error

### 4. Atomic Transaction Processing (`@Transactional`)
- Sender balance is debited by the transaction amount
- Recipient balance is credited by `amount + incentive`
- A `TransactionRecord` is persisted to the H2 database
- All operations run within a single database transaction

### 5. REST API — Balance Query
- `GET /balance?userId={id}` returns the current balance of a user
- Returns `Balance(0)` if the user does not exist

### 6. H2 In-Memory Database
- Auto-created on startup, destroyed on shutdown (`ddl-auto: create-drop`)
- H2 console enabled for development at `/h2-console`
- Connection URL: `jdbc:h2:mem:midasdb`

---

## 📐 Architecture Diagram

```
                        ┌────────────────────────┐
                        │   External Kafka Topic  │
                        │   (trader-updates)      │
                        └───────────┬────────────┘
                                    │ JSON Message
                                    ▼
                        ┌────────────────────────┐
                        │   TransactionListener  │  @KafkaListener
                        └───────────┬────────────┘
                                    │
                                    ▼
                        ┌────────────────────────┐
                        │   TransactionService   │  @Transactional
                        │  ┌──────────────────┐  │
                        │  │ Validate Sender  │  │──► UserRepository (H2)
                        │  │ Validate Recip.  │  │
                        │  │ Check Balance    │  │
                        │  └──────────────────┘  │
                        │          │             │
                        │          ▼             │
                        │  ┌──────────────────┐  │
                        │  │ IncentiveService │  │──► POST http://localhost:8080/incentive
                        │  └──────────────────┘  │
                        │          │             │
                        │          ▼             │
                        │  Update Balances        │──► UserRepository.save()
                        │  Persist Transaction    │──► TransactionRepository.save()
                        └────────────────────────┘

                        ┌────────────────────────┐
                        │   BalanceController    │  GET /balance?userId=X
                        └───────────┬────────────┘
                                    │
                                    ▼
                        ┌────────────────────────┐
                        │   UserRepository (H2)  │
                        └────────────────────────┘
```

---

## 🧪 Task Breakdown

The project is structured around **5 progressive tasks** from the Forage internship program:

| Task | Description |
|---|---|
| **Task 1** | Boot the Spring Boot application successfully. Verifies basic app setup and dependency configuration. |
| **Task 2** | Set up a Kafka consumer. Verify the application can receive and log transaction messages from the embedded Kafka broker. |
| **Task 3** | Integrate H2 database. Process transactions, persist records, and use the debugger to inspect `waldorf`'s final balance. |
| **Task 4** | Implement validation logic. Reject transactions with invalid sender/recipient IDs or insufficient balance. |
| **Task 5** | Full pipeline test. Connect Kafka + DB + REST API. Query all user balances via `BalanceQuerier` after processing a full transaction dataset. |

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+** installed (`java -version`)
- **Maven** (or use the included Maven Wrapper `./mvnw`)
- The **Incentive API service** must be running on port `8080` for Task 5:
  ```bash
  java -jar services/transaction-incentive-api.jar
  ```

### Run the Application

```bash
./mvnw spring-boot:run
```

The server starts on port **33400**.

### Run Tests

```bash
# Run all tests
./mvnw test

# Run a specific task test
./mvnw test -Dtest=TaskOneTests
./mvnw test -Dtest=TaskFiveTests
```

### H2 Console (Development)

While the app is running, access the H2 database console at:
```
http://localhost:33400/h2-console
JDBC URL: jdbc:h2:mem:midasdb
Username: sa
Password: (leave blank)
```

---

## 📡 API Reference

### `GET /balance`

Returns the current balance of a user.

**Query Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `userId` | `long` | The ID of the user to query |

**Example Request:**
```
GET http://localhost:33400/balance?userId=1
```

**Example Response:**
```json
{
  "amount": 1250.75
}
```

---

## 🗃️ Data Models

### `Transaction` (Kafka Message)
```json
{
  "senderId": 1,
  "recipientId": 2,
  "amount": 100.0
}
```

### `UserRecord` (JPA Entity)
| Field | Type | Description |
|---|---|---|
| `id` | `long` | Auto-generated primary key |
| `name` | `String` | User's name |
| `balance` | `float` | Current account balance |

### `TransactionRecord` (JPA Entity)
| Field | Type | Description |
|---|---|---|
| `id` | `long` | Auto-generated primary key |
| `sender` | `UserRecord` | FK → sending user |
| `recipient` | `UserRecord` | FK → receiving user |
| `amount` | `float` | Transaction amount |
| `incentive` | `float` | Incentive credited to recipient |

---

## 🔧 Configuration

All configuration is in `src/main/resources/application.yml`:

```yaml
server:
  port: 33400

general:
  kafka-topic: trader-updates

spring:
  datasource:
    url: jdbc:h2:mem:midasdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
  h2:
    console:
      enabled: true
```

Kafka bootstrap server defaults to `localhost:9092` and can be overridden via:
```yaml
spring:
  kafka:
    bootstrap-servers: <your-broker>
```

---

## 📦 Dependencies (pom.xml)

```xml
spring-boot-starter                  <!-- Core Spring Boot -->
spring-boot-starter-data-jpa         <!-- JPA / Hibernate ORM -->
spring-boot-starter-web              <!-- Embedded Tomcat + REST -->
spring-kafka                         <!-- Apache Kafka integration -->
h2                                   <!-- In-memory database -->
jackson-databind                     <!-- JSON serialization -->
spring-boot-starter-test             <!-- JUnit 5 + Mockito -->
spring-kafka-test                    <!-- Embedded Kafka for tests -->
testcontainers/kafka                 <!-- Kafka Testcontainers support -->
```

---

## 👤 Author

**Ajay** — Completed as part of the **JPMorgan Chase & Co. Advanced Software Engineering Virtual Experience** on [Forage](https://www.theforage.com/).

---

## 📄 License

This project is for educational purposes as part of the JPMC Forage Virtual Internship Program.
