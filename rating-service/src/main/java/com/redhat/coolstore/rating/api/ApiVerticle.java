package com.redhat.coolstore.rating.api;

import java.util.List;

import com.redhat.coolstore.rating.model.Rating;
import com.redhat.coolstore.rating.verticle.service.RatingService;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class ApiVerticle extends AbstractVerticle {

    private RatingService ratingService;

    private CircuitBreaker circuitBreaker;

    public ApiVerticle(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        Router router = Router.router(vertx);

        router.get("/api/ratings").handler(this::getRatings);
        router.get("/api/rating/:itemId").handler(this::getRating);
        router.route("/api/rating").handler(BodyHandler.create());
        router.post("/api/rating/:itemId/:rating").handler(this::addRating);

        //Health Checks
        router.get("/health/readiness").handler(rc -> rc.response().end("OK"));
        HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx)
                .register("health", this::health);
        router.get("/health/liveness").handler(healthCheckHandler);

        circuitBreaker = CircuitBreaker.create("rating-circuit-breaker", vertx,
                new CircuitBreakerOptions()
                    .setMaxFailures(3) // number of failure before opening the circuit
                    .setTimeout(1000) // consider a failure if the operation does not succeed in time
                    .setFallbackOnFailure(true) // do we call the fallback on failure
                    .setResetTimeout(5000) // time spent in open state before attempting to re-try
                );

        vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(config().getInteger("rating.http.port", 8080), result -> {
                if (result.succeeded()) {
                    startFuture.complete();
                } else {
                    startFuture.fail(result.cause());
                }
            });
    }

    private void getRatings(RoutingContext rc) {
        circuitBreaker.<JsonArray>execute(future -> ratingService.getRatings(ar -> {
            if (ar.succeeded()) {
                List<Rating> ratings = ar.result();
                JsonArray json = new JsonArray();
                ratings.stream()
                    .map(Rating::toJson)
                    .forEach(json::add);
                future.complete(json);
            } else {
                future.fail(ar.cause());
            }
        })).setHandler(ar -> {
            if (ar.succeeded()) {
                rc.response()
                    .putHeader("Content-type", "application/json")
                    .end(ar.result().encodePrettily());
            } else {
                rc.fail(503);
            }
        });
    }

    private void getRating(RoutingContext rc) {
        String itemId = rc.request().getParam("itemid");
        System.out.println("Rating request for: " + itemId);
        circuitBreaker.<JsonObject>execute(future -> ratingService.getRating(itemId, ar -> {
            if (ar.succeeded()) {
                Rating product = ar.result();
                JsonObject json = null;
                if (product != null) {
                    json = product.toJson();
                }
                future.complete(json);
            } else {
                future.fail(ar.cause());
            }
        })).setHandler(ar -> {
            if (ar.succeeded()) {
                if (ar.result() != null) {
                    rc.response()
                        .putHeader("Content-type", "application/json")
                        .end(ar.result().encodePrettily());
                } else {
                    rc.fail(404);
                }
            } else {
                rc.fail(503);
            }
        });
    }

    private void addRating(RoutingContext rc) {
        String itemId = rc.request().getParam("itemId");
        double rating = Double.parseDouble(rc.request().getParam("rating"));

        ratingService.addRating(new Rating(itemId, rating, 1), ar -> {
            if (ar.succeeded()) {
                rc.response().setStatusCode(201)
                        .putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(ar.result()));
            } else {
                rc.fail(ar.cause());
            }
        });
    }

    private void health(Future<Status> future) {
        ratingService.ping(ar -> {
            if (ar.succeeded()) {
                // HealthCheckHandler has a timeout of 1000s. If timeout is exceeded, the future will be failed
                if (!future.isComplete()) {
                    future.complete(Status.OK());
                }
            } else {
                if (!future.isComplete()) {
                    future.complete(Status.KO());
                }
            }
        });
    }

}
