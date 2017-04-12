package com.redhat.coolstore.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.kie.api.KieServices;
import org.kie.api.command.BatchExecutionCommand;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.redhat.coolstore.model.kie.PromoEvent;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.Promotion;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

import feign.Feign;
import feign.jackson.JacksonDecoder;

@Component
public class ShoppingCartServiceImplDecisionServer implements ShoppingCartService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ShoppingCartServiceImplDecisionServer.class);

	private static final String CATALOG_ENDPOINT = System.getenv("CATALOG_ENDPOINT");
	private static final String PRICING_ENDPOINT = System.getenv("PRICING_ENDPOINT");
	private static final String URL = PRICING_ENDPOINT + "/kie-server/services/rest/server";
	private static final String USER = System.getenv("KIE_SERVER_USER");
	private static final String PASSWORD = System.getenv("KIE_SERVER_PASSWORD");
	private static final String CONTAINER_SPEC = System.getenv("KIE_CONTAINER_DEPLOYMENT");
	private static final String CONTAINER_ID = CONTAINER_SPEC.substring(0, CONTAINER_SPEC.indexOf('='));
	private static final String KIE_SESSION_NAME = "coolstore-kie-session";
	private static final String RULEFLOW_PROCESS_NAME = "com.redhat.coolstore.PriceProcess";

	private static final MarshallingFormat FORMAT = MarshallingFormat.XSTREAM;

	private KieServicesConfiguration conf;
	private KieServicesClient kieServicesClient;
	private RuleServicesClient rulesClient;

	@Autowired
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

			BatchExecutionCommand batchCommand = buildBatchExecutionCommand(sc);

			ServiceResponse<ExecutionResults> executeResponse = rulesClient.executeCommandsWithResults(CONTAINER_ID, batchCommand);

			if (executeResponse.getType() == ResponseType.SUCCESS) {
				ExecutionResults results = executeResponse.getResult();
				com.redhat.coolstore.model.kie.ShoppingCart resultSc = (com.redhat.coolstore.model.kie.ShoppingCart) results.getValue("shoppingcart");
				mapShoppingCartPricingResults(resultSc, sc);
			} else {
				// TODO: Some proper, micro-service type error handling here.
				String message = "Error calculating prices.";
				LOGGER.error(message);
				throw new RuntimeException(message);
			}
		}
	}

	@Override
	public Product getProduct(String itemId) {
		if (!productMap.containsKey(itemId)) {

			CatalogService cat = Feign.builder().decoder(new JacksonDecoder()).target(CatalogService.class, CATALOG_ENDPOINT);

			// Fetch and cache products. TODO: Cache should expire at some point!
			List<Product> products = cat.products();
			productMap = products.stream().collect(Collectors.toMap(Product::getItemId, Function.identity()));
		}

		return productMap.get(itemId);
	}

	/**
	 * Builds the KIE {@link BatchExecutionCommand}, which contains all the KIE logic like insertion of facts, starting of ruleflow
	 * processes and firing of rules, from the given {@link ShoppingCart}.
	 *
	 * @param sc
	 *            the {@link ShoppingCart} from which the build the {@link BatchExecutionCommand}.
	 * @return the {@link BatchExecutionCommand}
	 */
	private BatchExecutionCommand buildBatchExecutionCommand(ShoppingCart sc) {
		KieCommands commandsFactory = KieServices.Factory.get().getCommands();
		// List of BRMS commands that will be send to the rules-engine (e.g. inserts, fireAllRules, etc).
		List<Command<?>> commands = new ArrayList<>();

		// Insert the promo first. Promotions are retrieved from the PromoService.
		for (Promotion promo : ps.getPromotions()) {
			PromoEvent promoEvent = new PromoEvent(promo.getItemId(), promo.getPercentOff());
			// Note that we insert the fact into the "Promo Stream".
			Command<?> insertPromoEventCommand = commandsFactory.newInsert(promoEvent, "outPromo", false, "Promo Stream");
			commands.add(insertPromoEventCommand);
		}

		/*
		 * Build the ShoppingCart fact from the given ShoppingCart.
		 */
		com.redhat.coolstore.model.kie.ShoppingCart factSc = buildShoppingCartFact(sc);

		commands.add(commandsFactory.newInsert(factSc, "shoppingcart", true, "DEFAULT"));

		// Insert the ShoppingCartItems.
		List<ShoppingCartItem> scItems = sc.getShoppingCartItemList();
		for (ShoppingCartItem nextSci : scItems) {
			// Build the ShoppingCartItem fact from the given ShoppingCartItem.
			com.redhat.coolstore.model.kie.ShoppingCartItem factSci = buildShoppingCartItem(nextSci);
			factSci.setShoppingCart(factSc);
			commands.add(commandsFactory.newInsert(factSci));
		}

		// Start the process (ruleflow).
		commands.add(commandsFactory.newStartProcess(RULEFLOW_PROCESS_NAME));

		// Fire the rules
		commands.add(commandsFactory.newFireAllRules());

		BatchExecutionCommand batchCommand = commandsFactory.newBatchExecution(commands, KIE_SESSION_NAME);
		return batchCommand;
	}

	private void initShoppingCartForPricing(ShoppingCart sc) {

		sc.setCartItemTotal(0);
		sc.setCartItemPromoSavings(0);
		sc.setShippingTotal(0);
		sc.setShippingPromoSavings(0);
		sc.setCartTotal(0);

		for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {

			Product p = getProduct(sci.getProduct().getItemId());

			// if product exist, create new product to reset price
			if (p != null) {
				sci.setProduct(new Product(p.getItemId(), p.getName(), p.getDesc(), p.getPrice()));
				sci.setPrice(p.getPrice());
			}

			sci.setPromoSavings(0);
		}
	}

	/**
	 * Builds a {@link com.redhat.coolstore.ShoppingCart} fact from the given {@link ShoppingCart}.
	 * 
	 * @param sc
	 *            the {@link ShoppingCart} from which to build the fact.
	 * @return the {@link com.redhat.coolstore.ShoppingCart} fact
	 */
	private com.redhat.coolstore.model.kie.ShoppingCart buildShoppingCartFact(ShoppingCart sc) {
		com.redhat.coolstore.model.kie.ShoppingCart factSc = new com.redhat.coolstore.model.kie.ShoppingCart();
		factSc.setCartItemPromoSavings(sc.getCartItemPromoSavings());
		factSc.setCartItemTotal(sc.getCartItemTotal());
		factSc.setCartTotal(sc.getCartTotal());
		factSc.setShippingPromoSavings(sc.getShippingPromoSavings());
		factSc.setShippingTotal(sc.getShippingTotal());
		return factSc;
	}

	/**
	 * Builds a {@link com.redhat.coolstore.ShoppingCartItem} fact from the given {@link ShoppingCartItem}.
	 * 
	 * @param sci
	 *            the {@link ShoppingCartItem} from which to build the fact.
	 * @return the {@link com.redhat.coolstore.ShoppingCartItem} fact.
	 */
	private com.redhat.coolstore.model.kie.ShoppingCartItem buildShoppingCartItem(ShoppingCartItem sci) {
		com.redhat.coolstore.model.kie.ShoppingCartItem factSci = new com.redhat.coolstore.model.kie.ShoppingCartItem();
		factSci.setItemId(sci.getProduct().getItemId());
		factSci.setName(sci.getProduct().getName());
		factSci.setPrice(sci.getProduct().getPrice());
		factSci.setQuantity(sci.getQuantity());
		return factSci;
	}

	/**
	 * Maps the {@link com.redhat.coolstore.ShoppingCart} pricing results to the given {@link ShoppingCart}.
	 * 
	 * @param resultSc
	 *            the {@link com.redhat.coolstore.ShoppingCart} containing the pricing defined by the rules engine.
	 * @param sc
	 *            the {@link ShoppingCart} onto which we need to map the results.
	 */
	private void mapShoppingCartPricingResults(com.redhat.coolstore.model.kie.ShoppingCart resultSc, ShoppingCart sc) {
		sc.setCartItemPromoSavings(resultSc.getCartItemPromoSavings());
		sc.setCartItemTotal(resultSc.getCartItemTotal());
		sc.setShippingPromoSavings(resultSc.getShippingPromoSavings());
		sc.setShippingTotal(resultSc.getShippingTotal());
		sc.setCartTotal(resultSc.getCartTotal());
	}

}
