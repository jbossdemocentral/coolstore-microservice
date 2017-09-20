package com.redhat.coolstore.api_gateway.model;

public class ShoppingCartItem  {

    public double price;
    public int quantity;
    public double promoSavings;
    public Product product;

    public String toString() {
        return ("productid: " + product.itemId + " quan: " + quantity + " price: " + price);
    }
    
    public ShoppingCartItem(){}
  
    public ShoppingCartItem(Product product,int quantity, double promoSavings){
    
    		this.product=product;
    		this.quantity = quantity;
    		this.promoSavings = promoSavings;
    		this.price=product.price*quantity;
    	
    }

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public double getPromoSavings() {
		return promoSavings;
	}

	public void setPromoSavings(double promoSavings) {
		this.promoSavings = promoSavings;
	}

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

    
    
}
