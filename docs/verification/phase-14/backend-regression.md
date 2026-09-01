# P14-T1 Backend Regression Evidence

- Date: 2026-09-01 (Asia/Saigon)
- Source commit: `338cbdb` (`dev`)
- Command: `server/mvnw.cmd test`
- Result: `BUILD SUCCESS`
- Tests: 1,551 run; 0 failures; 0 errors; 32 skipped
- Duration: 5 minutes 30 seconds

The regression covers the integrated Spring backend, including research, lab,
booking, check-in, audit, system configuration, notification, and AI runtime
tests present in the Maven test suite.

The 32 skipped tests are environment-gated MySQL/Testcontainers tests. This run
therefore proves the default Maven regression suite on the local H2-backed test
profile; it does not claim real-MySQL or deployed-environment verification.
