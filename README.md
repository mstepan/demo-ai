# Spring Boot AI + Oracle OCI Gen AI + Oracle 23ai DB

[Initilizer](https://start.spring.io/#!type=maven-project&language=java&platformVersion=3.5.9&packaging=jar&configurationFileFormat=yaml&jvmVersion=25&groupId=com.github.mstepan&artifactId=demo-ai&name=demo-ai&description=Demo%20AI%20Spring%20Boot%20Project%20%2B%20OCI%20and%20Oracle%2023ai%20DB&packageName=com.github.mstepan.demo-ai&dependencies=web,spring-ai-oci-genai,spring-ai-vectordb-oracle,validation)

## Building and Running

```bash
./mvnw clean package
```

```bash
./mvnw spring-boot:run
```

## Testing

* Execute unit and integration tests

```bash
./mvnw test
```

* Execute all test including exhaustive

Don't forget to obtain a valid OCI SESSION TOKEN for exhaustive tests

```bash
oci session authenticate --region us-chicago-1 --profile-name bmc_operator_access
```

```bash
./mvnw test -Pexhaustive
```

## Logo

Logo generated using [patorjk](https://patorjk.com/software/taag) and `Standard` style.

## Execute

### Simple request-reply

```bash
curl -H "Content-Type: application/json" \
  -X POST \
  --data '{"question":"Write fairy tail about pirates. No more than 10 sentences."}' \
  http://localhost:7171/ask

```

### Streaming

```bash
curl -N -H "Accept: text/event-stream" -H "Content-Type: application/json" -X POST --data '{"question":"Write fairy tail about pirates. At least 100 sentences.","stream":true}' http://localhost:7171/ask/stream

```

## References

* [Prompting Guide](https://www.promptingguide.ai/)
