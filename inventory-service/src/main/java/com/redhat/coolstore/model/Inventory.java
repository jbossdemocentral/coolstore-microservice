package com.redhat.coolstore.model;

import java.io.Serializable;

public class Inventory implements Serializable {

    private static final long serialVersionUID = -7304814269819778382L;
    private String itemId;
    private String availability;

    public Inventory() {

    }

    public Inventory(String itemId, String availability) {
        super();
        this.itemId = itemId;
        this.availability = availability;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    @Override
    public String toString() {
        return "Inventory [itemId=" + itemId + ", availability=" + availability + "]";
    }


}
