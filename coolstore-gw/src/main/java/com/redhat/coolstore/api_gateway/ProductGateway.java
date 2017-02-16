/**
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.coolstore.api_gateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.jackson.ListJacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.redhat.coolstore.api_gateway.model.Inventory;
import com.redhat.coolstore.api_gateway.model.Product;
import com.redhat.coolstore.api_gateway.model.ShoppingCart;

@Component
public class ProductGateway extends RouteBuilder {
	private static final Logger LOG = LoggerFactory.getLogger(ProductGateway.class);
	
	
	@Value("${hystrix.executionTimeout}")
	private int hystrixExecutionTimeout;
	
	@Value("${hystrix.groupKey}")
	private String hystrixGroupKey;
	
	@Value("${hystrix.circuitBreakerEnabled}")
	private boolean hystrixCircuitBreakerEnabled;
	
	@Autowired
	private Environment env;
	
    @Override
    public void configure() throws Exception {
    	try {
    		getContext().setTracing(Boolean.parseBoolean(env.getProperty("ENABLE_TRACER", "false")));	
		} catch (Exception e) {
			LOG.error("Failed to parse the ENABLE_TRACER value: {}", env.getProperty("ENABLE_TRACER", "false"));
		}
    	
        
        JacksonDataFormat productFormatter = new ListJacksonDataFormat();
        productFormatter.setUnmarshalType(Product.class);

       

        rest("/products/").description("Product Catalog Service")
            .produces(MediaType.APPLICATION_JSON_VALUE)

            
            
        // Handle CORS Pre-flight requests
        .options("/")
            .route().id("productsOptions").end()
        .endRest()

        .get("/").description("Get product catalog").outType(Product.class)
            .route().id("productRoute")
                .hystrix().id("Product Service")
                	.hystrixConfiguration()
		    			.executionTimeoutInMilliseconds(hystrixExecutionTimeout)
		    			.groupKey(hystrixGroupKey)
		    			.circuitBreakerEnabled(hystrixCircuitBreakerEnabled)
		    		.end()
                	.setBody(simple("null"))
                	.removeHeaders("CamelHttp*")
                	.recipientList(simple("http4://{{env:CATALOG_ENDPOINT:catalog:8080}}/api/products")).end()
                .onFallback()
                	.setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.SERVICE_UNAVAILABLE.getStatusCode()))
                    .to("direct:productFallback")
                    .stop()
                .end()
                .choice()
                	.when(body().isNull())
                		.to("direct:productFallback")
                	.end()	
                	.unmarshal(productFormatter)
	                .split(body()).parallelProcessing()
	                .enrich("direct:inventory", new InventoryEnricher())
	            .end()
	            
        .endRest();
        
        

        from("direct:productFallback")
                .id("ProductFallbackRoute")
                .transform()
                .constant(Collections.singletonList(new Product("0", "Unavailable Product", "Unavailable Product", 0, null)))
                .marshal().json(JsonLibrary.Jackson, List.class);
        
       

        from("direct:inventory")
            .id("inventoryRoute")
            .setHeader("itemId", simple("${body.itemId}"))            
            .hystrix().id("Inventory Service")
		         .hystrixConfiguration()
					.executionTimeoutInMilliseconds(hystrixExecutionTimeout)
					.groupKey(hystrixGroupKey)
					.circuitBreakerEnabled(hystrixCircuitBreakerEnabled)
				.end()
		    	.setBody(simple("null"))
		    	.removeHeaders("CamelHttp*")
		    	.recipientList(simple("http4://{{env:INVENTORY_ENDPOINT:inventory:8080}}/api/availability/${header.itemId}")).end()
            .onFallback()
            	.setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.SERVICE_UNAVAILABLE.getStatusCode()))
                .to("direct:inventoryFallback")
            .end()
            .choice().when(body().isNull())
            	.to("direct:inventoryFallback")
            .end()
            .setHeader("CamelJacksonUnmarshalType", simple(Inventory.class.getName()))
            .unmarshal().json(JsonLibrary.Jackson, Inventory.class);

        from("direct:inventoryFallback")
                .id("inventoryFallbackRoute")
                .transform()
                .constant(new Inventory("0", 0, "Local Store", "http://developers.redhat.com"))
                .marshal().json(JsonLibrary.Jackson, Inventory.class);
        
        
        
    }

    private class InventoryEnricher implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange original, Exchange resource) {

            // Add the discovered availability to the product and set it back
            Product p = original.getIn().getBody(Product.class);
            Inventory i = resource.getIn().getBody(Inventory.class);
            p.setAvailability(i);
            original.getOut().setBody(p);
            log.info("------------------->"+p);
            
            return original;

        }
    }
}
