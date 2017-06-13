#!/bin/sh
. ./config.sh
mkdir -p tmp
java -jar "`pwd -P`/target/cglossa.jar" >>log/prod.log 2>&1 &
