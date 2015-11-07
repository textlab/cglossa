#!/usr/bin/env bash

set -e

if [ "$#" -ne 3 ] ; then
    echo "Usage: $0 CORPUS_NAME VALUE_FILE CATEGORY_FILE"
    exit
fi

tmpd="${TMPDIR:-/tmp}"
valfile1=${tmpd}/glossa_valtmp.tsv
valfile2=${tmpd}/metadata_values.tsv
valfile3=${tmpd}/metadata_values_texts.tsv
valfile4=${tmpd}/texts.tsv
corpus=$1
catfile=`pwd`/$3

echo Cleaning data...

# The bounds field in old Glossa uses tabs to separate positions; replace them with colons
TAB=$'\t'
cat $2 | sed "s/\\\\${TAB}/:/g" > $valfile1

echo Creating import files...

lein run -m cglossa.data-import.metadata-values/write-import-tsv $valfile1 $catfile

echo Importing values...

# Note that we cannot use mysqlimport since it does not reset the autoincrement counter to 1
# when we give it the --delete option.
# Also note that we have to create indexes *after* importing the data - otherwise they don't
# work correctly (and mysql reports them as having cardinality 2...??)
mysql -u root \
    -e "TRUNCATE \`metadata_values\`;" \
    -e "LOAD DATA INFILE '$valfile2' INTO TABLE \`metadata_values\` (\`metadata_category_id\`, \`text_value\`);" \
    -e "TRUNCATE \`metadata_values_texts\`;" \
    -e "LOAD DATA INFILE '$valfile3' INTO TABLE \`metadata_values_texts\`;" \
    -e "TRUNCATE \`texts\`;" \
    -e "LOAD DATA INFILE '$valfile4' INTO TABLE \`texts\` (\`startpos\`, \`endpos\`, \`bounds\`);" \
    -e "CREATE INDEX \`metadata_value_id\` ON \`metadata_values_texts\` (\`metadata_value_id\`);" \
    -e "CREATE INDEX \`text_id\` on \`metadata_values_texts\` (\`text_id\`);" \
    -e "CREATE INDEX \`metadata_values_texts\` on \`metadata_values_texts\` (\`metadata_value_id\`, \`text_id\`);" \
    glossa_${corpus}

rm $valfile1 $valfile2

echo Values imported successfully
