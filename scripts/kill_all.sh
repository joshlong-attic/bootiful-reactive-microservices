#!/usr/bin/env bash

source common.sh || source scripts/common.sh || echo "No common.sh script found..."

set -o errexit
set -o errtrace
set -o pipefail

kill_all
