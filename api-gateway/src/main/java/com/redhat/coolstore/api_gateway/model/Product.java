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

	public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public String toString() {
		return ("Product toString: name:" + name + " id:" + itemId + " price:" + price + " desc:" + desc);
	}
}
