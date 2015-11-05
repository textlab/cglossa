#!/usr/bin/env bash

set -e

if [ "$#" -ne 3 ] ; then
    echo "Usage: $0 CORPUS_NAME VALUE_FILE CATEGORY_FILE"
    exit
fi

tmpd="${TMPDIR:-/tmp}"
valfile1=${tmpd}/glossa_valtmp.tsv
valfile2=${tmpd}/metadata_values.tsv
corpus=$1
catfile=`pwd`/$3

echo Cleaning data...

# The bounds field in old Glossa uses tabs to separate positions; replace them with colons
TAB=$'\t'
cat $2 | sed "s/\\\\${TAB}/:/g" > $valfile1

echo Creating import files...

lein run -m cglossa.data-import.metadata-values/write-import-tsv $valfile1 $catfile

echo Importing values...

# Note: We cannot use mysqlimport since it does not reset the autoincrement counter to 1
# when we give it the --delete option
mysql -u root \
    -e "TRUNCATE \`metadata_values\`;" \
    -e "LOAD DATA INFILE '$valfile2' INTO TABLE \`metadata_values\` (metadata_category_id, text_value)" \
    glossa_${corpus}

rm $valfile1 $valfile2

echo Values imported successfully
