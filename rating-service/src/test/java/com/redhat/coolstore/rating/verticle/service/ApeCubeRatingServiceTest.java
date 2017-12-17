package com.redhat.coolstore.rating.verticle.service;

import com.redhat.coolstore.rating.model.Rating;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import org.arquillian.ape.junit.rule.ArquillianPersistenceRule;
import org.arquillian.ape.nosql.NoSqlPopulator;
import org.arquillian.ape.nosql.NoSqlPopulatorConfigurator;
import org.arquillian.ape.nosql.mongodb.MongoDb;
import org.arquillian.cube.docker.junit.rule.ContainerDslRule;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class ApeCubeRatingServiceTest {

    public static final String TEST_DATABASE = "test";
    private Vertx vertx;
    private MongoClient mongoClient;

    // Starts in local dockerhost (docker machine or native) mongo docker image before running the test class
    @ClassRule
    public static ContainerDslRule mongodbContainer = new ContainerDslRule("mongo:3.2.18-jessie")
        .withPortBinding(27017);

    //Defines APE (Arquillian Persistence Extension to work as rule)
    @Rule
    public ArquillianPersistenceRule arquillianPersistenceRule = new ArquillianPersistenceRule();

    // Defines to use MongoDb as NoSql Populator
    @MongoDb
    @ArquillianResource
    NoSqlPopulator populator;

    @Before
    public void setup(TestContext context) {
        vertx = Vertx.vertx();
        vertx.exceptionHandler(context.exceptionHandler());
        JsonObject config = getConfig();
        mongoClient = MongoClient.createNonShared(vertx, config);
    }

    @After
    public void tearDown() {
        mongoClient.close();
        vertx.close();
        createPopulatorConfiguration().clean();
    }

    @Test
    public void should_add_new_items(TestContext context) {
        String itemId = "999999";
        double ratingVal1 = 5.0;
        Rating rating = new Rating();
        rating.setItemId(itemId);
        rating.setRating(ratingVal1);

        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();

        service.addRating(rating, ar -> {
            if (ar.failed()) {
                context.fail(ar.cause().getMessage());
            } else {
                JsonObject query = new JsonObject().put("_id", itemId);
                mongoClient.findOne("ratings", query, null, ar1 -> {
                    if (ar1.failed()) {
                        context.fail(ar1.cause().getMessage());
                    } else {
                        final JsonObject ratingResult = ar1.result();
                        assertThat(ratingResult.getDouble("rating")).isEqualTo(ratingVal1);
                        assertThat(ratingResult.getLong("count")).isEqualTo(1L);
                        async.complete();
                    }
                });
            }
        });
    }

    @Test
    public void should_calculate_average_rating_when_adding_an_already_inserted_item(TestContext context) {
        String itemId = "999999";
        double ratingVal1 = 4.3;
        double ratingVal2 = 1.1;
        Rating rating2 = new Rating();
        rating2.setItemId(itemId);
        rating2.setRating(ratingVal2);

        createPopulatorConfiguration()
            .usingDataSet("single_rating_with_double.json")
            .execute();

        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();

        service.addRating(rating2, ar2 -> {
            if (ar2.failed()) {
                context.fail(ar2.cause().getMessage());
            } else {
                JsonObject query = new JsonObject().put("_id", itemId);
                mongoClient.findOne("ratings", query, null, ar3 -> {
                    if (ar3.failed()) {
                        context.fail(ar3.cause().getMessage());
                    } else {
                        final JsonObject rating = ar3.result();
                        assertThat(rating.getDouble("rating")).isEqualTo((ratingVal1 + ratingVal2) / 2.0);
                        assertThat(rating.getLong("count")).isEqualTo(2L);
                        async.complete();
                    }
                });
            }
        });
    }

    @Test
    public void should_get_all_ratings(TestContext context) {
        createPopulatorConfiguration()
            .usingDataSet("ratings.json")
            .execute();

        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();

        service.getRatings(ar -> {
            if (ar.failed()) {
                context.fail(ar.cause().getMessage());
            } else {
                final List<Rating> ratings = ar.result();

                assertThat(ratings)
                    .isNotNull()
                    .hasSize(2);

                assertThat(ratings)
                    .extracting(Rating::getItemId)
                    .hasSize(2)
                    .containsExactlyInAnyOrder("111111", "222222");

                async.complete();
            }
        });
    }

    @Test
    public void should_get_rating_by_item_id(TestContext context) {
        createPopulatorConfiguration()
            .usingDataSet("ratings.json")
            .execute();

        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();

        service.getRating("111111", ar -> {
            if (ar.failed()) {
                context.fail(ar.cause().getMessage());
            } else {
                final Rating rating = ar.result();
                assertThat(rating).isNotNull();
                assertThat(rating).isEqualToIgnoringNullFields(new Rating("111111", 1.0, 1L));
                async.complete();
            }
        });
    }

    @Test
    public void should_get_a_null_rating_if_item_not_inserted(TestContext context) {
        createPopulatorConfiguration()
            .usingDataSet("single_rating.json")
            .execute();

        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();

        service.getRating("222222", ar -> {
            if (ar.failed()) {
                context.fail(ar.cause().getMessage());
            } else {
                assertThat(ar.result()).isNull();
                async.complete();
            }
        });
    }

    @Test
    public void should_execute_a_ping(TestContext context) {
        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();
        service.ping(ar -> {
            assertThat(ar.succeeded()).isTrue();
            async.complete();
        });
    }

    private NoSqlPopulatorConfigurator createPopulatorConfiguration() {
        return populator.forServer(
            mongodbContainer.getIpAddress(),
            mongodbContainer.getBindPort(27017))
            // by default mongodb container comes with test database created
            .withStorage(TEST_DATABASE);
    }

    private JsonObject getConfig() {
        JsonObject config = new JsonObject();
        config.put("connection_string",
            String.format("mongodb://%s:%d", mongodbContainer.getIpAddress(), mongodbContainer.getBindPort(27017)));
        config.put("db_name", TEST_DATABASE);
        return config;
    }
}
