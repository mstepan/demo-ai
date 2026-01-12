# Change: Add ChatGPT-like UI using Vaadin

## Why
Provide an in-app, ChatGPT-like conversational UI so users can interact with the existing LLM endpoints without external tools. This improves demoability, reduces friction for manual testing, and showcases streaming responses (NDJSON) in a familiar chat experience.

## What Changes
- Add Vaadin Flow (Spring Boot starter) to the project to build a server-driven web UI
- Create a Chat view (Vaadin) with:
  - Message list displaying alternating user/assistant bubbles
  - Text area with Enter to send (Shift+Enter for newline)
  - Toggle to use streaming (NDJSON) or non-streaming mode
  - Loading/progress indicator while awaiting model response
  - Error notification on failures or validation errors
- Implement API calls from the UI to the existing backend:
  - Non-streaming: POST /ask → returns {"answer": "..."}
  - Streaming: POST /ask/stream → application/x-ndjson stream of JSON strings
- Implement a client component (WebClient-based) to invoke the above endpoints from the Vaadin view
- Maintain conversation state per user session (in-memory, UI-scoped)
- Basic responsive layout and light/dark theme compatibility (Vaadin defaults)
- Documentation: update README with how to run the UI and demo streaming

Notes:
- No changes to existing REST endpoints or service behavior
- Keep dependencies minimal: only Vaadin starter + its BOM (managed versions)
- Default to streaming enabled, with a fallback to non-streaming when disabled or on error

## Impact
- Specs: Adds new capability spec "chat-ui" describing the UI behavior and streaming rendering
- Code areas affected:
  - pom.xml: add Vaadin BOM + vaadin-spring-boot-starter
  - New UI package: com.github.mstepan.demo_ai.ui (Vaadin view + helpers)
  - New API client: ChatApiClient (uses Spring WebClient) to call /ask and /ask/stream
  - Static/theme resources under src/main/resources if needed by Vaadin
- Observability: no changes required; existing metrics/actuator remain available
- Security: no authentication added; respects current app setup; avoid logging PII
- Backward compatibility: Non-breaking; existing endpoints remain unchanged

## Risks / Considerations
- Vaadin dev-time frontend tooling (Node downloads) may impact first startup time
- Streaming UI must handle partial chunks and cancellation gracefully
- Keep error messages user-friendly without exposing internal details

## Rollback
- Remove Vaadin dependencies and UI classes; no data migrations are introduced
