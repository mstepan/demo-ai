# Project Context

## Purpose
A Spring Boot demo service that exposes simple REST endpoints to:
- Submit a question to an LLM hosted on Oracle OCI Generative AI and return a single reply
- Stream the LLM answer as it is generated (NDJSON)
- Post‑validate the generated answer for relevancy using an LLM‑as‑a‑judge pattern, with retry and graceful fallback

This project demonstrates integrating Spring AI with a custom ChatModel backed by the Oracle BMC Generative AI Inference SDK, plus testing via WireMock.

## Tech Stack
- Language: Java 25
- Build: Maven (mvnw wrapper)
- Framework: Spring Boot 3.5.9
- HTTP: spring-boot-starter-web (servlet stack), Jackson for JSON
- Validation: Jakarta Bean Validation (records with @NotBlank, configuration validation)
- Resilience: spring-retry + AOP (@Retryable/@Recover)
- AI:
  - Spring AI BOM 1.1.2
  - Custom ChatModel/StreamingChatModel that calls Oracle OCI Generative AI Inference API
- Reactive: Project Reactor (Flux) for streaming responses
- Testing: JUnit 5, Spring Boot Test, WireMock Spring Boot 3.10.6, Mockito, Spring MVC slice tests (@WebMvcTest + MockMvc)
- Tooling: OCI CLI for session authentication to OCI
- Logging: SLF4J + structured debug logging of LLM requests/responses (when DEBUG enabled)
- Config format: application.yaml + @ConfigurationProperties

## Project Conventions

### Code Style
- Java 25 language features; DTOs as compact Java records:
  - web.Answer(String answer)
  - web.Question(String question)
- Validation on DTOs with Jakarta @NotBlank and clear messages
- Package naming: com.github.mstepan.demo_ai
- Logging: SLF4J per‑class logger using MethodHandles.lookup(); avoid noisy logs by default; deep request/response logs behind DEBUG level
- Configuration binding:
  - OCIGenAiProperties is @ConfigurationProperties(prefix = "oci.genai") with strong validation:
    - base.url must be http(s)
    - compartment must match OCID regex
    - temperature range 0.0–2.0
    - timeout ranges enforced
