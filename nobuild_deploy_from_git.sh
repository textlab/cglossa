#!/bin/sh

# Like deploy_from_git.sh, but does not rebuild the jar

git stash
git pull
git stash pop
./restart_prod.sh
