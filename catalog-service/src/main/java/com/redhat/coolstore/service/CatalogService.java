package com.redhat.coolstore.service;

import java.util.List;

import com.redhat.coolstore.model.Product;

public interface CatalogService {
	
	List<Product> getProducts();
	
	void add(Product product);
	
}