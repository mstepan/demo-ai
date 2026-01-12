# Change: Split Vaadin Web UI and REST API into a Maven multi-module project

## Why
The current single-module Spring Boot project couples the Vaadin web UI and the REST API in one runtime. This makes independent development, testing, deployment, and scaling harder. Separating concerns enables clearer ownership, smaller deployments, and simpler runtime configuration.

## What Changes
- Create a Maven parent project (packaging: pom) at the repo root.
- Add two child modules:
  - rest-api: Spring Boot service exposing the existing REST endpoints
  - webapp: Spring Boot Vaadin application that consumes the REST API
- Keep existing REST contracts (endpoints and DTOs) stable. No functional changes to APIs.
- Configure default ports for local development:
  - rest-api: 7171 (same as today)
  - webapp: 7170 (new; configurable)
- Move Vaadin frontend and UI-related Java code to webapp module.
- Keep service/evaluator/OCI integration in rest-api; webapp calls it via HTTP using the existing ChatApiClient abstraction (base URL configurable).
- Parent POM centralizes common versions and plugins (inherits Spring Boot BOM and Spring AI BOM as today).
- Update documentation and tests accordingly.

## Impact
- Affected specs: project-structure (new), chat-ui (references now point to webapp runtime), observability (actuator endpoints per module)
- Affected code:
  - com.github.mstepan.demo_ai.ui.* → moves to webapp
  - com.github.mstepan.demo_ai.web.*, service.*, oci.*, evaluators.* → rest-api
  - DemoAiApplication entry points become two apps
- Build system: root POM becomes parent with <modules>. Child POMs inherit common config.
- Runtime: Two processes in dev; CORS and base URL must be configured for webapp → rest-api calls (same-origin if proxied; otherwise configure).

## Out of Scope (for this change)
- Creating a shared third module for DTOs/common libraries (can be proposed later if/when duplication becomes painful)
- OpenAPI generation and client codegen
- Containerization/Dockerfiles
