#!/usr/bin/env bash

source common.sh || source scripts/common.sh || echo "No common.sh script found..."

set -o errexit
set -o errtrace
set -o pipefail

export BOM_VERSION="Finchley.BUILD-SNAPSHOT"
export PROFILE="finchley"
export EXTERNAL_IP
EXTERNAL_IP="$( "${ROOT_FOLDER}"/scripts/whats_my_ip.sh )"

echo "External IP equals [${EXTERNAL_IP}]"

echo "Ensure that apps are not running"
kill_all_apps

echo -e "Ensure that all the apps are built with $BOM_VERSION!\n"
build_all_apps

cat <<EOF

This Bash file will run all the apps required for Edgware tests.

NOTE:

- you need internet connection for the apps to download configuration from Github.
- you need docker-compose for RabbitMQ to start
- you need python to calculate current millis

We will do it in the following way:

01) Run config-server
02) Wait for the app (config-server) to boot (port: 8888)
03) Run eureka-service
04) Wait for the app (eureka-service) to boot (port: 8761)
05) Run reservation-client
06) Wait for the app (reservation-client) to boot (port: 9999)
07) Wait for the app (reservation-client) to register in Eureka Server
08) Run reservation-service
09) Wait for the app (reservation-service) to boot (port: 8000)
10) Wait for the app (reservation-service) to register in Eureka Server
11) Run zipkin-service
12) Wait for the app (zipkin-server) to boot (port: 9411)
13) Send a test request to populate some entries in Zipkin
14) Check if Zipkin has stored the trace for the aforementioned request

EOF

# Runs tests for a given language (java / kotlin)
function run_tests() {
    setup_infra
    cd ${ROOT_FOLDER}/${PROFILE}
    echo "Running tests for ${1}"
    pushd "${ROOT_FOLDER}/${PROFILE}/${1}"
    java_jar config-service
    wait_for_app_to_boot_on_port 8888

    java_jar eureka-service
    wait_for_app_to_boot_on_port 8761

    java_jar reservation-client
    wait_for_app_to_boot_on_port 9999
    check_app_presence_in_discovery RESERVATION-CLIENT

    java_jar reservation-service
    wait_for_app_to_boot_on_port 8000
    check_app_presence_in_discovery RESERVATION-SERVICE

    download_zipkin
    java_jar_zipkin
    wait_for_legacy_app_to_boot_on_port 9411

    send_test_request 9999 "reservations/names/"
    echo -e "\n\nThe ${BOM_VERSION} Reservation client successfully responded to the call"

    check_trace
    check_span_names
    popd
}


cd ${ROOT_FOLDER}

run_tests "java"
kill_all
run_tests "kotlin"
echo "Leaving apps running to click around"
