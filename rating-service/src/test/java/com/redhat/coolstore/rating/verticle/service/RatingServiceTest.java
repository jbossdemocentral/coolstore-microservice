package com.redhat.coolstore.rating.verticle.service;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.redhat.coolstore.rating.model.Rating;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class RatingServiceTest extends MongoTestBase {

    private Vertx vertx;

    @Before
    public void setup(TestContext context) throws Exception {
        vertx = Vertx.vertx();
        vertx.exceptionHandler(context.exceptionHandler());
        JsonObject config = getConfig();
        mongoClient = MongoClient.createNonShared(vertx, config);
        Async async = context.async();
        dropCollection(mongoClient, "ratings", async, context);
        async.await(10000);
    }

    @After
    public void tearDown() throws Exception {
        mongoClient.close();
        vertx.close();
    }

    @Test
    public void testAddRating(TestContext context) throws Exception {
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
                        assertThat(ar1.result().getDouble("rating"), equalTo(ratingVal1));
                        assertThat(ar1.result().getLong("count"), equalTo(1L));
                        async.complete();
                    }
                });
            }
        });
    }

    @Test
    public void testAverageAddRating(TestContext context) throws Exception {
        String itemId = "999999";
        double ratingVal1 = 4.3;
        double ratingVal2 = 1.1;
        Rating rating1 = new Rating();
        rating1.setItemId(itemId);
        rating1.setRating(ratingVal1);
        Rating rating2 = new Rating();
        rating2.setItemId(itemId);
        rating2.setRating(ratingVal2);

        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();

        service.addRating(rating1, ar -> {
            if (ar.failed()) {
                context.fail(ar.cause().getMessage());
            } else {
                service.addRating(rating2, ar2 -> {
                    if (ar2.failed()) {
                        context.fail(ar2.cause().getMessage());
                    } else {
                        JsonObject query = new JsonObject().put("_id", itemId);
                        mongoClient.findOne("ratings", query, null, ar3 -> {
                            if (ar3.failed()) {
                                context.fail(ar3.cause().getMessage());
                            } else {
                                assertThat(ar3.result().getDouble("rating"), equalTo((ratingVal1 + ratingVal2) / 2.0));
                                assertThat(ar3.result().getLong("count"), equalTo(2L));
                                async.complete();
                            }
                        });
                    }
                });

            }
        });
    }

    @Test
    public void testGetRatings(TestContext context) throws Exception {
        Async saveAsync = context.async(2);
        String itemId1 = "111111";
        JsonObject json1 = new JsonObject()
                .put("itemId", itemId1)
                .put("rating", 1.0);

        mongoClient.save("ratings", json1, ar -> {
            if (ar.failed()) {
                context.fail();
            }
            saveAsync.countDown();
        });

        String itemId2 = "222222";
        JsonObject json2 = new JsonObject()
                .put("itemId", itemId2)
                .put("rating", 2.0);

        mongoClient.save("ratings", json2, ar -> {
            if (ar.failed()) {
                context.fail();
            }
            saveAsync.countDown();
        });

        saveAsync.await();

        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();

        service.getRatings(ar -> {
            if (ar.failed()) {
                context.fail(ar.cause().getMessage());
            } else {
                assertThat(ar.result(), notNullValue());
                assertThat(ar.result().size(), equalTo(2));
                Set<String> itemIds = ar.result().stream().map(Rating::getItemId).collect(Collectors.toSet());
                assertThat(itemIds.size(), equalTo(2));
                assertThat(itemIds, allOf(hasItem(itemId1),hasItem(itemId2)));
                async.complete();
            }
        });
    }

    @Test
    public void testGetRating(TestContext context) throws Exception {
        Async saveAsync = context.async(2);
        String itemId1 = "111111";
        JsonObject json1 = new JsonObject()
                .put("itemId", itemId1)
                .put("rating", 1.0);

        mongoClient.save("ratings", json1, ar -> {
            if (ar.failed()) {
                context.fail();
            }
            saveAsync.countDown();
        });

        String itemId2 = "222222";
        JsonObject json2 = new JsonObject()
                .put("itemId", itemId2)
                .put("rating", 2.0);

        mongoClient.save("ratings", json2, ar -> {
            if (ar.failed()) {
                context.fail();
            }
            saveAsync.countDown();
        });

        saveAsync.await();

        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();

        service.getRating("111111", ar -> {
            if (ar.failed()) {
                context.fail(ar.cause().getMessage());
            } else {
                assertThat(ar.result(), notNullValue());
                assertThat(ar.result().getItemId(), equalTo("111111"));
                assertThat(ar.result().getRating(), equalTo(1.0));
                assertThat(ar.result().getCount(), equalTo(1L));
                async.complete();
            }
        });
    }

    @Test
    public void testGetNonExistingRating(TestContext context) throws Exception {
        Async saveAsync = context.async(1);
        String itemId1 = "111111";
        JsonObject json1 = new JsonObject()
                .put("itemId", itemId1)
                .put("rating", 1.0);

        mongoClient.save("ratings", json1, ar -> {
            if (ar.failed()) {
                context.fail();
            }
            saveAsync.countDown();
        });

        saveAsync.await();

        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();

        service.getRating("222222", ar -> {
            if (ar.failed()) {
                context.fail(ar.cause().getMessage());
            } else {
                assertThat(ar.result(), nullValue());
                async.complete();
            }
        });
    }

    @Test
    public void testPing(TestContext context) throws Exception {
        RatingService service = new RatingServiceImpl(vertx, getConfig(), mongoClient);

        Async async = context.async();
        service.ping(ar -> {
            assertThat(ar.succeeded(), equalTo(true));
            async.complete();
        });
    }

}
