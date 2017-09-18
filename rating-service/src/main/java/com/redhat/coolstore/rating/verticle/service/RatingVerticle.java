package com.redhat.coolstore.rating.verticle.service;

import java.util.Optional;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.serviceproxy.ProxyHelper;

public class RatingVerticle extends AbstractVerticle {

    private RatingService service;

    private MongoClient client;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        client = MongoClient.createShared(vertx, config());
        service = RatingService.create(vertx, config(), client);
        ProxyHelper.registerService(RatingService.class, vertx, service, RatingService.ADDRESS);

        startFuture.complete();
    }

    @Override
    public void stop() throws Exception {
        Optional.ofNullable(client).ifPresent(c -> c.close());
    }

}
