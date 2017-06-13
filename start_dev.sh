#!/bin/sh

. ./config.sh
mkdir -p tmp
lein run >>log/dev.log &
rlwrap lein figwheel
pkill -f " -Dleiningen.original.pwd=`pwd` .* run$"
