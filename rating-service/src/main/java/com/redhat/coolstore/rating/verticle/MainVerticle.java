package com.redhat.coolstore.rating.verticle;

import com.redhat.coolstore.rating.api.ApiVerticle;
import com.redhat.coolstore.rating.verticle.service.RatingService;
import com.redhat.coolstore.rating.verticle.service.RatingVerticle;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        ConfigStoreOptions jsonConfigStore = new ConfigStoreOptions().setType("json");
        ConfigStoreOptions appStore = new ConfigStoreOptions()
            .setType("configmap")
            .setFormat("yaml")
            .setConfig(new JsonObject()
                .put("name", "rating-config")
                .put("key", "rating-config.yaml"));

        ConfigRetrieverOptions options = new ConfigRetrieverOptions();
        if (System.getenv("KUBERNETES_NAMESPACE") != null) {
            //we're running in Kubernetes
            options.addStore(appStore);
        } else {
            //default to json based config
            jsonConfigStore.setConfig(config());
            options.addStore(jsonConfigStore);
        }

        ConfigRetriever.create(vertx, options)
            .getConfig(ar -> {
                if (ar.succeeded()) {
                    deployVerticles(ar.result(), startFuture);
                } else {
                    System.out.println("Failed to retrieve the configuration.");
                    startFuture.fail(ar.cause());
                }
            });
    }

    private void deployVerticles(JsonObject config, Future<Void> startFuture) {

        Future<String> apiVerticleFuture = Future.future();
        Future<String> ratingVerticleFuture = Future.future();

        RatingService ratingService = RatingService.createProxy(vertx);
        DeploymentOptions options = new DeploymentOptions();
        options.setConfig(config);
        vertx.deployVerticle(new RatingVerticle(), options, ratingVerticleFuture.completer());

        vertx.deployVerticle(new ApiVerticle(ratingService), options, apiVerticleFuture.completer());

        CompositeFuture.all(apiVerticleFuture, ratingVerticleFuture).setHandler(ar -> {
            if (ar.succeeded()) {
                System.out.println("Verticles deployed successfully.");
                startFuture.complete();
            } else {
                System.out.println("WARNINIG: Verticles NOT deployed successfully.");
                startFuture.fail(ar.cause());
            }
        });

    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        super.stop(stopFuture);
    }

}
