package com.redhat.coolstore.rest;

import com.redhat.coolstore.model.Review;
import com.redhat.coolstore.service.ReviewService;
import org.wildfly.swarm.health.Health;
import org.wildfly.swarm.health.HealthStatus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;
import java.util.List;

@ApplicationScoped
@Path("/infra")
public class InfraEndpoint implements Serializable {

    @Health
    @GET
    @Path("/health")
    public HealthStatus health() {
        return HealthStatus.named("infra").up();
    }

}
