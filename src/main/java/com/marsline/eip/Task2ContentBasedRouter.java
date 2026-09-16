package com.marsline.eip;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TASK 2 - CONTENT-BASED ROUTER
 * -----------------------------
 * MARSLINE Scenario: Booking Intake -> jms:queue:marsline.booking.inbound -> Router -> LOCAL or PROVINCIAL
 *
 * The router looks INSIDE each booking (the "destination" field) and decides which queue it
 * should go to. Note: "Quezon" (the province) and "Quezon City" (Metro Manila) are treated as
 * different destinations on purpose.
 */
public class Task2ContentBasedRouter {

    private static final String INBOUND_QUEUE = "marsline.booking.inbound";
    private static final String LOCAL_QUEUE = "marsline.booking.local";
    private static final String PROVINCIAL_QUEUE = "marsline.booking.provincial";

    private static final String[] METRO_MANILA_DESTINATIONS = {
            "Manila", "Makati", "Quezon City", "Pasig", "Taguig",
            "Mandaluyong", "Pasay", "Paranaque", "Caloocan"
    };

    // Records where each bookingId ACTUALLY ended up, filled in by the consumer routes below.
    private final Map<String, String> actualRouting = new ConcurrentHashMap<>();

    public void run() throws Exception {

        System.out.println("=".repeat(70));
        System.out.println("MARSLINE EIP TASK 2 - CONTENT-BASED ROUTER");
        System.out.println("=".repeat(70));

        CamelContext context = new DefaultCamelContext();

        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("vm://marsline-broker?broker.persistent=false&broker.useJmx=false");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        ObjectMapper mapper = new ObjectMapper();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // Parse the JSON body once and copy the fields we need into headers,
                // so the .choice()/.when() below can make its decision.
                from("jms:queue:" + INBOUND_QUEUE)
                        .routeId("Task2ContentBasedRouter")
                        .process(exchange -> {
                            String json = exchange.getIn().getBody(String.class);
                            Booking booking = mapper.readValue(json, Booking.class);
                            boolean isLocal = Arrays.asList(METRO_MANILA_DESTINATIONS).contains(booking.getDestination());
                            exchange.getIn().setHeader("bookingId", booking.getBookingId());
                            exchange.getIn().setHeader("destination", booking.getDestination());
                            exchange.getIn().setHeader("isLocal", isLocal);
                        })
                        .log("[ROUTER] Evaluating destination for ${header.bookingId}: ${header.destination}")
                        .choice()
                            .when(header("isLocal").isEqualTo(true))
                                .log("[ROUTER] ${header.bookingId} -> LOCAL")
                                .to("jms:queue:" + LOCAL_QUEUE)
                            .otherwise()
                                .log("[ROUTER] ${header.bookingId} -> PROVINCIAL")
                                .to("jms:queue:" + PROVINCIAL_QUEUE)
                        .end();

                // Two independent "processing systems" that just record what they actually received.
                from("jms:queue:" + LOCAL_QUEUE)
                        .routeId("Task2LocalProcessingSystem")
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            actualRouting.put(bookingId, "LOCAL");
                        });

                from("jms:queue:" + PROVINCIAL_QUEUE)
                        .routeId("Task2ProvincialProcessingSystem")
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            actualRouting.put(bookingId, "PROVINCIAL");
                        });
            }
        });

        context.start();

        ProducerTemplate producer = context.createProducerTemplate();

        // Test data required by the PDF: 5 bookings, expected 2 LOCAL / 3 PROVINCIAL.
        List<Booking> testBookings = List.of(
                new Booking("BKG-2001", "Reiza Atienza", "Cabuyao", "Manila", "2026-09-12", "01A", 180.00),
                new Booking("BKG-2002", "Marriah Asuncion", "Cabuyao", "Makati", "2026-09-12", "02B", 200.00),
                new Booking("BKG-2003", "Dwight Ramos", "Cabuyao", "Batangas", "2026-09-13", "03C", 220.00),
                new Booking("BKG-2004", "Liza Reyes", "Cabuyao", "Quezon", "2026-09-13", "04D", 210.00),
                new Booking("BKG-2005", "Noel Cruz", "Cabuyao", "Pampanga", "2026-09-14", "05E", 230.00)
        );

        Map<String, String> expectedRouting = new ConcurrentHashMap<>();
        for (Booking booking : testBookings) {
            boolean isLocal = List.of(METRO_MANILA_DESTINATIONS).contains(booking.getDestination());
            expectedRouting.put(booking.getBookingId(), isLocal ? "LOCAL" : "PROVINCIAL");
            producer.sendBody("jms:queue:" + INBOUND_QUEUE, mapper.writeValueAsString(booking));
        }

        int waitedMs = 0;
        while (actualRouting.size() < testBookings.size() && waitedMs < 5000) {
            Thread.sleep(100);
            waitedMs += 100;
        }

        System.out.println();
        System.out.println("======== ROUTING SUMMARY ========");
        long localCount = actualRouting.values().stream().filter("LOCAL"::equals).count();
        long provincialCount = actualRouting.values().stream().filter("PROVINCIAL"::equals).count();
        System.out.println("LOCAL routed      : " + localCount);
        System.out.println("PROVINCIAL routed : " + provincialCount);
        System.out.println("Total routed      : " + actualRouting.size() + " / " + testBookings.size());

        boolean noneDropped = actualRouting.size() == testBookings.size();
        boolean allCorrect = expectedRouting.entrySet().stream()
                .allMatch(e -> e.getValue().equals(actualRouting.get(e.getKey())));

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("  Expected : Every booking is routed to the branch matching its own destination; nothing dropped.");
        System.out.println("  Per-booking detail:");
        for (Booking booking : testBookings) {
            System.out.println("    " + booking.getBookingId() + " (" + booking.getDestination() + ") -> expected="
                    + expectedRouting.get(booking.getBookingId()) + ", actual=" + actualRouting.get(booking.getBookingId()));
        }
        boolean pass = noneDropped && allCorrect;
        System.out.println("  Status : " + (pass ? "PASS" : "FAIL"));
        System.out.println("=".repeat(70));

        context.stop();
    }
}
