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
  http://localhost:7171/ask | jq

```

### Streaming

```bash 
curl -N -H "Accept: application/x-ndjson" \
  -H "Content-Type: application/json" \
  -X POST --data '{"question":"Write a HUGE fairy tail about pirates. At least 250 sentences."}' \
  http://localhost:7171/ask/stream

```

## Observability

Health (returns {"status":"UP"}):
```bash
curl -s http://localhost:7171/actuator/health | jq
```

Info (application metadata):
```bash
curl -s http://localhost:7171/actuator/info | jq
```

Prometheus scrape endpoint (text exposition format):
```bash
curl -s http://localhost:7171/actuator/prometheus | head -n 40
```

JSON metrics (stable across environments), list a specific meter:
```bash
curl -s "http://localhost:7171/actuator/metrics/http.server.requests" | jq
```

Notes:
- Metrics appear only after traffic has been served. Generate requests (e.g., POST /ask and POST /ask/stream) before scraping.
- Metric tags are bounded for cardinality; avoid sending sensitive data to metrics.
- Production hardening recommendations:
  - Restrict exposed actuator endpoints via management.endpoints.web.exposure.include
  - Optionally bind management server to a dedicated port (management.server.port) and restrict access
  - Apply network policies or authentication/authorization per environment policy
  - Disable verbose/exporters not needed for production

## References

* [Prompting Guide](https://www.promptingguide.ai/)
