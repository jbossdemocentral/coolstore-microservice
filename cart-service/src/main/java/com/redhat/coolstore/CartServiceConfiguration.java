package com.redhat.coolstore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.redhat.coolstore.service.ShoppingCartService;
import com.redhat.coolstore.service.ShoppingCartServiceImpl;
import com.redhat.coolstore.service.ShoppingCartServiceImplDecisionServer;

@Configuration
public class CartServiceConfiguration {
    
    @Value("${pricing.service.impl}")
    private String pricingService;
    
    @Bean
    public ShoppingCartService getShoppingCartService() {
        if ("drools".equals(pricingService)) {
          return new ShoppingCartServiceImplDecisionServer();  
        } else {
          return new ShoppingCartServiceImpl();
        }
    }

}
