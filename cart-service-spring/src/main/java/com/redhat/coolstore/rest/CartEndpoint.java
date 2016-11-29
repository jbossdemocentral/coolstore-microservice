package com.redhat.coolstore.rest;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.ShoppingCartService;


@RestController
@Scope(scopeName = WebApplicationContext.SCOPE_SESSION)
@Path("/cart")
public class CartEndpoint implements Serializable {
	private static final Logger LOG = LoggerFactory.getLogger(CartEndpoint.class);

    /**
     *
     */
    private static final long serialVersionUID = -7227732980791688773L;

    @Autowired
    private ShoppingCartService shoppingCartService;

    @GET
    @Path("/{cartId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ShoppingCart getCart(@PathParam("cartId") String cartId) {

        return shoppingCartService.getShoppingCart(cartId);
    }

    @POST
    @Path("/{cartId}/{itemId}/{quantity}")
    @Produces(MediaType.APPLICATION_JSON)
    public ShoppingCart add(@PathParam("cartId") String cartId,
                            @PathParam("itemId") String itemId,
                            @PathParam("quantity") int quantity) throws Exception {
        ShoppingCart cart = shoppingCartService.getShoppingCart(cartId);

        Product product = shoppingCartService.getProduct(itemId);
        
        if (product == null) {
        	LOG.warn("Invalid product {} request to get added to the shopping cart. No product added", itemId);
        	return cart;
        }

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(product);
        sci.setQuantity(quantity);
        sci.setPrice(product.getPrice());
        cart.addShoppingCartItem(sci);

        try {
            shoppingCartService.priceShoppingCart(cart);
            cart.setShoppingCartItemList(dedupeCartItems(cart.getShoppingCartItemList()));
        } catch (Exception ex) {
            cart.removeShoppingCartItem(sci);
            throw ex;
        }

        return cart;
    }

    @POST
    @Path("/{cartId}/{tmpId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ShoppingCart set(@PathParam("cartId") String cartId,
                            @PathParam("tmpId") String tmpId) throws Exception {

        ShoppingCart cart = shoppingCartService.getShoppingCart(cartId);
        ShoppingCart tmpCart = shoppingCartService.getShoppingCart(tmpId);

        if (tmpCart != null) {
            cart.resetShoppingCartItemList();
            cart.setShoppingCartItemList(tmpCart.getShoppingCartItemList());
        }

        try {
            shoppingCartService.priceShoppingCart(cart);
            cart.setShoppingCartItemList(dedupeCartItems(cart.getShoppingCartItemList()));
        } catch (Exception ex) {
            throw ex;
        }

        return cart;
    }

    @DELETE
    @Path("/{cartId}/{itemId}/{quantity}")
    @Produces(MediaType.APPLICATION_JSON)
    public ShoppingCart delete(@PathParam("cartId") String cartId,
                               @PathParam("itemId") String itemId,
                               @PathParam("quantity") int quantity) throws Exception {

        List<ShoppingCartItem> toRemoveList = new ArrayList<>();

        ShoppingCart cart = shoppingCartService.getShoppingCart(cartId);

        cart.getShoppingCartItemList().stream()
                .filter(sci -> sci.getProduct().getItemId().equals(itemId))
                .forEach(sci -> {
                    if (quantity >= sci.getQuantity()) {
                        toRemoveList.add(sci);
                    } else {
                        sci.setQuantity(sci.getQuantity() - quantity);
                    }
                });

        toRemoveList.forEach(cart::removeShoppingCartItem);

        shoppingCartService.priceShoppingCart(cart);
        return cart;
    }

    @POST
    @Path("/checkout/{cartId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ShoppingCart checkout(@PathParam("cartId") String cartId) {
        // TODO: register purchase of shoppingCart items by specific user in session
        ShoppingCart cart = shoppingCartService.getShoppingCart(cartId);
        cart.resetShoppingCartItemList();
        shoppingCartService.priceShoppingCart(cart);
        return cart;
    }

    private List<ShoppingCartItem> dedupeCartItems(List<ShoppingCartItem> cartItems) {
        List<ShoppingCartItem> result = new ArrayList<>();
        Map<String, Integer> quantityMap = new HashMap<>();
        for (ShoppingCartItem sci : cartItems) {
            if (quantityMap.containsKey(sci.getProduct().getItemId())) {
                quantityMap.put(sci.getProduct().getItemId(), quantityMap.get(sci.getProduct().getItemId()) + sci.getQuantity());
            } else {
                quantityMap.put(sci.getProduct().getItemId(), sci.getQuantity());
            }
        }

        for (String itemId : quantityMap.keySet()) {
            Product p = shoppingCartService.getProduct(itemId);
            ShoppingCartItem newItem = new ShoppingCartItem();
            newItem.setQuantity(quantityMap.get(itemId));
            newItem.setPrice(p.getPrice());
            newItem.setProduct(p);
            result.add(newItem);
        }
        return result;
    }
}
