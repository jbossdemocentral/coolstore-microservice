package com.redhat.coolstore.service;

import java.util.Map;

import javax.ejb.Stateless;
import javax.inject.Inject;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

@Stateless
public class ShoppingCartServiceImpl implements ShoppingCartService {
	
	@Inject
	ShippingService ss;
	
	@Inject
	PromoService ps;
	
	@Inject
	ProductService pp;

	@Override
	public void priceShoppingCart(ShoppingCart sc) {

		if ( sc != null ) {
			
			initShoppingCartForPricing(sc);
			
			if ( sc.getShoppingCartItemList() != null && sc.getShoppingCartItemList().size() > 0) {
			
				ps.applyCartItemPromotions(sc);
				
				for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
					
					sc.setCartItemPromoSavings(sc.getCartItemPromoSavings() + sci.getPromoSavings() * sci.getQuantity());
					sc.setCartItemTotal(sc.getCartItemTotal() + sci.getPrice() * sci.getQuantity());
					
				}
				
				ss.calculateShipping(sc);				
				
			}
			
			ps.applyShippingPromotions(sc);
			
			sc.setCartTotal(sc.getCartItemTotal() + sc.getShippingTotal());
		
		}
		
	}

	private void initShoppingCartForPricing(ShoppingCart sc) {

		sc.setCartItemTotal(0);
		sc.setCartItemPromoSavings(0);
		sc.setShippingTotal(0);
		sc.setShippingPromoSavings(0);
		sc.setCartTotal(0);
		
		Map<String, Product> productMap = pp.getProductMap();
		
		for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
			
			Product p = productMap.get(sci.getProduct().getItemId());
			
			//if product exist, create new product to reset price
			if ( p != null ) {
			
				sci.setProduct(new Product(p.getItemId(), p.getName(), p.getDesc(), p.getPrice()));
				sci.setPrice(p.getPrice());
			}
			
			sci.setPromoSavings(0);
			
		}
		
		
	}
	
}