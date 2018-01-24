package com.redhat.coolstore;

import io.restassured.builder.RequestSpecBuilder;
import java.net.URISyntaxException;
import java.net.URL;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.arquillian.DefaultDeployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

@RunWith(Arquillian.class)
@DefaultDeployment(testable = false)
public class ReviewEndpointTest {

    @ArquillianResource
    URL url;

    @Test
    public void should_return_reviews_by_item_id() throws URISyntaxException {
        RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder();
        requestSpecBuilder.setBaseUri(url.toURI());

        given()
            .spec(requestSpecBuilder.build())
        .when()
            .get("api/review/329299")
        .then()
            .assertThat()
            .body("title", hasItems("Best hate EVAR", "Meh.. I've seen better"));

    }

}
