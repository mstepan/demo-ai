# Design: Actuator Observability and Prometheus Metrics

## Context
The service exposes two REST endpoints (`POST /ask`, `POST /ask/stream`) that call OCI Generative AI via a custom ChatModel. Operational visibility is minimal. The goal is to add standardized health/info/metrics endpoints and Prometheus scraping, and to introduce lightweight, bounded-cardinality metrics for LLM interactions and evaluator outcomes.

## Goals / Non-Goals
- Goals:
  - Expose Spring Boot Actuator endpoints (`health`, `info`, `metrics`, `prometheus`)
  - Enable HTTP server request metrics for all REST endpoints
  - Provide Prometheus exposition at `/actuator/prometheus`
  - Add basic LLM metrics (latency, success/failure, evaluator verdicts, retries)
  - Keep configuration simple for local dev and document hardening for production
- Non-Goals:
  - End-to-end tracing, distributed tracing, or OpenTelemetry integration
  - Full-blown dashboards/alerts; only ensure Prometheus compatibility
  - Persisting metrics or adding DB dependencies

## Decisions
1) Dependencies
   - Add `spring-boot-starter-actuator`
   - Add `micrometer-registry-prometheus`

2) Endpoint Exposure
   - Local defaults: expose `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`
   - Production: allow restricting exposure via `management.endpoints.web.exposure.include` and potentially `management.server.port` or security; document recommendations

3) HTTP Server Metrics
   - Rely on Spring Boot Autoconfiguration to publish `http_server_requests_*` series
   - Enable percentiles histogram for HTTP server requests for latency distribution
   - Verify metrics label `uri`/`path` for `/ask` and `/ask/stream` appear after traffic

4) Common Tags & Cardinality
   - Add stable tags: `application=${spring.application.name}`, optional `instance` if needed
   - Avoid unbounded-cardinality tags (e.g., raw prompts, OCIDs, request IDs)

5) Custom LLM Metrics (Micrometer)
   - Names (Prometheus-friendly):
     - Timer: `app_oci_chat_latency_seconds`
     - Counters: `app_oci_chat_success_total`, `app_oci_chat_failures_total`
     - Evaluator counter: `app_evaluator_relevancy_total{outcome=yes|no}`
     - Retry counter: `app_llm_retries_total{reason=AnswerNotRelevantException}`
   - Placement:
     - Record chat latency/success/failure in `ChatService` around OCI call
     - Record evaluator verdicts after evaluation completes (in `ChatService`, or directly in evaluator)
     - Increment retry counter in retry path or `@Recover`
   - Tag discipline:
     - Keep tag values low-cardinality (e.g., `outcome=yes|no`, `reason=AnswerNotRelevantException`)

6) Security / Privacy
   - Do not emit user prompts or generated content as tags or metric values
   - Document that DEBUG logging of prompts should be disabled in production
   - Production exposure of actuator endpoints should be restricted per environment policy

## Alternatives Considered
- Manual metrics endpoint vs Actuator:
  - Rejected: Actuator + Micrometer is standard, safer, and lower maintenance
- OpenTelemetry/OTLP first:
  - Deferred: scope limited to Prometheus exposition; can be added later without breaking this design

## Risks / Trade-offs
- Overhead: Histograms and extra counters add small overhead; keep histogram config minimal
- Security: Accidental high-cardinality tags or sensitive data leakage; mitigated via explicit naming and review
- Compatibility: Ensure Prometheus registry on classpath only in desired environments

## Migration Plan
1. Add dependencies and config (exposure, histograms, common tags)
2. Verify actuator endpoints locally
3. Add Micrometer instrumentation in `ChatService` and evaluator path
4. Add tests that:
   - Hit `/ask` and `/ask/stream`
   - Scrape `/actuator/prometheus`
   - Assert presence of `http_server_requests_*` and custom metrics
5. Document usage and production hardening in README

## Open Questions
- Should `/actuator` be moved to a dedicated management port in production?
- Do we need fine-grained outcome tags for upstream OCI errors vs evaluator failures, or keep single `failures_total`?
- Histogram buckets: keep defaults or tune for expected latencies (e.g., OCI round-trip times)?
