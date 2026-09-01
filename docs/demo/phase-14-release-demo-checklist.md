# Phase 14 - Reproducible release demo checklist

This checklist is a deployment-time acceptance script. It does not claim that a
deployed demo was run locally. Record every identifier and result during the
run; do not reuse production data or credentials.

## 1. Controlled setup

- [ ] Deploy the candidate commit to a restricted demo environment with a
  disposable MySQL database and Redis instance.
- [ ] Mount only the approved AI artifact bundle whose hashes match the serving
  descriptor. If it is unavailable, mark the AI happy path blocked and run the
  truthful-not-ready case instead.
- [ ] Configure the Face service and exactly one active face-security policy.
  Never upload a real biometric image; use consented synthetic demo material.
- [ ] Inject backend, frontend, mail, internal-service-token, JWT, encryption,
  CORS, and storage settings through the environment. Do not paste secret
  values into this checklist or screenshots.
- [ ] Create or select four disposable identities: Admin, Lab Manager, Student
  Leader, and Student Member. Record their generated IDs below, not passwords.
- [ ] Create one lab, one research project/group, one future slot, and one
  approved student booking owned by the Student Member.

Run manifest:

| Value | Recorded ID or commit |
| --- | --- |
| candidate commit | |
| lab | |
| project | |
| research group | |
| future slot | |
| approved booking | |
| Admin user | |
| Lab Manager user | |
| Student Leader user | |
| Student Member user | |
| AI artifact descriptor identity | |

Acceptance rule: stop the run if an identity, role, membership, booking owner,
artifact hash, or active policy differs from this manifest.

## 2. Authentication and route refusal

- [ ] Open `/admin/dashboard` without a session. Expect redirect to `/login`.
- [ ] Sign in as Student Member and open `/admin/dashboard`. Expect `/403`; no
  admin data or privileged API response may be visible.
- [ ] Sign in as Admin and open `/app/lab-slots`. Expect `/403` because Admin is
  not silently treated as Lab Manager.
- [ ] Open a login URL with `returnUrl=//example.invalid`. After successful
  login, expect the role-derived internal home path, never an external origin.
- [ ] Request a project/group belonging to a different lab or group using the
  Student Member session. Expect a denial or not-found response and no foreign
  entity fields.

Evidence: browser route, network status, actor role, target ID, and timestamp.
Do not capture bearer tokens or response bodies containing personal data.

## 3. Research proposal approval and refusal

- [ ] As Student Member, submit a task proposal through the Research UI. Verify
  it appears as `PENDING` and capture the proposal ID.
- [ ] Verify a `CREATE_TASK_PROPOSAL` audit record exists for target type
  `TASK_PROPOSAL` and the captured proposal ID.
- [ ] As an unrelated student or a member without review authority, attempt to
  approve the proposal. Expect denial; verify the proposal remains `PENDING`
  and no task was created.
- [ ] As Student Leader (or the authorized Lab Manager), approve the same
  proposal. Expect `APPROVED`, one created research task, and a notification to
  the proposal author.
- [ ] Verify one `REVIEW_TASK_PROPOSAL` audit record identifies the authorized
  reviewer and the proposal. Verify repeating approval is refused as a conflict
  and does not create a second task.
- [ ] Repeat with a new proposal and reject it with a non-sensitive reason.
  Expect `REJECTED`, no task creation, notification delivery, and a bounded
  review audit record.

Relevant active endpoints for API-assisted diagnosis:

- `POST /research/task-proposals`
- `GET /research/task-proposals?projectId={id}&groupId={id}`
- `POST /research/task-proposals/{proposalId}/approve`
- `POST /research/task-proposals/{proposalId}/reject`
- `GET /notifications`

## 4. Booking, Face check-in, and fallback

- [ ] Confirm the booking is `APPROVED`, belongs to Student Member, and is
  inside the permitted check-in window.
- [ ] With valid consent/profile and an available Face service, submit a
  synthetic face check-in. Expect a truthful match/non-match result; a
  non-match must not update attendance.
