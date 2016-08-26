package com.redhat.coolstore.api_gateway.model;

import java.util.ArrayList;
import java.util.List;

public class ShoppingCart  {


    public double cartItemTotal;

    public double cartItemPromoSavings;

    public double shippingTotal;

    public double shippingPromoSavings;

    public double cartTotal;

    public List<ShoppingCartItem> shoppingCartItemList = new ArrayList<ShoppingCartItem>();

}
