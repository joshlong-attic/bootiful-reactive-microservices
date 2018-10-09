#!/usr/bin/env bash

source common.sh || source scripts/common.sh || echo "No common.sh script found..."

set -o errexit
set -o errtrace
set -o pipefail

echo -e "This script will run tests for all release trains"

./scenario_finchley_tester.sh || ./scripts/scenario_finchley_tester.sh
./scenario_finchley_greenwich.sh || ./scripts/scenario_greenwich_tester.sh

echo -e "The tests passed successfully!"

cd $ROOT_FOLDER
