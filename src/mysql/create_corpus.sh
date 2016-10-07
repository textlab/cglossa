#!/usr/bin/env bash

set -e

is_speech=false

while getopts ":s" opt; do
  case $opt in
    s)
      is_speech=true
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
  esac
done

shift $((OPTIND-1))
if [ "$#" -ne 1 ] ; then
   echo "Usage: $0 [OPTIONS] CORPUS_NAME"
   exit
fi

prefix="${GLOSSA_PREFIX:-glossa}"
admin="${GLOSSA_DB_ADMIN:-root}"

cat ./create_corpus.sql | \
    sed -e s/{{corpus}}/$1/ \
        -e s/{{glossa_prefix}}/${prefix}/ \
        -e s/{{db_user}}/"${GLOSSA_DB_USER:-glossa}"/ | \
    mysql -u ${admin} -p

if ${is_speech} ; then
  mysql -u ${admin} -p \
    -e "USE ${prefix}_$1;" \
    -e "CREATE TABLE IF NOT EXISTS \`media_file\` (\`line_key_begin\` bigint DEFAULT NULL, \`line_key_end\` bigint DEFAULT NULL, \`basename\` VARCHAR(255)) ENGINE=InnoDB;"
fi

echo Created corpus $1
