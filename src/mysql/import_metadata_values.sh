#!/usr/bin/env bash

. ${PWD%src/mysql}/config.sh

position_cols="\`startpos\`, \`endpos\`"
language_col=""
while getopts ":sm" opt; do
  case $opt in
    s)
      position_cols="\`bounds\`"
      ;;
    m)
      language_col=", \`language\`"
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
  esac
done

shift $((OPTIND-1))

if [ "$#" -ne 3 ] ; then
    echo "Usage: $0 [OPTIONS] CORPUS_NAME VALUE_FILE CATEGORY_FILE"
    exit
fi

export TMPDIR="${PWD%src/mysql}/tmp"
valfile1=${TMPDIR}/glossa_valtmp.tsv
valfile2=${TMPDIR}/metadata_value.tsv
valfile3=${TMPDIR}/metadata_value_text.tsv
valfile4=${TMPDIR}/text.tsv
corpus=$1
catfile=`pwd`/$3

# We don't want to stop if this fails because it's the first time we create this corpus
# database, so we put it before the "set -e".
mysql -u "${GLOSSA_DB_ADMIN:-root}" -p  \
    -e "DROP INDEX \`metadata_value_id\` ON \`metadata_value_text\`;" \
    -e "DROP INDEX \`text_id\` on \`metadata_value_text\`;" \
    -e "DROP INDEX \`metadata_value_text\` on \`metadata_value_text\`;" \
    "${GLOSSA_PREFIX:-glossa}"_${corpus}

set -e

echo Cleaning data...

# The bounds field in old Glossa uses tabs to separate positions; replace them with colons
TAB=$'\t'
cat $2 | sed -e "s/\\\\${TAB}/:/g" -e "s/:${TAB}/${TAB}/" > $valfile1

echo Creating import files...

if [ "$ENV" = "dev" ]; then
    lein run -m cglossa.data-import.metadata-values $valfile1 $catfile
else
    java -cp cglossa.jar cglossa.data_import.metadata_values $valfile1 $catfile
fi

echo Importing values...

# Note that we cannot use mysqlimport since it does not reset the autoincrement counter to 1
# when we give it the --delete option.
# Also note that we have to create indexes *after* importing the data - otherwise they don't
# work correctly (and mysql reports them as having cardinality 2...??)
mysql -u "${GLOSSA_DB_ADMIN:-root}" -p --default-character-set=utf8 \
    -e "TRUNCATE \`metadata_value\`;" \
    -e "LOAD DATA INFILE '$valfile2' INTO TABLE \`metadata_value\` CHARACTER SET UTF8 (\`metadata_category_id\`, \`text_value\`);" \
    -e "TRUNCATE \`text\`;" \
    -e "LOAD DATA INFILE '$valfile4' INTO TABLE \`text\` (${position_cols}${language_col});" \
    -e "TRUNCATE \`metadata_value_text\`;" \
    -e "LOAD DATA INFILE '$valfile3' INTO TABLE \`metadata_value_text\`;" \
    -e "CREATE INDEX \`metadata_value_id\` ON \`metadata_value_text\` (\`metadata_value_id\`);" \
    -e "CREATE INDEX \`text_id\` on \`metadata_value_text\` (\`text_id\`);" \
    -e "CREATE INDEX \`metadata_value_text\` on \`metadata_value_text\` (\`metadata_value_id\`, \`text_id\`);" \
    -e "DELETE FROM \`metadata_value_text\` WHERE \`metadata_value_id\` IN (SELECT \`id\` FROM \`metadata_value\` AS v WHERE v.\`text_value\` = '' OR v.\`text_value\` IS NULL);" \
    -e "DELETE FROM metadata_value WHERE text_value = '' OR text_value IS NULL;" \
    "${GLOSSA_PREFIX:-glossa}"_${corpus}

rm $valfile1 $valfile2

echo Values imported successfully
