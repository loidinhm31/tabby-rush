# Plan review — static artifact validation

Date: 2026-10-18. Scope: planning deliverables only. No application implementation, builds, test suites, provider sends or native runtime acceptance performed.
Plan: [overview](../plan.md); [command access point](../evc-cmd-plan.md).

## Initial observed checks (before validation propagation)
| Check | Result |
|---|---|
| Phase files | 12; all required15 sections present in requested order; pending/0% |
| Overview lengths | plan.md60 lines; evc-cmd-plan.md40 lines; both under80 |
| YAML | Required fields present; status pending, P2, branch main, created2026-10-18 |
| Phase estimates | Sum276h; matches YAML; engineering estimate, no deadline/environment-wait promise |
| Research reports | 104 and100 lines; both <=150; both researchers returned terminal COMPLETE |
| Relative Markdown links | 240 checked before this report; zero missing file targets |
| Exclusive implementation ownership | 144 unique absolute paths; zero duplicates |
| Phase tables versus canonical manifest | File sets AND actions match for every phase; File Ownership explicitly incorporates that phase's full Related code files table |
| Planned file feasibility | Every modify target exists; no create target already exists at validation time |
| Dependency declarations | 26 edges; acyclic; overview Mermaid edge set exactly matches phase start prerequisites |
| Requirement coverage | A01..A13 one-to-one with all brainstorm acceptance rows; owner tests, actual surface checks and failure gates specified |
| Conditional scout | Skipped: summary generated2026-10-17 and modified2026-10-18, under3days; no fictitious scout report |

Validation used an in-memory throwaway Python checker via eval: headings/frontmatter/links, manifest tables/actions, file existence, effort sum and graph topological ordering. No checker file or scaffolding left in repository. These checks validate organization/consistency, not implementation feasibility or behavior.

## Parent review corrections applied
- Research suggestions corrected in authoritative synthesis: bounded full audience snapshot before acceptance; reclaim expired processing leases; generic FCM ambiguity not exactly-once; exact device-version retirement; no arbitrary caller userId/global native clear/AWT fallback.
- Removed proposed install-count restriction. Durable device pages/immutable batches bound memory without adding a per-user install cap.
- Current Windows source uses PowerShell WinRT projection behind JnaWindowsToastBridge typealias; older architecture unavailable-stub prose is stale. Source evidence and proposed architecture identify this distinction; display code is not native activation proof.
- Removed shared UI temporary default/shim instruction; required host signature migration belongs09/10.
- Made cache200 a sliding page window, not a200-message server-history truncation; full unexpired history remains cursor-reachable.
- Selected access-restricted raw FCM routing-token storage rather than leaving encryption/key-service choice unresolved; install possession secret remains hashed server-side and Keystore-protected client-side.
- Assigned all affected Linux guidance/acceptance, Tabby changelog, code standards, API/auth and UI documentation to P12, preserving exclusive ownership and143 other distinct paths.

## Deliverable status
Validation interview completed with3 answers: online details only,500-recipient bound and restricted Android/Linux pilot before Windows acceptance. Answers now propagated through plan/access point, P10/P11/P12 and design/contract/execution/acceptance reports. Separate pilot/full evidence, operator-approved cohort and stop/rollback gates defined; no Windows/full-delivery scope removed. Implementation, actual pilot approval and every runtime result remain pending.

## Unresolved prerequisites
Actual Mongo durability/index/read-back; Firebase service custody/public Android config/Google-enabled device; Linux actions-capable session; Windows x64 compiler/SDK/WiX/installed MSI testbed; actual browser/two-device/native proof. Inherited G2/G3/G6/G7/checksum/Glance holds remain. No prerequisite waived by static checks.
