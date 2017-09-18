package com.redhat.coolstore.rating.verticle.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.redhat.coolstore.rating.model.Rating;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class RatingServiceImpl implements RatingService {

    private MongoClient client;

    public RatingServiceImpl(Vertx vertx, JsonObject config, MongoClient client) {
        this.client = client;
    }

    @Override
    public void getRatings(Handler<AsyncResult<List<Rating>>> resulthandler) {
        JsonObject query = new JsonObject();
        client.find("ratings", query, ar -> {
            if (ar.succeeded()) {
                List<Rating> products = ar.result().stream()
                                           .map(Rating::new)
                                           .collect(Collectors.toList());
                resulthandler.handle(Future.succeededFuture(products));
            } else {
                resulthandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }

    @Override
    public void getRating(String itemId, Handler<AsyncResult<Rating>> resulthandler) {
        JsonObject query = new JsonObject().put("itemId", itemId);
        client.find("ratings", query, ar -> {
            if (ar.succeeded()) {
                Optional<JsonObject> result = ar.result().stream().findFirst();
                if (result.isPresent()) {
                    resulthandler.handle(Future.succeededFuture(new Rating(result.get())));
                } else {
                    resulthandler.handle(Future.succeededFuture(null));
                }
            } else {
                resulthandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }

    @Override
    public void addRating(Rating rating, Handler<AsyncResult<Rating>> resulthandler) {

        getRating(rating.getItemId(), ratingAsyncResult -> {
            if (ratingAsyncResult.succeeded()) {
                Rating existing = ratingAsyncResult.result();
                if (existing == null) {
                    Rating r = new Rating();
                    r.setCount(1);
                    r.setItemId(rating.getItemId());
                    r.setRating(rating.getRating());
                    client.save("ratings", toDocument(r), stringAsyncResult -> {
                        if (stringAsyncResult.succeeded()) {
                            resulthandler.handle(Future.succeededFuture(r));
                        } else {
                            resulthandler.handle(Future.failedFuture(stringAsyncResult.cause()));
                        }
                    });

                } else {
                    double newVal = ((existing.getRating() * existing.getCount()) + rating.getRating()) / (existing.getCount() + 1);
                    existing.setCount(existing.getCount() + 1);
                    existing.setRating(newVal);
                    client.save("ratings", toDocument(existing), stringAsyncResult -> {
                        if (stringAsyncResult.succeeded()) {
                            resulthandler.handle(Future.succeededFuture(existing));
                        } else {
                            resulthandler.handle(Future.failedFuture(stringAsyncResult.cause()));
                        }
                    });
                }
            } else {
                resulthandler.handle(Future.failedFuture(ratingAsyncResult.cause()));
            }
        });
    }

    @Override
    public void ping(Handler<AsyncResult<String>> resultHandler) {
        resultHandler.handle(Future.succeededFuture("OK"));
    }

    private JsonObject toDocument(Rating rating) {
        JsonObject document = rating.toJson();
        document.put("_id", rating.getItemId());
        return document;
    }

}
