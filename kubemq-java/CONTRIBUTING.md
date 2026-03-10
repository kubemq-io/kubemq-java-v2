# Contributing to KubeMQ Java SDK

Thank you for your interest in contributing to the KubeMQ Java SDK.

## Development Setup

### Prerequisites
- JDK 11 or later
- Maven 3.8+
- A running KubeMQ server (for integration tests)

### Building
```bash
cd kubemq-java
mvn clean install -DskipITs
```

### Running Tests

Unit tests:
```bash
mvn test
```

Integration tests (requires running KubeMQ server):
```bash
mvn verify -Dkubemq.address=localhost:50000
```

## Commit Message Format

We recommend (but do not enforce) [Conventional Commits](https://www.conventionalcommits.org/) format:

```
type(scope): description

[optional body]

[optional footer(s)]
```

### Types
- `feat` -- New feature (MINOR version bump)
- `fix` -- Bug fix (PATCH version bump)
- `docs` -- Documentation changes
- `test` -- Adding or updating tests
- `chore` -- Build process, dependency updates
- `refactor` -- Code changes that neither fix a bug nor add a feature
- `perf` -- Performance improvements

### Scope (optional)
- `pubsub` -- Events and Events Store
- `queues` -- Queue operations
- `cq` -- Commands and Queries
- `client` -- KubeMQClient core
- `transport` -- gRPC/connection layer

### Examples
```
feat(queues): add batch send support for queue messages
fix(pubsub): handle reconnection during events store subscription
docs: update README with TLS configuration examples
chore: upgrade gRPC to 1.75.0
```

### Breaking Changes
Append `!` after type or include `BREAKING CHANGE:` in the footer:
```
feat(client)!: change KubeMQClient builder API

BREAKING CHANGE: `address()` method renamed to `serverAddress()`
```

## Pull Requests

1. Create a feature branch from `main`
2. Make your changes
3. Ensure all tests pass (`mvn verify -DskipITs`)
4. Submit a pull request with a clear description of changes

## Changelog

All user-visible changes must be recorded in `CHANGELOG.md` following the
[Keep a Changelog](https://keepachangelog.com/) format. Add your entry under the
`[Unreleased]` section.

When recording a deprecation in the CHANGELOG, use this template:

```markdown
### Deprecated
- `PubSubClient.sendEventsMessage()` -- use `PubSubClient.publishEvent()` instead. Removal planned for 3.0.
```

## Deprecation Policy

### Rules

1. **Annotation:** Every deprecated public API must carry:
   ```java
   @Deprecated(since = "X.Y", forRemoval = true)
   ```
   Plus a Javadoc `@deprecated` tag naming the replacement:
   ```java
   /**
    * @deprecated Since 2.2. Use {@link NewClass#newMethod()} instead.
    *             Scheduled for removal in 3.0.
    */
   ```

2. **Notice period:** Minimum 2 minor versions OR 6 months (whichever is longer) before removal.

3. **Functionality:** Deprecated APIs continue to function identically until removal. No behavior changes in deprecated code paths.

4. **CHANGELOG:** Every deprecation must have a CHANGELOG entry under a "Deprecated" section.

5. **Migration guide:** When a deprecated API is removed in a major version, the migration guide (MIGRATION.md) must list:
   - The removed API
   - The replacement API
   - A code example showing the migration

### Deprecation Lifecycle

```
v2.1: API introduced
v2.3: API deprecated (@Deprecated annotation + CHANGELOG entry)
v2.4: Deprecated API still works, warning in Javadoc
v3.0: API removed (listed in MIGRATION.md)
```
