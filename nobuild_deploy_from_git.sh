#!/bin/sh

# Like deploy_from_git.sh, but does not rebuild the jar
set -ve
git stash
git pull
git stash pop
./restart_prod.sh
