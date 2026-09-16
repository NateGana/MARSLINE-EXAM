package com.marsline.eip;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TASK 4 - MESSAGE TRANSLATOR
 * ---------------------------
 * MARSLINE Scenario: Legacy Ticketing System (XML) -> Message Translator -> Modern MARSLINE CRM (JSON)
 *
 * The old ticketing machine only speaks XML. The new CRM only understands JSON. This class does a
 * REAL translation: it reads the XML tag-by-tag with the JDK's own DOM parser (no shortcuts, no
 * regex/string-replace tricks), fills a LegacyTicket object, then lets Jackson's ObjectMapper turn
 * that object into JSON.
 */
public class Task4MessageTranslator {

    private static final String LEGACY_QUEUE = "marsline.ticket.legacy";
    private static final String JSON_QUEUE = "marsline.ticket.json";

    private static final String[] REQUIRED_FIELDS = {
            "ticketId", "bookingId", "customerName", "origin", "destination", "travelDate", "seatNumber"
    };

    private final List<String> receivedJsonMessages = new CopyOnWriteArrayList<>();

    public void run() throws Exception {

        System.out.println("=".repeat(70));
        System.out.println("MARSLINE EIP TASK 4 - MESSAGE TRANSLATOR");
        System.out.println("=".repeat(70));

        CamelContext context = new DefaultCamelContext();

        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("vm://marsline-broker?broker.persistent=false&broker.useJmx=false");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        final ObjectMapper mapper = new ObjectMapper();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                from("jms:queue:" + LEGACY_QUEUE)
                        .routeId("Task4MessageTranslator")
                        .log("[LEGACY] Received legacy XML ticket message")
                        .process(exchange -> {
                            String xml = exchange.getIn().getBody(String.class);
                            System.out.println("---- LEGACY XML RECEIVED ----");
                            System.out.println(xml);

                            LegacyTicket ticket = parseLegacyXml(xml);

                            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ticket);
                            System.out.println("---- MODERN JSON GENERATED ----");
                            System.out.println(json);

                            exchange.getIn().setBody(json);
                        })
                        .log("[TRANSLATOR] Translation complete, sending to modern CRM queue")
                        .to("jms:queue:" + JSON_QUEUE);

                from("jms:queue:" + JSON_QUEUE)
                        .routeId("Task4ModernCrm")
                        .process(exchange -> receivedJsonMessages.add(exchange.getIn().getBody(String.class)))
                        .log("[CRM] Modern CRM received translated JSON ticket");
            }
        });

        context.start();

        ProducerTemplate producer = context.createProducerTemplate();

        String[] legacyXmlTickets = {
                buildLegacyXml("T-4001", "BKG-1001", "Raiza Atienza", "Cabuyao", "Quezon City", "2026-09-12", "12A"),
                buildLegacyXml("T-4002", "BKG-1002", "Marriah Asuncion", "Cabuyao", "Batangas City", "2026-09-13", "07C"),
                buildLegacyXml("T-4003", "BKG-1003", "Dwight Ramos", "Cabuyao", "Pampanga", "2026-09-14", "03B")
        };

        for (String xml : legacyXmlTickets) {
            producer.sendBody("jms:queue:" + LEGACY_QUEUE, xml);
        }

        int waitedMs = 0;
        while (receivedJsonMessages.size() < legacyXmlTickets.length && waitedMs < 5000) {
            Thread.sleep(100);
            waitedMs += 100;
        }

        // Count how many of the 7 required fields actually made it into each JSON message.
        int totalFieldsFound = 0;
        int totalFieldsExpected = legacyXmlTickets.length * REQUIRED_FIELDS.length;

        for (String json : receivedJsonMessages) {
            LegacyTicket parsedBack = mapper.readValue(json, LegacyTicket.class);
            if (isNotBlank(parsedBack.getTicketId())) totalFieldsFound++;
            if (isNotBlank(parsedBack.getBookingId())) totalFieldsFound++;
            if (isNotBlank(parsedBack.getCustomerName())) totalFieldsFound++;
            if (isNotBlank(parsedBack.getOrigin())) totalFieldsFound++;
            if (isNotBlank(parsedBack.getDestination())) totalFieldsFound++;
            if (isNotBlank(parsedBack.getTravelDate())) totalFieldsFound++;
            if (isNotBlank(parsedBack.getSeatNumber())) totalFieldsFound++;
        }

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("  Expected : " + legacyXmlTickets.length + " XML tickets translate to valid JSON, "
                + totalFieldsExpected + "/" + totalFieldsExpected + " fields preserved.");
        System.out.println("  Tickets translated : " + receivedJsonMessages.size() + " / " + legacyXmlTickets.length);
        System.out.println("  Fields preserved   : " + totalFieldsFound + " / " + totalFieldsExpected);

        boolean pass = receivedJsonMessages.size() == legacyXmlTickets.length && totalFieldsFound == totalFieldsExpected;
        System.out.println("  Status : " + (pass ? "PASS" : "FAIL"));
        System.out.println("=".repeat(70));

        context.stop();
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Reads the legacy XML with the JDK's real DOM parser (javax.xml.parsers.DocumentBuilder).
     * DOCTYPE declarations are disallowed to protect against XXE (XML External Entity) attacks -
     * this is the "XML security requirement" mentioned in the PDF.
     */
    private LegacyTicket parseLegacyXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();

        LegacyTicket ticket = new LegacyTicket();
        ticket.setTicketId(textOf(root, "ticketId"));
        ticket.setBookingId(textOf(root, "bookingId"));
        ticket.setCustomerName(textOf(root, "passenger"));
        ticket.setOrigin(textOf(root, "origin"));
        ticket.setDestination(textOf(root, "destination"));
        ticket.setTravelDate(textOf(root, "travelDate"));
        ticket.setSeatNumber(textOf(root, "seatNumber"));
        return ticket;
    }

    private String textOf(Element root, String tagName) {
        var nodeList = root.getElementsByTagName(tagName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        return nodeList.item(0).getTextContent();
    }

    private String buildLegacyXml(String ticketId, String bookingId, String passenger,
                                   String origin, String destination, String travelDate, String seatNumber) {
        return "<ticket>"
                + "<ticketId>" + ticketId + "</ticketId>"
                + "<bookingId>" + bookingId + "</bookingId>"
                + "<passenger>" + passenger + "</passenger>"
                + "<origin>" + origin + "</origin>"
                + "<destination>" + destination + "</destination>"
                + "<travelDate>" + travelDate + "</travelDate>"
                + "<seatNumber>" + seatNumber + "</seatNumber>"
                + "</ticket>";
    }
}
