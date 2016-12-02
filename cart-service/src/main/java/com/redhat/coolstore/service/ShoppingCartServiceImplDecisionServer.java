package com.redhat.coolstore.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.kie.api.KieServices;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.ExecutionResults;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.ServiceResponse.ResponseType;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.RuleServicesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.coolstore.PromoEvent;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.Promotion;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

import feign.Feign;
import feign.jackson.JacksonDecoder;

@Stateless
public class ShoppingCartServiceImplDecisionServer implements ShoppingCartService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ShoppingCartServiceImplDecisionServer.class);
	
	//private static final String URL = "http://coolstore-rules-coolstore.127.0.0.1.xip.io/kie-server/services/rest/server";
	private static final String URL = "http://coolstore-rules:8080/kie-server/services/rest/server";
	private static final String USER = "brmsAdmin";
	private static final String PASSWORD = "jbossbrms@01";
	private static final String CONTAINER_ID = "CoolStoreRulesContainer";

	private static final MarshallingFormat FORMAT = MarshallingFormat.XSTREAM;

	private KieServicesConfiguration conf;
	private KieServicesClient kieServicesClient;
	private RuleServicesClient rulesClient;

	@Inject
	private PromoService ps;

	private Map<String, ShoppingCart> cartDB = new HashMap<>();
	
	private Map<String, Product> productMap = new HashMap<>();
	
	/**
	 * Initializes the KIE-Server-Client.
	 */
	@PostConstruct
	public void initialize() {
		LOGGER.info("Initializing DecisionServer client.");
		conf = KieServicesFactory.newRestConfiguration(URL, USER, PASSWORD);
		conf.setMarshallingFormat(FORMAT);
		kieServicesClient = KieServicesFactory.newKieServicesClient(conf);
		rulesClient = kieServicesClient.getServicesClient(RuleServicesClient.class);
	}

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
		if (sc != null) {
			
			initShoppingCartForPricing(sc);

			KieCommands commandsFactory = KieServices.Factory.get().getCommands();
			// List of BRMS commands that will be send to the rules-engine (e.g. inserts, fireAllRules, etc).
			List<Command<?>> commands = new ArrayList<>();

			// Insert the promo first
			for (Promotion promo : ps.getPromotions()) {
				PromoEvent promoEvent = new PromoEvent(promo.getItemId(), promo.getPercentOff());
				// Note that we insert the fact into the "Promo Stream".
				Command<?> insertPromoEventCommand = commandsFactory.newInsert(promoEvent, "outPromo", false, "Promo Stream");
				commands.add(insertPromoEventCommand);
			}

			/*
			 * Insert the shoppingcart. Note that the shoppingcart does not contain the actual items. Those are inserted seperately as
			 * facts.
			 */
			/*
			 * Create the ShoppingCart object that we'll send over the wire. This needs mapping from our internal ShoppingCart model to the
			 * one used in our rules.
			 */
			com.redhat.coolstore.ShoppingCart factSc = new com.redhat.coolstore.ShoppingCart();
			factSc.setCartItemPromoSavings(sc.getCartItemPromoSavings());
			factSc.setCartItemTotal(sc.getCartItemTotal());
			factSc.setCartTotal(sc.getCartTotal());
			factSc.setShippingPromoSavings(sc.getShippingPromoSavings());
			factSc.setShippingTotal(sc.getShippingTotal());

			commands.add(commandsFactory.newInsert(factSc, "shoppingcart", true, "DEFAULT"));

			// Insert the ShoppingCartItems.
			List<ShoppingCartItem> scItems = sc.getShoppingCartItemList();
			for (ShoppingCartItem nextSci : scItems) {
				// Map to fact shoppingcartitem.
				com.redhat.coolstore.ShoppingCartItem factSci = new com.redhat.coolstore.ShoppingCartItem();
				factSci.setItemId(nextSci.getProduct().getItemId());
				factSci.setName(nextSci.getProduct().getName());
				factSci.setPrice(nextSci.getProduct().getPrice());
				factSci.setQuantity(nextSci.getQuantity());
				factSci.setShoppingCart(factSc);

				commands.add(commandsFactory.newInsert(factSci));
			}

			// Start the process
			commands.add(commandsFactory.newStartProcess("com.redhat.coolstore.PriceProcess"));

			// Fire the rules
			commands.add(commandsFactory.newFireAllRules());

			Command<?> batchCommand = commandsFactory.newBatchExecution(commands);
			
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Sending request to DecisionServer: " + batchCommand.toString());
			}
			
			ServiceResponse<ExecutionResults> executeResponse = rulesClient.executeCommandsWithResults(CONTAINER_ID, batchCommand);

			if (executeResponse.getType() == ResponseType.SUCCESS) {
				//Map values back to the original shoppingcart object.
				ExecutionResults results = executeResponse.getResult();
				com.redhat.coolstore.ShoppingCart resultSc = (com.redhat.coolstore.ShoppingCart) results.getValue("shoppingcart");
				sc.setCartItemPromoSavings(resultSc.getCartItemPromoSavings());
				sc.setCartItemTotal(resultSc.getCartItemTotal());
				sc.setShippingPromoSavings(resultSc.getShippingPromoSavings());
				sc.setShippingTotal(resultSc.getShippingTotal());
				sc.setCartTotal(resultSc.getCartTotal());
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Rules executed succesfully. Cart pricing: " + sc.toString());
				}
			} else {
				//TODO: Some proper, micro-service type error handling here.
				String message = "Error calculating prices.";
				LOGGER.error(message);
				throw new RuntimeException(message);		
			}
			
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
	
	public void setPs(PromoService ps) {
		this.ps = ps;
	}

}
