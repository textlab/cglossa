#!/usr/bin/env bash

set -e

if [ "$#" -ne 2 ] ; then
    echo "Usage: $0 CORPUS_NAME CATEGORY_FILE"
    exit
fi

tmpd="${TMPDIR:-/tmp}"
tmpfile=${tmpd}/glossa_cattmp.tsv
corpus=$1

# Remove rows that are not actual metadata categories
cat $2 | egrep -v '^(id|startpos|endpos|bounds)\b' > $tmpfile

# Note: We cannot use mysqlimport since it does not reset the autoincrement counter to 1
# when we give it the --delete option
mysql -u "${DB_ADMIN:-root}" -p  \
    -e "TRUNCATE \`metadata_category\`;" \
    -e "LOAD DATA INFILE '$tmpfile' INTO TABLE \`metadata_category\` (code, name)" \
    glossa_${corpus}

rm $tmpfile

echo Categories imported successfully
