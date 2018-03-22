package com.redhat.coolstore;

import com.redhat.coolstore.model.kie.Product;
import java.util.Arrays;
import java.util.List;

public class ProductsObjectMother {

    public static List<com.redhat.coolstore.model.kie.Product> createVehicleProducts() {
        return Arrays.asList(
            new com.redhat.coolstore.model.kie.Product("1111", "Car", "Super car", 1000),
            new Product("2222", "Bike", "Super bike", 200));
    }

}
