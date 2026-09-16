package com.marsline.eip;

public class Booking {

    private String bookingId;
    private String customer;
    private String origin;
    private String destination;
    private String travelDate;
    private String seat;
    private double fare;

    public Booking() {
    }

    public Booking(String bookingId, String customer, String origin, String destination,
                   String travelDate, String seat, double fare) {
        this.bookingId = bookingId;
        this.customer = customer;
        this.origin = origin;
        this.destination = destination;
        this.travelDate = travelDate;
        this.seat = seat;
        this.fare = fare;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getTravelDate() {
        return travelDate;
    }

    public void setTravelDate(String travelDate) {
        this.travelDate = travelDate;
    }

    public String getSeat() {
        return seat;
    }

    public void setSeat(String seat) {
        this.seat = seat;
    }

    public double getFare() {
        return fare;
    }

    public void setFare(double fare) {
        this.fare = fare;
    }

    @Override
    public String toString() {
        return "Booking{" +
                "bookingId='" + bookingId + '\'' +
                ", customer='" + customer + '\'' +
                ", origin='" + origin + '\'' +
                ", destination='" + destination + '\'' +
                ", travelDate='" + travelDate + '\'' +
                ", seat='" + seat + '\'' +
                ", fare=" + fare +
                '}';
    }
}
