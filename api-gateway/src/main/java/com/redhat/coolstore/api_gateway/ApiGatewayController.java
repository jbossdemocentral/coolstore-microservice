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

import com.redhat.coolstore.api_gateway.feign.FeignClientFactory;
import com.redhat.coolstore.api_gateway.model.Inventory;
import com.redhat.coolstore.api_gateway.model.Product;
import com.redhat.coolstore.api_gateway.model.ShoppingCart;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api")
public class ApiGatewayController {

    @Autowired
    private FeignClientFactory feignClientFactory;

    private static ExecutorService es = Executors.newCachedThreadPool();


    /**
     * This /api REST endpoint uses Java 8 concurrency to call two backend services to construct the result
     *
     * @return the list
     */
    @CrossOrigin(maxAge = 3600)
    @RequestMapping(method = RequestMethod.GET, value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation("Get a list of products")
    @ResponseBody
    public List<Product> list() throws ExecutionException, InterruptedException {

        final CompletableFuture<List<Product>> productList = CompletableFuture.supplyAsync(() ->
                feignClientFactory.getPricingClient().getService().getProducts(), es);

        return productList.thenCompose((List<Product> products) -> {

            List<CompletableFuture<Product>> all = products.stream()
                    .map(p -> productList.thenCombine(_getInventory(p.itemId),
                            (pl, a) -> {
                                p.availability = a;
                                return p;
                            }))
                    .collect(Collectors.toList());

            return CompletableFuture.allOf(all.toArray(new CompletableFuture[all.size()])).thenApply(v -> all.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList()));
        }).get();

    }

    @CrossOrigin(maxAge = 3600)
    @RequestMapping(method = RequestMethod.POST, value = "/products/checkout/{cartId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation("Cart checkout")
    @ResponseBody
    public ShoppingCart checkout(@PathVariable String cartId) throws ExecutionException, InterruptedException {

        final CompletableFuture<ShoppingCart> cart = CompletableFuture.supplyAsync(() ->
                feignClientFactory.getPricingClient().getService().checkout(cartId), es);

        return cart.get();
    }

    @CrossOrigin(maxAge = 3600)
    @RequestMapping(method = RequestMethod.GET, value = "/products/cart/{cartId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation("Get the user's cart")
    @ResponseBody
    public ShoppingCart getCart(@PathVariable String cartId) throws ExecutionException, InterruptedException {

        final CompletableFuture<ShoppingCart> cart = CompletableFuture.supplyAsync(() ->
                feignClientFactory.getPricingClient().getService().getCart(cartId), es);

        return cart.get();
    }

    @CrossOrigin(maxAge = 3600)
    @RequestMapping(method = RequestMethod.DELETE, value = "/products/cart/{cartId}/{itemId}/{quantity}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation("Delete from the user's cart")
    @ResponseBody
    public ShoppingCart deleteFromCart(@PathVariable String cartId, @PathVariable String itemId, @PathVariable int quantity) throws ExecutionException, InterruptedException {

        final CompletableFuture<ShoppingCart> cart = CompletableFuture.supplyAsync(() ->
                feignClientFactory.getPricingClient().getService().deleteFromCart(cartId, itemId, quantity), es);

        return cart.get();
    }

    @CrossOrigin(maxAge = 3600)
    @RequestMapping(method = RequestMethod.POST, value = "/products/cart/{cartId}/{itemId}/{quantity}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation("Add to user's cart")
    @ResponseBody
    public ShoppingCart addToCart(@PathVariable String cartId, @PathVariable String itemId, @PathVariable int quantity) throws ExecutionException, InterruptedException {

        final CompletableFuture<ShoppingCart> cart = CompletableFuture.supplyAsync(() ->
                feignClientFactory.getPricingClient().getService().addToCart(cartId, itemId, quantity), es);

        return cart.get();
    }

    /**
     * Returns a CompletableFuture that retrieves the availability of an item from the Inventory service.
     *
     * @param itemId The item to query
     * @return A completablefuture for getting the Inventory for this item
     */
    private CompletableFuture<Inventory> _getInventory(String itemId) {
        return CompletableFuture.supplyAsync(() ->
                feignClientFactory.getInventoryClient().getService().getAvailability(itemId), es);
    }
}
