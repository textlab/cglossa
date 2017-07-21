#!/bin/bash

. ${PWD%src/mysql}/config.sh
[ -z "$GLOSSA_DB_PASSWORD" ] && echo 'Warning: GLOSSA_DB_PASSWORD not set'
ruby ./adduser.rb
