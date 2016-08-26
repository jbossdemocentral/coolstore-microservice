package com.redhat.coolstore.service;

import javax.enterprise.context.ApplicationScoped;

import com.redhat.coolstore.model.Inventory;

@ApplicationScoped
public class InventoryService {

	private static final String[] CITIES = new String[] {"Tulsa", "Frankfurt", "Tokyo", "Paris", "Raleigh"};

	public InventoryService() {

	}
	
	public Inventory getInventory(String itemId) {
		String location = CITIES[(int)(Math.floor(Math.random() * CITIES.length))];
		String link = "http://maps.google.com/?q=" + location.toLowerCase();
		return new Inventory(itemId,
				(int)Math.floor(Math.random() * 100),
				CITIES[(int)(Math.floor(Math.random() * CITIES.length))],
				link);

	}
}
