#!/bin/bash

docker run --rm --network="tickernet" --name ticker -p9000:9000 -p9443:9443 rrmaje/parity-ticker:0.1.0-SNAPSHOT
