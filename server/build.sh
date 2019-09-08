#!/bin/bash

docker run --rm --network="tickernet" --name ticker -p9000:9000 -p9443:9443 -e OST_MULTICAST_INTERFACE=eth0 \
-e OST_REQUEST_ADDRESS=parity-system rrmaje/parity-ticker:0.1.0-SNAPSHOT