- Prompt templates:
  - Stored under src/main/resources/prompts/** as .st files
  - Passed to Spring AI via Resource and parameterized with .param("name", value)
- Threading: Virtual threads enabled (spring.threads.virtual.enabled=true) to reduce blocking overhead on servlet threads

### Architecture Patterns
- Controller → Service → Adapter layering
  - Web: ChatController
    - POST /ask → JSON Answer
    - POST /ask/stream → NDJSON stream (MediaType.APPLICATION_NDJSON_VALUE aka application/x-ndjson)
  - Service: ChatService
    - Renders system/user prompts
    - Calls ChatClient (Spring AI) for sync or streaming
    - Logs token usage metadata when available
    - Post‑generation relevancy check using Evaluator (LLM-as-judge)
    - Resilience: @Retryable on AnswerNotRelevantException with @Recover fallback response
  - OCI integration (Adapter):
    - OCIChatModel implements ChatModel and StreamingChatModel and invokes Oracle BMC Generative Ai Inference Client
    - OCIGenAiStreamingSinkFactory converts OCI event stream into Flux<ChatResponse>
    - GenAiClientFactoryFactory produces configured GenerativeAiInferenceClient (prototype scope)
    - OCILogService builds safe, structured loggable payloads for requests/responses
- Configuration/Validation at the edges:
  - OCIGenAiProperties centralizes all adjustable GenAI settings (endpoint, compartment, model id, temperature, tokens, timeouts)
- Evaluation pattern:
  - OCIGenAIRelevancyEvaluator: prompts a model to return YES/NO for relevancy; mapped to pass/fail
  - OCIGenAIFactsEvaluator: example evaluator verifying a claim against a small embedded “facts” document (not wired into ChatService by default)

### Testing Strategy
- Unit/integration default run:
  - maven-surefire-plugin excludes tests tagged as exhaustive
- Exhaustive/long/integration run:
  - Activate profile exhaustive to run all tests (removes excludedGroups)
  - Requires valid OCI session token (see README)
- WireMock-based integration:
  - ChatServiceWireMockTest stubs /20231130/actions/chat twice to cover both generation and evaluator calls
  - Test response payloads in src/test/resources (test-chat-response.json, test-relevance-evaluation-response.json)
- Web MVC slice tests:
  - ChatControllerSliceTest uses @WebMvcTest(controllers = ChatController.class) with MockMvc and ObjectMapper
  - @Import(ExceptionHandlerAdvice.class) to assert standardized error responses (application/problem+json)
  - Provides a @TestConfiguration with a @Primary ChatService mock via Mockito
  - Verifies:
    - POST /ask returns 200 with JSON Answer body and invokes chatService.askQuestion once
    - POST /ask with invalid payload returns 400 with application/problem+json and performs no service interaction
    - POST /ask/stream returns NDJSON stream and contains expected chunks
- Recommendations:
  - Add streaming contract tests to assert NDJSON framing and backpressure behavior
  - Add negative tests around evaluator returning NO and retry behavior
  - Validate @ConfigurationProperties constraints with dedicated tests

### Git Workflow
- Remote: origin https://github.com/mstepan/demo-ai.git
- Recommended approach (not enforced):
  - Trunk-based with short‑lived feature branches and PR reviews
  - Conventional Commits style for consistency (feat:, fix:, chore:, test:, docs:) is recommended
  - CI suggestion:
    - mvn -B clean verify for default tests
    - Optionally run -Pexhaustive in environments with OCI credentials to exercise live integrations

## Domain Context
- Problem: Provide a small, focused demo service for question/answer interactions with an LLM, including:
  - Non‑streaming single reply with post‑answer relevancy validation
  - Streaming responses for long outputs (NDJSON)
- Usage examples:
  - Creative text generation and simple Q&A (see README curl snippets)
- Evaluators:
  - Relevancy evaluator acts as a guardrail; non‑relevant answers trigger a retry (single retry configured) then a friendly fallback
  - Facts evaluator demonstrates “claim vs. document” verification pattern (example document with pirate names)
- Persistence:
  - No database usage in current sources (README mentions Oracle 23ai DB, but no DB integration is present yet)

## Important Constraints
- Runtime:
  - JDK 25 required
  - Server: port 7171; HTTP/2 enabled; response compression enabled
- OCI access:
  - Session Token auth via OCI CLI; obtain token before running exhaustive tests or live calls:
    - oci session authenticate --region us-chicago-1 --profile-name bmc_operator_access
  - oci.genai.base.url must be a valid regional inference endpoint
  - oci.genai.compartment must be a valid compartment OCID
  - Model id (oci.genai.model) must correspond to an available OCI model (e.g., openai.gpt-oss-120b)
- Timeouts and generation controls (defaults in application.yaml / OCIGenAiProperties):
  - connection_timeout: 10s
  - read_timeout: 60s
  - temperature: 1.0 (0.0 deterministic ↔ 2.0 more creative)
  - maxTokens: 2048
- Secrets & configuration:
  - Do not hardcode secrets; prefer environment variables and spring property resolution
  - Debug‑level request/response logging can include prompt text—use with care in production
- Observability:
  - Spring Boot Actuator endpoints exposed locally: /actuator/health, /actuator/info, /actuator/metrics, /actuator/prometheus
  - In production, restrict actuator exposure via management.endpoints.web.exposure.include; optionally bind to a dedicated management port (management.server.port) and secure endpoints per environment policy
  - Prometheus metrics are exported via Micrometer Prometheus registry at /actuator/prometheus; many series appear only after traffic

## External Dependencies
- Oracle OCI Generative AI Inference API (Java SDK)
- Spring Boot (web, validation, retry, aop)
- Spring AI (BOM 1.1.2) with custom ChatModel/StreamingChatModel
- Project Reactor for streaming
- WireMock Spring Boot (tests)
- OCI CLI (for session token auth)
- Spring Boot Actuator
- Micrometer Prometheus registry
