#!/usr/bin/env bash

. ${PWD%src/mysql}/config.sh

set -e

if [ "$#" -ne 1 ] ; then
   echo "Usage: $0 CORPUS_NAME"
   exit
fi

prefix="${GLOSSA_PREFIX:-glossa}"
admin="${GLOSSA_DB_ADMIN:-root}"

cat ./create_corpus.sql | \
    sed -e s/{{corpus}}/$1/ \
        -e s/{{glossa_prefix}}/${prefix}/ \
        -e s/{{db_user}}/"${GLOSSA_DB_USER:-glossa}"/ | \
    mysql -u ${admin} -p

echo Created corpus $1
