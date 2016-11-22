package com.redhat.coolstore.service;

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
		return em.find(Inventory.class,itemId);
	}
}
