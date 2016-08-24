package com.redhat.coolstore.api_gateway.feign;

import com.redhat.coolstore.api_gateway.GenericFeignClient;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.json.*;

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
                public JsonArray list() {
                    JsonObject temp = Json.createObjectBuilder()
                            .add("itemId", "0")
                            .add("name", "Unavailable Product")
                            .add("desc", "An unavailable Product")
                            .add("price", 0)
                            .build();
                    return Json.createArrayBuilder().add(temp).build();
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
                public JsonArray list() {
                    JsonObject temp = Json.createObjectBuilder()
                            .add("itemId", "0")
                            .add("availability", "Contact local store for availability")
                            .build();

                    return Json.createArrayBuilder().add(temp).build();
                }
            });
        }
    }

}
