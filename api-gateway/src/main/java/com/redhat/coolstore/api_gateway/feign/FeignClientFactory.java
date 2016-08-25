package com.redhat.coolstore.api_gateway.feign;

import com.redhat.coolstore.api_gateway.GenericFeignClient;
import com.redhat.coolstore.api_gateway.model.Inventory;
import com.redhat.coolstore.api_gateway.model.Product;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.json.*;
import java.util.ArrayList;
import java.util.List;

@Component
@Scope(value = "singleton")

public class FeignClientFactory {

    public PricingFeignClient getPricingClient() {
        return new PricingFeignClient();
    }
    public InventoryFeignClient getInventoryClient() {
        return new InventoryFeignClient();
    }

    public class PricingFeignClient extends GenericFeignClient<PricingService> {

        PricingFeignClient() {

            super(PricingService.class, "pricing-service",  new PricingService() {

                @Override
                public List<Product> list() {
                    List<Product> temp = new ArrayList<>();
                    Product p = new Product();
                    p.itemId = "0";
                    p.name = "Unavailable Product";
                    p.desc = "An unavailable Product";
                    p.price = 0;
                    temp.add(p);
                    return temp;
                }

                @Override
                public JsonArray cart() {
                    return Json.createArrayBuilder().add("foo").build();
                }
            });
        }
    }

    public class InventoryFeignClient extends GenericFeignClient<InventoryService> {

        InventoryFeignClient() {

            super(InventoryService.class, "inventory-service",  new InventoryService() {

                @Override
                public List<Inventory> list() {
                    List<Inventory> temp = new ArrayList<>();
                    Inventory p = new Inventory();
                    p.itemId = "0";
                    p.availability = "You Wish!";
                    temp.add(p);
                    return temp;
                }
            });
        }
    }

}
