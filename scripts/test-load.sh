#!/bin/sh
# 30s test
# 2 threads
# 10 HTTP connections open
# keep 2000 req/s throughput
wrk -t2 -c10 -d30s -R2000 -s auth.lua https://127.0.0.1:8080/api/files
