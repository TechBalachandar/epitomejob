# EpitomeJob — Full Java → Kotlin Migration + Microservices Completion Plan

**Branch:** `kotlin-migration` (based on `qa` @ `e546b39c`)
**Date:** 2026-11-15
**Author:** Solution Architect (overnight, while the user sleeps)
**Status:** Plan — no source code changed by this document. TheVoper implements the first code slice in parallel.

> **Status update — Nov 17, 2026 (after the migration merged):**
> - **Kotlin track: COMPLETE.** Merged as PR #23 into `main` (merge commit `74bd5a6`, Nov 17, 2026). Backend 304/304 tests green, resume-builder 60 tests green with JaCoCo branch coverage ≥ 0.70, all 9 CI checks green. Zero `.java` files remain in the five modules.
> - **PDF export: FIXED.** The 5 known-failing PDF tests (§1.6) are now green — `ExportService.exportPdf` was rewritten PDFBox-native (`cc20e29`), OpenHTMLToPDF removed. The §1.6 note no longer applies.
> - **Microservices track (5C–5F, §3 Phases 3–6): STILL PENDING.** This plan remains the roadmap for the Notification / Jobs+Matching / 5E service splits and the monolith retirement. Nothing in §3–§6 has been superseded except the Kotlin-track completion above.

**Scope of this plan (both tracks, interleaved):**
1. **Kotlin track** — migrate every Java module to Kotlin: `libs/epitomejob-common`, `gateway-service`, `resume-builder-service`, `eureka-server`, and the `backend` monolith.
2. **Microservices track** — complete the remaining Phase 5 slices: **5C** (Notification split), **5D** (Jobs+Matching), **5E** (Identity → Profile → Resume → Tracker → Interview → Coach proxy → Billing → RAG), **5F** (monolith retires).

Phase 5A+5B are already built and pushed (gateway, Eureka, MCP split, 17-service compose). Python (`agent-brain`, `mcp-service`) and the TypeScript web are **not** part of this migration.

---

## 1. Scope assessment (measured on branch `qa`, head `e546b39c`)

### 1.1 Java modules — file and line counts (measured, not estimated)

| Module | Main files | Main LOC | Test files | Test LOC | Total LOC |
|---|---|---|---|---|---|
| `backend` (monolith) | 117 | 5,399 | 41 | 6,430 | 11,829 |
| `gateway-service` | 5 | 384 | 4 | 551 | 935 |
| `resume-builder-service` | 20 | 1,743 | 12 | 1,467 | 3,210 |
| `eureka-server` | 1 | 19 | 1 | 13 | 32 |
| `libs/epitomejob-common` | 4 | 212 | 3 | 234 | 446 |
| **Total** | **147** | **7,757** | **61** | **8,695** | **16,452** |

Notes:
- 208 Java files exist in total across the repo; the table counts the 147 main + 61 test files in the five Java modules above (the remaining files are the same files counted once — 147 + 61 = 208).
- The backend is a modular monolith with 13 feature packages (`approval`, `billing`, `coach`, `common`, `identity`, `interview`, `job`, `matching`, `notification`, `profile`, `resume`, `tracker`), each with `controller / dto / model / repository / service`.
- Test LOC exceeds main LOC in the backend (6,430 vs 5,399) — the test suite is extensive and is the safety net for this migration. Do not shrink it.

### 1.2 Build and versions (measured from the poms)

- **Build:** Maven. Root aggregator `epitomejob-platform` builds `libs/epitomejob-common`, `eureka-server`, `gateway-service`. `backend` and `resume-builder-service` build standalone (own poms, `spring-boot-starter-parent` as parent). There is **no single shared parent pom** for all five modules — Kotlin config must be added per module (see §2.3).
- **Java:** 21 (`<java.version>21</java.version>` in every pom).
- **Spring Boot:** 3.5.4 everywhere. Spring Cloud 2025.0.x.
- **Dockerfiles:** all use `mvn -B -DskipTests package` (tests are skipped in image builds; CI is the test gate).
- **JaCoCo gate (backend):** LINE ≥ 0.80, BRANCH ≥ 0.70. The gate stays; it is never lowered for Kotlin.

### 1.3 Java language features in use (measured by grep)

