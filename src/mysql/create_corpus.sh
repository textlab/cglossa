#!/usr/bin/env bash

set -e

if [ "$#" -ne 1 ] ; then
   echo "Usage: $0 CORPUS_NAME"
   exit
fi

sed s/{{corpus}}/$1/ ./create_corpus.sql | mysql -u root

echo Created corpus $1
