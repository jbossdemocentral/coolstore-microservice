package com.redhat.coolstore.rating.model;

import java.io.Serializable;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class Rating implements Serializable {

    private static final long serialVersionUID = -6994655395272795259L;

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

    public Rating(JsonObject json) {
        this.itemId = json.getString("itemId");
        this.rating = json.getDouble("rating");
        try {
            this.count = json.getLong("count");
        } catch (NullPointerException ex) {
            this.count = 1;
        }
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
        return "[itemId: " + itemId + " rating: " + rating + " count: " + count + "]";
    }

    public JsonObject toJson() {

        final JsonObject json = new JsonObject();
        json.put("itemId", this.itemId);
        json.put("rating", this.rating);
        json.put("count", this.count);
        return json;
    }

}
