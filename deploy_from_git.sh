#!/bin/sh
set -ve
git stash
git pull
git stash pop
./build.sh
./restart_prod.sh
