package com.marsline.eip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

/**
 * TASK 3 SUPPORT CLASS - tells Camel's Aggregator HOW to merge several messages
 * (one BOOKING part, one PAYMENT part, one TRIP part) that share the same bookingId
 * into a single BookingSummary object.
 *
 * Camel calls aggregate(...) once per incoming message for a given correlation key:
 *   - oldExchange is null the very first time (nothing aggregated yet).
 *   - after that, oldExchange holds the BookingSummary built so far.
 */
public class BookingAggregationStrategy implements AggregationStrategy {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        try {
            BookingSummary summary;
            if (oldExchange == null) {
                summary = new BookingSummary();
                summary.setBookingId(newExchange.getIn().getHeader("bookingId", String.class));
            } else {
                summary = oldExchange.getIn().getBody(BookingSummary.class);
            }

            String partType = newExchange.getIn().getHeader("partType", String.class);
            String json = newExchange.getIn().getBody(String.class);
            JsonNode node = mapper.readTree(json);

            switch (partType) {
                case "BOOKING":
                    summary.setPassengerName(node.path("passengerName").asText());
                    summary.setRoute(node.path("route").asText());
                    summary.setTravelDate(node.path("travelDate").asText());
                    break;
                case "PAYMENT":
                    summary.setPaymentStatus(node.path("paymentStatus").asText());
                    summary.setFare(node.path("amount").asDouble());
                    break;
                case "TRIP":
                    // Trip part confirms the route/schedule; MARSLINE only needs to know it arrived.
                    if (summary.getRoute() == null) {
                        summary.setRoute(node.path("route").asText());
                    }
                    break;
                default:
                    // Unknown part type - ignore, but don't crash the whole aggregation.
                    break;
            }
            summary.getPartsReceived().add(partType);

            Exchange resultExchange = (oldExchange != null) ? oldExchange : newExchange;
            resultExchange.getIn().setBody(summary, BookingSummary.class);
            resultExchange.getIn().setHeader("bookingId", summary.getBookingId());
            return resultExchange;

        } catch (Exception e) {
            throw new RuntimeException("Failed to aggregate booking part", e);
        }
    }
}
