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
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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

    public static ExecutorService es = Executors.newCachedThreadPool();

    /**
     * Returns a CompletableFuture that retrieves the availability of an item from the Inventory service.
     *
     * @param itemId The item to query
     * @return A completablefuture for getting the Inventory for this item
     */
    private CompletableFuture<Inventory> getInventory(String itemId) {
        return CompletableFuture.supplyAsync(() ->
                feignClientFactory.getInventoryClient().getService().getAvailability(itemId), es);
    }

    /**
     * This /api REST endpoint uses Java 8 parallel stream to create the Feign, invoke it, and collect the result as a List that
     * will be rendered as a JSON Array.
     *
     * @return the list
     */
    @CrossOrigin(maxAge = 3600)
    @RequestMapping(method = RequestMethod.GET, value = "/products/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation("Get a list of products")
    public List<Product> list() throws ExecutionException, InterruptedException {

        final CompletableFuture<List<Product>> productList = CompletableFuture.supplyAsync(() ->
                feignClientFactory.getPricingClient().getService().list(), es);

        return productList.thenCompose((List<Product> products) -> {

            List<CompletableFuture<Product>> all = products.stream()
                    .map(p -> productList.thenCombine(getInventory(p.itemId),
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

    @RequestMapping(method = RequestMethod.GET, value = "/health")
    @ApiOperation("Used to verify the health of the service")
    public String health() {
        return "I'm ok";
    }
}