| Feature | Count | Kotlin mapping |
|---|---|---|
| `public record` (DTOs) | **49 declarations, 42 files** | Kotlin `data class` (primary constructor = record components) |
| Lombok (`import lombok`) | **0** | Nothing to map — no Lombok in the codebase |
| Sealed classes | **0** | Nothing to map |
| Annotation processors (Lombok/MapStruct/Dagger) | **0** | None |
| `var` (local type inference) | 170 | Kotlin `val`/`var` with inference |
| Text blocks (`"""`) | 48 | Kotlin triple-quoted strings |
| Switch expressions | 7 | Kotlin `when` |
| `instanceof` pattern matching | 1 | Kotlin `is` + smart cast |
| Spring stereotypes | 23 `@Service`, 16 `@RestController`, 15 `@Entity`, 9 `@Configuration`, 8 `@Component`, 13 `@Bean`, 5 `@SpringBootApplication` | Same annotations; `kotlin-spring` (all-open) makes them proxyable |

### 1.4 JPA entities (measured)

- **12 entities in the backend** (`Job`, `User`, `Profile`, `Resume`, `TrackerEntry`, `Notification`, `Question`, `InterviewSession`, `Answer`, `RefreshToken`, `Plan`, `Entitlement`). All are hand-written POJOs with field access, explicit getters, and an explicit `protected X() {}` no-arg constructor.
- Several have **validation logic in setters** (`Job.setSource`, `Job.setSourceUrl`, `Job.setSkills`) — this moves to Kotlin custom setters, not to a separate validator.
- **Boolean getter trap (real):** `Job.isSponsorsH1b()` is called from Java (`JobDto.from()`). A Kotlin `var sponsorsH1b: Boolean` generates `getSponsorsH1b()`, **not** `isSponsorsH1b()`. Rule: convert an entity and its callers/DTOs in the **same slice** (see §5.5).

### 1.5 Tests (measured)

- **JUnit 5:** 409 `@Test`. **Mockito:** 68 `@Mock`, 16 `@ExtendWith(MockitoExtension)`, 13 `@WebMvcTest`, 7 `@SpringBootTest`, 1 `@MockBean`.
- **Mockito 5.17.0** is managed by Spring Boot 3.5.4. Mockito 5 makes the inline mock maker the **default**, so final Kotlin classes can be mocked. (This removes the classic "Mockito can't mock final classes" blocker — but see risk §6.4 for the null-safety caveat.)
- The strict-stubs rule still applies: every `when(...)` stub must be reachable on that test's code path.

### 1.6 What is NOT in scope

- Python `agent-brain` and `mcp-service` (LangGraph/Temporal/FastMCP) — stay Python.
- TypeScript `web` (Next.js) — stays TypeScript.
- The PDF-export breakage (OpenHTMLToPDF 1.0.10 vs PDFBox 3.0.2, 5 failing tests) — a **pre-existing, separate decision** (PDFBox-native renderer vs downgrade to 2.x). The Kotlin conversion must **not** try to fix it and must not be blamed for it. Those 5 tests stay known-failing until the user decides.

---

## 2. Approach decisions

### 2.1 Build tool: KEEP Maven (do not migrate to Gradle)

**Decision: Maven with `kotlin-maven-plugin`.**

