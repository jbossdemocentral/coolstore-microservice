package com.redhat.coolstore.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

@XmlRootElement
public class Inventory implements Serializable {

    private static final long serialVersionUID = -7304814269819778382L;

    @XmlElement
    private String itemId;
    @XmlElement
    private int quantity;
    @XmlElement
    private String location;
    @XmlElement
    private String link;

    public Inventory() {

    }

    public Inventory(String itemId, int quantity, String location, String link) {
        super();
        this.itemId = itemId;
        this.quantity = quantity;
        this.location = location;
        this.link = link;
    }


    @Override
    public String toString() {
        return "Inventory [itemId=" + itemId + ", availability=" + quantity + "/" + location + " link=" + link + "]";
    }


}
