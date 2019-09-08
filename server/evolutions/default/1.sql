# --- First database schema

# --- !Ups

set ignorecase true;


create table user (
  id                        bigint not null auto_increment,
  username                      varchar(255) not null,
  password                      varchar(255) not null,
  parityuser                      varchar(255) not null,
  constraint pk_user primary key (id))
;

create sequence user_seq start with 1000;

# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists user;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists user_seq;