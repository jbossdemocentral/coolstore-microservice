package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import feign.RequestLine;

import java.util.List;

interface CatalogService {
    @RequestLine("GET /products")
    List<Product> products();
}

