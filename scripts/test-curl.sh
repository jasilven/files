curl -i --insecure -X POST -H "Content-type: multipart/form-data; boundary=1234567890" -F "file=@testfile.pdf;type=application/pdf" http://localhost:8080/api/files
