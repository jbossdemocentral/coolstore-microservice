package com.redhat.coolstore.service;

import org.springframework.stereotype.Component;

import com.redhat.coolstore.model.ShoppingCart;

@Component
public class ShippingService {

	public void calculateShipping(ShoppingCart sc) {
		if ( sc != null ) {
			if ( sc.getCartItemTotal() >= 0 && sc.getCartItemTotal() < 25) {
				sc.setShippingTotal(2.99);
			} else if ( sc.getCartItemTotal() >= 25 && sc.getCartItemTotal() < 50) {
				sc.setShippingTotal(4.99);
			} else if ( sc.getCartItemTotal() >= 50 && sc.getCartItemTotal() < 75) {
				sc.setShippingTotal(6.99);
			} else if ( sc.getCartItemTotal() >= 75 && sc.getCartItemTotal() < 100) {
				sc.setShippingTotal(8.99);				
			} else if ( sc.getCartItemTotal() >= 100 && sc.getCartItemTotal() < 10000) {
				sc.setShippingTotal(10.99);
			}
		}
	}
}
