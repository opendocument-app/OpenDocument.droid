#!/bin/sh

gpg --quiet --batch --yes --decrypt --passphrase="$PASSPHRASE" \
--output ./app/google-services.json google-services.json.gpg
