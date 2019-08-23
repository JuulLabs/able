#! /bin/bash

# Define a helper so logs show what command is being ran
function printExec { echo "\$ $@"; $@; }

CC_CMD=$(pwd)/cc-test-reporter
CC_DIR=$(pwd)/tmp-cc
mkdir $CC_DIR
$CC_CMD before-build
# Generate jacoco reports for whole project
printExec ./gradlew $GRADLE_ARGS jacocoTestReport
# For every gradle subproject...
for project in *; do
  if [ -f $project/build.gradle ]; then
    cd $project
    # ...for every report...
    for report in build/reports/jacoco/*; do
      if [ -d $report ]; then
        # ...format them for code-climate, and save to a temp dir...
        reportFile=$report/*.xml
        JACOCO_SOURCE_PATH=$(echo src/*/java)
        export JACOCO_SOURCE_PATH
        printExec $CC_CMD format-coverage -t jacoco -o $CC_DIR/codeclimate.$project.$(basename $reportFile .xml).json $reportFile
      fi
    done
    cd ../
  fi
done
# ...then aggregate and upload
printExec $CC_CMD sum-coverage $CC_DIR/codeclimate.*.json -o $CC_DIR/codeclimate.json
printExec $CC_CMD upload-coverage --id $CODE_CLIMATE_TEST_REPORTER_ID --input $CC_DIR/codeclimate.json
rm -r $CC_DIR