Reasons:
- The entire build is Maven (CI workflows, Dockerfiles, the Mac workflow, the team's muscle memory). Switching build tools **and** languages at once is two big changes — high risk, slow, and the user wants speed.
- `kotlin-maven-plugin` is mature and Spring Boot provides plugin management for it.
- Gradle's Kotlin DSL is nicer, but it does not justify a build migration. Revisit Gradle only after Kotlin is done, if ever.
- The Dockerfiles (`mvn -B -DskipTests package`) keep working unchanged.

### 2.2 Kotlin version: 2.1.20 (override `kotlin.version`)

**Decision: Kotlin 2.1.20.**

- The Spring Boot 3.5.4 BOM manages **Kotlin 1.9.25** (verified from `spring-boot-dependencies-3.5.4.pom`). Do **not** use 1.9.25 — it is the last 1.9.x, has no K2 compiler, and is a dead end.
- Spring Boot explicitly documents customizing the Kotlin version via the `kotlin.version` property, and Spring Initializr generates Spring Boot 3.5 + Kotlin projects with **Kotlin 2.1.20**. That is the supported, well-tested combination.
- Kotlin 2.1.20 exists on Maven Central (verified). It has the K2 compiler (faster builds, better analysis).
- Do **not** jump to Kotlin 2.2.x yet — it exists, but 2.1.20 is the Initializr default for Boot 3.5 and the conservative choice. Revisit 2.2.x after the migration is green.
- Set `<kotlin.version>2.1.20</kotlin.version>` in each module pom (there is no shared parent — see §2.3). Never hardcode Kotlin artifact versions; let the BOM + the `kotlin.version` property manage them.

### 2.3 Where the Kotlin config lives

There is no single shared parent pom (each module's parent is `spring-boot-starter-parent`). So:

- **Now:** add the Kotlin plugin + dependencies **per module pom**, with identical `<kotlin.version>` and plugin configuration in each. Copy-paste is acceptable for the migration; divergence is the risk, and Quester's CI (which builds every module) catches it.
- **Follow-up (after the migration is green):** introduce a shared parent pom (or a Maven BOM) for the Kotlin configuration so the version and plugin config live in one place. Do not do this during the migration — it is a build refactor on top of a language migration.

**Per-module plugin set (standard Spring Boot Kotlin Maven layout):**
- `org.jetbrains.kotlin:kotlin-maven-plugin` with executions `compile` and `test-compile`, running **before** `maven-compiler-plugin`.
- `org.jetbrains.kotlin:kotlin-spring` (all-open) — opens `@Component`, `@Service`, `@RestController`, `@Configuration`, `@Entity` classes for Spring proxying.
- `org.jetbrains.kotlin:kotlin-jpa` (no-arg) — generates the no-arg constructor for `@Entity` classes.
- `org.jetbrains.kotlin:kotlin-stdlib` + `org.jetbrains.kotlin:kotlin-reflect`.
- `com.fasterxml.jackson.module:jackson-module-kotlin` — required for JSON in Kotlin (Spring Boot auto-configures it when present).
- `org.mockito.kotlin:mockito-kotlin` (test scope) — for Kotlin tests (see §6.4).
- Keep `maven-compiler-plugin` with `-parameters` and `spring-boot-maven-plugin` repackage as-is.

### 2.4 The ordering decision: INTERLEAVED — split into Kotlin

**Decision: interleave the two tracks. Each microservice slice lands in Kotlin.**

Three orderings were considered:

| Ordering | What happens | Verdict |
|---|---|---|
| **A. Kotlin first, then split** | Convert the whole monolith (117 files) to Kotlin, then split it into services. | **Rejected.** Big-bang Kotlin conversion before any microservices value; the monolith stays a monolith longest. |
| **B. Split first (Java), then Kotlin** | Split all services in Java, then Kotlinize each service. | **Rejected.** Converts code twice (once in the monolith context, once in the service) — wasted work, slowest. |
| **C. Interleaved — split into Kotlin** | Each split extracts a package and converts it to Kotlin in the same slice. The new service is born in Kotlin. | **Chosen.** Delivers both goals in the same slices, no double conversion, every slice independently shippable and testable. |

**Why C is fastest:** the Kotlin conversion is largely mechanical (records → data classes, POJOs → classes with properties), and the existing test suites guard each slice. Doing the split and the conversion together means each slice is verified once (tests + compose + gateway route), instead of verifying a Kotlin conversion and then re-verifying a split of the same code.

**The one sequencing constraint:** the foundation must come first (Phase 0: build setup + `libs` pilot). Everything else depends on the Kotlin build working and on the shared library being Kotlin (or at least Kotlin-compatible). After Phase 0, slices are independent and can proceed in the order below.

---

## 3. Phase plan (full scope)

Each phase has a size (S/M/L — rough, not fake precision) and done criteria. **Done criteria are the same shape for every phase:** the module/service compiles, its tests pass, and the app still runs end to end (compose up + gateway health + the affected flows). No phase merges without Quester's regression + the Mac run.

### Phase 0 — Kotlin foundation + `libs` pilot (Size: S)

- Add the Kotlin plugin set (§2.3) and `<kotlin.version>2.1.20</kotlin.version>` to `libs/epitomejob-common/pom.xml` first.
- Convert `libs/epitomejob-common` (4 files / 212 LOC + 3 test files): `JwtValidator`, `TraceIdFilter`, `ApiError`, `TraceConstants`.
  - Why libs first: it is the smallest module, has no JPA, no records, and **every other service depends on it**. Converting it first proves the build, the JJWT interop (JJWT is Java — Kotlin calls it fine), and that downstream Java modules still compile against a Kotlin library (binary compatibility).
- Prove the build: `mvn -B verify` on libs (Mac + CI), and a downstream module (gateway) still compiles against the Kotlin libs.
- **Done:** libs is Kotlin, its 3 test files pass, gateway still builds against it, Docker build still works.
- **This is the phase TheVoper is doing in parallel (first code slice).**

### Phase 1 — Infrastructure services to Kotlin (Size: S)

- **`eureka-server`** (1 file / 19 LOC): trivial. Convert the application class + config to Kotlin. Done: Eureka registers and the dashboard works.
- **`gateway-service`** (5 files / 384 LOC): convert `GatewayServiceApplication`, `RateLimitConfig`, `JwtAuthFilter`, `ThreadPoolBulkheadGatewayFilterFactory`, and the `RouteConfigTest` + other tests.
  - **Keep Reactor.** The gateway uses WebFlux `Mono`/`Flux`. Kotlin works with Reactor directly. Do **not** rewrite to coroutines — that is a separate architectural decision, not part of this migration.
  - **Do not regress the #22 fix.** The gateway `application.yml` was migrated to `spring.cloud.gateway.server.webflux.*` (commit `64b92ccb`). The Kotlin conversion must keep those keys. The `RouteConfigTest` (14 routes, MCP `http://mcp-server:8001`) must pass in Kotlin.
- **Done:** Eureka + gateway are Kotlin, gateway tests pass (including `RouteConfigTest`), gateway routes in compose, no migration warnings in the log.

### Phase 2 — `resume-builder-service` to Kotlin (Size: M)

- Convert all 20 main files / 1,743 LOC + 12 test files: `ResumeBuilderApplication`, controllers, services (`ResumeStateParser`, export service), JPA models, DTOs (records → data classes), security, config.
- **Do not touch the PDF export.** It is already broken (5 failing tests, OpenHTMLToPDF vs PDFBox 3.0.2). Convert the code as-is; those 5 tests stay known-failing and are excluded from this phase's "tests pass" criterion (documented, not hidden).
- **Done:** builder tests pass except the 5 known PDF failures, compose service up, Builder preview + parse flow work end to end.

### Phase 3 — 5C Notification split, born in Kotlin (Size: L)

- Extract the `notification` package from the monolith into a new `notification-service` **in Kotlin** (convert during extraction).
- **Own DB** (new Postgres schema/database for notifications), **first real Kafka producers/consumers**, **transactional outbox** (write the notification + the outbox row in one transaction; a relay publishes to Kafka).
- Gateway route `/notifications/**` → notification-service. Monolith `notification` package removed.
- **Done:** notification-service up in compose, notifications flow via Kafka (produce → consume → deliver), outbox relay works, monolith Notification code deleted, tests pass (service + contract).

### Phase 4 — 5D Jobs+Matching split, born in Kotlin (Size: L)

- Extract the `job` + `matching` packages into Kotlin services (one `jobs-service` with matching, or two services — decide at implementation; default to one `jobs-service` containing matching, split later only if it grows).
- **CQRS read models:** separate read models for job search/listing vs the write side. The Adzuna provider (`AdzunaJobProvider`) is converted to Kotlin.
- Gateway routes `/jobs/**`, `/matches/**` → jobs-service. Monolith `job` + `matching` packages removed.
- **Done:** jobs/matching flow end to end (Adzuna search, matching, apply links), CQRS read models work, tests pass.

### Phase 5 — 5E service splits, born in Kotlin (Size: L — 8 services)

Extract one service at a time, in this order. Each is a Kotlin-born split (convert during extraction), with its own DB where applicable, gateway route, and tests. The monolith shrinks with each slice.

| # | Service | From monolith package | Notes |
|---|---|---|---|
| 5E-1 | `identity-service` | `identity` (User, RefreshToken) | **First** — auth is the foundation the others depend on (JWT, refresh tokens). |
| 5E-2 | `profile-service` | `profile` | User profile data. Depends on identity. |
| 5E-3 | `resume-service` | `resume` | Resume upload/parse + blob storage (RustFS/MinIO). |
| 5E-4 | `tracker-service` | `tracker` | Job-application tracker. |
| 5E-5 | `interview-service` | `interview` | Interview sessions/questions/answers. |
| 5E-6 | `coach-proxy` | `coach` | Thin proxy to the Python agent-brain. The agent-brain stays Python; only the proxy is Kotlin. |
| 5E-7 | `billing-service` | `billing` | Stripe integration. Do it late (external dependency — keep it stable while the others move). |
| 5E-8 | `rag-service` | (new) | Retrieval service. Depends on the others' data; do it last. |

- **Monolith `common` packages** (`common/constants`, `common/security`, `common/util`, `common/exception`, `common/filter`, `common/config`): as services are extracted, the common code they need is moved into `libs/epitomejob-common` (Kotlin) or duplicated into the service. Do not leave a new Kotlin service depending on the Java monolith's internals — that would keep the monolith alive as a library.
- **Done (per service):** service up in compose, gateway route works, its tests pass, the monolith package is deleted, no new service depends on the monolith.

### Phase 6 — 5F monolith retires (Size: M)

- Convert whatever remains in the monolith to Kotlin (or move it to `libs` / the owning service), then **delete the `backend` module**.
- Gateway routes everything to services. Compose drops the monolith. CI drops the backend job (or renames it).
- **Done (final):** `find . -name '*.java'` in the Java modules returns **0** files. Full compose (18+ services) is green. All CI suites green (backend suite is gone; each service has its own suite). The app runs end to end (login, jobs, matching, tracker, resume, coach, billing).

---

## 4. Effort (rough — T-shirt sizes, not fake precision)

| Phase | Size | What dominates the effort |
|---|---|---|
| 0 — Foundation + libs pilot | **S** | Build setup + proving the Kotlin toolchain. The code is small. |
| 1 — Eureka + gateway | **S** | Small files; gateway is reactive but the conversion is mechanical. |
| 2 — Resume Builder | **M** | 20 files + JPA + PDF/POI; the PDF breakage is a known-failing distraction, not a conversion task. |
| 3 — 5C Notification | **L** | New service + own DB + Kafka + transactional outbox (first real eventing). |
| 4 — 5D Jobs+Matching | **L** | CQRS read models + Adzuna + matching; the largest single slice after the monolith. |
| 5 — 5E (8 services) | **L** | Each service is S–M, but there are 8 of them; the total is the largest block. |
| 6 — 5F retire | **M** | Final conversion + decommission + compose/CI cleanup. |
| **Total** | **L (multi-week)** | Not an overnight task. The plan makes it achievable in phases; the phases are ordered so value lands early and each phase is independently shippable. |

**Honest note on speed:** the user asked to move fast and accepted the risk. The interleaved order (§2.4) is the fastest path because it avoids double conversion. But the total is still a multi-week programme, not a single night. Overnight, only the plan + the Phase 0 first slice can be done and (partially) proven. Every later phase needs its own Mac run + Quester regression.

---

## 5. Interoperability rules (the contract for the transition)

These rules apply while Java and Kotlin coexist (Phases 0–5). They are mandatory, not advisory.

### 5.1 Kotlin calling Java
- Java return types are **platform types** — treat them as nullable unless annotated. Prefer safe calls (`?.`) and Elvis (`?:`) over `!!`. Never use `!!` in a production code path without a comment explaining why the value cannot be null.
- Java getters/setters become Kotlin properties (`job.title`, `job.source = x`). Java `isX()` boolean getters are the exception — see §5.5.

### 5.2 Java calling Kotlin
- Kotlin `object` → Java sees `INSTANCE`. Where Java must call it statically, add `@JvmStatic`.
- `const val` in an `object` compiles to a `public static final` field — Java access is unchanged. Use `const val` for String/primitive constants (e.g. `AppConstants`). Non-`const` `val`s are instance fields on `INSTANCE` — for constants Java still calls, use `const val` or `@JvmField`.
- Kotlin top-level functions → Java sees them on a `*Kt` class. Prefer `object` + `@JvmStatic` for anything Java calls.

### 5.3 Null-safety and Spring injection
- Prefer **constructor injection** with `val` (non-null by construction). This is the default in the codebase already.
- Avoid `@Autowired` fields; if unavoidable, use `lateinit var` (never a nullable `var` for a bean that Spring always injects).
- For `@ConfigurationProperties` / `@Value`, use non-null `val` with defaults where the property always exists.

### 5.4 Records → data classes
- Java `record` (49 DTOs) → Kotlin `data class` with `val` properties. The record's canonical constructor becomes the data-class primary constructor.
- Record static factories (e.g. `JobDto.from(Job)`) → `companion object { @JvmStatic fun from(job: Job): JobDto }`. Keep `@JvmStatic` while any Java caller remains; drop it when the callers are Kotlin.
- Jackson: data classes need `jackson-module-kotlin` (§2.3) — without it, deserialization of non-null constructor params fails.

### 5.5 Entities (JPA) — the detailed rules
- Keep **field access** (annotations on fields). Properties must be `var` (not `val`) so Hibernate can set them and proxy them.
- Use the **`kotlin-jpa` (no-arg) plugin** — it generates the no-arg constructor. Do not hand-write `protected X() {}` in Kotlin (the plugin replaces the Java pattern).
- **Custom setters with validation** (`Job.setSource`, `Job.setSourceUrl`, `Job.setSkills`) become Kotlin custom setters on the property. Keep the validation logic identical.
- **Boolean getter trap:** `var sponsorsH1b: Boolean` generates `getSponsorsH1b()`, but Java callers use `isSponsorsH1b()`. **Rule: convert an entity and its callers/DTOs in the same slice.** Do not convert `Job` to Kotlin while `JobDto` is still Java calling `isSponsorsH1b()` — it will not compile. (Alternative: rename the Kotlin property to `isSponsorsH1b` + `@JsonProperty("sponsorsH1b")`, but that renames the field and is uglier; prefer the same-slice rule.)
- **Do not use Kotlin `data class` for JPA entities.** Data-class `equals()`/`hashCode()` on entities with lazy associations causes bugs. Use a regular `class` with `var` properties.

### 5.6 Spring beans and proxying
- **Never annotate a class `open` by hand for Spring.** The `kotlin-spring` (all-open) plugin opens `@Component`, `@Service`, `@RestController`, `@Configuration`, `@Entity` automatically. Hand-written `open` is a code smell and will be removed in review.
- `@Transactional`, `@Cacheable`, `@Async`, Spring Security — all rely on proxying and therefore on all-open. Verify with a `@Transactional` service test in Phase 0.

### 5.7 Reactor (gateway) — keep it
- The gateway uses WebFlux `Mono`/`Flux`. Kotlin calls Reactor APIs directly. **Do not rewrite to coroutines** in this migration. Coroutines are a separate architectural decision for later, if ever.

### 5.8 Tests
- Convert tests to Kotlin **in the same slice as their module**. Keep JUnit 5.
- Use **`mockito-kotlin`** (`org.mockito.kotlin`) in Kotlin tests. Plain Mockito returns `null` for unstubbed calls, which violates Kotlin non-null types and throws at runtime; mockito-kotlin handles this.
- Mockito 5.17.0 (managed) already uses the inline mock maker by default, so final Kotlin classes can be mocked. Verify this in Phase 0 with a test that mocks a Kotlin `final` class.
- The strict-stubs rule still applies in Kotlin tests.

### 5.9 Constants and static utilities
- `public static final` constant classes (`AppConstants`, `TraceConstants`) → Kotlin `object` with `const val` (String/primitive) or `val` (collections). Java callers keep working for `const val`; for collection constants, Java callers use `AppConstants.Job.INSTANCE.getSOURCES()` unless `@JvmField` is added — prefer converting the callers in the same slice.
- Static utility methods → top-level functions or `object` + `@JvmStatic` (if Java still calls them).

---

## 6. Risks and mitigations

| # | Risk | Likelihood / Impact | Mitigation |
|---|---|---|---|
| 6.1 | **Spring proxying fails** — Kotlin classes are `final` by default; Spring AOP (`@Transactional`, security) needs open classes. | High / High | `kotlin-spring` (all-open) plugin (§2.3). Never hand-write `open`. Verify with a `@Transactional` service test in Phase 0. |
| 6.2 | **Jackson fails on Kotlin data classes** — without the Kotlin module, deserialization of non-null constructor params throws. | High / High | Add `jackson-module-kotlin`; Spring Boot auto-configures it. Verify with a `@WebMvcTest` round-trip (POST a DTO, read it back) in Phase 0. |
| 6.3 | **JPA entities lack a no-arg constructor** — Hibernate needs one. | High / High | `kotlin-jpa` (no-arg) plugin. Entities use `var` properties. Verify with a `@DataJpaTest` / Testcontainers IT in the builder slice. |
| 6.4 | **Mockito + Kotlin null-safety** — Mockito returns `null` for unstubbed calls; Kotlin non-null types reject it at runtime. | Medium / Medium | Use `mockito-kotlin` in Kotlin tests. Mockito 5.17 (inline by default) already handles final classes. Verify in Phase 0. |
| 6.5 | **Boolean getter mismatch (`isX` vs `getX`)** — breaks Java callers of a converted entity. | Medium / Medium | Same-slice rule (§5.5): convert the entity and its callers/DTOs together. |
| 6.6 | **Build plugin ordering** — `kotlin-maven-plugin` must run before `maven-compiler-plugin`; the Spring Boot repackage must see Kotlin classes. | Medium / High | Use the standard Spring Boot Kotlin Maven layout (§2.3). Phase 0 proves it with a trivial Kotlin file before any real conversion. |
| 6.7 | **Mixed Java/Kotlin compilation** — joint compilation is handled by `kotlin-maven-plugin`, but a module left half-converted for long is fragile. | Medium / Medium | Convert a whole module/slice at once; do not leave a module half-converted across phases. Each phase's done criteria require the module to be fully one language. |
| 6.8 | **JaCoCo coverage gate shifts** — Kotlin generates different bytecode (data-class `copy()`/`equals()`, default args), which can move coverage. | Medium / Medium | Keep the gate (LINE ≥ 0.80 / BRANCH ≥ 0.70). If Kotlin-generated code drops coverage, exclude generated methods — **never lower the gate**. |
| 6.9 | **Docker builds skip tests** (`-DskipTests`) — a broken test will not fail the image build. | Low / Medium | CI runs the full `verify` (with tests) before merge. The Docker build is not a substitute for CI. |
| 6.10 | **PDF export blame** — the 5 failing PDF tests are pre-existing; the Kotlin conversion will be blamed for them. | Medium / Low | Document them as known-failing (§1.6) and exclude them from the builder phase's done criteria. Do not fix them in the Kotlin migration. |
| 6.11 | **Gateway config regression (#22 lesson)** — the gateway YAML was migrated to `spring.cloud.gateway.server.webflux.*`; a careless edit could regress it. | Low / High | The gateway slice (Phase 1) must keep the new keys and `RouteConfigTest` must pass. Quester's gateway job (added in `6971169e`) guards it in CI. |
| 6.12 | **No verification on the architect VM** — no working Maven / no Docker here, so this plan is based on code reading + docs, not a compiled proof. | High / High | **Phase 0 is explicitly a "prove the build" phase on the Mac + CI before any large conversion.** Nothing large is converted until Phase 0 is green on the Mac and in CI. This is the single most important mitigation. |
| 6.13 | **Scope creep — the plan is big and the user wants speed** — pressure to skip verification to go faster. | High / Medium | The phased done criteria (§3) are the guardrail. Each phase merges only after Quester regression + the Mac run. Skipping verification is how the Nov 14 CI breakage happened (Adzuna commit); the user's own rule is "verify before committing." |

---

## 7. What this plan does NOT do (out of scope, decided explicitly)

- **No Gradle migration.** Maven stays (§2.1).
- **No coroutines rewrite.** Reactor stays in the gateway (§5.7).
- **No PDF-export fix.** Separate decision, separate work (§1.6).
- **No Spring Boot 4 upgrade.** Stay on Boot 3.5.4. (Boot 4 needs Kotlin 2.2+ and is a separate migration; do not combine it with this one.)
- **No Python / TypeScript changes.** Agent-brain, mcp-service, and web are untouched.
- **No `kotlinx.serialization`.** Jackson stays the JSON library (with `jackson-module-kotlin`).

---

## 8. Verification and done criteria (global)

Every phase merges only when **all** of these hold:
1. The module/service **compiles** (`mvn -B verify` on the Mac).
2. Its **tests pass** (except the 5 documented PDF failures in the builder slice).
3. The **app still runs end to end** (compose up + gateway health + the affected flows).
4. **Quester regression is green** on the phase's branch (the 5 suites + gateway job + smoke).
5. **No Java files remain** in a module once its phase is done (except the monolith, which shrinks slice by slice until Phase 6).

**Verification environment (honest):** this plan was written on a VM with **no working Maven and no Docker**, so nothing in it is compile-proven here. Verification happens on the user's Mac and in CI. Phase 0 exists precisely to prove the toolchain before anything large is converted.

---

## 9. Riskiest assumptions (marked explicitly)

1. **Kotlin 2.1.20 + Spring Boot 3.5.4 is the right combination.** Evidence: Spring Initializr generates Boot 3.5 + Kotlin 2.1.20; the BOM manages 1.9.25 but documents the `kotlin.version` override. Risk: if 2.1.20 has an incompatibility with a specific dependency (e.g. JJWT, Resilience4j, Stripe), Phase 0 will surface it — that is what Phase 0 is for.
2. **The interleaved order (split into Kotlin) is faster than Kotlin-first.** Evidence: it avoids double conversion. Risk: each slice is larger (split + conversion). Mitigation: the conversion is mechanical and the test suites guard each slice.
3. **Mockito 5.17 inline mocking works for Kotlin final classes in this codebase.** Evidence: Mockito 5 made inline the default. Risk: a specific test (e.g. mocking a Kotlin `object` or a class with inline/value-class params) may fail. Mitigation: `mockito-kotlin` + Phase 0 verification.
4. **The 5C outbox + Kafka design follows the existing Phase 5 plan.** This plan does not redesign 5C–5F; it sequences them and adds the Kotlin interleaving. The detailed event/DB design lives in the Execution Plan v3 / LLD Bible v2. If those docs and this plan disagree on a slice boundary, the Phase 5 docs win and this plan is updated.
5. **The user accepted the risk of moving fast.** The plan is aggressive by design. The phased done criteria (§8) are the guardrail — they are not optional even when moving fast.

---

## 10. Immediate next steps (for the morning)

1. **User:** re-copy the workflow once (`cp agents/quester-qa.yml .github/workflows/quester-qa.yml`, commit, push) — this activates the gateway job + the #19 smoke fix (from the overnight #22/#19 fixes). Then run `mvn -B verify` on the Mac.
2. **TheVoper (in parallel):** Phase 0 first code slice — Kotlin build setup + `libs` pilot (per `agents/thevoper.md`).
3. **Quester:** regression + smoke on the new workflow run; then regression on each Kotlin phase as it lands.
4. **Architect (this plan):** review the plan with the user in the morning; adjust the 5E service order if he prefers a different sequence (e.g. Billing earlier because of a deadline).

---

## Appendix — Source data for this plan

- Repo: `TechBalachandar/epitomejob`, branch `qa`, head `e546b39c` (2026-11-15).
- Counts measured by `find` + `wc -l` on a full `qa` checkout and by `grep` over the Java sources.
- Kotlin version: Spring Boot 3.5.4 BOM (`spring-boot-dependencies-3.5.4.pom`) manages `kotlin.version` = **1.9.25**; Spring Initializr generates Boot 3.5 + Kotlin **2.1.20** (verified to exist on Maven Central). Recommendation: override to **2.1.20**.
- Mockito 5.17.0 is the version managed by Spring Boot 3.5.4 (verified from the BOM).
- Spring Boot Kotlin support: https://docs.spring.io/spring-boot/reference/features/kotlin.html (requires Kotlin ≥ 1.7; manages the Kotlin BOM; `kotlin.version` property customizes it; Jackson Kotlin module required).
- Prior art in this repo: the user already writes Kotlin (Android / Build C, Kotlin Compose) — the language is not new to him; only the Spring Boot + Maven Kotlin toolchain is new here.
