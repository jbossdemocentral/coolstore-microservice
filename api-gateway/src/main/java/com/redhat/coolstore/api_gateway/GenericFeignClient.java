/**
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.coolstore.api_gateway;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import feign.Logger;
import feign.Logger.Level;
import feign.httpclient.ApacheHttpClient;
import feign.hystrix.HystrixFeign;

/**
 * This class constructs a Feign Client to be invoked
 *
 */
public abstract class GenericFeignClient<T> {

    private String serviceName;

    private Class<T> classType;

    private T fallBack;

    /**
     * We need the following information to instantiate a FeignClient
     * 
     * @param classType Service that will be invoked
     * @param serviceName the name of the service. It will be used in the hostname and in zipking tracing
     * @param fallback the fallback implementation
     */
    public GenericFeignClient(Class<T> classType, String serviceName, T fallback) {
        this.classType = classType;
        this.serviceName = serviceName;
        this.fallBack = fallback;
    }

    public T getService() {
        return createFeign();
    }

    /**
     * This is were the "magic" happens: it creates a Feign, which is a proxy interface for remote calling a REST endpoint with
     * Hystrix fallback support.
     **
     * @return The feign pointing to the service URL and with Hystrix fallback.
     */
    protected T createFeign() {
        final CloseableHttpClient httpclient =
            HttpClients.custom()
                .build();
        String url = String.format("http://%s:8080/", serviceName);
        return HystrixFeign.builder()
            // Use apache HttpClient which contains the ZipKin Interceptors
            .client(new ApacheHttpClient(httpclient))
            .logger(new Logger.ErrorLogger()).logLevel(Level.BASIC)
            .target(classType, url, fallBack);
    }

}
