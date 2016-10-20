package com.redhat.coolstore.api_gateway.model;

public class Product  {

	public String itemId;
	public String name;
	public String desc;
	public double price;
	public Inventory availability;

	public Product() {

	}
	public Product(String itemId, String name, String desc, double price, Inventory availability) {
		this.itemId = itemId;
		this.name = name;
		this.desc = desc;
		this.price = price;
		this.availability = availability;
	}

	public Inventory getAvailability() {
		return availability;
	}

	public void setAvailability(Inventory availability) {
		this.availability = availability;
	}


	public String toString() {
		return ("Product toString: name:" + name + " id:" + itemId + " price:" + price + " desc:" + desc);
	}
}
