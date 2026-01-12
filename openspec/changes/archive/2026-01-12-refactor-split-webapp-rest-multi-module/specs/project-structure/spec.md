## ADDED Requirements

### Requirement: Maven Multi-Module Layout

The repository SHALL use a Maven parent POM with two child modules to separate concerns between UI and API.

- Parent POM has packaging "pom" and lists modules: "rest-api" and "webapp".
- Child modules inherit dependencyManagement and pluginManagement from the parent.

#### Scenario: Parent and modules are declared

- WHEN running "./mvnw -q -Dexec.printEffectivePOM=true help:effective-pom"
- THEN the output contains a parent with packaging pom and modules named "rest-api" and "webapp"

### Requirement: REST API Service

The rest-api module SHALL provide the existing endpoints without breaking changes.

- Expose POST /ask (application/json) and POST /ask/stream (application/x-ndjson)
- Keep default server port 7171 for local development
- Exclude Vaadin UI dependencies from this module

#### Scenario: REST endpoints unchanged

- WHEN running the rest-api module
- THEN POST /ask returns JSON Answer and POST /ask/stream returns NDJSON with at least one line

### Requirement: Web Application Service

The webapp module SHALL host the Vaadin UI and consume the REST API via HTTP.

- Default server port is 7170 (configurable)
- REST base URL is configurable (default http://localhost:7171)
- UI features continue to function using the rest-api responses

#### Scenario: UI calls REST API successfully

- WHEN webapp runs on port 7170 and rest-api runs on port 7171
- THEN the UI can submit a question and receives an answer rendered in the chat view

### Requirement: Build and Run Commands

The build MUST support independent compilation, test, and run of each module, and aggregate build at the parent.

- Parent supports ./mvnw -B -T1C clean verify
- Each module supports spring-boot:run independently under its directory or via -pl

#### Scenario: Independent runs

- WHEN executing "./mvnw -pl rest-api spring-boot:run" and "./mvnw -pl webapp spring-boot:run" in separate shells
- THEN rest-api starts on 7171 and webapp starts on 7170, and the webapp operates end-to-end against rest-api
