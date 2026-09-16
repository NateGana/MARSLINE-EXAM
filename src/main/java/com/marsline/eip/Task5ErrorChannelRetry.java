package com.marsline.eip;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TASK 5 - ERROR HANDLING + RETRY
 * -------------------------------
 * MARSLINE Scenario: Booking Request -> Backend (simulated outage) -> Retry -> Success OR Parking Lot
 *
 * The "Booking Backend" here is a small simulator: it is programmed to genuinely fail a set number
 * of times for each test booking, then behave normally. Camel's own redelivery machinery
 * (maximumRedeliveries) does the retrying - this is not a hand-written while-loop. Every failed
 * attempt is logged to an error queue as an audit trail. If retries run out, the booking is moved
 * to a parking-lot queue instead of being silently lost.
 */
public class Task5ErrorChannelRetry {

    private static final String REQUEST_QUEUE = "marsline.booking.requests.task5";
    private static final String ERROR_QUEUE = "marsline.booking.errors";
    private static final String PARKING_LOT_QUEUE = "marsline.booking.parkinglot";

    // How many times each test booking should fail BEFORE finally succeeding.
    // BKG-5003 uses a huge number so it never succeeds within the 2 allowed redeliveries.
    private final Map<String, AtomicInteger> failuresRemaining = new ConcurrentHashMap<>(Map.of(
            "BKG-5001", new AtomicInteger(2),
            "BKG-5002", new AtomicInteger(1),
            "BKG-5003", new AtomicInteger(Integer.MAX_VALUE)
    ));

    private final Map<String, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();

    private final List<String> succeeded = new CopyOnWriteArrayList<>();
    private final List<String> parkedForReview = new CopyOnWriteArrayList<>();

    public void run() throws Exception {

        System.out.println("=".repeat(70));
        System.out.println("MARSLINE EIP TASK 5 - ERROR HANDLING / RETRY");
        System.out.println("=".repeat(70));

        CamelContext context = new DefaultCamelContext();

        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("vm://marsline-broker?broker.persistent=false&broker.useJmx=false");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        ProducerTemplate producerForAudit = context.createProducerTemplate();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // Real Camel redelivery policy: try again automatically up to 2 more times
                // (3 attempts total) with a short pause between attempts. If it still fails,
                // the message is sent to the parking lot queue instead of being lost.
                errorHandler(deadLetterChannel("jms:queue:" + PARKING_LOT_QUEUE)
                        .maximumRedeliveries(2)
                        .redeliveryDelay(200)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .logExhausted(true)
                        .onRedelivery(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            int attempt = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, 0, Integer.class);
                            String reason = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class) != null
                                    ? exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class).getMessage()
                                    : "unknown";
                            producerForAudit.sendBody("jms:queue:" + ERROR_QUEUE,
                                    "AUDIT: " + bookingId + " failed attempt " + attempt + " - " + reason);
                        }));

                from("jms:queue:" + REQUEST_QUEUE)
                        .routeId("Task5ErrorChannelRetry")
                        .log("[BACKEND] Processing booking ${header.bookingId} (attempt "
                                + "${header." + Exchange.REDELIVERY_COUNTER + "})")
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            attemptCounts.putIfAbsent(bookingId, new AtomicInteger(0));
                            int attemptNumber = attemptCounts.get(bookingId).incrementAndGet();

                            AtomicInteger remaining = failuresRemaining.get(bookingId);
                            if (remaining != null && remaining.get() > 0) {
                                remaining.decrementAndGet();
                                System.out.println("[BACKEND] Backend unavailable for " + bookingId
                                        + " (attempt " + attemptNumber + ") - FAILED");
                                throw new RuntimeException("Simulated backend outage for " + bookingId);
                            }
                            System.out.println("[BACKEND] " + bookingId + " processed successfully on attempt "
                                    + attemptNumber + " - SUCCESS");
                            succeeded.add(bookingId);
                        });

                from("jms:queue:" + PARKING_LOT_QUEUE)
                        .routeId("Task5ParkingLot")
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            parkedForReview.add(bookingId);
                        })
                        .log("[PARKING LOT] ${header.bookingId} moved to parking lot for manual review");
            }
        });

        context.start();

        ProducerTemplate producer = context.createProducerTemplate();

        for (String bookingId : List.of("BKG-5001", "BKG-5002", "BKG-5003")) {
            System.out.println();
            System.out.println("--- Sending " + bookingId + " ---");
            producer.sendBodyAndHeader("jms:queue:" + REQUEST_QUEUE, "{}", "bookingId", bookingId);
            // Small pause so the console log for each booking's retries stays readable/in order.
            Thread.sleep(1500);
        }

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("  Expected : BKG-5001 and BKG-5002 recover via retry and are delivered.");
        System.out.println("             BKG-5003 is moved to " + PARKING_LOT_QUEUE + " after 2 retries.");
        System.out.println("  Succeeded          : " + succeeded);
        System.out.println("  Parked for review  : " + parkedForReview);

        boolean pass = succeeded.contains("BKG-5001")
                && succeeded.contains("BKG-5002")
                && parkedForReview.contains("BKG-5003")
                && !succeeded.contains("BKG-5003");

        System.out.println("  Status : " + (pass ? "PASS" : "FAIL"));
        System.out.println("=".repeat(70));

        context.stop();
    }
}
