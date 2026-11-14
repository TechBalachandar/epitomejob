# resume-builder-service (RB1)

Resume Builder microservice: parse an uploaded resume (PDF/DOC/DOCX),
edit it against a template, export PDF/DOCX, get AI Co-Pilot suggestions,
and save the result as a labelled resume in the monolith (W6 multi-resume
list). Split out of the monolith — the monolith is untouched.

Base: live repo main @ `3855e76` (verified: `git` HEAD
`3855e7668ed170bdf6a95ab7b816c1ad4e0ef9b9`, "Remove agent-brain/app/mcp").

## Endpoints (all under the gateway: `http://localhost:8080/resume-builder/...`)

| Method | Path | Notes |
|---|---|---|
| GET | `/resume-builder/templates` | 3 templates: modern, classic, minimal (id, name, description, HTML+CSS, JSON layout) |
| POST | `/resume-builder/parse` | multipart `file` (PDF/DOC/DOCX, ≤10 MB) → structured `state` + `rawText` |
| POST | `/resume-builder/documents` | `{title?, templateId, state?}` → 201; blank state when `state` omitted |
| GET | `/resume-builder/documents` | caller's documents, newest first |
| GET | `/resume-builder/documents/{id}` | owner-only (other users get 404) |
| PUT | `/resume-builder/documents/{id}` | full state replace; `state` required |
| POST | `/resume-builder/documents/{id}/copilot` | proxy to agent-brain, JSON verbatim; agent-brain down → 502 |
| POST | `/resume-builder/documents/{id}/export?format=pdf\|docx` | file bytes + `Content-Disposition: attachment` |
| POST | `/resume-builder/documents/{id}/save-to-profile` | `{label}` → `{resumeId, label}` via monolith POST /resumes |

Auth: the gateway validates the JWT (JwtAuthFilter GlobalFilter). The
service re-validates it (JwtAuthFilter + JwtTokenService, same secret and
same raw-UTF-8-bytes key derivation as the monolith/gateway) and re-checks
ownership on every document (`findByIdAndUserId` — another user's document
is a 404, never a 403 that confirms it exists). Refresh tokens are rejected.

## State shape (the `state` JSON stored per document)

```json
{
  "contact": {"fullName": "", "email": "", "phone": "", "linkedin": "", "location": ""},
  "summary": "",
  "experience": [{"title": "", "company": "", "startDate": "", "endDate": "", "bullets": [""]}],
  "education": [{"degree": "", "school": "", "year": ""}],
  "skills": [""],
  "rawText": ""
}
```

`rawText` always holds the full extracted text — anything the parser
cannot structure is preserved there, never silently dropped.

## Run

```bash
# from the repo root
docker compose up -d --build resume-builder   # internal only on 8082, behind the gateway
# or bare-metal (needs Postgres + Eureka on localhost)
cd resume-builder-service && mvn -B verify
```

## Environment

| Var | Default | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/epitomejob` | shared Postgres instance; isolation is the `resume_builder` Flyway schema |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `epitomejob` / `epitomejob-dev` | |
| `APP_JWT_SECRET` | dev-only default (same as monolith/gateway) | must match the gateway's secret |
| `EUREKA_URL` | `http://localhost:8761/eureka` | compose/k8s: `http://eureka:8761/eureka` |
| `JAVA_BASE_URL` | `http://localhost:8081` | monolith, direct (5A decision) |
| `AGENT_BRAIN_URL` | `http://localhost:8000` | agent-brain, direct |
| `SERVER_PORT` | `8082` (also the jar default) | backend 8081, gateway 8080 |

## Decisions (deviations / judgement calls)

