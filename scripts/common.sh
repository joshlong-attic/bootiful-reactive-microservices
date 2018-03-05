#!/usr/bin/env bash

set -e

WAIT_TIME="${WAIT_TIME:-5}"
RETRIES="${RETRIES:-30}"
SERVICE_PORT="${SERVICE_PORT:-8081}"
TEST_ENDPOINT="${TEST_ENDPOINT:-check}"
JAVA_PATH_TO_BIN="${JAVA_HOME}/bin/"
if [[ -z "${JAVA_HOME}" ]] ; then
    JAVA_PATH_TO_BIN=""
fi
BUILD_FOLDER="${BUILD_FOLDER:-target}" #target - maven, build - gradle
PRESENCE_CHECK_URL="${PRESENCE_CHECK_URL:-http://localhost:8761/eureka/apps}"
TEST_PATH="${TEST_PATH:-reservations/names}"
HEALTH_HOST="${DEFAULT_HEALTH_HOST:-localhost}" #provide DEFAULT_HEALT HOST as host of your docker machine
export RABBIT_MQ_PORT="${RABBIT_MQ_PORT:-9672}"
export KAFKA_PORT=7092
export ZK_PORT=4181
export MONGO_DB_PORT=29017
SYSTEM_PROPS="-Dspring.rabbitmq.host=${HEALTH_HOST} -Dspring.rabbitmq.port=${RABBIT_MQ_PORT} -Dendpoints.default.web.enabled=true -Dspring.kafka.bootstrapServers=localhost:${KAFKA_PORT} -Dspring.data.mongodb.port=${MONGO_DB_PORT}"
CLI_BOOT_VERSION="${CLI_BOOT_VERSION:-2.0.0.RELEASE}"
CLI_VERSION="${CLI_VERSION:-1.3.2.RELEASE}"

# ${RETRIES} number of times will try to curl to /actuator/health endpoint to passed port $1 and localhost
function wait_for_app_to_boot_on_port() {
    curl_health_endpoint $1 "127.0.0.1" "actuator/health"
}

# ${RETRIES} number of times will try to curl to /health endpoint to passed port $1 and localhost
function wait_for_legacy_app_to_boot_on_port() {
    curl_health_endpoint $1 "127.0.0.1" "health"
}

# ${RETRIES} number of times will try to curl to /health endpoint to passed port $1 and host $2
function curl_health_endpoint() {
    local PASSED_HOST="${2:-$HEALTH_HOST}"
    local HEALTH_PATH="${3:-actuator/health}"
    local READY_FOR_TESTS=1
    for i in $( seq 1 "${RETRIES}" ); do
        sleep "${WAIT_TIME}"
        curl -sSf -m 5 "${PASSED_HOST}:$1/${HEALTH_PATH}" && READY_FOR_TESTS=0 && break
        echo "Fail #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds"
    done
    if [[ "${READY_FOR_TESTS}" == "1" ]] ; then
        echo -e "\n\nThe app failed to start :( Printing all logs\n\n"
        print_all_logs
    fi
    return ${READY_FOR_TESTS}
}

# Check the app $1 (in capital)
function check_app_presence_in_discovery() {
    echo -e "\n\nChecking for the presence of $1 in Service Discovery for [$(( WAIT_TIME * RETRIES ))] seconds"
    READY_FOR_TESTS="no"
    for i in $( seq 1 "${RETRIES}" ); do
        sleep "${WAIT_TIME}"
        curl -sS -m 5 ${PRESENCE_CHECK_URL} | grep $1 && READY_FOR_TESTS="yes" && break
        echo "Fail #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds"
    done
    if [[ "${READY_FOR_TESTS}" == "yes" ]] ; then
        return 0
    else
        return 1
    fi
}

# Runs the `java -jar` for given application $1 and system properties $2
function java_jar() {
    echo -e "\n\nStarting app $1 \n"
    local APP_JAVA_PATH=$1/${BUILD_FOLDER}
    local EXPRESSION="nohup ${JAVA_PATH_TO_BIN}java $2 $SYSTEM_PROPS -jar $APP_JAVA_PATH/*.jar >$APP_JAVA_PATH/nohup.log &"
    echo -e "\nTrying to run [$EXPRESSION]"
    eval ${EXPRESSION}
    pid=$!
    echo ${pid} > ${APP_JAVA_PATH}/app.pid
    echo -e "[$1] process pid is [$pid]"
    echo -e "System props are [$2]"
    echo -e "Logs are under [$APP_JAVA_PATH/nohup.log]\n"
    return 0
}

# Runs the `java -jar` for given application $1 and system properties $2
function java_jar_zipkin() {
    echo -e "\n\nStarting Zipkin\n"
    local APP_JAVA_PATH="zipkin-service/${BUILD_FOLDER}"
    pushd "${APP_JAVA_PATH}"
    local EXPRESSION="KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:${KAFKA_PORT} nohup \
    ${JAVA_PATH_TO_BIN}java \
    -Dloader.path='kafka10.jar,kafka10.jar!/lib' \
    -Dspring.profiles.active=kafka \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher >nohup.log &"
    echo -e "\nTrying to run [$EXPRESSION]"
    eval ${EXPRESSION}
    pid=$!
    echo ${pid} > app.pid
    echo -e "[zipkin-service] process pid is [$pid]"
    echo -e "Logs are under [$APP_JAVA_PATH/nohup.log]\n"
    popd
    return 0
}

