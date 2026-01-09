## 1. Planning & Validation

- [x] 1.1 Review openspec/project.md context and ensure scope is limited to observability
- [x] 1.2 Validate change skeleton with `openspec validate add-actuator-prometheus-observability --strict`

## 2. Dependencies

- [x] 2.1 Add `spring-boot-starter-actuator` to pom.xml
- [x] 2.2 Add `micrometer-registry-prometheus` to pom.xml

## 3. Configuration (application.yaml)

- [x] 3.1 Expose actuator endpoints: `management.endpoints.web.exposure.include=health,info,metrics,prometheus`
- [x] 3.2 Enable health probes details: `management.endpoint.health.probes.enabled=true`
- [x] 3.3 Add stable common tags: `management.metrics.tags.application=${spring.application.name}`, include instance tag
  if desired
- [x] 3.4 Enable HTTP server metrics histogram:
  `management.metrics.distribution.percentiles-histogram.http.server.requests=true`
- [x] 3.5 Document production hardening options (restrict exposure, management.server.port, security)

## 4. HTTP Metrics Coverage

- [x] 4.1 Verify that request metrics cover POST /ask and POST /ask/stream
- [x] 4.2 Confirm metrics appear under Prometheus exposition format (`/actuator/prometheus`) after traffic
- [x] 4.3 Ensure no unbounded-cardinality tags (avoid raw prompts as tags)

## 5. Custom LLM Metrics (Micrometer)

- [x] 5.1 Decide metric names and tags (prefix `app_` to avoid collisions)
    - Timers: `app_oci_chat_latency_seconds`
    - Counters: `app_oci_chat_success_total`, `app_oci_chat_failures_total`
    - Evaluator counter: `app_evaluator_relevancy_total{outcome=yes|no}`
    - Retry counter: `app_llm_retries_total{reason=AnswerNotRelevantException}`
- [x] 5.2 Wire `MeterRegistry` into ChatService to record chat latency and outcomes
- [x] 5.3 Instrument evaluator verdicts (YES/NO) in OCIGenAIRelevancyEvaluator (or via ChatService after evaluation)
- [x] 5.4 Increment retry counter in `@Recover`/retry path when `AnswerNotRelevantException` occurs
- [x] 5.5 Add minimal docstrings/comments describing metric purpose and tag constraints

## 6. Tests

- [x] 6.1 Add Spring Boot test verifying `/actuator/health` returns 200 and `"status":"UP"`
- [x] 6.2 Add test verifying `/actuator/prometheus` returns 200 or fallback JSON; assert non-empty payload
- [x] 6.3 Add test that performs a POST `/ask` then asserts http.server.requests presence via `/actuator/metrics`
- [x] 6.4 Add test that performs a POST `/ask/stream` then asserts http.server.requests presence via `/actuator/metrics`
- [x] 6.5 Add WireMock test that exercises successful chat + evaluator YES → assert success/evaluator metrics
- [x] 6.6 Add WireMock test that triggers `AnswerNotRelevantException` → assert retry counter increments (JSON endpoint)

## 7. Documentation

- [x] 7.1 Update README with:
    - curl examples for `/actuator/health` and `/actuator/prometheus`
    - note that metrics appear after requests are served
    - production hardening recommendations (restrict endpoints, security)
- [x] 7.2 Update openspec/project.md “Important Constraints” and “External Dependencies” sections to mention actuator &
  prometheus (if needed)

## 8. Finalization

- [x] 8.1 Run `./mvnw -B clean verify`
- [x] 8.2 Re-run strict spec validation: `openspec validate add-actuator-prometheus-observability --strict`
- [x] 8.3 Ensure all tasks checked and proposal remains in sync with implementation
