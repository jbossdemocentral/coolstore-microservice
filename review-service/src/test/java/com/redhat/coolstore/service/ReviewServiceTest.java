package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Review;
import javax.ejb.EJB;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.spi.api.JARArchive;

@RunWith(Arquillian.class)
public class ReviewServiceTest {

    @Deployment
    public static Archive createDeployment() {
        JARArchive archive = ShrinkWrap.create(JARArchive.class);
        archive.addClasses(ReviewService.class, Review.class);
        archive.addAsResource("META-INF/persistence.xml", "META-INF/persistence.xml");
        archive.addAsResource("project-defaults.yml", "project-defaults.yml");
        archive.addAsResource("db/migration/V1_0__CreateSchema.sql", "db/migration/V1_0__CreateSchema.sql");
        archive.addAsResource("db/migration/V1_1__AddInitialData.sql", "db/migration/V1_1__AddInitialData.sql");

        return archive;
    }

    @EJB
    ReviewService reviewService;

    @Test
    public void should_get_review_by_id() {
        System.out.println(reviewService.getReviews("329299"));
    }

}