# Kills an app with given $1 version
function kill_app() {
    echo -e "Killing app $1"
    pkill -f "$1" && echo "Killed $1" || echo "$1 was not running"
    echo -e ""
    return 0
}

# Runs H2 from proper folder
function build_all_apps() {
    ${ROOT_FOLDER}/scripts/build_all.sh
}

# Kill all the apps
function kill_all_apps() {
    ${ROOT_FOLDER}/scripts/kill_all.sh
}

# Calls a POST curl to /person to an app on localhost with port $1 on path $2
function send_test_request() {
    local token=${TOKEN:-token}
    READY_FOR_TESTS="no"
    for i in $( seq 1 "${RETRIES}" ); do
        sleep "${WAIT_TIME}"
        echo -e "Sending a GET to 127.0.0.1:$1/$2 . This is the response:\n"
        curl -sS --fail "127.0.0.1:${1}/${2}"  -H "Authorization: Bearer $token" && READY_FOR_TESTS="yes" && break
        echo "Fail #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds"
    done
    if [[ "${READY_FOR_TESTS}" == "yes" ]] ; then
        return 0
    else
        return 1
    fi
}

# Calls a GET to zipkin to dependencies
function check_trace() {
    echo -e "\nChecking if Zipkin has stored the trace"
    local STRING_TO_FIND="\"parent\":\"reservation-client\",\"child\":\"reservation-service\""
    local CURRENT_TIME=`python -c 'import time; print int(round(time.time() * 1000))'`
    local URL_TO_CALL="http://localhost:9411/api/v1/dependencies?endTs=$CURRENT_TIME"
    READY_FOR_TESTS="no"
    for i in $( seq 1 "${RETRIES}" ); do
        sleep "${WAIT_TIME}"
        echo -e "Sending a GET to $URL_TO_CALL . This is the response:\n"
        curl -sS --fail "$URL_TO_CALL" | grep ${STRING_TO_FIND} &&  READY_FOR_TESTS="yes" && break
        echo "Fail #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds"
    done
    if [[ "${READY_FOR_TESTS}" == "yes" ]] ; then
        echo -e "\n\nSuccess! Zipkin is working fine!"
        return 0
    else
        echo -e "\n\nFailure...! Zipkin failed to store the trace!"
        return 1
    fi
}

function check_span_names_for_service() {
    local EXPECTED_ENTRIES=$1
    local PARSED_EXPECTED_ENTRIES
    local RECEIVED_RESPONSE=$2
    local SERVICE_NAME=$3
    local READY_FOR_TESTS="no"

    echo -e "\nChecking if Sleuth has properly stored names of spans [$EXPECTED_ENTRIES] for service [$SERVICE_NAME]"
    echo -e "\nThe response is $RECEIVED_RESPONSE"

    # Split var by ',' and put into array
    IFS=',' read -ra PARSED_EXPECTED_ENTRIES <<< "$EXPECTED_ENTRIES"

    for i in ${PARSED_EXPECTED_ENTRIES[@]}
    do
         echo ${RECEIVED_RESPONSE} | grep "$i" && echo "The array contains $i" && READY_FOR_TESTS="yes"
    done

    if [[ "${READY_FOR_TESTS}" == "yes" ]] ; then
        echo -e "\n\n$SERVICE_NAME contains proper span names!"
        return 0
    fi
    echo -e "\n\nFailure...! $SERVICE_NAME does not contain proper span names!"
    return 1
}

# Downloads the latest version of Zipkin to build/zipkin.jar
function download_zipkin() {
    mkdir zipkin-service || echo "zipkin-service already created"
    pushd zipkin-service
    mkdir target || echo "target already exists"
    cd target
    curl -sSL https://zipkin.io/quickstart.sh | bash -s
    curl -sSL https://zipkin.io/quickstart.sh | bash -s io.zipkin.java:zipkin-autoconfigure-collector-kafka10:LATEST:module kafka10.jar
    popd
}

# Calls a GET to zipkin to check span names for services
function check_span_names() {
    local RESERVATION_CLIENT_URL_TO_CALL="http://localhost:9411/api/v1/spans?serviceName=reservation-client"
    local RESERVATION_SERVICE_URL_TO_CALL="http://localhost:9411/api/v1/spans?serviceName=reservation-service"
    local EXPECTED_RESERVATION_CLIENT_ENTRIES=("http:/reservations","http:/reservations/names","names")
    local EXPECTED_RESERVATION_SERVICE_ENTRIES=("get-collection-resource","http:/reservations")

    local RESERVATION_CLIENT_RESPONSE=`curl -sS --fail "$RESERVATION_CLIENT_URL_TO_CALL"`
    local RESERVATION_SERVICE_RESPONSE=`curl -sS --fail "$RESERVATION_SERVICE_URL_TO_CALL"`

    check_span_names_for_service ${EXPECTED_RESERVATION_CLIENT_ENTRIES} ${RESERVATION_CLIENT_RESPONSE} "Reservation Client"
    check_span_names_for_service ${EXPECTED_RESERVATION_SERVICE_ENTRIES} ${RESERVATION_SERVICE_RESPONSE} "Reservation Service"
}

