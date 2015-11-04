#!/usr/bin/env bash

if [ "$#" -lt 3 ] ; then
    echo "Usage: $0 [-s LINES_TO_SKIP] CORPUS_NAME CATEGORY_FILE"
    exit
fi

skip_lines=0
if getopts s: o ; then
    skip_lines=$OPTARG
fi

shift $((OPTIND - 1))

mysql -u root \
    -e "TRUNCATE \`metadata_categories\`;" \
    -e "LOAD DATA INFILE '`pwd`/$2' INTO TABLE \`metadata_categories\` IGNORE $skip_lines LINES (code, name)" \
    glossa_${1}
