# EpitomeJob — Plan Validation (CTO Review)
**Plan reviewed:** Phases 0–7, multi-platform (Android + Web React + React Native), modular monolith → microservices → AKS
**Verdict: VALID, with 5 corrections. Architecture is sound. Ordering is ~80% right. Do not start Phase 5 or 7 as written.**

## What is right (keep it)
- **Modular monolith first, microservices later.** This is the correct strangler approach and matches the HLD. Splitting on day 1 would have been the mistake.
- **One working vertical slice per phase, with an exit demo.** This is the cake rule from SampAgent: never enter a broken state, never add a layer on an unverified one.
- **Security in Phase 0, not bolted on later.** JWT + roles from the start is right.
- **Brain (Phase 2) before RAG/MCP depth (Phase 3), Docker (Phase 4) before split (Phase 5), split before K8s (Phase 6), multimodal last (Phase 7).** That ordering is correct.
- **Human-in-the-loop interrupts in Phase 2.** Correct — approval is a core feature, not polish.

## Corrections — the 5 moves

### 1. Move approval gate + SSE from Phase 1 → Phase 0
Approval is safety, not polish. In SampAgent the approval gate was core, and every write went through it from the start.
**Fix:** Phase 0 exit demo must include: create a draft application → approval card appears → Approve → it runs → audit log shows it. SSE streaming for the approval inbox belongs here too.

### 2. Move version catalogs from Phase 1 → Phase 0, Day 1
SampAgent lost days to this exact mistake: KSP 2.2.10-1.0.31 did not exist, Hilt 2.52 did not support AGP 9. Versions were assembled, never compiled together.
**Fix:** `libs.versions.toml` (Android) and a Spring Boot BOM + LangGraph pinned versions on Day 1. No hardcoded versions anywhere. Every phase's first step is: build clean from a fresh checkout.

### 3. Move R8/ProGuard from Phase 1 → Phase 6 (pre-launch)
Release-build hardening in Phase 1 is premature. It adds nothing to the vertical slice and burns a day.
**Fix:** Phase 1 keeps Room/offline + design system. R8 moves to just before the AKS deploy, with a release smoke test then.

### 4. Add Temporal + observability explicitly — they are missing
The plan mentions LangGraph interrupts and "state recovery" but never names the durable-workflow engine, and observability does not appear until Phase 6.
**Fix:** Phase 2 adds Temporal (workflows, retries, timers, approval pause/resume). Phase 0 adds OpenTelemetry + structured logs + one dashboard. Phase 4's "restart mid-flight, no data loss" only works if LangGraph/Temporal state lives in Postgres/Temporal — never in memory. State that in the exit demo.
**Also fix:** "cryptographic approval gates" is vague. Define it: a signed approval token + idempotency key, so tapping Approve twice cannot apply twice, and an approval cannot be forged by replaying a request.

### 5. Fix Phase 5 split list + Phase 7 React Native
**Phase 5 list is incomplete vs the HLD's 14 services.** It lists 6 groups and merges Notification + Billing (different bounded contexts — split them). It omits Matching Engine, Interview Prep, Learning, Coach Agent, MCP Server, and RAG as separate services.
**Fix:** split in this order: MCP Server first (stateless, easiest), then Notification, then Job Discovery/Matching, then the rest. One service per phase-step, with a data-migration note per service (who owns which tables, how the monolith tables move).
**Phase 7 React Native is the biggest risk in the plan.** Three clients (Android native + Web React + React Native) triples maintenance for a solo developer.
**Fix:** choose one: (a) React Native *replaces* native Android as the mobile client, or (b) defer React Native to post-launch. Do not build and maintain all three in Phase 7.

## Gaps — add these, or they will bite later
| Gap | Where to add it |
|---|---|
| No per-phase verification checklist | Every phase exit demo needs a Postman-style pass/fail list (SampAgent Slice 1 used 15/15 tests — copy that discipline) |
| No AI eval gate in CI | Phase 6 CI must fail the deploy if LangSmith eval score drops; unit tests alone do not catch a worse model/prompt |
| No LLM cost / token budget | Add per-user token limits + cost dashboard in Phase 6; Ollama is free locally, Azure OpenAI is not |
| No PII / retention policy | Resumes are PII. Add redaction in logs, retention + delete-my-data in Phase 0/1, not at launch |
| No data migration plan for the split | Phase 5 needs: table ownership map, dual-write or backfill plan, rollback per service |
| S3 in Phase 0 with no local equivalent | Use MinIO (S3-compatible, local) in dev; real S3/Blob only in cloud. Otherwise Phase 0 cannot run offline |
| kind on a 2019 Intel Mac | kind + Ollama + Postgres + Kafka on one Mac will be slow. Set CPU/memory limits, and keep Ollama to one small model in dev |
| Phase 0 is too big for one phase | Login + profile + resume upload is Phase 0. Job Discovery is Phase 0.5 — it can wait a few days without breaking the slice |

## Realistic timeline (solo, part-time)
This is not a 1-week plan. As written, it is 8 phases × 1–2 weeks each for one developer: **roughly 2–3 months to Phase 6, Phase 7 after that.** That is normal for a production microservices platform. Do not compress it by skipping the exit demos — that is how SampAgent's enterprise package shipped uncompiled.

## Corrected order (summary)
0. Foundation + **approval gate + version catalogs + observability** (login, profile, resume upload)
1. Polish (clean architecture, Room/offline, design system) — no R8 yet
2. Brain (LangGraph supervisor + **Temporal** + signed approval interrupts)
3. Knowledge (pgvector, ingestion, MCP, hybrid search + evals)
4. Containers (Dockerfiles, Compose, **state in Postgres/Temporal**)
5. Split (MCP first, then Notification, then Job/Matching, then the rest — one at a time, with migration notes)
6. Cloud (kind → AKS, CI/CD **with eval gate**, cost limits)
7. Senses + **one** mobile strategy (RN *or* native, not both) + capstone

## Bottom line
**Approve the plan, with the 5 corrections above.** The direction is right, the layering is right, and the exit-demo discipline is right. The risks are not in the architecture — they are in Phase 0 being too big, approval/versioning being too late, Temporal/observability being missing, and Phase 7 trying to maintain three clients at once. Fix those five, and this plan will work.
