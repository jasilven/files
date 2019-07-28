#!/bin/sh
# 30s test
# 2 threads
# 10 HTTP connections open
# keep 1000 req/s throughput
echo "--- files list"
wrk -t2 -c10 -d30s -R1000 -s test-load-config.lua https://127.0.0.1:8080/api/files
echo

echo "--- get file $1"
wrk -t2 -c10 -d30s -R1000 -s test-load-config.lua https://127.0.0.1:8080/api/files/$1
