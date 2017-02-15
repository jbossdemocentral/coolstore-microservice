package com.redhat.coolstore.api_gateway;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.coolstore.api_gateway.model.Product;
import com.redhat.coolstore.api_gateway.model.ShoppingCart;
import com.redhat.coolstore.api_gateway.model.ShoppingCartItem;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class CartGatewayTest {

	@Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CamelContext camelContext;


    @Test
    @DirtiesContext
    public void activeCheckoutTest() throws Exception {
    	
    	String cartResponseStr = genTestData();
    	
        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .fromRoute("checkoutRoute")
            .whenDone(1)
            .create();
    	
    	camelContext.getRouteDefinition("checkoutRoute").adviceWith(camelContext, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("http4*").skipSendToOriginalEndpoint().setBody(constant(cartResponseStr));
				
			}
		});
    	
    	Map<String, String> param = new HashMap<String, String>(); 
        ResponseEntity<String> checkoutResponse = restTemplate.postForEntity("/api/cart/checkout/FOO",param, String.class);
        assertThat(checkoutResponse.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));
        
        //assertThat(notify.matches(30, TimeUnit.SECONDS), Matchers.is(true));
        
        JsonNode node = new ObjectMapper(new JsonFactory()).readTree(checkoutResponse.getBody());
        
        assertThat("ID1", equalTo(node.get("shoppingCartItemList").get(0).get("product").get("itemId").asText()));
        assertThat(40, equalTo(node.get("cartTotal").asInt()));        
    }
    
    @Test
    @DirtiesContext
    public void inactiveCheckoutTest() throws Exception {
   	
    	camelContext.getRouteDefinition("checkoutRoute").adviceWith(camelContext, new AdviceWithRouteBuilder() {
			
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("http4*").skipSendToOriginalEndpoint().throwException(new IllegalArgumentException("Forced"));
			}
		});

    	Map<String, String> param = new HashMap<String, String>(); 
        ResponseEntity<String> checkoutResponse = restTemplate.postForEntity("/api/cart/checkout/FOO",param, String.class);
        
        assertThat(checkoutResponse.getStatusCodeValue(), equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));
    }
    
    @Test
    @DirtiesContext
    public void activeCartTest() throws Exception {
    
    	String cartResponseStr = genTestData();
    	
        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .fromRoute("getCartRoute")
            .whenDone(1)
            .create();
    	
    	camelContext.getRouteDefinition("getCartRoute").adviceWith(camelContext, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("http4*").skipSendToOriginalEndpoint().setBody(constant(cartResponseStr));
				
			}
		});
    	
    	ResponseEntity<String> checkoutResponse = restTemplate.getForEntity("/api/cart/FOO", String.class);
        assertThat(checkoutResponse.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));
        
        //assertThat(notify.matches(30, TimeUnit.SECONDS), Matchers.is(true));
        
        JsonNode node = new ObjectMapper(new JsonFactory()).readTree(checkoutResponse.getBody());
        
        assertThat("ID1", equalTo(node.get("shoppingCartItemList").get(0).get("product").get("itemId").asText()));
        assertThat(40, equalTo(node.get("cartTotal").asInt()));        
    }
    
    @Test
    @DirtiesContext
    public void inactiveCartTest() throws Exception {
   	
    	camelContext.getRouteDefinition("getCartRoute").adviceWith(camelContext, new AdviceWithRouteBuilder() {
			
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("http4*").skipSendToOriginalEndpoint().throwException(new IllegalArgumentException("Forced"));
			}
		});

    	ResponseEntity<String> checkoutResponse = restTemplate.getForEntity("/api/cart/FOO", String.class);
    	
        assertThat(checkoutResponse.getStatusCodeValue(), equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));
    }
    
    
    private String genTestData(){
    	ObjectMapper mapper = new ObjectMapper();
    	ShoppingCart cart = new ShoppingCart();
    	Product product = new Product("ID1", "Test Product", "Test Product", 10, null);
    	ShoppingCartItem item = new ShoppingCartItem(product,2, 3);
    	List<ShoppingCartItem> items = new ArrayList<ShoppingCartItem>();
    	items.add(item);
    	cart.setShoppingCartItemList(items);
    	
    	String cartResponseStr="";
		try {
			cartResponseStr = mapper.writeValueAsString(cart);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return cartResponseStr;
    }
}
