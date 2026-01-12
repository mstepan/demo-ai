# chat-ui Specification

## Purpose
TBD - created by archiving change add-chatgpt-like-ui-vaadin. Update Purpose after archive.
## Requirements
### Requirement: Chat View Layout and Navigation
The system SHALL provide a ChatGPT-like conversational UI as the default route.
- The chat view SHALL be available at the application root path "/".
- The view SHALL contain a scrollable message list and an input area with a send control.
- The view SHALL include a toggle to enable/disable streaming mode.

#### Scenario: Initial load shows empty conversation
- WHEN a user opens the root URL
- THEN the chat view loads with an empty message list
- AND the input area is focused and ready for typing

### Requirement: Send Message (Non-Streaming)
The system SHALL send user prompts to the existing non-streaming endpoint and render the single response.
- Endpoint: POST /ask with JSON body {"question": "..."}
- Response: JSON body {"answer": "..."}
- The UI SHALL append the user message immediately upon send and the assistant message after the response is received.
- Inputs SHALL be validated so that blank messages cannot be sent.

#### Scenario: Successful non-streaming send
- GIVEN streaming mode is disabled
- WHEN the user enters a non-empty prompt and presses Enter (without Shift)
- THEN the UI appends the user message to the conversation
- AND calls POST /ask
- AND after a 200 response, appends the assistant message containing the "answer" text
- AND clears the input field

#### Scenario: Empty input cannot be sent
- GIVEN the input is empty or whitespace-only
- WHEN the user attempts to send
- THEN the send action is disabled or ignored
- AND a brief validation hint is shown

### Requirement: Streaming Responses (NDJSON)
The system SHALL stream assistant responses using the existing streaming endpoint and render chunks incrementally.
- Endpoint: POST /ask/stream with JSON body {"question": "..."}
- Content-Type: application/x-ndjson (or application/ndjson)
- Each line in the stream SHALL be a JSON string chunk (e.g., "partial token text")
- The UI SHALL append tokens as they arrive and finalize the assistant message when the stream completes.
- Streaming mode SHALL be enabled by default and user-toggleable.

#### Scenario: Streaming success renders incremental chunks
- GIVEN streaming mode is enabled
- WHEN the user sends a valid prompt
- THEN the UI appends the user message
- AND calls POST /ask/stream
- AND as NDJSON lines arrive, the assistant message is updated in place with the concatenated text
- AND when the stream completes, the assistant message remains as a single finalized message

#### Scenario: User cancels an in-flight stream
- GIVEN a streaming response is in progress
- WHEN the user presses a Cancel control
- THEN the streaming request is aborted
- AND no further chunks are rendered
- AND the partial assistant message remains as-is without error

### Requirement: Error Handling and Notifications
The system SHALL present user-friendly error notifications for recoverable failures and validation issues.
- 4xx validation errors SHALL display a brief explanation and preserve the user input
- 5xx or network errors SHALL display a retry suggestion
- Internal details SHALL NOT be exposed to the user

#### Scenario: Validation error from backend
- GIVEN the user sends an invalid payload
- WHEN the backend responds 400 with problem+json
- THEN the UI shows a validation notification and does not append an assistant message

#### Scenario: Server error during streaming
- GIVEN streaming mode is enabled
- WHEN the backend returns a 5xx
- THEN the UI shows an error notification with a retry action
- AND any partial assistant message remains visible

### Requirement: Session-Scoped Conversation State
The system SHALL maintain conversation state in memory per user session without persistence.
- Messages SHALL be preserved while the session remains active
- A Clear Conversation control SHALL remove all messages from the list

#### Scenario: Clear conversation resets state
- GIVEN a session with several messages
- WHEN the user presses Clear Conversation
- THEN the message list becomes empty

### Requirement: Accessibility and Keyboard Behavior
The system SHALL support accessible interactions and expected keyboard shortcuts.
- Enter to send; Shift+Enter to insert a newline
- Controls SHALL include accessible names/ARIA labels
- Focus management SHALL return to the input after send

#### Scenario: Keyboard shortcut to send
- GIVEN the input contains text
- WHEN the user presses Enter without Shift
- THEN the message is sent
- AND focus returns to the input after the assistant response is finalized

### Requirement: Responsive Layout and Theme
The system SHALL render correctly on mobile and desktop and respect light/dark themes.
- Layout SHALL adapt to small screens (stacked controls, readable message widths)
- Message bubbles SHALL maintain sufficient contrast in both light and dark modes

#### Scenario: Mobile viewport renders usable layout
- GIVEN a narrow viewport (<= 420px width)
- WHEN the chat view loads
- THEN the input and controls stack vertically and messages wrap appropriately without overflow

