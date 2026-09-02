# Phase 16: Booking Performance and Reliable Async Processing

## Objective

Reduce perceived and measured latency in booking flows without weakening capacity, duplicate-booking, authorization, check-in, cancellation, or completion consistency.

## Architectural boundary

- Keep booking state transitions synchronous and transactional when the caller must know the committed result immediately.
- Move non-critical side effects such as email delivery and external notification fan-out out of the booking transaction.
- Optimize database access before adding queue infrastructure; a message broker does not make synchronous read queries faster.
- Use a transactional outbox before introducing a durable broker so database state and emitted events cannot diverge.
- Keep SSE as the browser delivery channel and treat the broker as backend work distribution, not as a frontend transport.

## Work items

### P16-T1: Establish latency and query baselines

**Scope**

- Add bounded metrics for booking commands, lab-slot reads, notification persistence, and asynchronous delivery.
- Record endpoint latency and query counts for representative small and large slot lists.
- Define measurable targets from the baseline instead of inventing fixed thresholds in advance.

**Acceptance criteria**

- Booking and slot-list endpoints expose measurable latency and failure-rate evidence.
- The baseline identifies query count and slowest operations for representative datasets.
- Metrics and logs contain identifiers needed for diagnosis without tokens or sensitive payloads.

### P16-T2: Remove slot-list N+1 queries

**Scope**

- Replace per-slot approved, checked-in, and pending count queries with a bounded aggregate query and typed projection.
- Preserve authorization, hidden-status filtering, count semantics, ordering, and response contracts.

**Acceptance criteria**

- Query count no longer grows by three queries for every returned slot.
- Existing slot-list behavior and tests remain valid.
- Repository tests cover aggregate counts for empty, mixed-status, and deleted booking data.

### P16-T3: Move side effects after booking commit

**Scope**

- Remove email network I/O from booking transactions.
- Ensure realtime notification fan-out does not delay the booking response.
- Preserve immediate persistence of the user-visible booking and notification state.

**Acceptance criteria**

- Core booking results do not depend on email provider availability.
- Side effects run only for committed state transitions.
- Failures are observable and retryable without changing a successful booking into a failure.

### P16-T4: Introduce transactional outbox

**Scope**

- Persist versioned, typed domain-event records in the same transaction as the booking change.
- Add an idempotent relay with bounded retries and explicit terminal failure handling.
- Define retention and cleanup for successfully delivered outbox records.

**Acceptance criteria**

- A committed booking event is not lost when the process stops before delivery.
- Re-delivery does not duplicate user-visible notifications or emails.
- Outbox backlog, retries, failures, and processing duration are observable.

### P16-T5: Add durable queue infrastructure

**Scope**

- Select and configure the broker only after deployment constraints are confirmed.
- Prefer RabbitMQ for the current command/worker workload unless measured requirements justify Kafka.
- Add retry, dead-letter, health, and local-development configuration without embedding credentials.

**Acceptance criteria**

- Producer and consumer contracts are versioned and backward compatible.
- Consumers are idempotent and failed messages are diagnosable and recoverable.
- The system keeps core booking consistency when the broker is unavailable.

### P16-T6: Refine asynchronous frontend states

**Scope**

- Keep immediate command feedback separate from background delivery status.
- Update React Query caches from SSE events without requiring a page refresh.
- Use explicit states for genuinely asynchronous operations: queued, processing, completed, and failed.

**Acceptance criteria**

- Users immediately see that a committed request is awaiting manager review.
- Manager decisions update the user UI through SSE and raise a visible notification with unread indication.
- Background email or fan-out failures do not display a false booking failure.
- No operation remains behind an indefinite generic loading state.

## Delivery order

1. P16-T1 baseline and instrumentation.
2. P16-T2 database query optimization.
3. P16-T3 post-commit side effects.
4. P16-T4 transactional outbox.
5. P16-T5 durable broker.
6. P16-T6 final frontend-state refinement and end-to-end verification.

Each task must be delivered as an independently reviewable commit and keep the application in a working state.

## Verification checkpoints

- After P16-T2: compare endpoint latency and query count against the P16-T1 baseline.
- After P16-T4: verify commit, rollback, retry, duplicate-delivery, and process-restart scenarios.
- After P16-T6: verify user and manager booking flows in separate browser sessions without manual refresh.
- At phase completion: run focused backend tests, the frontend production build, migration verification, and the repository's applicable regression suite.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Capacity races or duplicate bookings | Keep authoritative state transitions synchronous and transactional. |
| Database commit succeeds but event publication fails | Use the transactional outbox pattern. |
| Duplicate event delivery | Require stable event IDs and idempotent consumers. |
| Queue outage blocks booking | Keep the broker outside the core booking commit path and relay from persisted outbox records. |
| Misleading frontend success state | Distinguish committed booking state from background side-effect delivery. |
| Added infrastructure without proven benefit | Measure first and introduce the broker only after P16-T1 to P16-T3 evidence. |

## Open decision

Confirm the production deployment constraints and broker ownership before P16-T5. The current local stack includes Redis without durable persistence and does not yet include RabbitMQ or Kafka.
