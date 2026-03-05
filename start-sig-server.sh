#!/bin/bash
# Start the Node sig-server for login and order signatures.
# Run in one terminal; run mvn test in another.
cd "$(dirname "$0")/sig-server" && node signatures/server.js
