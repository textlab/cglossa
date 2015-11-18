#!/usr/bin/env bash

set -e

mysql -u "${DB_ADMIN:-root}" -p < ./setup.sql
echo Setup completed