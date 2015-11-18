#!/usr/bin/env bash

set -e

mysql -u "${GLOSSA_DB_ADMIN:-root}" -p < ./setup.sql
echo Setup completed