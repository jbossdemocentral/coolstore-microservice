package com.redhat.coolstore.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

@Entity
@XmlRootElement
@Table(name = "PRODUCT_INVENTORY", uniqueConstraints = @UniqueConstraint(columnNames = "itemId"))
public class Inventory implements Serializable {

    private static final long serialVersionUID = -7304814269819778382L;

    @XmlElement
    @Id
    private String itemId;


    @XmlElement
    private String location;


    @XmlElement
    private int quantity;


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
    
    public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	@Override
    public String toString() {
        return "Inventory [itemId=" + itemId + ", availability=" + quantity + "/" + location + " link=" + link + "]";
    }
}
