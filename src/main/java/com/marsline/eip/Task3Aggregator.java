package com.marsline.eip;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TASK 3 - AGGREGATOR
 * -------------------
 * MARSLINE Scenario: Booking / Payment / Trip systems -> jms:queue:booking.parts
 *                     -> Aggregator (correlated by bookingId) -> jms:queue:booking.summaries -> Fulfillment
 *
 * Three separate systems each send ONE piece of a booking. The Aggregator waits for all three
 * pieces (BOOKING, PAYMENT, TRIP) that share the same bookingId, and only forwards a summary once
 * all three are in. If a booking is still incomplete after 3 seconds, it is NOT forwarded.
 */
public class Task3Aggregator {

    private static final String PARTS_QUEUE = "booking.parts";
    private static final String SUMMARIES_QUEUE = "booking.summaries";

    // Booking IDs that actually reached Fulfillment (i.e. were completed and forwarded).
    private final List<String> completedBookingIds = new CopyOnWriteArrayList<>();

    public void run() throws Exception {

        System.out.println("=".repeat(70));
        System.out.println("MARSLINE EIP TASK 3 - AGGREGATOR");
        System.out.println("=".repeat(70));

        CamelContext context = new DefaultCamelContext();

        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("vm://marsline-broker?broker.persistent=false&broker.useJmx=false");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        final ObjectMapper mapper = new ObjectMapper();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                from("jms:queue:" + PARTS_QUEUE)
                        .routeId("Task3Aggregator")
                        .log("[AGGREGATOR] Received ${header.partType} part for bookingId=${header.bookingId}")
                        .aggregate(header("bookingId"), new BookingAggregationStrategy())
                            .completionSize(3)
                            .completionTimeout(3000)
                            .choice()
                                .when(exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("timeout"))
                                    .log("[AGGREGATOR] Booking ${header.bookingId} INCOMPLETE - timed out after 3s, NOT forwarded to Fulfillment")
                                .otherwise()
                                    .process(exchange -> {
                                        BookingSummary summary = exchange.getIn().getBody(BookingSummary.class);
                                        exchange.getIn().setBody(mapper.writeValueAsString(summary));
                                    })
                                    .log("[AGGREGATOR] Booking ${header.bookingId} COMPLETE - forwarding to Fulfillment")
                                    .to("jms:queue:" + SUMMARIES_QUEUE)
                            .end();

                from("jms:queue:" + SUMMARIES_QUEUE)
                        .routeId("Task3Fulfillment")
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            completedBookingIds.add(bookingId);
                        })
                        .log("[FULFILLMENT] Received completed booking summary: ${body}");
            }
        });

        context.start();

        ProducerTemplate producer = context.createProducerTemplate();

        // BKG-3001 and BKG-3002: send all 3 parts, deliberately mixed / out of order.
        // BKG-3003: TRIP part is deliberately withheld -> should time out incomplete.
        sendPart(producer, "BKG-3001", "PAYMENT", "{\"paymentStatus\":\"PAID\",\"amount\":180.00}");
        sendPart(producer, "BKG-3002", "BOOKING", "{\"passengerName\":\"Marriah Asuncion\",\"route\":\"Cabuyao -> Batangas City\",\"travelDate\":\"2026-09-13\"}");
        sendPart(producer, "BKG-3001", "BOOKING", "{\"passengerName\":\"Reiza Atienza\",\"route\":\"Cabuyao -> Quezon City\",\"travelDate\":\"2026-09-12\"}");
        sendPart(producer, "BKG-3003", "BOOKING", "{\"passengerName\":\"Dwight Ramos\",\"route\":\"Cabuyao -> Batangas City\",\"travelDate\":\"2026-09-13\"}");
        sendPart(producer, "BKG-3001", "TRIP", "{\"route\":\"Cabuyao -> Quezon City\"}");
        sendPart(producer, "BKG-3002", "PAYMENT", "{\"paymentStatus\":\"PAID\",\"amount\":220.00}");
        sendPart(producer, "BKG-3003", "PAYMENT", "{\"paymentStatus\":\"PAID\",\"amount\":210.00}");
        sendPart(producer, "BKG-3002", "TRIP", "{\"route\":\"Cabuyao -> Batangas City\"}");
        // Note: BKG-3003's TRIP part is intentionally never sent.

        // Wait past the 3-second completion timeout so BKG-3003 has a chance to time out.
        Thread.sleep(4000);

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("  Expected : BKG-3001 and BKG-3002 reach Fulfillment as complete summaries (3/3 parts).");
        System.out.println("             BKG-3003 times out INCOMPLETE and never reaches Fulfillment.");
        System.out.println("  Actually completed and forwarded: " + completedBookingIds);

        boolean pass = completedBookingIds.contains("BKG-3001")
                && completedBookingIds.contains("BKG-3002")
                && !completedBookingIds.contains("BKG-3003");

        System.out.println("  Status : " + (pass ? "PASS" : "FAIL"));
        System.out.println("=".repeat(70));

        context.stop();
    }

    private void sendPart(ProducerTemplate producer, String bookingId, String partType, String jsonBody) {
        java.util.Map<String, Object> headers = new java.util.HashMap<>();
        headers.put("bookingId", bookingId);
        headers.put("partType", partType);
        producer.sendBodyAndHeaders("jms:queue:" + PARTS_QUEUE, jsonBody, headers);
    }
}
