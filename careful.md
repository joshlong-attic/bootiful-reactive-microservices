* make sure the versions are M5 && Finchley.BUILD-SNAPSHOT
* make sure `spring-boot-starter-web` is provided
* make sure to add `spring-cloud-stream-reactive`  (both!) and `spring-cloud-starter-gateway` (`reservation-client`)
* make sure the config server refresh is: `curl -Hcontent-type: application/json -XPOST http://localhost:8000/application/refresh`
* add `DiscoveryClientRouteDefinitionLocator(dc)`
* add `fun routeRouteLocator():RouteLocator  = Routers.*`


* Look into Resilience4J