1. **Schema, not a separate database.** The task said "own Postgres
   DB/schema `resume_builder`". This service uses the shared Postgres
   instance with its own Flyway schema `resume_builder` (own tables, own
   `flyway_schema_history`, zero changes to the monolith's V1–V8). Reason:
   it works on the existing `backend_pgdata` volume with no postgres-service
   change and no CREATE DATABASE step (the same reason Temporal needed a
   manual step). A dedicated database is a one-line datasource change.
2. **Standalone service, no `epitomejob-common` dependency.** The gateway
   depends on the shared lib (its Dockerfile copies `libs/`), but this
   service keeps its own small constants/security copy so its build context
   is just `./resume-builder-service` (like the backend's Dockerfile).
3. **Gateway fallback URI is `forward:/fallback/resume-builder`.** The
   matching method was added to the gateway's `FallbackController` by the
   coordinator during verification (it only had `/fallback/backend` and
   `/fallback/mcp` on live main).
   ```java
   @RequestMapping("/fallback/resume-builder")
   public Mono<ResponseEntity<ApiError>> resumeBuilderFallback(ServerWebExchange exchange) {
     return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
         .body(new ApiError("SERVICE_UNAVAILABLE",
             "The Resume Builder is temporarily unavailable. Please try again.",
             traceId(exchange))));
   }
   ```

## Known gaps (found against live main 3855e76 — NOT fixed here)

1. **Agent-brain `POST /resume-builder/copilot` ships in the agent-brain
   zip** (the Co-Pilot worker's deliverable) — it was missing on live main
   3855e76, so the proxy targets an endpoint that only exists once both
   zips land. The proxy itself is proven by `CopilotProxyServiceTest` +
   the journey IT (stubbed endpoint).
2. **The monolith's `POST /resumes` accepts PDF only** (live
   `ResumeService`: non-PDF → 400 "Phase 0 accepts PDF only (DOCX comes in
   Phase 3)"). Save-to-profile therefore uploads the PDF rendering of the
   document (coordinator fix — it originally rendered DOCX and would have
   400'd). DOCX stays a download-only export. Proven by
   `SaveToProfileServiceTest` + the journey IT (stubbed backend).
3. **OpenHTMLToPDF 1.0.10 was compiled against PDFBox 2.0.24 / FontBox
   2.0.24** (its parent POM pins `<pdfbox.version>2.0.24</pdfbox.version>`),
   while this service parses with PDFBox 3.0.2 per the brief. The pom
   declares PDFBox 3.0.2 first so Maven mediation picks 3.0.2 (and FontBox
   3.0.2 wins the depth tie), but whether 1.0.10 calls any PDFBox/FontBox
   API that changed in 3.x is only proven by running the export tests.
   If `ExportServiceTest` / the journey IT fail with `NoSuchMethodError`,
   the fallback is a PDFBox-3-native PDF renderer in `ExportService`.
4. **JWT key derivation is assumed, not proven.** `JwtTokenService` uses
   the raw UTF-8 bytes of `APP_JWT_SECRET` (the standard JJWT approach
   and the monolith's documented fix). If the gateway derives its key
   differently (e.g. Base64-decoded), gateway-minted tokens will 401 here;
   that is a one-line change in `JwtTokenService`.
5. **Legacy DOC fixture is an empty HWPF document.** POI HWPF has no
   convenient writer for populated `.doc` files, so the DOC parse test
   proves the code path (valid OLE2 → WordExtractor) but not content
   extraction from a real-world `.doc`. Manual check with a real `.doc`
   is a Mac-gate item.

## Mac gate (must run on the Mac — this VM has no Java/Maven/Docker)

```bash
cd resume-builder-service && mvn -B verify        # unit + slice + journey IT; JaCoCo 80%/70%
docker compose build resume-builder && docker compose up -d resume-builder
curl -s http://localhost:8082/actuator/health
# gateway-minted token must validate here:
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/resume-builder/templates
bash k8s/deploy-kind.sh                            # includes the resume-builder rollout
```

## Tests

- `ResumeStateParserTest` — contact/summary/experience/education/skills + rawText preservation.
- `ResumeParseServiceTest` — PDF/DOCX/DOC fixtures built in-test (PDFBox/XWPF/HWPF); empty/unsupported/corrupt → 400.
- `TemplateServiceTest` — 3 templates, HTML + layout, render substitution + HTML escaping.
- `ExportServiceTest` — PDF starts `%PDF-` (all 3 templates); DOCX is a readable Word doc.
- `JwtTokenServiceTest` — valid / no-type / refresh-rejected / expired / wrong-secret / garbage.
- `JwtAuthFilterTest` — valid → chain; missing/bad → 401, chain never called; /actuator/health skips.
- `ResumeDocumentServiceTest` — Mockito; every `when(...)` traced to its test's code path (see the header comment).
- `CopilotProxyServiceTest` — real JDK HttpServer stub: 200 verbatim, 401/500 forwarded, unreachable → 502.
- `SaveToProfileServiceTest` — real stub: 201 → resume id; backend 400 passes through; blank label → 400; unreachable → 502.
- `ResumeBuilderControllerTest` — @WebMvcTest, filters off; all 9 endpoints incl. bad-format export (no export stub — it is never reached).
- `ResumeBuilderJourneyIT` — Testcontainers Postgres + Flyway V1: parse → create → get (incl. other-user 404) → PUT → copilot → export pdf+docx → save-to-profile → no-token 401.
