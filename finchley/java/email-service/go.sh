#!/usr/bin/env bash

eval $(minikube docker-env)

mvn -DskipTests=true clean package

# delete existing function 
riff delete -n emailer --all

# logical name
app=emailer

# deploy 
riff create java -a target/${app}-0.0.1-SNAPSHOT.jar --force -i $app -n $app --handler "email"

# shortcut for `curl 
riff publish -i $app -d 'hello world' -r

