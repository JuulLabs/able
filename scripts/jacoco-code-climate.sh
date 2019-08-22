#! /bin/bash

CC_CMD=$(pwd)/cc-test-reporter
CC_DIR=$(pwd)/tmp-cc
mkdir $CC_DIR
$CC_CMD before-build
# For every gradle subproject...
for project in *; do
  if [ -f $project/build.gradle ]; then
    cd $project
    # ...generate jacoco reports...
    ../gradlew $GRADLE_ARGS jacocoTestReport
    for report in build/reports/jacoco/*; do
      if [ -d $report ]; then
        # ...format them for code-climate, and save to a temp dir...
        reportFile=$report/*.xml
        JACOCO_SOURCE_PATH=$(echo src/*/java)
        export JACOCO_SOURCE_PATH
        $CC_CMD format-coverage -t jacoco -o $CC_DIR/codeclimate.$project.$(basename $reportFile .xml).json $reportFile
      fi
    done
    cd ../
  fi
done
# ...then aggregate and upload
$CC_CMD sum-coverage $CC_DIR/codeclimate.*.json -o $CC_DIR/codeclimate.json
$CC_CMD upload-coverage --id $CODE_CLIMATE_TEST_REPORTER_ID --input $CC_DIR/codeclimate.json
rm -r $CC_DIR
