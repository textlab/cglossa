#!/bin/sh

git stash
git pull
git stash pop
./build.sh
./restart_dev.sh
