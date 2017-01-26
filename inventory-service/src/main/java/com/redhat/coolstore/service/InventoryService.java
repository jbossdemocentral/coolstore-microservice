package com.redhat.coolstore.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import com.redhat.coolstore.model.Inventory;

@Stateless
public class InventoryService {

	@PersistenceContext
	private EntityManager em;

	public InventoryService() {

	}
	
	public Inventory getInventory(String itemId) {
		Inventory inventory = em.find(Inventory.class,itemId);
		
//		List<String> recalledProducts = Arrays.asList("165613","165614");
//		if (recalledProducts.contains(inventory.getItemId())) {
//			inventory.setQuantity(0);
//		}
		
		return inventory;
	}
}
