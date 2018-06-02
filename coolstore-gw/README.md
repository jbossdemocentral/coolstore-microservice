# CoolStore Gateway

This service is the CoolStore Gateway which acts as an API aggregator towards other
services using Apache Camel (Fuse Integration Services) and Spring Boot.

This service connects to Cart service, Catalog service and inventory service

Required Environment Variables

CART_ENDPOINT=cart:8080
CATALOG_ENDPOINT=catalog:8080
INVENTORY_ENDPOINT=inventory:8080
RATING_ENDPOINT=rating:8080
REVIEW_ENDPOINT=review:8080
