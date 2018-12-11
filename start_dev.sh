#!/bin/sh

. ./config.sh
export BOOT_JVM_OPTIONS="-XX:-OmitStackTraceInFastThrow"

mkdir -p tmp
lein run >>log/dev.log &
rlwrap lein figwheel
pkill -f " -Dleiningen.original.pwd=`pwd` .* run$"
