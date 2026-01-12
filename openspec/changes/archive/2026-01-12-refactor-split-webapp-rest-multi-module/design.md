## Context
The current application bundles Vaadin UI and REST endpoints in a single Spring Boot app. This creates tight coupling and limits deployment topologies. We want a clean separation: a REST service and a Vaadin web application that consumes it.

## Goals / Non-Goals
- Goals:
  - Two independently buildable and runnable modules (rest-api, webapp)
  - Stable REST contract; no behavioral change to endpoints
  - Simple local dev experience (default ports, minimal config)
- Non-Goals:
  - Introducing a shared "common" module (can be considered later)
  - Generating clients via OpenAPI
  - Containerization

## Decisions
- Project structure: Maven multi-module
  - Parent (packaging: pom) → modules: rest-api, webapp
- Runtime ports:
  - rest-api: 7171 (unchanged)
  - webapp: 7170 (new default)
- Communication: webapp → rest-api over HTTP using existing ChatApiClient abstraction
  - Base URL property: webapp.api.base-url (default http://localhost:7171)
- Dependency management: parent POM with Spring Boot BOM and Spring AI BOM; align versions to current project.md
- Packaging: both modules as executable JARs with Spring Boot
- CORS: if webapp and rest-api run on different ports, enable minimal CORS on rest-api for required endpoints in local profile only; prefer same-origin via reverse proxy in prod (out of scope here)

## Alternatives Considered
- Single app with separate contexts: rejected (keeps coupling)
- Adding a third "common" library: deferred; only add once duplication justifies it

## Risks / Trade-offs
- Duplicate DTOs between webapp and rest-api → risk of drift
  - Mitigation: Treat REST contract as source of truth; add contract tests; consider common module later
- Operational complexity (two processes) in dev
  - Mitigation: clear README instructions and defaults

## Migration Plan
1) Introduce parent + modules skeleton
2) Move backend (REST) classes to rest-api; boot app DemoAiRestApplication
3) Move Vaadin UI and frontend to webapp; boot app DemoAiWebappApplication
4) Wire webapp to call rest-api; defaults work locally
5) Update docs and tests

## Open Questions
- Do we want an explicit CORS policy for local dev, or keep same-origin via proxy? (Default: minimal CORS in dev only)
- Should we later extract DTOs to a common module? (Out of scope for now)
