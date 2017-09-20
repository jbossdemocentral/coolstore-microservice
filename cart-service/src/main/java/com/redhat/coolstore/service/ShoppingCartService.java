package com.redhat.coolstore.service;

import com.redhat.coolstore.model.kie.Product;
import com.redhat.coolstore.model.kie.ShoppingCart;

public interface ShoppingCartService {
	public void priceShoppingCart(ShoppingCart sc);
	public ShoppingCart getShoppingCart(String cartId);
	public Product getProduct(String itemId);
}
