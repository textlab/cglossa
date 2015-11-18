#!/usr/bin/env bash

set -e

cat ./setup.sql | sed -e s/{{glossa_prefix}}/"${GLOSSA_PREFIX:-glossa}"/  | \
    mysql -u "${GLOSSA_DB_ADMIN:-root}" -p
echo Setup completed