docker cp postgres:/tmp default/1.sql 
docker cp postgres:/tmp default/2.sql  

docker exec -it postgres psql -U postgres -f /tmp/1.sql
docker exec -it postgres psql -U postgres -f /tmp/2.sql

