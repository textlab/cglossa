#!/bin/sh

pkill -f "java -jar `pwd -P`/target/cglossa.jar"
# Give the processes time to shut down
sleep 3
./start_prod.sh
