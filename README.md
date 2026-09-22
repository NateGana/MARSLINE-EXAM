# MARSLINE — Enterprise Integration Patterns (ITP103 Midterm Lab Exam)


🌐 VIEW LIVE PAGE

https://nategana.github.io/Marsline-System/

## 1. Project Description

MARSLINE is a fictional provincial bus line (Cabuyao, Laguna) created for this exam. Its booking,
payment, trip assignment, legacy ticketing, and CRM systems need to exchange data reliably even
though they use different formats and can go down temporarily. This project implements **5
Enterprise Integration Patterns (Hohpe & Woolf)** using **Apache Camel** to solve that problem.

## 2. Project / Team Purpose

Built for the ITP103 Enterprise Profiling Midterm Lab Exam, Group MARSLINE.

| Task | Pattern | Member (per exam PDF) |
|---|---|---|
| 1 | Message Channel | Baculinao, Mark Joseph L. |
| 2 | Content-Based Router | Bernal, Mars Jairus G. |
| 3 | Aggregator | Bunao, Ron Rupert M. |
| 4 | Message Translator | Butin, Josel Reimar M. |
| 5 | Error Handling & Retry | Gana, Nathaniel E. |

## 3. Technologies Used

- Java 17
- Maven
- Apache Camel 4.8.0 (camel-core, camel-jms, camel-main)
- Apache ActiveMQ 6.1.8 (embedded broker, Jakarta JMS client)
- Jackson 2.17.2 (databind)
- SLF4J 2.0.13 (slf4j-simple, console logging)
- JDK `DocumentBuilder` (DOM XML parser)

## 4. Architecture

```
Task 1: Booking reaches the backend (Message Channel)
   -> Task 2: Routed LOCAL vs PROVINCIAL (Content-Based Router)
      -> Task 3: Booking + Payment + Trip merged (Aggregator)
         -> Task 4: Legacy XML becomes JSON (Message Translator)
            -> Task 5: Failures recover or get parked (Error Handling + Retry)
```

Each task runs as its own independent, self-contained program (its own embedded ActiveMQ broker
instance and its own Camel routes) so each one can be run and demonstrated on its own.

## 5. The Five Enterprise Integration Patterns

1. **Message Channel** — `marsline.booking.requests` queue decouples the Online Booking System
   from the Booking Backend.
2. **Content-Based Router** — reads the `destination` field and routes to LOCAL or PROVINCIAL.
3. **Aggregator** — merges BOOKING + PAYMENT + TRIP parts correlated by `bookingId`, with a
   3-second completion timeout for incomplete bookings.
4. **Message Translator** — converts legacy XML tickets into JSON using a real DOM parser +
   Jackson (no string replace / regex tricks).
5. **Error Handling + Retry** — Camel's own redelivery policy (`maximumRedeliveries(2)`) with a
   parking-lot queue for messages that never recover.

## 6. Project Structure

```
MARSLINE/
├── pom.xml
├── README.md
├── .gitignore
└── src/
    └── main/
        └── java/
            └── com/marsline/eip/
                ├── MarslineEipApplication.java   (entry point / task dispatcher)
                ├── Booking.java                  (shared booking model)
                ├── Task1MessageChannel.java
                ├── Task2ContentBasedRouter.java
                ├── Task3Aggregator.java
                ├── BookingAggregationStrategy.java
                ├── BookingSummary.java
                ├── Task4MessageTranslator.java
                ├── LegacyTicket.java
                └── Task5ErrorChannelRetry.java
```

## 7. Installation Requirements

- JDK 17 or newer
- Apache Maven 3.9+
- Internet access the first time you build (Maven needs to download Camel/ActiveMQ/Jackson from
  Maven Central into your local `.m2` repository)
- Windows, macOS, or Linux (commands below are shown for Windows Command Prompt / PowerShell)

## 8. How to Run Each Task (Windows)

Open Command Prompt in the project folder (the one containing `pom.xml`), then:

```
mvn compile exec:java -Dexec.args="task1"
mvn compile exec:java -Dexec.args="task2"
mvn compile exec:java -Dexec.args="task3"
mvn compile exec:java -Dexec.args="task4"
mvn compile exec:java -Dexec.args="task5"
```

Each command prints readable console logs and ends with a `[CHECKPOINT]` block that reports
`Status: PASS` or `Status: FAIL`, computed from what the program actually did (message counts,
routing decisions, aggregation results, field checks, or retry/parking-lot outcomes) — never a
hardcoded value.

## 9. Expected Behavior vs Actual Result

**Expected Result** (what the code is designed to do, per task):
- Task 1: 3/3 bookings delivered through the queue, no data loss.
- Task 2: 5/5 bookings routed correctly (2 LOCAL, 3 PROVINCIAL), 0 dropped.
- Task 3: BKG-3001 and BKG-3002 complete and reach Fulfillment; BKG-3003 times out and does not.
- Task 4: 3/3 XML tickets translated, 21/21 fields preserved.
- Task 5: BKG-5001 and BKG-5002 recover via retry; BKG-5003 is moved to the parking lot.

**Actual Result:** *Execution must be verified locally on Windows.* This code was written and
reviewed carefully against the Apache Camel 4.8 / ActiveMQ 6.1.8 APIs, but it has **not** been
compiled or run in the environment that generated it (no access to Maven Central from that
sandbox to download the required libraries). Run the 5 commands above on your own machine, fix
any compilation errors Maven reports (see Troubleshooting below), and record the real console
output — that real output is your actual evidence, not this document.

## 10. Explanation of Each Task

See the companion file `PRESENTATION_GUIDE.md` for a simple explanation of each task, the exact
code section to point at, and a short script for your presentation.

## 11. Testing

Test data is hard-coded in each task's `run()` method (no external test framework needed for this
exam):
- Task 1: BKG-1001, BKG-1002, BKG-1003
- Task 2: 5 bookings with destinations Manila, Makati, Batangas, Quezon, Pampanga
- Task 3: BKG-3001 (complete), BKG-3002 (complete), BKG-3003 (TRIP part withheld)
- Task 4: 3 legacy XML ticket records, 7 fields each
- Task 5: BKG-5001 (fails twice), BKG-5002 (fails once), BKG-5003 (always fails)
