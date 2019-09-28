FROM openjdk:8-jre
WORKDIR /app
COPY target/universal/stage/lib/* ./lib/
COPY target/universal/stage/conf/ ./conf/
COPY ./docker-entrypoint.sh .
USER root 
RUN apt-get update
RUN apt-get install -y haveged
USER daemon
EXPOSE 9000
ENTRYPOINT ["sh", "/app/docker-entrypoint.sh"]
