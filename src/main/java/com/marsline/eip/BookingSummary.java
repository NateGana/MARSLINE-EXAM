package com.marsline.eip;

import java.util.HashSet;
import java.util.Set;

/**
 * The combined result of merging the BOOKING, PAYMENT, and TRIP parts of one booking
 * (used by Task 3 - Aggregator).
 */
public class BookingSummary {

    private String bookingId;
    private String passengerName;
    private String route;
    private String travelDate;
    private double fare;
    private String paymentStatus;

    // Tracks which part types have arrived so far (not sent as JSON, just used internally).
    private final Set<String> partsReceived = new HashSet<>();

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getTravelDate() {
        return travelDate;
    }

    public void setTravelDate(String travelDate) {
        this.travelDate = travelDate;
    }

    public double getFare() {
        return fare;
    }

    public void setFare(double fare) {
        this.fare = fare;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Set<String> getPartsReceived() {
        return partsReceived;
    }

    public boolean isComplete() {
        return partsReceived.containsAll(Set.of("BOOKING", "PAYMENT", "TRIP"));
    }

    @Override
    public String toString() {
        return "{\"bookingId\":\"" + bookingId + "\",\"passengerName\":\"" + passengerName +
                "\",\"route\":\"" + route + "\",\"travelDate\":\"" + travelDate +
                "\",\"paymentStatus\":\"" + paymentStatus + "\",\"fare\":" + fare + "}";
    }
}
