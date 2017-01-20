#!/bin/sh

. ./config.sh
lein run >>log/dev.log &
rlwrap lein figwheel
pkill -f " -Dleiningen.original.pwd=`pwd` .* run$"
