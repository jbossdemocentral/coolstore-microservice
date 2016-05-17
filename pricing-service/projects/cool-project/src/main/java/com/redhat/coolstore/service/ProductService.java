package com.redhat.coolstore.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Singleton;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import com.redhat.coolstore.model.Product;

@ApplicationScoped
@Path("/products")
public class ProductService {

	private List<Product> products;
	
	public ProductService() {
		
		products = new ArrayList<Product>();
		
		products.add(new Product("329299", "Red Fedora", "Official Red Hat Fedora", 34.99));
		products.add(new Product("329199", "Forge Laptop Sticker", "JBoss Community Forge Project Sticker", 8.50));
		products.add(new Product("165613", "Solid Performance Polo", "Moisture-wicking, antimicrobial 100% polyester design wicks for life of garment. No-curl, rib-knit collar; special collar band maintains crisp fold; three-button placket with dyed-to-match buttons; hemmed sleeves; even bottom with side vents; Import. Embroidery. Red Pepper.", 17.80));
		products.add(new Product("165614", "Ogio Caliber Polo", "Moisture-wicking 100% polyester. Rib-knit collar and cuffs; Ogio jacquard tape inside neck; bar-tacked three-button placket with Ogio dyed-to-match buttons; side vents; tagless; Ogio badge on left sleeve. Import. Embroidery. Black.", 28.75));
		products.add(new Product("165954", "16 oz. Vortex Tumbler", "Double-wall insulated, BPA-free, acrylic cup. Push-on lid with thumb-slide closure; for hot and cold beverages. Holds 16 oz. Hand wash only. Imprint. Clear.", 6.00));
		products.add(new Product("444434", "Pebble Smart Watch", "Smart glasses and smart watches are perhaps two of the most exciting developments in recent years. ", 24.00));
		products.add(new Product("444435", "Oculus Rift", "The world of gaming has also undergone some very unique and compelling tech advances in recent years. Virtual reality, the concept of complete immersion into a digital universe through a special headset, has been the white whale of gaming and digital technology ever since Geekstakes Oculus Rift GiveawayNintendo marketed its Virtual Boy gaming system in 1995.Lytro", 106.00));
		products.add(new Product("444436", "Lytro Camera", "Consumers who want to up their photography game are looking at newfangled cameras like the Lytro Field camera, designed to take photos with infinite focus, so you can decide later exactly where you want the focus of each image to be. ", 44.30));
		
	}
	
	public List<Product> getProducts() {
		
		return new ArrayList<Product>(products);
		
	}
	
	public void setProducts(List<Product> products) {
		
		if ( products != null ) {
		
			this.products = new ArrayList<Product>(products);
			
		} else {
			
			this.products = new ArrayList<Product>();
		}		
		
	}
	
	public Map<String, Product> getProductMap() {
		
		Map<String, Product> productMap = new HashMap<String, Product>();
		
		for (Product p : getProducts()) {
			
			productMap.put(p.getItemId(), p);
			
		}
		
		return productMap;
		
	}
	
	
}
