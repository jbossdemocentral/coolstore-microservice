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

import com.redhat.coolstore.api_gateway.model.Inventory;
import com.redhat.coolstore.api_gateway.model.Product;
import com.redhat.coolstore.api_gateway.model.ShoppingCart;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.jackson.ListJacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ApiGatewayRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

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

        rest("/products/").description("Product Catalog Service")
            .produces(MediaType.APPLICATION_JSON_VALUE)

        // Handle CORS Pre-flight requests
        .options("/")
            .route().id("productsOptions").end()
        .endRest()

        .get("/").description("Get product catalog").outType(Product.class)
            .route().id("productRoute")
                .setBody(simple("null"))
                .removeHeaders("CamelHttp*")
                .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                .setHeader(Exchange.HTTP_URI, simple("http://catalog:8080/api/products"))
                .hystrix().id("Product Service")
                    .to("http4://DUMMY")
                .onFallback()
                    .to("direct:productFallback")
                .end()
                .choice().when(body().isNull()).to("direct:productFallback").end()
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
            .setBody(simple("null"))
            .removeHeaders("CamelHttp*")
            .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
            .setHeader(Exchange.HTTP_URI, simple("http://inventory:8080/api/availability/${header.itemId}"))
            .hystrix().id("Inventory Service")
                .to("http4://DUMMY2")
            .onFallback()
                .to("direct:inventoryFallback")
            .end()
            .choice().when(body().isNull()).to("direct:inventoryFallback").end()
            .setHeader("CamelJacksonUnmarshalType", simple(Inventory.class.getName()))
            .unmarshal().json(JsonLibrary.Jackson, Inventory.class);

        from("direct:inventoryFallback")
                .id("inventoryFallbackRoute")
                .transform()
                .constant(new Inventory("0", 0, "Local Store", "http://redhat.com"))
                .marshal().json(JsonLibrary.Jackson, Inventory.class);

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
                    .setHeader(Exchange.HTTP_URI, simple("http://cart:8080/api/cart/checkout/${header.cartId}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
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
                    .setHeader(Exchange.HTTP_URI, simple("http://cart:8080/api/cart/${header.cartId}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
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
                    .setHeader(Exchange.HTTP_URI, simple("http://cart:8080/api/cart/${header.cartId}/${header.itemId}/${header.quantity}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
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
                    .setHeader(Exchange.HTTP_URI, simple("http://cart:8080/api/cart/${header.cartId}/${header.itemId}/${header.quantity}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
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
                .setHeader(Exchange.HTTP_URI, simple("http://cart:8080/api/cart/${header.cartId}/${header.tmpId}"))
                .to("http4://DUMMY")
                .onFallback()
                // TODO: improve fallback
                .transform().constant(null)
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest();

    }

    private class InventoryEnricher implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange original, Exchange resource) {

            // Add the discovered availability to the product and set it back
            Product p = original.getIn().getBody(Product.class);
            Inventory i = resource.getIn().getBody(Inventory.class);
            p.setAvailability(i);
            original.getOut().setBody(p);
            return original;

        }
    }
}
