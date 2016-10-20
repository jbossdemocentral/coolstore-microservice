package com.redhat.coolstore.api_gateway.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Inventory {

	public String itemId;
	public int quantity;
	public String location;
	public String link;

	public Inventory() {

	}

	public Inventory(String itemId, int quantity, String location, String link) {
		this.itemId = itemId;
		this.quantity = quantity;
		this.location = location;
		this.link = link;
	}

	public String toString() {
		return ("Inventory toString: id:" + itemId + " q:" + quantity + " loc:" + location + " link:" + link);
	}
}
