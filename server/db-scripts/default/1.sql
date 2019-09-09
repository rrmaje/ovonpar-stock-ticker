create table parity_user (
  id                        bigint not null DEFAULT nextval('user_seq'),
  username                      varchar(255) not null,
  password                      varchar(255) not null,
  parityuser                      varchar(255) not null,
  constraint pk_user primary key (id))
;

create sequence user_seq start with 1000;