function get_token() {
    local SYSTEM=`uname -s`
    local VERSION='linux64'
    if [[ "${SYSTEM}" == "Darwin" ]] ; then
        VERSION='osx-amd64'
    fi
    curl -L -o /tmp/jq "https://github.com/stedolan/jq/releases/download/jq-1.5/jq-$VERSION"
    chmod +x /tmp/jq
    local token=`curl 'localhost:9191/uaa/oauth/token?username=jlong&password=spring&grant_type=password' --user acme:acmesecret -X POST --data '{ "client_secret" : "acmesecret", "client_id" : "acme", "scope" : "openid", "granttype" : "password", "username" : "jlong", "password" : "spring" }' | /tmp/jq -r .access_token`
    export TOKEN="$token"
    echo "The token is equal to $TOKEN"
}

function print_all_logs() {
    print_logs config-service
    print_logs eureka-service
    print_logs reservation-client
    print_logs reservation-service
    print_logs zipkin-service
}

function print_logs() {
    local app_name=$1
    echo -e "\n\nPrinting [${app_name}] logs"
    cat ${app_name}/${BUILD_FOLDER}/nohup.log || echo "Failed to print the logs"
}

function kill_all() {
    echo -e "I'm in folder `pwd`"
    echo -e "Killing all apps\n"
    kill_app config-service
    kill_app eureka-service
    kill_app reservation-client
    kill_app reservation-service
    kill_app zipkin-service
    kill_app kafka
    docker-compose kill && echo "Killed rabbit" || echo "Failed to kill Rabbit"
}

function setup_infra() {
    cd ${ROOT_FOLDER}
    echo "Starting docker-compose"
    docker-compose up -d
    start_kafka
}

export CLI_PATH

function run_kafka() {
    local APP_JAVA_PATH="${ROOT_FOLDER}/${BUILD_FOLDER}/"
    mkdir -p ${APP_JAVA_PATH} || echo "Folder already created"
    local EXPRESSION="nohup ${CLI_PATH}spring cloud kafka >$APP_JAVA_PATH/kafka.log &"
    echo -e "\nTrying to run [$EXPRESSION]"
    eval ${EXPRESSION}
    pid=$!
    echo ${pid} > ${APP_JAVA_PATH}/app.pid
    echo -e "[kafka] process pid is [$pid]"
    echo -e "Logs are under [target/kafka.log]\n"
    return 0
}

function start_kafka() {
    echo "Will run Kafka on port [${KAFKA_PORT}]"
    echo -e "\nCheck if sdkman is installed"
    SDK_INSTALLED="no"
    [[ -s "${HOME}/.sdkman/bin/sdkman-init.sh" ]] && yes | source "${HOME}/.sdkman/bin/sdkman-init.sh"
    sdk version && SDK_INSTALLED="true" || echo "Failed to execute SDKman"
    CLI_PATH="${HOME}/.sdkman/candidates/springboot/${CLI_BOOT_VERSION}/bin/"
    if [[ "${SDK_INSTALLED}" == "no" ]] ; then
      echo "Installing SDKman"
      curl -s "https://get.sdkman.io" | bash
      source "${HOME}/.sdkman/bin/sdkman-init.sh"
    fi
    echo -e "\nInstalling spring boot [${CLI_BOOT_VERSION}] and spring cloud [${CLI_VERSION}] plugins"
    yes | sdk use springboot "${CLI_BOOT_VERSION}"
    echo "Path to Spring CLI [${CLI_PATH}]"
    yes | "${CLI_PATH}"spring install org.springframework.cloud:spring-cloud-cli:${CLI_VERSION}
    echo -e "\nPrinting versions"
    ${CLI_PATH}spring version
    ${CLI_PATH}spring cloud --version

    echo -e "\nRunning Kafka"
    run_kafka
    echo "Waiting for Kafka to boot for [10] seconds"
    sleep 10
}

export WAIT_TIME
export RETIRES
export SERVICE_PORT

export -f wait_for_app_to_boot_on_port
export -f curl_health_endpoint
export -f java_jar
export -f build_all_apps
export -f kill_app
export -f kill_all_apps
export -f check_app_presence_in_discovery
export -f send_test_request
export -f download_zipkin
export -f check_span_names
export -f check_span_names_for_service

ROOT_FOLDER=`pwd`
if [[ ! -e "${ROOT_FOLDER}/.git" ]]; then
    cd ..
    ROOT_FOLDER=`pwd`
    if [[ ! -e "${ROOT_FOLDER}/.git" ]]; then
        echo "No .git folder found"
        exit 1
    fi
fi

mkdir -p "${ROOT_FOLDER}/${BUILD_FOLDER}"
