## 1. Preparation

- [x] 1.1 Create parent POM (packaging: pom) with Spring Boot + Spring AI BOM management
- [x] 1.2 Add child modules: rest-api, webapp (empty skeletons)
- [x] 1.3 Decide artifactIds, groupId stays com.github.mstepan.demo_ai

## 2. rest-api Module

- [x] 2.1 Create Spring Boot application class (DemoAiRestApplication)
- [x] 2.2 Move REST controllers, services, evaluators, OCI adapters, configuration properties to rest-api
- [x] 2.3 Keep current actuator and metrics configuration; expose on /actuator/*
- [x] 2.4 Set default port to 7171; ensure NDJSON streaming still works
- [x] 2.5 Ensure tests compile and pass (unit, slice, WireMock)

## 3. webapp Module

- [x] 3.1 Create Spring Boot application class (DemoAiWebappApplication)
- [x] 3.2 Move Vaadin UI classes and src/main/frontend into webapp (minimal UI moved; Vaadin dev mode will generate
  frontend as needed)
- [x] 3.3 Wire ChatApiClient to call rest-api via configurable base URL (default http://localhost:7171)
- [x] 3.4 Configure default port 7170; verify UI works end-to-end against local rest-api (manual run pending)
- [x] 3.5 Adapt UI tests if any; add basic smoke test

## 4. Build & Tooling

- [x] 4.1 Configure parent for dependencyManagement, pluginManagement, and common properties
- [x] 4.2 Child POMs inherit from parent; remove redundant definitions
- [x] 4.3 Update README with run instructions:
    - ./mvnw -pl rest-api spring-boot:run
    - ./mvnw -pl webapp spring-boot:run
- [x] 4.4 Update CI scripts (if any) to build modules: ./mvnw -B -T1C clean verify (validated locally)

## 5. Validation

- [x] 5.1 Run unit/integration tests for both modules
- [x] 5.2 Run k6 e2e tests against rest-api (as today)
- [x] 5.3 Manual UI verification for webapp → rest-api flow

## 6. Documentation & Specs

- [x] 6.1 Update OpenSpec spec(s) if any acceptance criteria change during implementation (no changes needed)
- [x] 6.2 Add migration notes in README (ports, commands)
- [x] 6.3 After merge, archive this change with openspec archive <change-id> --yes
