package com.redhat.coolstore.service;

import com.redhat.coolstore.ProductsObjectMother;
import com.redhat.coolstore.model.kie.Product;
import com.redhat.coolstore.model.kie.ShoppingCart;
import com.redhat.coolstore.model.kie.ShoppingCartItem;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "CATALOG_ENDPOINT=localhost")
public class ShoppingCartServiceTest {

    @MockBean
    CatalogService catalogService;

    @Autowired
    ShoppingCartService shoppingCartService;

    @Test
    public void should_get_initialized_shopping_cart_in_case_of_not_exists() {

        final ShoppingCart shoppingCart = shoppingCartService.getShoppingCart("1111");

        assertThat(shoppingCart)
            .returns(0.0, ShoppingCart::getCartItemPromoSavings)
            .returns(0.0, ShoppingCart::getCartItemTotal)
            .returns(0.0, ShoppingCart::getShippingPromoSavings)
            .returns(0.0, ShoppingCart::getCartTotal);

    }

    @Test
    public void should_calculate_prive_of_cart() {

        final ShoppingCart shoppingCart = shoppingCartService.getShoppingCart("1");
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(new Product("1111", "Car", "Super car", 1000));
        sci.setQuantity(2);
        sci.setPrice(1000);
        shoppingCart.addShoppingCartItem(sci);

        shoppingCartService.priceShoppingCart(shoppingCart);

        assertThat(shoppingCart)
            .returns(0.0, ShoppingCart::getCartItemPromoSavings)
            .returns(2000.0, ShoppingCart::getCartItemTotal)
            .returns(-10.99, ShoppingCart::getShippingPromoSavings)
            .returns(2000.0, ShoppingCart::getCartTotal);

    }

    @Test
    public void should_get_product_id() {
        given(this.catalogService.products()).willReturn(ProductsObjectMother.createVehicleProducts());

        final Product product = shoppingCartService.getProduct("2222");
        assertThat(product)
            .isEqualToIgnoringNullFields(new Product("2222", "Bike", "Super bike", 200));

    }

}
