package com.redhat.coolstore.rest;

import java.io.Serializable;
import java.util.List;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.service.CatalogService;

@SessionScoped
@Path("/products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CatalogEndpoint implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = -7227732980791688773L;

    @Inject
    private CatalogService catalogService;

    @GET
    @Path("/")
    public List<Product> listAll() {
        return catalogService.getProducts();
    }

    @POST
    @Path("/")
    public Response add(Product product) {
        catalogService.add(product);
        return Response.ok().build();
    }

}