- [ ] Withdraw face consent or remove the disposable profile, then request QR
  fallback with `FACE_PROFILE_UNAVAILABLE`. Expect fallback only when the
  active policy allows it and the profile is in fact unavailable.
- [ ] As Lab Manager, confirm the issued QR token once. Expect checked-in state
  and one `QR_FALLBACK_CHECKIN` audit record. Reuse of the token must fail.
- [ ] Request `FACE_SERVICE_UNAVAILABLE` fallback without a preceding recorded
  Face `SERVICE_ERROR`. Expect refusal and no QR token.
- [ ] Produce a genuine service-unavailable failure in the controlled
  environment, retry the same fallback reason, and expect it only when policy
  permits and the latest booking evidence is the recorded service error.
- [ ] If manual override is enabled, have Lab Manager use a bounded operational
  reason. Expect one `MANUAL_CHECKIN` audit record. If disabled, expect refusal
  and no booking mutation.

Relevant active endpoints:

- `POST /face/check-in`
- `POST /checkin/qr`
- `POST /checkin/confirm`
- `POST /checkin/manual`

Never record the face image, embedding, QR token, encryption key, or internal
service token in the evidence packet.

## 5. AI assistant happy path and fail-closed behavior

- [ ] Call `POST /ai/assistants/{assistantKey}/chat` from the UI with an
  authorized capability/resource pair and an `X-Request-Id`. If the approved
  artifact is mounted and ready, expect a bounded assistant response and an
  `AI_ASSISTANT_REQUEST` audit record correlated by the safe request ID.
- [ ] Repeat with a resource outside the actor's lab/group. Expect refusal; no
  foreign context, raw downstream response, model path, or internal token may
  appear in the response or logs.
- [ ] Submit an assistant/capability mismatch or invalid resource selection.
  Expect validation/authorization refusal before model execution.
- [ ] Stop or isolate the AI service. Expect a generic retryable/unavailable
  response from Spring; no false assistant answer and no secret leakage.
- [ ] Start AI service without the approved artifact bundle. `/health` may be
  up, but `/ready` must remain not ready and assistant requests must fail
  closed. Do not mark this case as a model happy-path pass.
- [ ] Verify any proposed write remains draft/proposal-only and requires the
  existing authorized approval flow. The assistant must not directly create or
  approve a business mutation.

## 6. Audit and notification evidence

- [ ] As Admin, query `/admin/audit-logs` by actor, module, action, and the run
  time window. Confirm the expected records above appear once and denied/no-op
  paths did not create success records.
- [ ] Confirm audit metadata contains bounded identifiers/statuses only: no JWT,
  password, internal token, raw face material, embedding, unrestricted prompt,
  model filesystem path, or raw exception/downstream body.
- [ ] As each intended recipient, open notifications and mark one notification
  read. Verify another user's notification ID cannot be read by the current
  actor.
- [ ] Correlate screenshots and network evidence by timestamp/request ID. Redact
  personal data and all credentials before sharing the packet.

## 7. Completion and cleanup

| Scenario | Pass/fail/block | Evidence reference | Notes |
| --- | --- | --- | --- |
| route and cross-scope refusal | | | |
| proposal submit/approve/reject | | | |
| duplicate approval refusal | | | |
| booking and Face happy path | | | |
| QR/manual fallback policy | | | |
| AI authorized response | | | |
| AI unavailable/not-ready refusal | | | |
| audit sanitization | | | |
| notification ownership | | | |

- [ ] Export only the redacted evidence packet.
- [ ] Delete synthetic face profiles and revoke consent.
- [ ] Remove disposable accounts/data by restoring the known database snapshot,
  not by ad-hoc production deletion.
- [ ] Revoke demo secrets/tokens and remove mounted demo artifacts.
- [ ] Record unresolved blocked rows as release blockers; never convert a
  skipped or environment-blocked row into a pass.
