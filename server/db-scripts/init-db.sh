docker cp default/1.sql postgres:/tmp 
docker cp default/2.sql postgres:/tmp 

docker exec -it postgres psql -U postgres -f /tmp/1.sql && docker exec -it postgres psql -U postgres -f /tmp/2.sql

