# CLAUDE.md — working notes for this repository

Guidance for automated agents and contributors working on MittelstandGPT.
Keep this file in sync when conventions change.

## What this is

A self-hosted, GDPR-compliant **agentic RAG** assistant. Java 21 + Spring Boot
3.4.5 + **Spring AI 1.0.0**. Answers are grounded strictly in the user's
documents, with source citations, and the system abstains when the documents
don't support an answer.

## Golden rules

- **Stay Java / Spring AI native.** Do not introduce Python orchestration
  frameworks (LangGraph/CrewAI/RAGAS) into the app. Prefer Spring AI / Spring
  Boot built-ins; justify any new dependency in the commit message.
- **Never fabricate evaluation numbers.** Metrics must come from running the
  pipeline (`mvn -Peval test`). A green eval with faked scores is a failure.
- **Strict grounding.** The agent must never answer from model knowledge when
  the documents don't support it. Keep the German no-answer sentinel
  `"Diese Information ist in den vorliegenden Dokumenten nicht enthalten."`
- **No secrets in code.** Everything via env vars + Spring profiles.
- **Keep the `local` profile fully offline.** It is the GDPR / on-prem USP.
- **Commits:** clear messages, imperative mood. No mention of AI/Claude/
  assistants, no `Co-authored-by` footers. Commit each milestone. Do not
  `git push` without explicit authorization.

## Build & test

Build with Maven on a Temurin/OpenJDK **21** toolchain (`cd backend`).

| Command | Runs | Needs services |
| --- | --- | --- |
| `mvn package` / `mvn test` | Fast offline unit tests (mocked) + the local-profile wiring test. This is the CI gate. | none |
| `MI_IT=true mvn -Dtest=AgenticRagIT test` | Live agentic integration test | Ollama on :11434 |
| `mvn -Peval test` | Live evaluation gate (writes `eval/report.json`) | Ollama on :11434 |

Test conventions:
- **Unit tests** mock `VectorStore` / `RelevanceGrader` / `ChatClient` and run
  offline; they are the build gate.
- **Live integration tests** (`*IT`, e.g. `AgenticRagIT`) are guarded by
  `@EnabledIfEnvironmentVariable(MI_IT=true)` and use an in-memory
  `SimpleVectorStore` backed by real Ollama embeddings (Docker Hub rate limits
  block pulling a Qdrant image in some environments; the agent depends only on
  the `VectorStore` interface, so this exercises identical behaviour).
- The **eval gate** (`EvalRunnerTest`) is JUnit-tagged `eval`, excluded from the
  default build and run only under the `-Peval` Maven profile.
- The local model runs at **temperature 0** for faithful, reproducible answers.

## Architecture (chat package)

The old single-shot `RagService` was split and extended into:

- `RetrievalService` — provider-agnostic similarity search over `VectorStore`
  (+ optional `mi.rag.similarity-threshold`). Used by both the loop and tools.
- `KnowledgeBaseTools` — retrieval exposed as Spring AI `@Tool` methods
  (`searchKnowledgeBase`, `listDocuments`, `searchWithinDocument`) for
  model-driven calls and future MCP reuse.
- `RelevanceGrader` / `LlmRelevanceGrader` — Corrective-RAG sufficiency grader.
  Note: with the local 3B model, LLM relevance *filtering* proved too noisy, so
  recall stays deterministic (similarity search) and the grader judges
  *sufficiency* and proposes a follow-up query. Fails open.
- `GroundedAnswerService` — the grounding contract: strict German system prompt,
  the no-answer sentinel (tolerant detection), source citation.
- `AgenticRagService` — the bounded, logged, self-correcting loop
  (`retrieveCorrectively`): retrieve → grade sufficiency → reformulate →
  re-retrieve → answer. Hop count bounded by `mi.rag.max-hops`. Carries the
  chunks actually used into the answer and its citations.
- `ChatController` — unchanged contract: `POST /api/chat`, `POST /api/chat/stream`
  (SSE: `token` → `sources` → `done`).

Config knobs (env): `MI_RAG_MAX_HOPS` (3), `MI_RAG_TOPK` (4),
`MI_RAG_GRADING_ENABLED` (true), `MI_RAG_SIMILARITY_THRESHOLD` (0.0).

## Profiles (provider portability)

Backend is selected purely by Spring profile; business logic depends only on the
`VectorStore` + `ChatClient` interfaces — no provider `if/else`.

- `local` (default): Ollama (`qwen2.5:3b-instruct`) + `nomic-embed-text` +
  Qdrant. Fully offline. Azure autoconfigurations are excluded here.
- `azure`: Azure OpenAI + Azure AI Search (`application-azure.yml`, env-driven).
  Requires Azure credentials → not exercised offline; wire-verified only.

Config is split into `application.yml` (shared), `application-local.yml`,
`application-azure.yml`. Both provider starters are on the classpath; the active
one is chosen by `spring.ai.model.chat`/`embedding` plus per-profile
`spring.autoconfigure.exclude`.

## Evaluation (eval/)

- `eval/corpus/*.md` — self-contained German fixture documents.
- `eval/dataset.jsonl` — golden questions: single-doc, multi-hop, must-abstain.
- `eval/report.json` — written by each `mvn -Peval test` run (real numbers).
- Metrics: retrieval (hit-rate@k, MRR, context precision/recall), generation
  (faithfulness via `FactCheckingEvaluator`, relevance via `RelevancyEvaluator`),
  abstention accuracy. Gate: `MI_EVAL_MIN_FAITHFULNESS`, `MI_EVAL_MIN_HITRATE`.
- The gate is real: `MI_RAG_SIMILARITY_THRESHOLD=1.0` breaks retrieval →
  hit-rate 0 → gate fails.

## Observability (observability package)

- Spring AI auto-emits observations for ChatClient/ChatModel/VectorStore/tools;
  Micrometer Tracing → OpenTelemetry exports spans over OTLP/HTTP to self-hosted
  Langfuse (`docker-compose.yml`).
- Span export is opt-in via the `dev`/`prod` profile (`management.tracing.enabled`).
  The default profile makes no export attempts (stays offline); Prometheus metrics
  are always on at `/actuator/prometheus`.
- `TokenCostMetrics` records `mittelstandgpt.tokens{type=input|output}` and
  `mittelstandgpt.cost.total` (prices from `mi.cost.*`, default 0 = free local
  model). Fed from `chatResponse().getMetadata().getUsage()` in
  `GroundedAnswerService` and `LlmRelevanceGrader`.
- GDPR: prompt/response CONTENT export (`spring.ai.chat.observations.log-prompt`/
  `log-completion`) is OFF by default and under `prod`; ON only under `dev`.
  `ProdTelemetryTest` guards this.
- Langfuse can't be pulled/run in every sandbox (image rate limits); the compose
  wiring is correct (validated with `docker compose config`) and wire-verified.

## Environment notes

- Docker daemon may be remote/unavailable; use named volumes, not host bind
  mounts. When a path can't be exercised offline (Azure, or pulling images under
  Docker Hub rate limits), say so and mark it wire-verified — never fake results.
