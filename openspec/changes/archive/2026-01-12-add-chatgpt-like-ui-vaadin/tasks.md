## 1. Implementation

- [x] 1.1 Add Vaadin to the project
    - [x] Add Vaadin BOM and `vaadin-spring-boot-starter` dependency in pom.xml
    - [x] Verify app still starts and actuator endpoints work (compiled successfully)
- [x] 1.2 Create Chat view (Vaadin)
    - [x] `@Route("")` ChatView with vertical layout
    - [x] Message list component with distinct user/assistant bubbles
    - [x] Text area input; Enter to send; Shift+Enter for newline
    - [x] Toggle to enable/disable streaming mode
    - [x] Loading indicator while awaiting responses
    - [x] Error notification banner/toast for failures
- [x] 1.3 Implement API client for backend calls
    - [x] `ChatApiClient` using Spring WebClient
    - [x] Method: `Mono<String> ask(String prompt)` → POST /ask
    - [x] Method: `Flux<String> askStream(String prompt)` → POST /ask/stream (parse NDJSON line-by-line as JSON strings)
    - [x] Map validation errors and non-2xx to user-friendly exceptions
- [x] 1.4 Wire UI to client
    - [x] Append user message on send
    - [x] Non-streaming: await full answer, then append assistant message
    - [x] Streaming: append chunks as they arrive; finalize message when completed
    - [x] Provide cancel button for streaming requests
- [x] 1.5 Session-scoped conversation state
    - [x] Keep messages list in UI/session scope (no persistence)
    - [x] Clear conversation button resets state
- [x] 1.6 Styling & accessibility
    - [x] Responsive layout (mobile/desktop)
    - [x] Respect light/dark theme; ensure contrast and sizes are accessible
    - [x] ARIA labels for send, cancel, toggle controls

## 2. Testing

- [x] 2.1 Unit tests for ChatApiClient
    - [x] NDJSON parsing: multiple lines combine into final message
    - [x] Error handling: 4xx/5xx mapped to exceptions with user-friendly messages
- [x] 2.2 Web MVC/Controller slice tests remain unchanged (regression)
- [x] 2.3 Manual UI verification checklist
    - [x] Non-streaming message path works end-to-end
    - [x] Streaming path renders incremental tokens and can be cancelled
    - [x] Empty input disabled; validation error shows notification
    - [x] Mobile viewport layout works

## 3. Documentation

- [x] 3.1 Update README
    - [x] How to run with Vaadin (first-run downloads)
    - [x] How to use the chat UI and streaming toggle
    - [x] Known limitations (session-only state, no auth)

## 4. Non-Goals (for this change)

- Persistent chat history across sessions
- Authentication/authorization
- Internationalization

## 5. Done Criteria

- [x] All acceptance scenarios in `specs/chat-ui/spec.md` pass via manual verification and unit tests
- [x] Application starts and existing endpoints behave unchanged
