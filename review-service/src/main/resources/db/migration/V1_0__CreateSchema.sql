create table REVIEW (
    reviewId int4 not null,
    itemId varchar(255) not null,
    username varchar(255) not null,
    content varchar(2550) not null,
    rating int4 not null,
    title varchar(255) not null,
    createDate TIMESTAMP not null,
    primary key (reviewId)
);