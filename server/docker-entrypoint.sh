#!/bin/sh

haveged

java -Duser.dir=/app -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -cp conf/:lib/* play.core.server.ProdServerStart
