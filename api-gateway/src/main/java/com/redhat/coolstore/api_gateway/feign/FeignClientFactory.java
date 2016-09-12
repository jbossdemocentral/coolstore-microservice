package com.redhat.coolstore.api_gateway.feign;

import com.redhat.coolstore.api_gateway.GenericFeignClient;
import com.redhat.coolstore.api_gateway.model.Inventory;
import com.redhat.coolstore.api_gateway.model.Product;
import com.redhat.coolstore.api_gateway.model.ShoppingCart;
import feign.Param;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Scope(value = "singleton")

public class FeignClientFactory {

    public CatalogFeignClient getPricingClient() {
        return new CatalogFeignClient();
    }
    public InventoryFeignClient getInventoryClient() {
        return new InventoryFeignClient();
    }
    public CartFeignClient getCartClient() {
        return new CartFeignClient();
    }

    public class CatalogFeignClient extends GenericFeignClient<CatalogService> {

        CatalogFeignClient() {

            super(CatalogService.class, "catalog-service", new CatalogService() {

                @Override
                public List<Product> getProducts() {
                    List<Product> temp = new ArrayList<>();
                    Product p = new Product();
                    p.itemId = "0";
                    p.name = "Unavailable Product";
                    p.desc = "An unavailable Product";
                    p.price = 0;
                    temp.add(p);
                    return temp;
                }

            });
        }
    }

    public class CartFeignClient extends GenericFeignClient<CartService> {

        CartFeignClient() {

            super(CartService.class, "cart-service", new CartService() {

                @Override
                public ShoppingCart getCart(String cartId) {
                    return null;
                }

                @Override
                public ShoppingCart addToCart(String cartId, String itemId, int quantity) {
                    return null;
                }

                @Override
                public ShoppingCart deleteFromCart(@Param("cartId") String cartId, @Param("itemId") String itemId, @Param("cartId") int quantity) {
                    return null;
                }
                @Override
                public ShoppingCart checkout(@Param("cartId") String cartId) {
                    return null;
                }


            });
        }
    }


    public class InventoryFeignClient extends GenericFeignClient<InventoryService> {

        InventoryFeignClient() {

            super(InventoryService.class, "inventory-service", itemId -> {
                Inventory p = new Inventory();
                p.itemId = itemId;
                p.location = "Local";
                p.link = "http://redhat.com";
                p.quantity = 0;
                return p;
            });
        }
    }

}
