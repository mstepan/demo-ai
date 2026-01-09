## ADDED Requirements
### Requirement: Actuator Endpoints Exposure
The system SHALL expose Spring Boot Actuator endpoints for health, info, and metrics over HTTP.

#### Scenario: Health endpoint is available
- WHEN a client performs GET /actuator/health
- THEN the response status is 200
- AND the response JSON contains "status":"UP"

#### Scenario: Info endpoint is available
- WHEN a client performs GET /actuator/info
- THEN the response status is 200
- AND the response contains application metadata (e.g., info.app.name="demo-ai")

### Requirement: Prometheus Metrics Endpoint
The system SHALL expose a Prometheus scrape endpoint at /actuator/prometheus that returns text/plain in Prometheus exposition format.

#### Scenario: Prometheus endpoint is available
- WHEN a client performs GET /actuator/prometheus
- THEN the response status is 200
- AND the Content-Type is text/plain
- AND the body contains metric series names (e.g., "http_server_requests_seconds_count")

### Requirement: HTTP Server Request Metrics
The system SHALL publish HTTP server request metrics for all REST endpoints, including latency histograms and outcome dimensions.

#### Scenario: Request metrics include /ask
- GIVEN the service has handled at least one request to POST /ask
- WHEN a client scrapes /actuator/prometheus
- THEN the output includes "http_server_requests_seconds_count" with labels that include path="/ask" and outcome

#### Scenario: Request metrics include /ask/stream
- GIVEN the service has handled at least one request to POST /ask/stream
- WHEN a client scrapes /actuator/prometheus
- THEN the output includes "http_server_requests_seconds_count" with labels that include path="/ask/stream" and outcome

### Requirement: LLM Interaction Metrics
The system SHALL publish LLM interaction metrics covering request counts, success/failure outcomes, latency, evaluator verdicts, and retry occurrences.

#### Scenario: LLM chat latency is recorded
- WHEN a question is processed through the non-streaming endpoint
- THEN a timer metric (e.g., "app_oci_chat_latency_seconds") records duration for the upstream OCI call

#### Scenario: LLM chat success/failure counters
- WHEN a question completes successfully on the first attempt
- THEN a counter (e.g., "app_oci_chat_success_total") increments by 1
- WHEN a question fails due to upstream error or non-successful response
- THEN a counter (e.g., "app_oci_chat_failures_total") increments by 1

#### Scenario: Evaluator relevancy verdicts
- WHEN the relevancy evaluator returns YES
- THEN a counter (e.g., "app_evaluator_relevancy_total{outcome=\\"yes\\"}") increments by 1
- WHEN the relevancy evaluator returns NO
- THEN a counter (e.g., "app_evaluator_relevancy_total{outcome=\\"no\\"}") increments by 1

#### Scenario: Retry occurrences are tracked
- WHEN AnswerNotRelevantException triggers a retry
- THEN a counter (e.g., "app_llm_retries_total{reason=\\"AnswerNotRelevantException\\"}") increments by 1

### Requirement: Metric Tags and Cardinality
The system SHALL attach common tags to metrics to aid aggregation while avoiding unbounded cardinality.

#### Scenario: Common tags present
- WHEN scraping /actuator/prometheus
- THEN metric series include stable tags such as application and instance (e.g., "application=\\"demo-ai\\"")
- AND dynamic user-provided values (e.g., raw prompt contents) are NOT used as tags

### Requirement: Configuration and Security Defaults
The system SHALL provide configuration to expose actuator endpoints for local development, and SHALL support restricting actuator exposure in production.

#### Scenario: Local development defaults
- WHEN running locally with default configuration
- THEN /actuator/health, /actuator/info, /actuator/metrics, and /actuator/prometheus are accessible without authentication

#### Scenario: Production hardening support
- WHEN production configuration is applied
- THEN actuator exposure can be limited via configuration (e.g., endpoint filtering, network policies, auth), while Prometheus scraping remains possible as per environment policy
