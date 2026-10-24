# Ride Booking API

> An Uber-style ride-booking backend built step by step with Spring Boot, PostgreSQL (Supabase), WebSocket and Stripe.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-336791)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED)
![Stripe](https://img.shields.io/badge/Stripe-test%20mode-635BFF)

---

## Table of contents

1. [Introduction](#1-introduction)
2. [Research](#2-research)
3. [Tech stack and the reasons](#3-tech-stack-and-the-reasons)
4. [Libraries used](#4-libraries-used)
5. [Features, with screenshots](#5-features-with-screenshots)
6. [Getting started](#6-getting-started)
7. [Conclusion](#7-conclusion)

---

## 1. Introduction

### What is this project?
This is the **backend** of a ride-booking service like Uber or Ola. A rider asks for a ride, the system finds the nearest available driver, both of them follow the trip in real time, the rider pays by card, and both sides can rate each other afterwards.

There is no mobile app or website in this repository. The API is exercised with Postman, automated tests and a small WebSocket test page.

### Why I built it
I wanted a project that goes past simple CRUD and covers the problems real backends have to solve: authentication, money, concurrency, real-time updates, security, testing and deployment. I built it one step at a time, and every step ships with tests.

### Who uses it

| Role | What they can do |
|---|---|
| **Rider** | Estimate a fare, book a ride, track it live, pay, get a receipt, rate the driver, see history |
| **Driver** | Create a profile, go online or offline, share location, accept rides automatically by proximity, start and complete rides, rate the rider |
| **Admin** | Read-only access to payments and ratings of any ride |

### Main features
- Registration and login with **JWT** tokens and role-based access
- **Fare estimates** from distance, time, minimum fare and surge multiplier
- **Nearest-driver matching** that is safe when two riders ask at the same moment
- Ride lifecycle: `REQUESTED → ACCEPTED → IN_PROGRESS → COMPLETED`, or `CANCELLED`
- **Real-time** ride status and driver location over WebSocket (STOMP)
- **Stripe** card payments with signed, repeat-safe webhooks
- Paginated **ride history**, and **receipts** as JSON and PDF
- **Ratings and reviews** with driver averages
- Security hardening: rate limiting, CORS, security headers, safe error messages
- Unit, integration, end-to-end and concurrency tests
- Docker image and cloud deployment

---

## 2. Research

Before writing code for each feature, I studied how real systems handle it. These are the questions I worked through and what I decided.

### 2.1 How a ride-hailing system works

```
Estimate fare → Request ride → Match nearest driver → Trip (start / complete)
      → Pay → Receipt → Rate each other → History
```

Every arrow is a place where something can go wrong: two riders competing for one driver, a payment that arrives twice, a user reading someone else's ride. The research below is about those failure points.

### 2.2 Problems and decisions

| Problem | What I learned | Decision in this project |
|---|---|---|
| **Distance without a maps API** | The **Haversine formula** gives the straight-line distance between two coordinates on a sphere. Roads are longer than straight lines. | Haversine, multiplied by a configurable **road factor (1.3)**. Duration comes from a configurable average speed (30 km/h) |
| **Pricing** | Ride apps combine a base fare, a per-km rate, a per-minute rate, a minimum fare and a surge multiplier | All of them are properties, so they can change without code changes |
| **Money in code** | `double` cannot store values like 0.1 exactly | `BigDecimal` with `HALF_UP` rounding. Stripe amounts are sent in the smallest unit (paise) |
| **Finding a driver** | Checking every driver's distance in Java does not scale | A **bounding-box query** in the database (backed by an index) narrows the list. Exact distance is then checked and the nearest driver is chosen |
| **Two riders, one driver** | "Read, check, then write" has a gap where both riders succeed (a race condition) | One **atomic conditional update** (`claimIfAvailable`) so only one request can win. A multi-threaded test proves it |
| **Authentication** | Server-side sessions need shared memory; tokens do not | **Stateless JWT**, passwords hashed with **BCrypt**, and a clear difference between **401** (not logged in) and **403** (not allowed) |
| **Real-time updates** | Polling wastes requests and feels slow | **WebSocket with STOMP**. The token is checked on connect, and ride membership is checked on subscribe. Messages are sent only **after the database commit** |
| **Payments** | The browser must never decide the amount, and Stripe may send the same event twice | Amount is read from the ride on the server. **PaymentIntent** flow, webhook **signature verification**, **idempotent** handling, and a late "failed" event can never undo a success |
| **Ratings** | A user could rate twice or rate the wrong person | The server decides who is rated, one rating per person per ride, enforced in code **and** by a database unique constraint |
| **Security** | Common attacks: password guessing, privilege escalation, data leaks in error pages | Login lockout, per-IP request limits, ADMIN cannot be self-registered, password rules, allow-listed CORS origins, security headers, generic 500 messages |
| **Performance** | The N+1 query problem, missing foreign-key indexes, repeated reads | Entity graphs, composite indexes, a short-lived cache with eviction, a tuned connection pool. Measured with `EXPLAIN ANALYZE` and a test that counts SQL statements |
| **Testing** | Unit tests alone miss bugs between layers | A test pyramid: many unit tests, integration tests with MockMvc and H2, one end-to-end journey, one concurrency test, and a coverage report |
| **Deployment** | Secrets must not be in Git or in an image; some hosts cannot reach IPv6 databases | Multi-stage Docker build, secrets as environment variables, `.dockerignore`, and Supabase's **Session pooler** connection |

### 2.3 Further reading
- [Spring Boot reference documentation](https://docs.spring.io/spring-boot/)
- [Stripe PaymentIntents](https://docs.stripe.com/payments/payment-intents) and [webhooks](https://docs.stripe.com/webhooks)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [PostgreSQL documentation](https://www.postgresql.org/docs/) (indexes and `EXPLAIN`)
- [Supabase documentation](https://supabase.com/docs)

---

## 3. Tech stack and the reasons

| Technology | Why I chose it |
|---|---|
| **Java 17** | A long-term-support release with modern features such as records and text blocks. Widely used for backend jobs |
| **Spring Boot** | Sets up a web server, security, database access and testing with sensible defaults, so I can focus on the business logic |
| **Maven** | Dependency management and a standard build lifecycle, with a wrapper (`mvnw`) so nobody needs Maven installed |
| **PostgreSQL** | Rides, payments and ratings are related data that need **transactions** and constraints. A relational database fits better than a document store |
| **Supabase** | Free managed PostgreSQL with a dashboard, so I could inspect tables without running a database myself |
| **Spring Data JPA / Hibernate** | Maps Java objects to tables and writes the common queries for me. Custom queries are used where speed matters |
| **Spring Security + JWT** | Industry-standard security. Stateless tokens keep the server simple and easy to scale |
| **WebSocket (STOMP)** | Pushes ride updates to clients the moment they happen |
| **Stripe** | Handles card details so my server never touches them. It has a full test mode, so no real money is involved |
| **H2 (tests only)** | An in-memory database so tests run fast, offline and without any secrets |
| **JUnit 5, Mockito, AssertJ, MockMvc** | The standard Java testing toolbox, from isolated unit tests to full HTTP-level tests |
| **Docker** | The same image runs on my laptop and on the server |
| **Render** | A free host that builds straight from a Dockerfile and a GitHub repository |

### Architecture

```
Client (Postman / WebSocket page)
        │
        ▼
┌─────────────────────────────────────────────┐
│ Filters:  RateLimitFilter → JwtAuthFilter   │  security
├─────────────────────────────────────────────┤
│ Controllers   (HTTP in, HTTP out)           │
├─────────────────────────────────────────────┤
│ Services      (business rules, transactions)│
├─────────────────────────────────────────────┤
│ Repositories  (Spring Data JPA)             │
├─────────────────────────────────────────────┤
│ PostgreSQL (Supabase)          Stripe API   │
└─────────────────────────────────────────────┘
```

Each layer only talks to the one below it. Controllers never touch the database, and services never touch HTTP.

### Project structure

```
src/main/java/com/nithin/ridebooking/
├── config/       CORS, caching, WebSocket setup
├── controller/   REST endpoints and the WebSocket controller
├── dto/          Request and response objects (the API's public shape)
├── entity/       Database tables: User, Driver, Ride, Payment, Rating
├── event/        Events published after a ride changes status
├── exception/    Custom errors and the global error handler
├── payment/      Stripe gateway behind an interface (easy to fake in tests)
├── repository/   Database access
├── security/     JWT filter, rate limiter, security configuration
├── service/      Business logic: fares, matching, rides, payments, ratings
└── util/         Distance and bounding-box calculations
```

---

## 4. Libraries used

### Web and API
| Library | What it does here |
|---|---|
| `spring-boot-starter-webmvc` | REST controllers, JSON conversion and the embedded Tomcat server |
| `spring-boot-starter-validation` | Checks request fields with annotations such as `@NotNull`, `@Min`, `@Size` |

### Database
| Library | What it does here |
|---|---|
| `spring-boot-starter-data-jpa` | JPA with Hibernate: entities, repositories, paging, transactions |
| `postgresql` | The JDBC driver that connects to Supabase |
| HikariCP (included with Spring Boot) | The connection pool. Tuned for the pooler's limits |

### Security
| Library | What it does here |
|---|---|
| `spring-boot-starter-security` | Authentication, authorization, password hashing (BCrypt), CORS and security headers |
| `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.12.6) | Creates and verifies signed JSON Web Tokens |

### Real-time
| Library | What it does here |
|---|---|
| `spring-boot-starter-websocket` | WebSocket and STOMP messaging for live ride status and driver location |

### Payments and documents
| Library | What it does here |
|---|---|
| `stripe-java` | Creates PaymentIntents and verifies webhook signatures |
| `pdfbox` (3.0.4) | Generates the PDF receipt |

### Performance
| Library | What it does here |
|---|---|
| `spring-boot-starter-cache` and `caffeine` | A fast in-memory cache for driver rating summaries, with expiry and eviction |

### Developer productivity
| Library | What it does here |
|---|---|
| `lombok` | Generates getters, setters, builders and constructors at compile time |
| `spring-boot-devtools` | Automatic restart while developing |

### Testing
| Library | What it does here |
|---|---|
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ and MockMvc in one dependency |
| `spring-security-test` | Helps test secured endpoints |
| `h2` | In-memory database for tests, in PostgreSQL compatibility mode |
| `jacoco-maven-plugin` | Test coverage report |

---

## 5. Features, with screenshots

> Screenshots live in `docs/screenshots/`. Tokens, keys and passwords are hidden in every image.

### 5.1 Health check
`GET /health` returns a simple response without needing a login. Hosting platforms use it to know the app is alive.

![Health check](docs/screenshots/01-health.png)

### 5.2 Registration and login
`POST /api/auth/register` creates a rider or a driver. The password is stored as a BCrypt hash and is never returned. `POST /api/auth/login` returns a **JWT**, which is sent as `Authorization: Bearer <token>` on every other request. Passwords must be 8 to 72 characters with a letter and a number, and admin accounts cannot be self-registered.

![Register](docs/screenshots/02-register.png)
![Login and token](docs/screenshots/03-login.png)

### 5.3 Driver profile, availability and location
A driver creates a profile (vehicle and licence), goes online with `PUT /api/drivers/me/availability`, and shares coordinates with `PUT /api/drivers/me/location`. Only available drivers with a location can be matched.

![Driver setup](docs/screenshots/04-driver-setup.png)

### 5.4 Fare estimate
`POST /api/rides/estimate` shows the price before booking. The distance comes from the Haversine formula and the road factor. The fare is the base fare plus the distance and time charges, multiplied by the surge multiplier, and never below the minimum fare.

![Fare estimate](docs/screenshots/05-fare-estimate.png)

### 5.5 Booking and driver matching
`POST /api/rides` books a ride. The rider is taken from the login token, not from the request, so nobody can book as someone else. The system picks the **nearest available driver** within the radius and claims them with one atomic database update, so two riders can never get the same driver. The response includes the driver's name and vehicle.

![Book a ride](docs/screenshots/06-book-ride.png)

### 5.6 Real-time tracking
A small test page (`/ws-test.html`) connects over **WebSocket**. The rider sees status changes (`ACCEPTED`, `IN_PROGRESS`, `COMPLETED`) the moment the driver triggers them, and sees the driver's location as it is sent. Only the rider and driver of that ride may subscribe to its updates.

![Live updates](docs/screenshots/07-websocket.png)

### 5.7 Ride lifecycle
The driver calls `PUT /api/rides/{id}/start` and `PUT /api/rides/{id}/complete`. The rider (or driver) can cancel before the trip ends. Each change is checked: for example, a rider cannot start a ride, and a finished ride cannot be cancelled. Completing or cancelling frees the driver for the next request.

![Start and complete](docs/screenshots/08-ride-lifecycle.png)

### 5.8 Payment with Stripe
After a ride completes, the rider calls `POST /api/rides/{id}/payment`. The server creates a Stripe **PaymentIntent** using the ride's own fare. When the card is confirmed, Stripe calls `POST /api/payments/webhook`. The server verifies the signature and marks the payment `SUCCEEDED`. A payment cannot be made twice, and repeated webhook calls are safe.

![Create payment](docs/screenshots/09-payment-created.png)
![Payment succeeded in Stripe](docs/screenshots/10-payment-succeeded.png)

### 5.9 Receipts
`GET /api/rides/{id}/receipt` returns the trip details as JSON, and `GET /api/rides/{id}/receipt/pdf` returns a downloadable **PDF**. Only the people on the ride can open them.

![Receipt PDF](docs/screenshots/11-receipt-pdf.png)

### 5.10 Ride history
`GET /api/rides/history?page=0&size=10` returns a paginated list, newest first, for either role. It runs a fixed number of SQL statements however many rides there are, and a test makes sure that stays true.

![Ride history](docs/screenshots/12-history.png)

### 5.11 Ratings and reviews
After a completed ride, both sides call `POST /api/rides/{id}/rating` with 1 to 5 stars and an optional comment. The server works out who is being rated. Each person can rate once per ride. `GET /api/drivers/{id}/rating` shows the average and count, and `/reviews` lists reviews with first names only.

![Rating](docs/screenshots/13-rating.png)
![Driver rating summary](docs/screenshots/14-rating-summary.png)

### 5.12 Security in action
Requests without a token get `401`. Another rider's ride gives `403`. Repeated wrong passwords give `429` with a `Retry-After` header. Unexpected server errors return a generic message and are logged privately.

![Security responses](docs/screenshots/15-security.png)

### 5.13 Database
The tables in Supabase: `users`, `drivers`, `rides`, `payments` and `ratings`. Row Level Security is enabled, so Supabase's own web API cannot read them. The app connects directly.

![Supabase tables](docs/screenshots/16-database.png)

### 5.14 Tests and coverage
`./mvnw clean test` runs unit tests, MockMvc integration tests, a full end-to-end journey and a concurrency test. The JaCoCo report shows which lines the tests execute.

![Tests passing](docs/screenshots/17-tests.png)
![Coverage report](docs/screenshots/18-coverage.png)

### 5.15 Docker and deployment
The app builds into a small Docker image and runs on Render, with secrets provided as environment variables.

![Deployed on Render](docs/screenshots/19-deployed.png)

### API reference

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/health` | Public | Liveness check |
| POST | `/api/auth/register` | Public | Create an account |
| POST | `/api/auth/login` | Public | Get a JWT |
| GET | `/api/auth/me` | Logged in | Current user |
| POST | `/api/drivers/profile` | Driver | Create a driver profile |
| GET | `/api/drivers/me` | Driver | Own profile |
| PUT | `/api/drivers/me/availability` | Driver | Go online or offline |
| PUT | `/api/drivers/me/location` | Driver | Update position |
| POST | `/api/rides/estimate` | Rider | Fare estimate |
| POST | `/api/rides` | Rider | Book a ride |
| GET | `/api/rides/{id}` | Rider or driver of the ride | Ride details |
| PUT | `/api/rides/{id}/start` | Driver | Start the trip |
| PUT | `/api/rides/{id}/complete` | Driver | Finish the trip |
| PUT | `/api/rides/{id}/cancel` | Rider or driver | Cancel |
| GET | `/api/rides/history` | Logged in | Paginated history |
| POST / GET | `/api/rides/{id}/payment` | Rider (GET: rider or admin) | Start or check payment |
| POST | `/api/payments/webhook` | Stripe only | Payment result |
| GET | `/api/rides/{id}/receipt` | Rider or driver | Receipt as JSON |
| GET | `/api/rides/{id}/receipt/pdf` | Rider or driver | Receipt as PDF |
| POST | `/api/rides/{id}/rating` | Rider or driver | Rate the other person |
| GET | `/api/rides/{id}/ratings` | Rider, driver or admin | Ratings on a ride |
| GET | `/api/ratings/me` | Logged in | Own average rating |
| GET | `/api/drivers/{id}/rating` | Logged in | A driver's average |
| GET | `/api/drivers/{id}/reviews` | Logged in | A driver's reviews |
| WebSocket | `/ws` | Rider or driver of the ride | Live status and location |

---

## 6. Getting started

### Requirements
- Docker Desktop, **or** Java 17 and Git
- A free [Supabase](https://supabase.com) project (use the **Session pooler** connection details)
- A [Stripe](https://stripe.com) account in **test mode** (optional; the app starts without keys)

### Run with Docker
```bash
git clone https://github.com/Nithin0248/ridebooking.git
cd ridebooking
copy .env.example .env        # on macOS/Linux: cp .env.example .env
# fill in the values in .env
docker compose up --build
```
Then open `http://localhost:8080/health`.

### Run without Docker
Create `src/main/resources/application-local.properties` (it is git-ignored) with your database and secrets:
```properties
spring.datasource.url=jdbc:postgresql://<pooler-host>:5432/postgres
spring.datasource.username=postgres.<project-ref>
spring.datasource.password=<password>
jwt.secret=<a long random string>
stripe.secret-key=sk_test_...
stripe.webhook-secret=whsec_...
```
```bash
./mvnw spring-boot:run
```

### Settings

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | Database connection |
| `JWT_SECRET` | Signs login tokens. Use a long random value |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Stripe test keys |
| `CORS_ALLOWED_ORIGINS` | Browser origins allowed to call the API |

Fare, matching and rate-limit values are in `application.properties` and can be changed there.

### Run the tests
```bash
./mvnw clean test
```
The tests use an in-memory H2 database, so they need no secrets and no internet.

---

## 7. Conclusion

### What I built
A complete ride-booking backend: accounts with roles, fare estimates, safe driver matching, a full ride lifecycle, live updates, card payments, receipts, ratings, hardened security, performance tuning, a test suite, and a Docker deployment.

### What I learned
- **Design for failure.** Most of the interesting work was in what can go wrong: two riders and one driver, a webhook that arrives twice, a user guessing another user's ride id.
- **Never trust the client.** The rider, the amount and the person being rated are all decided on the server.
- **Keep secrets out of the code.** Configuration that is safe lives in Git, and secrets live only in environment variables.
- **Measure before optimising.** Query counts and `EXPLAIN ANALYZE` showed what actually needed fixing.
- **Tests change how you code.** Putting Stripe behind an interface made payments testable without the internet.
- **Small steps win.** Building and testing one feature at a time made bugs easy to find.

### Known limitations
- The rate limiter and cache live inside one server instance. Several instances would need a shared store such as Redis.
- Database changes use Hibernate's `ddl-auto=update`. A real product would use versioned migrations (for example Flyway).
- Tokens last 24 hours and there is no refresh-token flow yet.
- The free hosting plan sleeps when idle.
- There is no mobile or web client yet.

### Ideas for the future
- Flyway migrations
- Refresh tokens and logout
- A React or Android client using the same API
- Driver accept/decline instead of automatic assignment
- Redis for shared caching and rate limiting
- Load testing with k6 or JMeter
- Saved payment methods and refunds

### Author
**Nithin**
GitHub: [Nithin0248](https://github.com/Nithin0248)
