#!/bin/sh

amount="$1"

process=sample2

curl --header "Content-Type: application/json" \
  --request POST \
  --data "{\"customerId\":\"foo_id\",\"amount\":\"$amount\"}" \
  "http://localhost:8071/$process"
