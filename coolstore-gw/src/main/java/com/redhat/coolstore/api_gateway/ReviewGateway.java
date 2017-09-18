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

import com.redhat.coolstore.api_gateway.model.Product;
import com.redhat.coolstore.api_gateway.model.Review;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.jackson.ListJacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ReviewGateway extends RouteBuilder {
	private static final Logger LOG = LoggerFactory.getLogger(ReviewGateway.class);
	
	
	@Value("${hystrix.executionTimeout}")
	private int hystrixExecutionTimeout;
	
	@Value("${hystrix.groupKey}")
	private String hystrixGroupKey;
	
	@Value("${hystrix.circuitBreakerEnabled}")
	private boolean hystrixCircuitBreakerEnabled;
	
	@Autowired
	private Environment env;
	
    @Override
    public void configure() throws Exception {
    	try {
    		getContext().setTracing(Boolean.parseBoolean(env.getProperty("ENABLE_TRACER", "false")));	
		} catch (Exception e) {
			LOG.error("Failed to parse the ENABLE_TRACER value: {}", env.getProperty("ENABLE_TRACER", "false"));
		}

        restConfiguration().component("servlet")
            .bindingMode(RestBindingMode.auto).apiContextPath("/api-docs").contextPath("/api").apiProperty("host", "")
            .apiProperty("api.title", "CoolStore Microservice Review API Gateway")
            .apiProperty("api.version", "1.0.0")
            .apiProperty("api.description", "The API of the gateway which fronts the various backend microservices in the CoolStore")
            .apiProperty("api.contact.name", "Red Hat Developers")
            .apiProperty("api.contact.email", "developers@redhat.com")
            .apiProperty("api.contact.url", "https://developers.redhat.com");


		JacksonDataFormat reviewFormatter = new ListJacksonDataFormat();
		reviewFormatter.setUnmarshalType(Review.class);

		rest("/review").description("Product Review Service")
            .produces(MediaType.APPLICATION_JSON_VALUE)

            .get("/{itemId}").description("Get Product Reviews")
				.param().name("itemId").type(RestParamType.path).description("The ID of the item to process").dataType("string").endParam()
                .outType(Review.class)
                .route().id("getReviewRoute")
                .hystrix().id("Review Service (Get Review for Product)")
                    .hystrixConfiguration()
		    			.executionTimeoutInMilliseconds(hystrixExecutionTimeout)
		    			.groupKey(hystrixGroupKey)
		    			.circuitBreakerEnabled(hystrixCircuitBreakerEnabled)
		    		.end()
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:REVIEW_ENDPOINT:review:8080}}/api/review/${header.itemId}"))
                    .to("http4://DUMMY")
                .onFallback()
        			.setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
        			.to("direct:getReviewFallback")
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(Review.class.getName()))
                .unmarshal(reviewFormatter)
            .endRest();

        // Provide a response
        from("direct:getReviewFallback").routeId("getreviewfallback").description("Get Review Fall back response")
        	.process(exchange -> exchange.getIn().setBody(Collections.singletonList(new Review())))
        .marshal().json(JsonLibrary.Jackson);
           

    }

    
}
