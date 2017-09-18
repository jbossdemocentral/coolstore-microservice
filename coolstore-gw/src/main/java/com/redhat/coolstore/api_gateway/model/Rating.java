package com.redhat.coolstore.api_gateway.model;

public class Rating {

    private String itemId;
    private double rating;
    private long count;

    public Rating() {

    }

    public Rating(String itemId, double rating, long count) {
        this.itemId = itemId;
        this.rating = rating;
        this.count = count;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public String toString() {
        return "[Rating: itemId: " + itemId + " rating: " + rating + " count: " + count + "]";
    }


}
