package com.redhat.coolstore.service;

import com.redhat.coolstore.model.kie.Product;
import com.redhat.coolstore.model.kie.ShoppingCart;

public interface ShoppingCartService {
    public ShoppingCart getShoppingCart(String cartId);

    public Product getProduct(String itemId);

    public ShoppingCart deleteItem(String cartId, String itemId, int quantity);

    public ShoppingCart checkout(String cartId);

    public ShoppingCart addItem(String cartId, String itemId, int quantity);

    public ShoppingCart set(String cartId, String tmpId);
}
