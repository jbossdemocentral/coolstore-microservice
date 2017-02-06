package com.redhat.coolstore.utils;

import java.util.Arrays;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.core.ODataClientImpl;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;

import org.apache.commons.lang3.StringUtils;

import com.redhat.coolstore.service.CatalogService;
import com.redhat.coolstore.service.DVCatalogService;
import com.redhat.coolstore.service.MongoCatalogService;

public class Producers {

    public static final String JDV_DATASOURCE_IMPL = "JDV";
    public static final String MONGO_DATASOURCE_IMPL = "MONGODB";

    Logger log = Logger.getLogger(Producers.class.getName());

    @Produces
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

    @Produces @ApplicationScoped
    public ODataClient createODataClient() {
            log.info("Creating ODataClient");
    		return new ODataClientImpl();
    }

    @Produces @ApplicationScoped
    public MongoClient createMongoClient() {
        log.info("Creating MongoClient");
        String dbName = System.getenv("DB_NAME");
        if(dbName==null || dbName.isEmpty()) {
            log.info("Could not get environment variable DB_NAME using the default value of 'CatalogDB'");
            dbName = "CatalogDB";
        }

        String dbServer = System.getenv("DB_SERVER");
        if(dbServer==null || dbServer.isEmpty()) {
            log.info("Could not get environment variable DB_SERVER using the default value of 'localhost'");
            dbServer = "localhost";
        }


        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");
        if(dbUsername!=null && !dbUsername.isEmpty() && dbPassword!=null && !dbPassword.isEmpty()) {
            log.info(String.format("Connecting to MongoDB %s@%s using %s user credentials",dbName,dbServer,dbUsername));
            return new MongoClient(new ServerAddress(dbServer), Arrays.asList(MongoCredential.createCredential(dbUsername, dbName, dbPassword.toCharArray())));
        } else {
            log.info(String.format("Connecting to MongoDB %s@%s without authentication",dbName,dbServer));
            return new MongoClient(dbServer);
        }

    }

    @Produces @Preferred
    public CatalogService getCatalogService(MongoCatalogService mongodb, DVCatalogService dv) {
        String dsImplEnv = System.getenv("CATALOG_SERVICE_DS_IMPL");
        // If the env CATALOG_SERVICE_DS_IMPL is empty the MongoDB should be the default
        if(StringUtils.isEmpty(dsImplEnv) || dsImplEnv.equals(MONGO_DATASOURCE_IMPL)) {
            return mongodb;
        } else if(dsImplEnv.equals(JDV_DATASOURCE_IMPL)) {
            return dv;
        } else {
            throw new UnsupportedOperationException("Unknown data source implementation set in env DATASOURCE_IMPL. A datasource implementation of '" + dsImplEnv + "' is not supported!!");
        }
    }
}
