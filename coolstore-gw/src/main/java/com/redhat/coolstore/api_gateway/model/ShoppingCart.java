package com.redhat.coolstore.api_gateway.model;

import java.util.ArrayList;
import java.util.List;

public class ShoppingCart  {

    private String cartId;

    public double cartItemTotal=0.0;

    public double cartItemPromoSavings=0.0;

    public double shippingTotal=0.0;

    public double shippingPromoSavings=0.0;

    public double cartTotal=0.0;

    public List<ShoppingCartItem> shoppingCartItemList = new ArrayList<ShoppingCartItem>();
    
    public ShoppingCart(String cartId) {
		this.cartId = cartId;
    }    

    public ShoppingCart() {
    }  

	public String getCartId() {
		return cartId;
	}

	public void setCartId(String cartId) {
		this.cartId = cartId;
	}

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
	}
    
    
}
