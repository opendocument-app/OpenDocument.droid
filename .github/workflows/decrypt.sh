#!/bin/sh

touch ../../app/google-services.json

gpg --quiet --batch --yes --decrypt --passphrase="$PASSPHRASE" \
--output ./app/google-services.json ./google-services.json.gpg
