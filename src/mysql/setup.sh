#!/usr/bin/env bash

set -e

mysql -u root < ./setup.sql
echo Setup completed