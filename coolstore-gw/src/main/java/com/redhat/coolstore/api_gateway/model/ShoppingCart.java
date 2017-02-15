package com.redhat.coolstore.api_gateway.model;

import java.util.ArrayList;
import java.util.List;

public class ShoppingCart  {


    public double cartItemTotal=0.0;

    public double cartItemPromoSavings=0.0;

    public double shippingTotal=0.0;

    public double shippingPromoSavings=0.0;

    public double cartTotal=0.0;

    public List<ShoppingCartItem> shoppingCartItemList = new ArrayList<ShoppingCartItem>();
    

    public String toString() {
        return ("cart: total: " + cartTotal + " sci list: " + shoppingCartItemList);
    }


	public double getCartItemTotal() {
		return cartItemTotal;
	}


	public void setCartItemTotal(double cartItemTotal) {
		this.cartItemTotal = cartItemTotal;
	}


	public double getCartItemPromoSavings() {
		return cartItemPromoSavings;
	}


	public void setCartItemPromoSavings(double cartItemPromoSavings) {
		this.cartItemPromoSavings = cartItemPromoSavings;
	}


	public double getShippingTotal() {
		return shippingTotal;
	}


	public void setShippingTotal(double shippingTotal) {
		this.shippingTotal = shippingTotal;
	}


	public double getShippingPromoSavings() {
		return shippingPromoSavings;
	}


	public void setShippingPromoSavings(double shippingPromoSavings) {
		this.shippingPromoSavings = shippingPromoSavings;
	}


	public double getCartTotal() {
		return cartTotal;
	}


	public void setCartTotal(double cartTotal) {
		this.cartTotal = cartTotal;
	}


	public List<ShoppingCartItem> getShoppingCartItemList() {
		return shoppingCartItemList;
	}


	public void setShoppingCartItemList(List<ShoppingCartItem> shoppingCartItemList) {
		this.shoppingCartItemList = shoppingCartItemList;
		
		for(ShoppingCartItem item:shoppingCartItemList){
			cartItemTotal++;

		    cartItemPromoSavings+=item.getPromoSavings();

		    shippingTotal+=item.getPrice();

		    shippingPromoSavings+=item.getPromoSavings();

		    cartTotal+=item.getPrice();
		}
	}
    
    
}
