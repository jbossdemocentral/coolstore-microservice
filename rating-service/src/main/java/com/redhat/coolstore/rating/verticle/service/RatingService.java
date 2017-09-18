package com.redhat.coolstore.rating.verticle.service;

import java.util.List;

import com.redhat.coolstore.rating.model.Rating;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

@ProxyGen
public interface RatingService {

    final static String ADDRESS = "rating-service";

    static RatingService create(Vertx vertx, JsonObject config, MongoClient client) {
        return new RatingServiceImpl(vertx, config, client);
    }

    static RatingService createProxy(Vertx vertx) {
        return new RatingServiceVertxEBProxy(vertx, ADDRESS);
    }

    void getRatings(Handler<AsyncResult<List<Rating>>> resulthandler);

    void getRating(String itemId, Handler<AsyncResult<Rating>> resulthandler);

    void addRating(Rating rating, Handler<AsyncResult<Rating>> resulthandler);

    void ping(Handler<AsyncResult<String>> resultHandler);

}
