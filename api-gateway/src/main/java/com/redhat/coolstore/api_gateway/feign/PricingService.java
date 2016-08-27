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
package com.redhat.coolstore.api_gateway.feign;

import com.redhat.coolstore.api_gateway.model.Product;
import com.redhat.coolstore.api_gateway.model.ShoppingCart;
import com.redhat.coolstore.api_gateway.model.ShoppingCartItem;
import feign.Param;
import feign.RequestLine;

import javax.json.JsonArray;
import java.util.List;

public interface PricingService {

    @RequestLine("GET /api/products")
    List<Product> getProducts();

    @RequestLine("GET /api/products/cart/{cartId}")
    ShoppingCart getCart(@Param("cartId") String cartId);

    @RequestLine("POST /api/products/cart/{cartId}/{itemId}/{quantity}")
    ShoppingCart addToCart(@Param("cartId") String cartId, @Param("itemId") String itemId, @Param("quantity") int quantity);

    @RequestLine("DELETE /api/products/cart/{cartId}/{itemId}/{quantity}")
    ShoppingCart deleteFromCart(@Param("cartId") String cartId, @Param("itemId") String itemId, @Param("quantity") int quantity);

    @RequestLine("POST /api/products/cart/checkout/{cartId}")
    ShoppingCart checkout(@Param("cartId") String cartId);

}
