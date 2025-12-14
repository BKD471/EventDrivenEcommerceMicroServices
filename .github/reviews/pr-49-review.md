# Review of "Configure Kafka For Notification Service." (PR #49)

Summary
- Adds Kafka configuration to the notification service. Merged into master (2 commits).
- Good focused change and follow-up commit for review feedback. Tests were not added.

Positives
- Enables event-driven notifications; single-purpose change.
- Author incorporated review feedback.

Main concerns / recommended follow-ups
1. Tests and CI
   - Add unit tests (serialization/wiring) and integration tests (local Kafka or testcontainers). Add CI steps to run them.

2. Configuration & secrets
   - Ensure broker endpoints and credentials are not hard-coded and are loaded from environment/secret store. Confirm no secrets were committed.

3. Robustness and operations
   - Add retry/backoff for transient failures and a clear reconnect strategy.
   - Ensure graceful shutdown commits offsets and closes clients to avoid duplicates.

4. Topic management and compatibility
   - Decide if topics are pre-created or created by the service; document behavior.
   - Document serialization format and consider schema/versioning (Schema Registry, Avro/Protobuf).

5. Observability
   - Add metrics (rates, error counters, lag) and structured logs with correlation IDs.

6. Error handling / dead-lettering
   - Implement or document dead-lettering for poison messages and avoid infinite retry loops.

7. Documentation
   - Update README/service docs with required env vars, local run instructions (docker-compose/testcontainers), expected topics and schemas, and a runbook.

8. Security
   - Validate TLS and SASL auth for non-local environments and ensure secure defaults.

9. Small/misc
   - Lock Kafka client dependency versions and ensure code follows repo style.

Suggested next PRs
- Tests (unit + integration)
- Documentation (env vars, local run, schema)
- Observability (metrics/logs)
- Resilience (retries, graceful shutdown, DLQ)

Signed-off-by: BKD471
