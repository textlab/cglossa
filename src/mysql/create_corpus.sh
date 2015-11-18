#!/usr/bin/env bash

set -e

if [ "$#" -ne 1 ] ; then
   echo "Usage: $0 CORPUS_NAME"
   exit
fi

cat ./create_corpus.sql | \
    sed -e s/{{corpus}}/$1/ -e s/{{glossa_prefix}}/"${GLOSSA_PREFIX:-glossa}"/ | \
    mysql -u "${GLOSSA_DB_ADMIN:-root}" -p

echo Created corpus $1
