package com.redhat.coolstore.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Path;

import com.redhat.coolstore.model.Inventory;

@ApplicationScoped
public class InventoryService {

	public static final String[] CITIES = new String[] {"Tulsa", "Frankfurt", "Tokyo", "Paris", "Raleigh"};

	public InventoryService() {

	}
	
	public Inventory getInventory(String itemId) {

		return new Inventory(itemId, getRandomAvailability());

	}

	private String getRandomAvailability() {
		int amt = (int)Math.floor(Math.random() * 100);

		String city = CITIES[(int)(Math.floor(Math.random() * CITIES.length))];

		return amt + " available at " + city + " store!";
	}
}
