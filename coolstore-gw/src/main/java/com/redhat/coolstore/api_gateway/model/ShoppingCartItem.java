package com.redhat.coolstore.api_gateway.model;

public class ShoppingCartItem  {

    public double price;
    public int quantity;
    public double promoSavings;
    public Product product;

    public String toString() {
        return ("productid: " + product.itemId + " quan: " + quantity + " price: " + price);
    }

}
