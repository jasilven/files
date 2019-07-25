#!/bin/sh
# 30s test
# 2 threads
# 10 HTTP connections open
# keep 2000 req/s throughput
echo "files list"
wrk -t2 -c10 -d30s -R2000 -s test-load-config.lua https://127.0.0.1:8080/api/files

echo "get file"
wrk -t2 -c10 -d30s -R2000 -s test-load-config.lua https://127.0.0.1:8080/api/files/5548fb85-243d-4e2f-9650-b7dab6d30fd0
