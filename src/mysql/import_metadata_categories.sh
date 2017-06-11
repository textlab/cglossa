#!/usr/bin/env bash

. ${PWD%src/mysql}/config.sh

set -e

if [ "$#" -ne 2 ] ; then
    echo "Usage: $0 CORPUS_NAME CATEGORY_FILE"
    exit
fi

export TMPDIR="${PWD%src/mysql}/tmp"
tmpfile=${TMPDIR}/glossa_cattmp.tsv
corpus=$1

# Remove rows that are not actual metadata categories
cat $2 | egrep -v '^(id|startpos|endpos|bounds|language)\b' > $tmpfile

# Note: We cannot use mysqlimport since it does not reset the autoincrement counter to 1
# when we give it the --delete option
mysql -u "${GLOSSA_DB_ADMIN:-root}" -p  \
    -e "TRUNCATE \`metadata_category\`;" \
    -e "LOAD DATA INFILE '$tmpfile' INTO TABLE \`metadata_category\` (code, name)" \
    "${GLOSSA_PREFIX:-glossa}"_${corpus}

rm $tmpfile

echo Categories imported successfully
