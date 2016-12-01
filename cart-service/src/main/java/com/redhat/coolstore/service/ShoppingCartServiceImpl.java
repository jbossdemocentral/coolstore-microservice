package com.redhat.coolstore.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ejb.Stateless;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

import feign.Feign;
import feign.jackson.JacksonDecoder;

@Stateless
@Alternative
public class ShoppingCartServiceImpl implements ShoppingCartService {
	
	@Inject
	ShippingService ss;
	
	@Inject
	PromoService ps;
	
	private Map<String, ShoppingCart> cartDB = new HashMap<>();

	private Map<String, Product> productMap = new HashMap<>();

	@Override
	public ShoppingCart getShoppingCart(String cartId) {
		if (!cartDB.containsKey(cartId)) {
			ShoppingCart c = new ShoppingCart();
			cartDB.put(cartId, c);
			return c;
		} else {
			return cartDB.get(cartId);
		}
	}

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

		for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
			
			Product p = getProduct(sci.getProduct().getItemId());
			
			//if product exist, create new product to reset price
			if ( p != null ) {
			
				sci.setProduct(new Product(p.getItemId(), p.getName(), p.getDesc(), p.getPrice()));
				sci.setPrice(p.getPrice());
			}
			
			sci.setPromoSavings(0);
			
		}
		
		
	}

	@Override
	public Product getProduct(String itemId) {
		if (!productMap.containsKey(itemId)) {

			CatalogService cat = Feign.builder()
					.decoder(new JacksonDecoder())
					.target(CatalogService.class, "http://catalog-service:8080");

			// Fetch and cache products. TODO: Cache should expire at some point!
			List<Product> products = cat.products();
			productMap = products.stream().collect(Collectors.toMap(Product::getItemId, Function.identity()));
		}

		return productMap.get(itemId);
	}
}