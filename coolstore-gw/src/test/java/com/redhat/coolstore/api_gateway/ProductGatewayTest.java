package com.redhat.coolstore.api_gateway;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.coolstore.api_gateway.model.Inventory;
import com.redhat.coolstore.api_gateway.model.Product;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ProductGatewayTest {

	@Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CamelContext camelContext;

   // @Test
    public void testSwaggerEndpoint() {
        ResponseEntity<String> orderResponse = restTemplate.getForEntity("/api/api-docs", String.class);
        assertThat(orderResponse.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));
    }

    //Product route will also trigger the inventory service, so will not do individual inventory tests
    @Test
    @DirtiesContext
    public void activeProductTest() throws Exception {
    	
    	ObjectMapper mapper = new ObjectMapper();
    	Inventory inventory = new Inventory("ID1", 5, "Westford", "http://developers.redhat.com");
    	
    	List<Product> products = new ArrayList<Product>();
    	products.add(new Product("ID1", "Test Product", "Test Product", 10, null));
    	
    	String productResponseStr = mapper.writeValueAsString(products);
    	String inventoryResponseStr = mapper.writeValueAsString(inventory);
  
        NotifyBuilder notify = new NotifyBuilder(camelContext)
            .fromRoute("productRoute")
            .whenDone(1)
            .create();
    	
    	camelContext.getRouteDefinition("productRoute").adviceWith(camelContext, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("http4://{{env:CATALOG_ENDPOINT:catalog:8080}}/api/products").skipSendToOriginalEndpoint().setBody(constant(productResponseStr));
				interceptSendToEndpoint("http4://{{env:INVENTORY_ENDPOINT:inventory:8080}}/api/availability/*").skipSendToOriginalEndpoint().setBody(constant(inventoryResponseStr));
			}
		});

        ResponseEntity<String> orderResponse = restTemplate.getForEntity("/api/products", String.class);
        assertThat(orderResponse.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));
        
        assertThat(notify.matches(10, TimeUnit.SECONDS), Matchers.is(true));
        
        JsonNode node = new ObjectMapper(new JsonFactory()).readTree(orderResponse.getBody());
        assertThat("ID1", equalTo(node.get(0).get("itemId").asText()));
        assertThat(5, equalTo(node.get(0).get("availability").get("quantity").asInt()));
        
    }
    
    @Test
    @DirtiesContext
    public void inactiveProductTest() throws Exception {
   	
    	camelContext.getRouteDefinition("productRoute").adviceWith(camelContext, new AdviceWithRouteBuilder() {
			
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("http4*").skipSendToOriginalEndpoint().throwException(new IllegalArgumentException("Forced"));
			}
		});

        ResponseEntity<String> orderResponse = restTemplate.getForEntity("/api/products", String.class);
        assertThat(orderResponse.getStatusCodeValue(), equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));
        
       
    }
}
