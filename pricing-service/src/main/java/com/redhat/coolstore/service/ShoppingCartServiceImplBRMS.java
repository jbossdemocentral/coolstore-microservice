package com.redhat.coolstore.service;

import java.io.Serializable;

import javax.ejb.Stateful;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;

import com.redhat.coolstore.PromoEvent;
import com.redhat.coolstore.model.Promotion;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.util.BRMSUtil;

@ApplicationScoped
public class ShoppingCartServiceImplBRMS implements ShoppingCartService, Serializable {

	private static final long serialVersionUID = 6821952169434330759L;

	@Inject
	private BRMSUtil brmsUtil; 
	
	@Inject
	private PromoService promoService; 
	
	public ShoppingCartServiceImplBRMS() {
		
	}

	@Override
	public void priceShoppingCart(ShoppingCart sc) {

		if ( sc != null ) {
						
			com.redhat.coolstore.ShoppingCart factShoppingCart = new com.redhat.coolstore.ShoppingCart();
			
			factShoppingCart.setCartItemPromoSavings(0d);
			factShoppingCart.setCartItemTotal(0d);
			factShoppingCart.setCartTotal(0d);
			factShoppingCart.setShippingPromoSavings(0d);
			factShoppingCart.setShippingTotal(0d);
			
			KieSession ksession = null;
			
			try {
			
				//if at least one shopping cart item exist
				if ( sc.getShoppingCartItemList().size() > 0 ) {
				
					ksession = brmsUtil.getStatefulSession();
					
					EntryPoint promoStream = ksession.getEntryPoint("Promo Stream");
					
					for (Promotion promo : promoService.getPromotions()) {
																	
						PromoEvent pv = new PromoEvent(promo.getItemId(), promo.getPercentOff());
						
						promoStream.insert(pv);
						
					}
																	
					ksession.insert(factShoppingCart);
					
					for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
						
						com.redhat.coolstore.ShoppingCartItem factShoppingCartItem = new com.redhat.coolstore.ShoppingCartItem();
						factShoppingCartItem.setItemId(sci.getProduct().getItemId());
						factShoppingCartItem.setName(sci.getProduct().getName());
						factShoppingCartItem.setPrice(sci.getProduct().getPrice());
						factShoppingCartItem.setQuantity(sci.getQuantity());
						factShoppingCartItem.setShoppingCart(factShoppingCart);
						factShoppingCartItem.setPromoSavings(0d);
						
						ksession.insert(factShoppingCartItem);
						
					}
					
					ksession.startProcess("com.redhat.coolstore.PriceProcess");
					
					ksession.fireAllRules();
				
				}
				
				sc.setCartItemTotal(factShoppingCart.getCartItemTotal());
				sc.setCartItemPromoSavings(factShoppingCart.getCartItemPromoSavings());
				sc.setShippingTotal(factShoppingCart.getShippingTotal());
				sc.setShippingPromoSavings(factShoppingCart.getShippingPromoSavings());
				sc.setCartTotal(factShoppingCart.getCartTotal());
											
			} finally {
				
				if ( ksession != null ) {
					
					ksession.dispose();
					
				}
			}
		}
		
	}
	
}
