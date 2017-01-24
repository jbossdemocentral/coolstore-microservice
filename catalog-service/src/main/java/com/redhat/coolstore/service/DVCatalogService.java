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

import java.net.URI;
import java.lang.StringBuilder;

import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetIteratorRequest;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientEntitySetIterator;
import org.apache.olingo.client.core.http.BasicAuthHttpClientFactory;

import com.redhat.coolstore.model.Product;


@ApplicationScoped
public class DVCatalogService implements CatalogService {

    @Inject
    private ODataClient oc;

    @Inject
    Logger log;

    private List<Product> productCollection;
    
	public DVCatalogService() {
	}

	public List<Product> getProducts() {
        if (productCollection.size()==0 || productCollection == null) {
            log.info("Product collection is empty, reset to default values");
            productCollection = DEFAULT_PRODUCT_LIST;
        }
        return productCollection;

    }

    public void add(Product product) {
        productCollection.add(product);
    }

    @PostConstruct
    protected void init() {
        log.info("@PostConstruct is called...");

        String hostName = System.getenv("CATALOG_DV_SERVICE_HOST");
        String portNo = System.getenv("CATALOG_DV_SERVICE_PORT");
        String userName = System.getenv("TEIID_USERNAME");
        String password = System.getenv("TEIID_PASSWORD");
        String vdbName = "CatalogVDB";
        String schemaName = "CatalogDB";
        String entitySetName = "products";

        if(hostName==null || hostName.isEmpty()) {
            log.info("Could not get environment variable CATALOG_DV_SERVICE_HOST using the default value of 'localhost'");
            hostName = "localhost";
        }
        
        if(portNo==null || portNo.isEmpty()) {
            log.info("Could not get environment variable CATALOG_DV_SERVICE_PORT using the default value of '8080'");
            hostName = "8080";
        }
        
        if(userName==null || userName.isEmpty()) {
            log.info("Could not get environment variable TEIID_USERNAME using the default value of 'teiidUser'");
            userName = "teiidUser";
        }

        if(password==null || password.isEmpty()) {
            log.info("Could not get environment variable TEIID_PASSWORD using the default value of 'redhat1!'");
            password = "redhat1!";
        }

        StringBuilder odataUrl = new StringBuilder();
        odataUrl.append("http://").append(hostName).append(":").append(portNo).append("/").append(vdbName).append("/").append(schemaName);


        oc.getConfiguration().setHttpClientFactory(new BasicAuthHttpClientFactory(userName,password));
		URI absoluteUri = oc.newURIBuilder(odataUrl.toString()).appendEntitySetSegment(entitySetName).build();
	
		ODataEntitySetIteratorRequest<ClientEntitySet, ClientEntity> request = 
		oc.getRetrieveRequestFactory().getEntitySetIteratorRequest(absoluteUri);
	
		request.setAccept("application/json;odata.metadata=minimal");

	
		ODataRetrieveResponse<ClientEntitySetIterator<ClientEntitySet, ClientEntity>> response = request.execute(); 
		ClientEntitySetIterator<ClientEntitySet, ClientEntity> iterator = response.getBody();

		while (iterator.hasNext()) {
		     ClientEntity ce = iterator.next();
             add(toProduct(ce));
		}		
    }

    /**
     * This method converts OData ClientEntity to Product POJOs, normally we would place this in a DAO
     * @param ClientEntity
     * @return
     */
    private Product toProduct(ClientEntity ce) {
        Product product =  new Product();
        product.setItemId(ce.getProperty("itemId").getPrimitiveValue().toString());
        product.setName(ce.getProperty("name").getPrimitiveValue().toString());
        product.setDesc(ce.getProperty("description").getPrimitiveValue().toString());
        product.setPrice(Double.parseDouble(ce.getProperty("price").getPrimitiveValue().toString()));
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