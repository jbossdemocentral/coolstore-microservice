package com.redhat.coolstore.service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.redhat.coolstore.model.Product;
import org.bson.Document;


@ApplicationScoped
public class CatalogService {

    @Inject
    private MongoClient mc;

    @Inject
    Logger log;

    private MongoCollection<Document> productCollection;

	public CatalogService() {
	}

	public List<Product> getProducts() {
        return StreamSupport.stream(productCollection.find().spliterator(), false)
                .map(d -> toProduct(d))
                .collect(Collectors.toList());

    }


    public void add(Product product) {
        productCollection.insertOne(toDocument(product));
    }

    public void addAll(List<Product> products) {
        List<Document> documents = products.stream().map(p -> toDocument(p)).collect(Collectors.toList());
        productCollection.insertMany(documents);
    }

    @PostConstruct
    protected void init() {
        log.info("@PostConstruct is called...");

        String dbName = System.getenv("DB_NAME");
        if(dbName==null || dbName.isEmpty()) {
            log.info("Could not get environment variable DB_NAME using the default value of 'CatalogDB'");
            dbName = "CatalogDB";
        }

        MongoDatabase db = mc.getDatabase(dbName);


        productCollection = db.getCollection("products");

        // Drop the collection if it exists and then add default content
        productCollection.drop();
        addAll(DEFAULT_PRODUCT_LIST);

    }

    @PreDestroy
    protected void destroy() {
        log.info("Closing MongoClient connection");
        if(mc!=null) {
            mc.close();
        }
    }

    /**
     * This method converts Product POJOs to MongoDB Documents, normally we would place this in a DAO
     * @param product
     * @return
     */
    private Document toDocument(Product product) {
        return new Document()
                .append("itemId",product.getItemId())
                .append("name",product.getName())
                .append("desc",product.getDesc())
                .append("price",product.getPrice());
    }

    /**
     * This method converts MongoDB Documents to Product POJOs, normally we would place this in a DAO
     * @param document
     * @return
     */
    private Product toProduct(Document document) {
        Product product =  new Product();
        product.setItemId(document.getString("itemId"));
        product.setName(document.getString("name"));
        product.setDesc(document.getString("desc"));
        product.setPrice(document.getDouble("price"));
        return product;
    }



    private static List<Product> DEFAULT_PRODUCT_LIST = new ArrayList<>();
    static {
        DEFAULT_PRODUCT_LIST.add(new Product("329299", "Red Fedora", "Official Red Hat Fedora", 34.99));
        DEFAULT_PRODUCT_LIST.add(new Product("329199", "Forge Laptop Sticker", "JBoss Community Forge Project Sticker", 8.50));
        DEFAULT_PRODUCT_LIST.add(new Product("165613", "Solid Performance Polo", "Moisture-wicking, antimicrobial 100% polyester design wicks for life of garment. No-curl, rib-knit collar; special collar band maintains crisp fold; three-button placket with dyed-to-match buttons; hemmed sleeves; even bottom with side vents; Import. Embroidery. Red Pepper.", 17.80));
        DEFAULT_PRODUCT_LIST.add(new Product("165614", "Ogio Caliber Polo", "Moisture-wicking 100% polyester. Rib-knit collar and cuffs; Ogio jacquard tape inside neck; bar-tacked three-button placket with Ogio dyed-to-match buttons; side vents; tagless; Ogio badge on left sleeve. Import. Embroidery. Black.", 28.75));
        DEFAULT_PRODUCT_LIST.add(new Product("165954", "16 oz. Vortex Tumbler", "Double-wall insulated, BPA-free, acrylic cup. Push-on lid with thumb-slide closure; for hot and cold beverages. Holds 16 oz. Hand wash only. Imprint. Clear.", 6.00));
        DEFAULT_PRODUCT_LIST.add(new Product("444434", "Pebble Smart Watch", "Smart glasses and smart watches are perhaps two of the most exciting developments in recent years. ", 24.00));
        DEFAULT_PRODUCT_LIST.add(new Product("444435", "Oculus Rift", "The world of gaming has also undergone some very unique and compelling tech advances in recent years. Virtual reality, the concept of complete immersion into a digital universe through a special headset, has been the white whale of gaming and digital technology ever since Geekstakes Oculus Rift GiveawayNintendo marketed its Virtual Boy gaming system in 1995.Lytro", 106.00));
        DEFAULT_PRODUCT_LIST.add(new Product("444436", "Lytro Camera", "Consumers who want to up their photography game are looking at newfangled cameras like the Lytro Field camera, designed to take photos with infinite focus, so you can decide later exactly where you want the focus of each image to be. ", 44.30));

    }

}
