package com.redhat.coolstore.rest;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.ProductService;
import com.redhat.coolstore.service.ShoppingCartService;

@SessionScoped
@Path("/products")
public class ProductEndpoint implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7227732980791688773L;

	@Inject
	private ProductService productService;

	@Inject
	private ShoppingCartService shoppingCartService;

	@Inject
	private ShoppingCart shoppingCart;
	
	@GET
	@Path("/list")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Product> listAll() {
		
		return productService.getProducts();
	}

	@GET
	@Path("/cart")
	@Produces(MediaType.APPLICATION_JSON)
	public ShoppingCart getCart() {
		
		return shoppingCart;
	}

	@POST
	@Path("/cart")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public ShoppingCart add(final List<ShoppingCartItem> cartItems) throws Exception {
		Map<String, Product> productMap = productService.getProductMap();
		
		for (ShoppingCartItem sci: cartItems) {
			shoppingCart.addShoppingCartItem(sci);
		}		

		try {
			shoppingCartService.priceShoppingCart(shoppingCart);
			shoppingCart.setShoppingCartItemList(dedupeCartItems(productMap, shoppingCart.getShoppingCartItemList()));
		} catch (Exception ex) {
			for (ShoppingCartItem sci: cartItems) {
				shoppingCart.removeShoppingCartItem(sci);
			}
			throw ex;
		}
		
		System.out.println("cart after adding: pricing items: " + shoppingCart);
		return shoppingCart;
	}

	@POST
	@Path("/cart/delete")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public ShoppingCart delete(final List<ShoppingCartItem> cartItems) {

		List<ShoppingCartItem> toRemoveList = new ArrayList<ShoppingCartItem>();
		
		for (ShoppingCartItem toRemove :cartItems) {
			for (ShoppingCartItem sci: shoppingCart.getShoppingCartItemList()) {
				if (sci.getProduct().getItemId().equals(toRemove.getProduct().getItemId())) {
					if (toRemove.getQuantity() >= sci.getQuantity()) {
						toRemoveList.add(sci);
					} else {
						sci.setQuantity(sci.getQuantity() - toRemove.getQuantity());
					}
				}
			}
		}
		
		for (ShoppingCartItem sci : toRemoveList) {
			shoppingCart.removeShoppingCartItem(sci);
		}
		
		shoppingCartService.priceShoppingCart(shoppingCart);
		System.out.println("cart after removing: pricing items: " + shoppingCart);
		return shoppingCart;
	}

	@POST
	@Path("/checkout")
	@Produces(MediaType.APPLICATION_JSON)
	public ShoppingCart checkout() {
		// TODO: register purchase of shoppingCart items
		shoppingCart.resetShoppingCartItemList();
		shoppingCartService.priceShoppingCart(shoppingCart);
		return shoppingCart;
	}
	
	private List<ShoppingCartItem> dedupeCartItems(Map<String, Product> productMap, List<ShoppingCartItem> cartItems) {
		List<ShoppingCartItem> result = new ArrayList<ShoppingCartItem>();
		Map<String, Integer> quantityMap = new HashMap<String, Integer>();
		for (ShoppingCartItem sci : cartItems) {
			if (quantityMap.containsKey(sci.getProduct().getItemId())) {
				quantityMap.put(sci.getProduct().getItemId(), quantityMap.get(sci.getProduct().getItemId()) + sci.getQuantity());
			} else {
				quantityMap.put(sci.getProduct().getItemId(), sci.getQuantity());
			}
		}
		
		for (String itemId : quantityMap.keySet()) {
			ShoppingCartItem newItem = new ShoppingCartItem();
			newItem.setQuantity(quantityMap.get(itemId));
			newItem.setProduct(productMap.get(itemId));
			result.add(newItem);
		}
		return result;
	}
}
