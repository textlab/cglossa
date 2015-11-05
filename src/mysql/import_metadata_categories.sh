#!/usr/bin/env bash

set -e

if [ "$#" -ne 2 ] ; then
    echo "Usage: $0 CORPUS_NAME CATEGORY_FILE"
    exit
fi

tmpd="${TMPDIR:-/tmp}"
tmpfile=${tmpd}/glossa_cattmp.tsv

# Remove rows that are not actual metadata categories
cat $2 | egrep -v '^(id|startpos|endpos|bounds)\b' > $tmpfile

mysql -u root \
    -e "TRUNCATE \`metadata_categories\`;" \
    -e "LOAD DATA INFILE '`pwd`/$tmpfile' INTO TABLE \`metadata_categories\` (code, name)" \
    glossa_${1}

rm $tmpfile

echo Categories imported successfully
