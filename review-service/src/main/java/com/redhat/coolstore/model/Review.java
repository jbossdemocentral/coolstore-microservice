package com.redhat.coolstore.model;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "REVIEW", uniqueConstraints = @UniqueConstraint(columnNames = "reviewId"))
public class Review implements Serializable {

    private static final long serialVersionUID = -7304814269819778382L;

	@Id
	private int reviewId;
	private String itemId;
	private int rating;
    private String content;
    private String username;
    private String title;

	@Temporal(TemporalType.TIMESTAMP)
	private Date createDate;

    public Review() {

    }

	public Review(int reviewId, String itemId, int rating, String content, String username, String title, Date createDate) {
		this.reviewId = reviewId;
		this.itemId = itemId;
		this.rating = rating;
		this.content = content;
		this.username = username;
		this.title = title;
		this.createDate = createDate;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	public int getReviewId() {
		return reviewId;
	}

	public void setReviewId(int reviewId) {
		this.reviewId = reviewId;
	}

	public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public int getRating() {
		return rating;
	}

	public void setRating(int rating) {
		this.rating = rating;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Override
    public String toString() {
        return "Rating [itemId=" + itemId + ", content=" + content + " rating=" + rating + " user=" + username + "]";
    }
}
