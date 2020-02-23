# rsocket proto gradle groovy DSL [![CI](https://github.com/daggerok/rsocket-proto/workflows/CI/badge.svg)](https://github.com/daggerok/rsocket-proto/actions)
TODO: implement me...

```bash
./gradlew -p server genProto
./gradlew -p client generateProto

./gradlew -p server
./gradlew -p client

java -jar server/build/libs/*.jar &
npx wait-port 7070
java -jar client/build/libs/*.jar
```

## resources

* https://github.com/netifi/rsocket-rpc-example/tree/master/service
* https://github.com/netifi/rsocket-rpc-example/tree/master/client/src/main/java/io/netifi/rsocket/example/client
* https://github.com/b3rnoulli/rsocket-examples/tree/d1d6e4c4c0d280cf931cce4adcf0b56ffa2e88ed
* https://dzone.com/articles/reactive-service-to-service-communication-with-rso-3
* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/2.2.4.RELEASE/gradle-plugin/reference/html/)
* [Spring Configuration Processor](https://docs.spring.io/spring-boot/docs/2.2.4.RELEASE/reference/htmlsingle/#configuration-metadata-annotation-processor)
* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)
