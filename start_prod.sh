#!/bin/sh
. ./config.sh
java -jar "`pwd -P`/target/cglossa.jar" >>log/prod.log 2>&1 &
