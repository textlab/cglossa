#!/usr/bin/env bash

. ${PWD%src/mysql}/config.sh

set -e

if [ -z ${GLOSSA_DB_PASSWORD+x} ]; then
    echo "Please set the GLOSSA_DB_PASSWORD environment variable before running this script," \
         "and optionally also GLOSSA_DB_USER (defaults to 'glossa')."
    exit
fi

cat ./setup.sql | \
    sed -e s/{{glossa_prefix}}/"${GLOSSA_PREFIX:-glossa}"/ \
        -e s/{{db_user}}/"${GLOSSA_DB_USER:-glossa}"/ \
        -e s/{{db_password}}/"${GLOSSA_DB_PASSWORD}"/ | \
    mysql -u "${GLOSSA_DB_ADMIN:-root}" -p
echo Setup completed
