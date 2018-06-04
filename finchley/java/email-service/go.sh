#!/usr/bin/env bash

eval $(minikube docker-env)

mvn -DskipTests=true clean package

# delete existing function 
riff delete -n emailer --all

# logical name
app=emailer

# deploy 
riff create java -a target/${app}-0.0.1-SNAPSHOT.jar -i $app -n $app --handler "email"

# shortcut for `curl 
riff publish -i $app  --content-type "application/json" -d '{ "reservationId":"5b14d1c7938f702e79e70701" }' -r

