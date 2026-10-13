# Historical existing-protocol baseline review
Date:2026-10-13. This reviewed100h baseline was superseded when validation selected Doggo-only strict sync. Preserve findings as research history,not active acceptance/scope. [Current decisions](design-decisions.md) and [strict contract review](strict-contract-review.md) control;implementation pending0%.

## Scope and source fit
Read all ten revised phases, architecture, contracts, decisions, ownership, dependencies and both reports. Hub source facts cited in source-review/scouts control over stale Hub docs or earlier research. User-selected Hub login+schema sync replaces Worker/D1; native signup/access requests, offline Room, guarded wipe and Android background work retained. ForumFlash light theme and Android→Linux→manual Windows order retained. No implementation/install or external source changes.

## Findings resolved in the plan
| Finding | Disposition |
|---|---|
| B1 overloaded401 | Contracts2–3; phases02/04/06 distinguish auth expiry, wrong key, missing grant and access-request service failures; bounded diagnosis, no rotation loop. |
| B2 full scan | Contracts5–7; phase05 staging upsert/later occurrence, verbatim timestamps, no snapshot guarantee; stale/missing rows and clock/order assumptions admitted. |
| B3 overclaimed race repair | Phase03 only absent-row insert-only + duplicate per-record failure; deterministic old-code-fail/new-code-pass required with mandatory Mongo and existing-app smoke. No global absent+assumed rule. Purge race/equal-version weakness remain explicit. |
| B4 false user conflicts | All conflict/record-failure replies reconciled by frozen-revision equality before review; tombstones compare version/deleted. |
| H1 refresh persistence | Any uncertain post-dispatch result conservatively requires reauth. Failed successor persistence keeps memory-only pair, pauses background and durably marks recovery via frozen callback into05 metadata. |
| H2 privileged sessions | Warn/recommend non-admin login; admin JWT remains privileged despite no admin UI. |
| H3 key recovery | One-time key custody, no fabricated rotation API; operator repair/new-app migration only. |
| H4 invalid schema/data | Explicit paused DataInvalid/ProtocolIncompatible with retained local editing; three failed reconciled attempts -> visible blocked/manual retry, never discard. |
| H5 worker/leave | Durable dispatch/generation/wipe marker, both work types canceled, callbacks plus WorkInfo awaited, startup marker before vault/DB, no lost wakeups. |
| M1–M6 | Current grant from /me; latest Doggo request; required conflicts/optional arrays; full-page extra pull; pacing429; app-wide purge caveat; all local links resolve. |

## Conservative choices versus reviewer alternatives
- No blind refresh replay, even apparently pre-handler failures; accepts extra sign-in prompts rather than treating gateway JSON/status as trustworthy execution provenance.
- Any undecodable/non2xx push after dispatch is uncertain; HTTP client never auto-replays POST. Auth recovery may proceed, but coordinator scans before replay.
- No optional second-absence scan presented as proof. No broader Hub sync hardening, receipt service or absent-row semantic change smuggled into the narrow repair.
- Admin warning rather than refusal; user may revisit during validation.

## Parallelization review
01 tooling →02 frozen contracts→03/04/05/06 independent coding→07 real Android integration→08 deployed Android release→09 Linux→10 Windows. Runtime dependencies are explicit, not hidden behind mocks. Auth recovery callback separates04 from05 persistence implementation. Original owner alone repairs contract/config defects; integration does not edit another lane's files.
09 owns desktop source/resource host selection and per-target lock mechanism;10 adds Windows provider/descriptor/test/lock only. No missing Windows class referenced by Linux, no shared desktop lock edited by both phases. Measured native proof required, not assumed from design.

## Structural validation executed
Read-only Python validator over planning markdown, no permanent test/script created:
- Ten phases: required15 headings in order; exact related-code lists match registry owner rows.
-194 unique absolute paths, zero overlaps; per-phase counts6,35,8,14,29,20,25,19,23,15.
- YAML required keys present; phase sum100h; twelve DAG edges match dependency matrix; acyclic and ideal critical path68h.
-165 local links resolved at check time; main overview69 lines, command index36 (<80).
-Current research reports28/32 lines (<=150). Subsequent status/disposition links are reporting-only changes.
No source tests/builds/lint/install run. Source review and documentation validator do not certify runtime behavior.

## Remaining gates, not hidden completion claims
Prior health probes: public documented endpoint502; documented local ports3000/15001 refused. No successful live auth/isolation/sync proof. Actual approved Hub revision/operator configuration/Mongo uniqueness/deployment/recovery, stable toolchain, fonts/vaults/signing/Xpra and Windows machine remain implementation prerequisites. First implementation step is Phase01; it has not started.

## Unresolved product questions
Core backend/onboarding/sync choices answered by user. Optional validation interview can revisit admitted current-protocol risks, admin login warning and local-only logout. No secret values needed in chat.
