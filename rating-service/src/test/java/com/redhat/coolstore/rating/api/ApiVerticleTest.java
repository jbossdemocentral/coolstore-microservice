package com.redhat.coolstore.rating.api;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import com.redhat.coolstore.rating.model.Rating;
import com.redhat.coolstore.rating.verticle.service.RatingService;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ApiVerticleTest {

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
    public void testGetRatings(TestContext context) throws Exception {
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

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/ratings", response -> {
                assertThat(response.statusCode(), equalTo(200));
                assertThat(response.headers().get("Content-type"), equalTo("application/json"));
                response.bodyHandler(body -> {
                    JsonArray json = body.toJsonArray();
                    Set<String> itemIds =  json.stream()
                            .map(j -> new Rating((JsonObject)j))
                            .map(Rating::getItemId)
                            .collect(Collectors.toSet());
                    assertThat(itemIds.size(), equalTo(2));
                    assertThat(itemIds, allOf(hasItem(itemId1),hasItem(itemId2)));
                    verify(ratingService).getRatings(any());
                    async.complete();
                })
                .exceptionHandler(context.exceptionHandler());
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testGetRatingsWhenRatingServiceThrowsError(TestContext context) {
        doAnswer(invocation -> {
            Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(0);
            handler.handle(Future.failedFuture("error"));
            return null;
         }).when(ratingService).getRatings(any());

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/ratings", response -> {
                assertThat(response.statusCode(), equalTo(503));
                response.bodyHandler(body -> {
                    assertThat(body.toString(), equalTo("Service Unavailable"));
                    verify(ratingService).getRatings(any());
                    async.complete();
                })
                .exceptionHandler(context.exceptionHandler());
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testGetRating(TestContext context) throws Exception {
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

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/rating/111111", response -> {
                assertThat(response.statusCode(), equalTo(200));
                assertThat(response.headers().get("Content-type"), equalTo("application/json"));
                response.bodyHandler(body -> {
                    JsonObject result = body.toJsonObject();
                    assertThat(result, notNullValue());
                    assertThat(result.containsKey("itemId"), is(true));
                    assertThat(result.getString("itemId"), equalTo("111111"));
                    assertThat(result.getLong("count"), equalTo(1L));
                    verify(ratingService).getRating(eq("111111"),any());
                    async.complete();
                })
                .exceptionHandler(context.exceptionHandler());
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testGetRatingsWithTimeOut(TestContext context) {
        doAnswer(invocation -> {
            vertx.setTimer(1500, l -> {
                Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(0);
                handler.handle(Future.succeededFuture(new ArrayList<>()));
            });
            return null;
         }).when(ratingService).getRatings(any());

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/ratings", response -> {
                assertThat(response.statusCode(), equalTo(503));
                response.bodyHandler(body -> {
                    assertThat(body.toString(), equalTo("Service Unavailable"));
                    verify(ratingService).getRatings(any());
                    async.complete();
                })
                .exceptionHandler(context.exceptionHandler());
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testGetRatingsWithCircuitOpen(TestContext context) {
        doAnswer(invocation -> {
            Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(0);
            handler.handle(Future.failedFuture("Error"));
            return null;
         }).when(ratingService).getRatings(any());

        Async async = context.async();
        // Circuit Breaker opens after 3 failed calls
        Async calls = context.async(3);
        for (int i = 0; i < 3; i++) {
            vertx.createHttpClient().get(port, "localhost", "/api/ratings", response -> calls.countDown())
                .exceptionHandler(context.exceptionHandler())
                .end();
        }
        calls.await();
        vertx.createHttpClient().get(port, "localhost", "/api/ratings", response -> {
                assertThat(response.statusCode(), equalTo(503));
                response.bodyHandler(body -> {
                    assertThat(body.toString(), equalTo("Service Unavailable"));
                    // Protected service no longer called after 3 failed calls
                    verify(ratingService, times(3)).getRatings(any());
                    async.complete();
                })
                .exceptionHandler(context.exceptionHandler());
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testGetRatingsWhenCircuitResetAfterOpen(TestContext context) {
        doAnswer(invocation -> {
            Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(0);
            handler.handle(Future.failedFuture("Error"));
            return null;
         }).when(ratingService).getRatings(any());

        Async async = context.async();
        // Circuit Breaker opens after 3 failed calls
        Async calls = context.async(3);
        for (int i = 0; i < 10; i++) {
            vertx.createHttpClient().get(port, "localhost", "/api/ratings", response -> calls.countDown())
                .exceptionHandler(context.exceptionHandler())
                .end();
        }
        calls.await();
        reset(ratingService);

        vertx.setTimer(5000, l -> vertx.createHttpClient().get(port, "localhost", "/api/ratings", response -> {
            assertThat(response.statusCode(), equalTo(503));
            response.bodyHandler(body -> {
                assertThat(body.toString(), equalTo("Service Unavailable"));
                // Protected service is called again after reset
                verify(ratingService, times(1)).getRatings(any());
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler());
        })
        .exceptionHandler(context.exceptionHandler())
        .end());
    }

    @Test
    public void testGetNonExistingRating(TestContext context) throws Exception {
        doAnswer(invocation -> {
            Handler<AsyncResult<Rating>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(null));
            return null;
         }).when(ratingService).getRating(eq("111111"),any());

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/rating/111111", response -> {
                assertThat(response.statusCode(), equalTo(404));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testGetRatingWhenRatingServiceThrowsError(TestContext context) {
        doAnswer(invocation -> {
            Handler<AsyncResult<List<Rating>>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("error"));
            return null;
         }).when(ratingService).getRating(any(),any());

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/rating/111111", response -> {
                assertThat(response.statusCode(), equalTo(503));
                response.bodyHandler(body -> {
                    assertThat(body.toString(), equalTo("Service Unavailable"));
                    verify(ratingService).getRating(eq("111111"),any());
                    async.complete();
                })
                .exceptionHandler(context.exceptionHandler());
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testAddRating(TestContext context) throws Exception {
        doAnswer(invocation -> {
            Handler<AsyncResult<String>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(null));
            return null;
         }).when(ratingService).addRating(any(),any());

        Async async = context.async();
        String itemId = "111111";
        double rating = 1.0;
        vertx.createHttpClient().post(port, "localhost", "/api/rating/" + itemId + "/" + rating)
            .exceptionHandler(context.exceptionHandler())
            .handler(response -> {
                assertThat(response.statusCode(), equalTo(201));
                ArgumentCaptor<Rating> argument = ArgumentCaptor.forClass(Rating.class);
                verify(ratingService).addRating(argument.capture(), any());
                assertThat(argument.getValue().getItemId(), equalTo(itemId));
                assertThat(argument.getValue().getCount(), equalTo(rating));
                async.complete();
            })
            .end();
    }

    @Test
    public void testLivenessHealthCheck(TestContext context) {
        doAnswer(invocation -> {
            Handler<AsyncResult<Long>> handler = invocation.getArgument(0);
            handler.handle(Future.succeededFuture(10L));
            return null;
         }).when(ratingService).ping(any());

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/health/liveness", response -> {
                assertThat(response.statusCode(), equalTo(200));
                response.bodyHandler(body -> {
                    JsonObject json = body.toJsonObject();
                    assertThat(json.toString(), containsString("\"outcome\":\"UP\""));
                    async.complete();
                }).exceptionHandler(context.exceptionHandler());
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testFailingLivenessHealthCheck(TestContext context) {
        doAnswer(invocation -> {
            Handler<AsyncResult<Long>> handler = invocation.getArgument(0);
            handler.handle(Future.failedFuture("error"));
            return null;
         }).when(ratingService).ping(any());

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/health/liveness", response -> {
                assertThat(response.statusCode(), equalTo(503));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testLivenessHealthCheckTimeOut(TestContext context) {
        doAnswer(invocation -> {
            Handler<AsyncResult<String>> handler = invocation.getArgument(0);
            // Simulate long operation to force timeout - default timeout = 1000ms
            vertx.<String>executeBlocking(f -> {
                try {
                    Thread.sleep(1100);
                } catch (InterruptedException ignored) {}
                f.complete();
            }, res -> handler.handle(Future.succeededFuture("OK")));
            return null;
        }).when(ratingService).ping(any());

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/health/liveness", response -> {
                // HealthCheck Timeout returns a 500 status code
                assertThat(response.statusCode(), equalTo(500));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

    @Test
    public void testReadinessHealthCheck(TestContext context) {
        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/health/readiness", response -> {
                assertThat(response.statusCode(), equalTo(200));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }
}
