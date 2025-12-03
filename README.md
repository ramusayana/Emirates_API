# Loyalty Points Service – How to Build and Run Tests

This project shows a small **Loyalty Points Quote Service** API built with Vert.x and tested with **Java (JUnit)**, and outlines how you would run **Android Espresso** and **iOS XCUITest (XCUI)** tests.

***

## 1. Prerequisites

Before you start, please make sure you have:

- **Java** 17 or later
- **Maven** 3.8 or later
- **Git**

For the mobile sections (optional, if you want to run UI tests):

- **Android Studio** + Android SDK (for Espresso tests)
- **Xcode** + iOS Simulator (for XCUITest)

***

## 2. Build the project and run API tests (Maven)

All Java API tests are run with Maven.

### 2.1 Clean and build

```bash
mvn clean install
```

This command:

- Cleans previous builds.
- Compiles the code.
- Runs all tests (including `LoyaltyPointsServiceAlternativeTest`).

### 2.2 Run tests only

```bash
mvn test
```

This:

- Starts the Vert.x HTTP server in test mode.
- Sends real HTTP requests to `POST /v1/points/quote` using Vert.x WebClient.
- Verifies:
    - HTTP status codes
    - JSON response fields (basePoints, tierBonus, promoBonus, totalPoints, effectiveFxRate)
    - Validation errors (e.g. missing `fareAmount`)

***

## 3. Run the API service locally

You can run the Loyalty Points service as a standalone HTTP API.

### 3.1 Package the project

```bash
mvn clean package
```

### 3.2 Start the service

```bash
java -cp target/*:. com.emirates.loyalty.LoyaltyPointsService
```

By default the service listens on:

- **URL:** `http://localhost:8080/v1/points/quote`
- **Method:** `POST`
- **Content-Type:** `application/json`

### 3.3 Example request payload

```json
{
  "fareAmount": 1234.50,
  "currency": "USD",
  "cabinClass": "ECONOMY",
  "customerTier": "SILVER",
  "promoCode": "SUMMER25"
}
```

You can send this using any REST client (Postman, curl, etc.) and you should receive a JSON response with calculated loyalty points.