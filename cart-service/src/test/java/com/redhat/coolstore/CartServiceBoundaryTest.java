package com.redhat.coolstore;

import com.redhat.coolstore.model.kie.ShoppingCart;
import io.specto.hoverfly.junit.rule.HoverflyRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.HttpBodyConverter.json;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "CATALOG_ENDPOINT=catalog")
public class CartServiceBoundaryTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @ClassRule
    public static HoverflyRule hoverflyRule = HoverflyRule.inSimulationMode(dsl(
        service("catalog")
            .get("/api/products")
            .willReturn(success(json(ProductsObjectMother.createVehicleProducts())))
    ));

    @Test
    public void should_add_item_to_shopping_cart() {

        final ShoppingCart shoppingCart = this.restTemplate.postForObject("/api/cart/1/1111/2", "", ShoppingCart.class);

        assertThat(shoppingCart)
            .returns(0.0, ShoppingCart::getCartItemPromoSavings)
            .returns(2000.0, ShoppingCart::getCartItemTotal)
            .returns(-10.99, ShoppingCart::getShippingPromoSavings)
            .returns(2000.0, ShoppingCart::getCartTotal)
            .extracting(ShoppingCart::getShoppingCartItemList)
            .hasSize(1);
    }
}
