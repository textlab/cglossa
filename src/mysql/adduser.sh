#!/bin/bash

[ -z "$GLOSSA_DB_PASSWORD" ] && echo 'Warning: GLOSSA_DB_PASSWORD not set'
echo -n 'E-mail: '; read mail
echo -n 'Full name: '; read displayName
echo -n 'Password: '; stty -echo; read password; stty echo
echo
lein exec -p "${0%%.sh}.clj" "$mail" "$displayName" "$password"
