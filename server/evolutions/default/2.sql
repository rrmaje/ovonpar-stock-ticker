# --- Sample dataset

# --- !Ups

insert into user (id,username,password,parityuser) values (  1,'test@foo.com','test','10101010');
insert into user (id,username,password,parityuser) values (  2,'test2@foo.com','test','20202020');

# --- !Downs

delete from user;