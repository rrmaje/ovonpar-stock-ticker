#!/bin/bash

docker run --rm --network="tickernet" --name ticker -p9000:9000 parity-ticker:0.1.0-SNAPSHOT
