package com.redhat.coolstore.service;


import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import com.redhat.coolstore.model.Review;

import java.util.List;

@Stateless
public class ReviewService {

    @PersistenceContext
    private EntityManager em;

    public ReviewService() {

    }

    public List<Review> getReviews(String itemId) {


        TypedQuery<Review> query =
                em.createQuery("SELECT r FROM " + Review.class.getName() + " r WHERE r.itemId = :id", Review.class);
        query.setParameter("id", itemId);

        return query.getResultList();
    }
}
