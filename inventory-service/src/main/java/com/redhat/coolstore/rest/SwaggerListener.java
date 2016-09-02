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
package com.redhat.coolstore.rest;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.swagger.jaxrs.config.BeanConfig;

@WebListener
public class SwaggerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setVersion("1.0.0");
        beanConfig.setSchemes(new String[] { "http" });
        beanConfig.setTitle("Inventory REST API");
        beanConfig.setDescription("Operations that can be invoked in the inventory microservice");
        beanConfig.setResourcePackage("com.redhat.coolstore.rest");
        beanConfig.setLicense("Apache 2.0");
        beanConfig.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        beanConfig.setContact("developer@redhat.com");
        beanConfig.setBasePath("/api");
        beanConfig.setPrettyPrint(true);
        beanConfig.setScan(true);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }

}