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

import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.jackson.ListJacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.redhat.coolstore.api_gateway.model.Product;
import com.redhat.coolstore.api_gateway.model.ShoppingCart;

@Component
public class CartGateway extends RouteBuilder {
	private static final Logger LOG = LoggerFactory.getLogger(CartGateway.class);
	
	
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

        restConfiguration().component("servlet")
            .bindingMode(RestBindingMode.auto).apiContextPath("/api-docs").contextPath("/api").apiProperty("host", "")
            .apiProperty("api.title", "CoolStore Microservice API Gateway")
            .apiProperty("api.version", "1.0.0")
            .apiProperty("api.description", "The API of the gateway which fronts the various backend microservices in the CoolStore")
            .apiProperty("api.contact.name", "Red Hat Developers")
            .apiProperty("api.contact.email", "developers@redhat.com")
            .apiProperty("api.contact.url", "https://developers.redhat.com");


        rest("/cart/").description("Personal Shopping Cart Service")
            .produces(MediaType.APPLICATION_JSON_VALUE)

            // Handle CORS Preflight requests
            .options("/{cartId}")
            .route().id("getCartOptionsRoute").end().endRest()
            .options("/checkout/{cartId}")
            .route().id("checkoutCartOptionsRoute").end().endRest()
            .options("/{cartId}/{tmpId}")
            .route().id("cartSetOptionsRoute").end().endRest()
            .options("/{cartId}/{itemId}/{quantity}")
            .route().id("cartAddDeleteOptionsRoute").end().endRest()

            .post("/checkout/{cartId}").description("Finalize shopping cart and process payment")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .outType(ShoppingCart.class)
                .route().id("checkoutRoute")
                .hystrix().id("Cart Service (Checkout Cart)")
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.POST)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/api/cart/checkout/${header.cartId}"))
                    .to("http4://DUMMY")
                .onFallback()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.SERVICE_UNAVAILABLE.getStatusCode()))
        			.setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
        			.to("direct:defaultFallback")
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest()

            .get("/{cartId}").description("Get the current user's shopping cart content")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .outType(ShoppingCart.class)
                .route().id("getCartRoute")
                .hystrix().id("Cart Service (Get Cart)")
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/api/cart/${header.cartId}"))
                    .to("http4://DUMMY")
                .onFallback()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.SERVICE_UNAVAILABLE.getStatusCode()))
        			.setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
        			.to("direct:defaultFallback")
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest()

            .delete("/{cartId}/{itemId}/{quantity}").description("Delete items from current user's shopping cart")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .param().name("itemId").type(RestParamType.path).description("The ID of the item to delete").dataType("string").endParam()
                .param().name("quantity").type(RestParamType.path).description("The number of items to delete").dataType("integer").endParam()
                .outType(ShoppingCart.class)
                .route().id("deleteFromCartRoute")
                .hystrix().id("Cart Service (Delete Cart)")
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.DELETE)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/api/cart/${header.cartId}/${header.itemId}/${header.quantity}"))
                    .to("http4://DUMMY")
                .onFallback()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.SERVICE_UNAVAILABLE.getStatusCode()))
        			.setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
        			.to("direct:defaultFallback")
                    .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest()

            .post("/{cartId}/{itemId}/{quantity}").description("Add items from current user's shopping cart")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .param().name("itemId").type(RestParamType.path).description("The ID of the item to add").dataType("string").endParam()
                .param().name("quantity").type(RestParamType.path).description("The number of items to add").dataType("integer").endParam()
                .outType(ShoppingCart.class)
                .route().id("addToCartRoute")
                .hystrix().id("Cart Service (Add To Cart)")
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.POST)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/api/cart/${header.cartId}/${header.itemId}/${header.quantity}"))
                    .to("http4://DUMMY")
                .onFallback()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.SERVICE_UNAVAILABLE.getStatusCode()))
        			.setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
        			.to("direct:defaultFallback")
        		.end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest()

            .post("/{cartId}/{tmpId}").description("Transfer temp shopping items to user's cart")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .param().name("tmpId").type(RestParamType.path).description("The ID of the temp cart to transfer").dataType("string").endParam()
                .outType(ShoppingCart.class)
                .route().id("setCartRoute")
                .hystrix().id("Cart Service (Set Cart)")
                .removeHeaders("CamelHttp*")
                .setBody(simple("null"))
                .setHeader(Exchange.HTTP_METHOD, HttpMethods.POST)
                .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/api/cart/${header.cartId}/${header.tmpId}"))
                .to("http4://DUMMY")
                .onFallback()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.SERVICE_UNAVAILABLE.getStatusCode()))
        			.setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
        			.to("direct:defaultFallback")
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest();
        
        
        // Provide a response
        from("direct:defaultFallback").routeId("defaultfallback").description("Default Fall back response response")
        	.process(new Processor() {
			@Override
			public void process(Exchange exchange) throws Exception {				
				exchange.getIn().setBody(new ShoppingCart());
			}
        })
        .marshal().json(JsonLibrary.Jackson);
           

    }

    
}
