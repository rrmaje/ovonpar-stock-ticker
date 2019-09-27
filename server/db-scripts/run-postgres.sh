docker run --name postgres -d --network="tickernet" -v /tmp/postgres:/var/lib/postgresql/data -p 5432:5432 postgres:11-alpine
