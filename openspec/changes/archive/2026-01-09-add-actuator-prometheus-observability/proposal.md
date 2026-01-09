# Change: Add Actuator Observability and Prometheus Metrics Endpoint

## Why
Operational visibility is currently limited. We need:
- Health/info endpoints for quick diagnostics
- HTTP server metrics for REST endpoints
- Scrapeable Prometheus metrics for monitoring/alerting
- Basic LLM-specific metrics (timing, success/failure, retries) for understanding model interactions and guardrail behavior

## What Changes
- Add Spring Boot Actuator and Micrometer Prometheus registry
- Expose health, info, metrics, and prometheus endpoints over HTTP
- Enable HTTP server request metrics for all REST endpoints
- Define common metric tags (application, instance) via configuration
- Add LLM call instrumentation (timers, counters) for:
  - chat call latency/success/failure
  - evaluator verdicts (relevant/not relevant)
  - retry occurrences due to AnswerNotRelevantException
- Document new observability capability in OpenSpec (requirements + scenarios)
- Provide minimal configuration for local development (all endpoints exposed) with notes for production hardening

## Impact
- Build: add dependencies (spring-boot-starter-actuator, micrometer-registry-prometheus)
- Configuration: management endpoints exposure and metric tags in application.yaml
- Code (follow-up implementation after approval): optional meter bindings in service/adapter layers for LLM interactions and evaluator outcomes
- Tests: integration tests to verify endpoint exposure and the presence of key meters (http_server_requests, custom LLM metrics)
