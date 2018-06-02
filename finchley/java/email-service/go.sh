mvn -DskipTests=true clean package

riff delete -n emailer --all

riff create java -a target/emailer-0.0.1-SNAPSHOT.jar -i emailer -n emailer --handler "email&main=com.example.emailer.EmailerApplication"

riff publish -i emailer -d '{"reservationId":"22" }' -r --content-type "application/json"  
