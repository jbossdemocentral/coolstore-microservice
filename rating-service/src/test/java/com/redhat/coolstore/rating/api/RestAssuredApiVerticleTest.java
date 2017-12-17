package com.redhat.coolstore.rating.api;

import com.redhat.coolstore.rating.model.Rating;
import com.redhat.coolstore.rating.verticle.service.RatingService;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(VertxUnitRunner.class)
public class RestAssuredApiVerticleTest {


    private Vertx vertx;
    private Integer port;
    private RatingService ratingService;

    /**
     * Before executing our test, let's deploy our verticle.
     * <p/>
     * This method instantiates a new Vertx and deploy the verticle. Then, it waits in the verticle has successfully
     * completed its start sequence (thanks to `context.asyncAssertSuccess`).
     *
     * @param context the test context.
     */
    @Before
    public void setUp(TestContext context) throws IOException {
        vertx = Vertx.vertx();

        // Register the context exception handler
        vertx.exceptionHandler(context.exceptionHandler());

        // Let's configure the verticle to listen on the 'test' port (randomly picked).
        // We create deployment options and set the _configuration_ json object:
        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("rating.http.port", port));

        //Mock the rating Service
        ratingService = mock(RatingService.class);

        // We pass the options as the second parameter of the deployVerticle method.
        vertx.deployVerticle(new ApiVerticle(ratingService), options, context.asyncAssertSuccess());
    }

    /**
     * This method, called after our test, just cleanup everything by closing
     * the vert.x instance
     *
     * @param context
     *            the test context
     */
    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void should_get_all_ratings() {
        String itemId1 = "111111";
        JsonObject json1 = new JsonObject()
            .put("itemId", itemId1)
            .put("rating", 1.0);
        String itemId2 = "222222";
        JsonObject json2 = new JsonObject()
            .put("itemId", itemId2)
            .put("rating", 2.0);
        List<Rating> ratings = new ArrayList<>();
        ratings.add(new Rating(json1));
        ratings.add(new Rating(json2));

        doAnswer(invocation -> {
            Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(0);
            handler.handle(Future.succeededFuture(ratings));
            return null;
        }).when(ratingService).getRatings(any());

        final RequestSpecification requestSpecification = createRequestSpecificationApiEndpoint();

        given()
            .spec(requestSpecification)
            .when()
            .get("ratings")
            .then()
            .assertThat()
            .statusCode(200)
            .contentType("application/json")
            .body("itemId", hasItems(itemId1, itemId2));

        verify(ratingService).getRatings(any());

    }

    @Test
    public void should_get_specific_rating_by_item_id() {
        String itemId = "111111";
        JsonObject json = new JsonObject()
            .put("itemId", itemId)
            .put("rating", 1.0);
        Rating rating = new Rating(json);
        doAnswer(invocation -> {
            Handler<AsyncResult<Rating>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(rating));
            return null;
        }).when(ratingService).getRating(eq("111111"),any());


        final RequestSpecification requestSpecification = createRequestSpecificationApiEndpoint();

        given()
            .spec(requestSpecification)
            .when()
            .get("rating/111111")
            .then()
            .assertThat()
            .statusCode(200)
            .contentType("application/json")
            .body("itemId", is(itemId))
            .body("count", is(1));

        verify(ratingService).getRating(eq("111111"),any());
    }

    @Test
    public void should_get_a_text_message_when_rating_service_timeout() {
        doAnswer(invocation -> {
            vertx.setTimer(1500, l -> {
                Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(0);
                handler.handle(Future.succeededFuture(new ArrayList<>()));
            });
            return null;
        }).when(ratingService).getRatings(any());

        final RequestSpecification requestSpecification = createRequestSpecificationApiEndpoint();
        final String body = given()
            .spec(requestSpecification)
            .when()
            .get("ratings")
            .then()
            .using()
            .defaultParser(Parser.TEXT)
            .assertThat()
            .statusCode(503)
            .extract()
            .body()
            .asString();

        assertThat(body).isEqualTo("Service Unavailable");

        verify(ratingService).getRatings(any());
    }

    @Test
    public void should_get_a_text_message_when_circuit_breaker_open() {
        doAnswer(invocation -> {
            Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(0);
            handler.handle(Future.failedFuture("Error"));
            return null;
        }).when(ratingService).getRatings(any());

        openCircuit();

        final RequestSpecification requestSpecification = createRequestSpecificationApiEndpoint();
        final String body = given()
            .spec(requestSpecification)
            .when()
            .get("ratings")
            .then()
            .using()
            .defaultParser(Parser.TEXT)
            .assertThat()
            .statusCode(503)
            .extract()
            .body()
            .asString();

        assertThat(body).isEqualTo("Service Unavailable");

        verify(ratingService, times(3)).getRatings(any());
    }

    @Test
    public void should_get_a_text_message_when_circuit_reset_after_open() {
        doAnswer(invocation -> {
            Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(0);
            handler.handle(Future.failedFuture("Error"));
            return null;
        }).when(ratingService).getRatings(any());

        openCircuit();
        reset(ratingService);

        waitFor(5, TimeUnit.SECONDS);

        final RequestSpecification requestSpecification = createRequestSpecificationApiEndpoint();
        final String body = given()
            .spec(requestSpecification)
            .when()
            .get("ratings")
            .then()
            .using()
            .defaultParser(Parser.TEXT)
            .assertThat()
            .statusCode(503)
            .extract()
            .body()
            .asString();

        assertThat(body).isEqualTo("Service Unavailable");
        verify(ratingService, times(1)).getRatings(any());
    }

    private void openCircuit() {
        final RequestSpecification requestSpecificationApiEndpoint = createRequestSpecificationApiEndpoint();
        for (int i = 0; i < 3; i++) {
            given()
                .spec(requestSpecificationApiEndpoint)
                .when()
                .get("ratings")
                .then()
                .assertThat()
                .statusCode(503);
        }
    }


    private void waitFor(long time, TimeUnit unit) {
        try {
            unit.sleep(time);
        } catch (InterruptedException ignored) {}
    }

    @Test
    public void should_return_a_404_when_no_rating_present_for_given_item_id() {
        doAnswer(invocation -> {
            Handler<AsyncResult<Rating>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(null));
            return null;
        }).when(ratingService).getRating(eq("111111"),any());


        final RequestSpecification requestSpecification = createRequestSpecificationApiEndpoint();

        given()
            .spec(requestSpecification)
            .when()
            .get("rating/111111")
            .then()
            .assertThat()
            .statusCode(404);

    }

    @Test
    public void should_get_a_text_message_when_rating_service_throws_error() {
        doAnswer(invocation -> {
            Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("error"));
            return null;
        }).when(ratingService).getRating(any(),any());

        final RequestSpecification requestSpecification = createRequestSpecificationApiEndpoint();
        final String body = given()
            .spec(requestSpecification)
            .when()
            .get("rating/111111")
            .then()
            .using()
            .defaultParser(Parser.TEXT)
            .assertThat()
            .statusCode(503)
            .extract()
            .body()
            .asString();

        assertThat(body).isEqualTo("Service Unavailable");
        verify(ratingService).getRating(eq("111111"),any());

    }


    @Test
    public void should_add_new_rating() {
        doAnswer(invocation -> {
            Handler<AsyncResult<String>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(null));
            return null;
        }).when(ratingService).addRating(any(),any());

        final RequestSpecification requestSpecification = createRequestSpecificationApiEndpoint();

        String itemId = "111111";
        double rating = 1.0;

        given()
            .spec(requestSpecification)
            .when()
            .post(String.format("rating/%s/%.1f", itemId, rating))
            .then()
            .assertThat()
            .statusCode(201)
            .contentType("application/json");

        ArgumentCaptor<Rating> argument = ArgumentCaptor.forClass(Rating.class);
        verify(ratingService).addRating(argument.capture(), any());
    }

    @Test
    public void should_check_for_liveness_health() {
        doAnswer(invocation -> {
            Handler<AsyncResult<Long>> handler = invocation.getArgument(0);
            handler.handle(Future.succeededFuture(10L));
            return null;
        }).when(ratingService).ping(any());


        final RequestSpecification requestSpecification = createRequestSpecificationHealthEndpoint();

        given()
            .spec(requestSpecification)
            .when()
            .get("liveness")
            .then()
            .assertThat()
            .statusCode(200)
            .contentType("application/json")
            .body("outcome", is("UP"));

    }

    @Test
    public void should_no_check_when_liveness_health_fails() {
        doAnswer(invocation -> {
            Handler<AsyncResult<Long>> handler = invocation.getArgument(0);
            handler.handle(Future.failedFuture("error"));
            return null;
        }).when(ratingService).ping(any());

        final RequestSpecification requestSpecification = createRequestSpecificationHealthEndpoint();

        given()
            .spec(requestSpecification)
            .when()
            .get("liveness")
            .then()
            .assertThat()
            .statusCode(503);

    }


    @Test
    public void should_no_check_when_liveness_health_fails_because_of_timeout() {
        doAnswer(invocation -> {
            Handler<AsyncResult<String>> handler = invocation.getArgument(0);
            // Simulate long operation to force timeout - default timeout = 1000ms
            vertx.<String>executeBlocking(f -> {
                waitFor(1100, TimeUnit.MILLISECONDS);
                f.complete();
            }, res -> handler.handle(Future.succeededFuture("OK")));
            return null;
        }).when(ratingService).ping(any());

        final RequestSpecification requestSpecification = createRequestSpecificationHealthEndpoint();

        given()
            .spec(requestSpecification)
            .when()
            .get("liveness")
            .then()
            .assertThat()
            .statusCode(500);
    }


    @Test
    public void should_check_for_readiness_health() {

        final RequestSpecification requestSpecification = createRequestSpecificationHealthEndpoint();

        given()
            .spec(requestSpecification)
            .when()
            .get("readiness")
            .then()
            .assertThat()
            .statusCode(200);

    }

    private RequestSpecification createRequestSpecificationApiEndpoint() {
        return new RequestSpecBuilder()
            .setBaseUri(String.format("http://localhost:%d/api", port))
            .build();
    }

    private RequestSpecification createRequestSpecificationHealthEndpoint() {
        return new RequestSpecBuilder()
            .setBaseUri(String.format("http://localhost:%d/health", port))
            .build();
    }

}
