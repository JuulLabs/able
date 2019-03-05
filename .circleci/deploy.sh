#!/bin/bash
#
# Deploy a jar, source jar, and javadoc jar to Sonatype's snapshot repo.
#
# Adapted from https://raw.githubusercontent.com/JakeWharton/retrofit2-kotlinx-serialization-converter/670dc5002a61726afe8feaef982c92f451495bee/.buildscript/deploy_snapshot.sh

REPOSITORY_URL="git@github.com:JuulLabs-OSS/able.git"
BRANCH="develop"

set -e

if [ "$CIRCLE_REPOSITORY_URL" != "$REPOSITORY_URL" ]; then
    echo "Skipping snapshot deployment: wrong repository. Expected '$REPOSITORY_URL' but was '$CIRCLE_REPOSITORY_URL'."
elif [ ! -z "$CIRCLE_PULL_REQUEST" ]; then
    echo "Skipping snapshot deployment: was pull request."
elif [ "$CIRCLE_BRANCH" != "$BRANCH" ]; then
    echo "Skipping snapshot deployment: wrong branch. Expected '$BRANCH' but was '$CIRCLE_BRANCH'."
else
    echo "Deploying snapshot..."
    ./gradlew uploadArchives \
        -Psigning.keyId="$SIGNING_KEY_ID" \
        -Psigning.password="$SIGNING_PASSWORD" \
        -Psigning.secretKeyRingFile="$HOME/.gnupg/secring.gpg"
    echo "Snapshot deployed!"
fi
