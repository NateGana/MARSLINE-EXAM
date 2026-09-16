package com.marsline.eip;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Task1MessageChannel {

    private static final String QUEUE_NAME = "marsline.booking.requests";

    private final List<String> receivedBookingIds = new CopyOnWriteArrayList<>();

    public void run() throws Exception {

        System.out.println("=".repeat(70));
        System.out.println("MARSLINE EIP TASK 1 - MESSAGE CHANNEL");
        System.out.println("=".repeat(70));

        CamelContext context = new DefaultCamelContext();


        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("vm://marsline-broker?broker.persistent=false&broker.useJmx=false");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        ObjectMapper mapper = new ObjectMapper();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:onlineBookingSystem")
                        .routeId("Task1SourceRoute")
                        .log("[SOURCE] Online Booking System sending ${body}")
                        .to("jms:queue:" + QUEUE_NAME)
                        .log("[QUEUE] Sent to jms:queue:" + QUEUE_NAME);

                from("jms:queue:" + QUEUE_NAME)
                        .routeId("Task1TargetRoute")
                        .log("[TARGET] Booking Backend received ${body}")
                        .process(exchange -> {
                            String json = exchange.getIn().getBody(String.class);
                            Booking booking = mapper.readValue(json, Booking.class);
                            receivedBookingIds.add(booking.getBookingId());
                        });
            }
        });

        context.start();

        ProducerTemplate producer = context.createProducerTemplate();

        List<Booking> testBookings = List.of(
                new Booking("BKG-1001", "Juan Dela Cruz", "Cabuyao", "Manila", "2026-09-15", "12A", 180.00),
                new Booking("BKG-1002", "Maria Santos", "Cabuyao", "Batangas", "2026-09-15", "07C", 220.00),
                new Booking("BKG-1003", "Pedro Reyes", "Cabuyao", "Quezon", "2026-09-16", "03B", 190.00)
        );

        for (Booking booking : testBookings) {
            String json = mapper.writeValueAsString(booking);
            producer.sendBody("direct:onlineBookingSystem", json);
        }

        int waitedMs = 0;
        while (receivedBookingIds.size() < testBookings.size() && waitedMs < 5000) {
            Thread.sleep(100);
            waitedMs += 100;
        }

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("  Expected : All " + testBookings.size()
                + " booking messages travel through the JMS message channel '" + QUEUE_NAME + "'"
                + " and are received intact by the Booking Backend.");

        boolean allReceived = receivedBookingIds.size() == testBookings.size();
        boolean allMatch = true;
        for (Booking booking : testBookings) {
            if (!receivedBookingIds.contains(booking.getBookingId())) {
                allMatch = false;
            }
        }
        boolean pass = allReceived && allMatch;

        System.out.println("  Messages Sent     : " + testBookings.size());
        System.out.println("  Messages Received : " + receivedBookingIds.size());
        System.out.println("  Received IDs      : " + receivedBookingIds);
        System.out.println("  Status : " + (pass ? "PASS" : "FAIL"));
        System.out.println("=".repeat(70));

        context.stop();
    }
}
