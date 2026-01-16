# k6 End-to-End Tests

This folder contains k6 e2e tests for the REST API.

## Prerequisites

- The application is running and reachable (default: http://localhost:7171). You can override the base URL with the
  `BASE_URL` environment variable.
- k6 is installed (verified version in the task description).

## Tests

- ask.test.js — Tests POST /ask (JSON)
- ask-stream.test.js — Tests POST /ask/stream (NDJSON streaming)

## Run

To run e2e tests you need to start the demo-ai service and also obtain OCI SessionToken. You can use

```bash 
./run-e2e-tests.sh
``` 

script for execute e2e tests.

Use the k6 `-e` flag to pass BASE_URL (recommended for cross-platform):

- Run /ask test:
  k6 run -e BASE_URL=http://localhost:7171 e2e-tests/ask.test.js

- Run /ask/stream test:
  k6 run -e BASE_URL=http://localhost:7171 e2e-tests/ask-stream.test.js

- Run /ping test:
  k6 run --vus 10 --duration 30s --summary-trend-stats="med,p(95),p(99)" -e BASE_URL=http://localhost:7171
  e2e-tests/ping.test.js

Both tests include basic thresholds and assertions:

- /ask validates HTTP 200, JSON response, and a non-empty `answer` field.
- /ask/stream validates HTTP 200, NDJSON content-type, at least one line, each line parses as a JSON string chunk, and
  the combined text is non-empty.

## Notes

- If your server uses a different port or host, pass it via `-e BASE_URL=http://host:port`.
- The /ask/stream endpoint returns `application/x-ndjson` (or `application/ndjson`) with one JSON string per line. The
  test aggregates chunks and asserts non-empty content.
