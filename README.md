# MittelstandGPT

Selbst-gehosteter, **DSGVO-konformer RAG-Wissensassistent**: Dokumente (PDF, DOCX,
TXT) hochladen und in natürlicher Sprache Fragen stellen. Die Antworten kommen von
einem **lokalen LLM – inklusive Quellenangaben**. Es verlässt **kein einziges Byte**
den Server: kein OpenAI, keine Cloud.

> _Self-hosted, GDPR-compliant RAG knowledge assistant. Upload documents, ask
> questions in natural language, get answers from a local LLM with source
> citations — nothing ever leaves your server. (English section below.)_

---

## 🇩🇪 Deutsch

### Was ist das?

MittelstandGPT beantwortet Fragen zu Ihren eigenen Dokumenten. Hochgeladene Dateien
werden in Textabschnitte zerlegt, als Vektoren gespeichert und bei einer Frage werden
die passendsten Abschnitte gesucht und einem lokalen Sprachmodell als Kontext
übergeben. Das Modell antwortet **ausschließlich auf Basis dieser Dokumente** und gibt
die verwendeten Quellen (Datei + Seite) an. Steht die Antwort nicht in den Dokumenten,
sagt es das ehrlich.

Die Suche ist **agentisch und selbstkorrigierend** (Corrective-RAG): Statt einmalig
die Top-Treffer zu übernehmen, durchläuft das System eine **begrenzte Schleife** aus
_Suchen → Relevanz/Vollständigkeit prüfen → Anfrage umformulieren → erneut suchen_ und
antwortet erst, wenn der gesammelte Kontext ausreicht. So lassen sich auch Fragen
beantworten, deren Antwort über **mehrere Dokumente** verteilt ist – jeweils mit den
tatsächlich verwendeten Quellen. Die Anzahl der Schleifendurchläufe ist nach oben
begrenzt (`MI_RAG_MAX_HOPS`, Standard 3).

### Architektur

```
                          ┌─────────────────────────────┐
                          │          BROWSER            │
                          │   React-UI (Chat + Upload)  │
                          └──────────────┬──────────────┘
                                         │ REST / SSE
                                         ▼
                          ┌─────────────────────────────┐
                          │   BACKEND (Spring Boot)      │
   Upload  ──────────────►│  Tika → Chunking → Embedding │
   (PDF/DOCX/TXT)         │  Agentische Schleife ↻ → LLM │
                          └─────────┬──────────┬─────────┘
                       Embeddings/  │          │  Vektor-Suche
                       Generierung  ▼          ▼
                          ┌──────────────┐  ┌──────────────┐
                          │   OLLAMA     │  │   QDRANT     │
                          │ (LLM lokal)  │  │ (Vektor-DB)  │
                          └──────────────┘  └──────────────┘

        ↑ Alles in Docker. Alles auf dem eigenen Server. Keine Cloud. ↑
```

| Schicht        | Technologie                                                  |
| -------------- | ------------------------------------------------------------ |
| Frontend       | React + TypeScript + Vite + Tailwind CSS                     |
| Backend / API  | Java 21 + Spring Boot 3.4 + Spring AI 1.0                    |
| Agentik        | Spring AI Tools + Advisors – Corrective-RAG-Schleife        |
| LLM (lokal)    | Ollama – `qwen2.5:3b-instruct`                              |
| Embeddings     | Ollama – `nomic-embed-text`                                 |
| Vektor-DB      | Qdrant                                                       |
| Dok.-Parsing   | Apache Tika (DOCX/TXT) + PDFBox (PDF, seitengenau)          |
| Orchestrierung | Docker Compose                                              |

### Schnellstart

Voraussetzung: **Docker** und **Docker Compose**. Sonst nichts – kein lokales Java,
Node oder Python nötig.

```bash
docker compose up --build
```

Beim **ersten Start** lädt der `ollama-init`-Dienst die Modelle herunter
(`qwen2.5:3b-instruct` ≈ 2 GB und `nomic-embed-text` ≈ 0,3 GB). Das kann einige
Minuten dauern; danach liegen die Modelle im Docker-Volume und der Start ist schnell.
Das Backend startet erst, wenn die Modelle bereitstehen.

Danach erreichbar:

| Dienst             | URL                                    |
| ------------------ | -------------------------------------- |
| **Frontend (UI)**  | http://localhost:5173                  |
| Backend-API        | http://localhost:8080/api/health       |
| KI-Verbindung      | http://localhost:8080/api/health/ai    |
| Qdrant-Dashboard   | http://localhost:6333/dashboard        |

Ollama läuft nur intern (`http://ollama:11434` im Docker-Netz, kein Host-Port).

### Bedienung

1. Oben rechts auf **„Dokumente"** klicken und PDF/DOCX/TXT hochladen
   (Drag & Drop oder Durchsuchen).
2. Im Chat eine Frage stellen – die Antwort erscheint Wort für Wort, darunter die
   **Quellen** (Datei · Seite). Enter sendet, Umschalt+Enter macht eine neue Zeile.

### Konfiguration (Umgebungsvariablen)

Alle Werte sind optional – die App läuft ohne `.env` mit sinnvollen Vorgaben.
Zum Überschreiben `.env.example` nach `.env` kopieren:

```bash
cp .env.example .env
```

| Variable                 | Vorgabe              | Bedeutung                          |
| ------------------------ | -------------------- | ---------------------------------- |
| `OLLAMA_CHAT_MODEL`      | `qwen2.5:3b-instruct`| Sprachmodell für die Antworten     |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text`   | Modell für die Vektor-Einbettung   |
| `QDRANT_COLLECTION`      | `mittelstandgpt`     | Name der Qdrant-Collection         |
| `MI_RAG_MAX_HOPS`        | `3`                  | Max. Such-/Korrektur-Durchläufe    |
| `MI_RAG_TOPK`            | `4`                  | Treffer pro Vektor-Suche           |
| `MI_RAG_GRADING_ENABLED` | `true`               | Relevanz-/Vollständigkeitsprüfung  |

### Modell tauschen

Größeres/anderes Modell wählen (z. B. für bessere Qualität auf stärkerer Hardware):

```bash
# .env
OLLAMA_CHAT_MODEL=qwen2.5:7b-instruct
```

Dann `docker compose up` neu starten – das neue Modell wird automatisch geladen.
Wird das **Embedding-Modell** geändert, ändert sich i. d. R. die Vektor-Dimension;
in dem Fall die Qdrant-Collection neu anlegen (anderen `QDRANT_COLLECTION`-Namen
verwenden oder das Qdrant-Volume zurücksetzen) und die Dokumente erneut hochladen.

### Backend wechseln: lokal oder Azure (Profile)

Das Backend ist **provider-portabel**: derselbe Agent-, Eval- und
Observability-Code läuft entweder vollständig lokal oder auf Azure – ausgewählt
allein über ein Spring-Profil, **ohne Code-Änderung** (die Geschäftslogik hängt nur
von den Spring-AI-Schnittstellen `VectorStore` und `ChatClient` ab).

- `local` (Standard): Ollama + Qdrant, vollständig offline – der DSGVO-USP.
- `azure`: Azure OpenAI (Chat + Embeddings) + Azure AI Search.

```bash
SPRING_PROFILES_ACTIVE=azure \
  AZURE_OPENAI_API_KEY=... AZURE_OPENAI_ENDPOINT=https://<res>.openai.azure.com \
  AZURE_OPENAI_CHAT_DEPLOYMENT=gpt-4o-mini \
  AZURE_OPENAI_EMBEDDING_DEPLOYMENT=text-embedding-3-small \
  AZURE_AISEARCH_ENDPOINT=https://<svc>.search.windows.net AZURE_AISEARCH_API_KEY=... \
  docker compose up
```

- **Daten/DSGVO-Abwägung:** Das `azure`-Profil sendet Inhalte an Azure OpenAI –
  dafür bewusst eine **EU-Region** wählen und Azures Datenverarbeitung prüfen. Das
  `local`-Profil hält alles on-prem. Eine bewusste, dokumentierte Architektur-Wahl.
- **Re-Embedding:** nomic-embed-text (768 Dim.) und Azure `text-embedding-3-*` haben
  unterschiedliche Vektor-Dimensionen; jedes Profil nutzt daher seinen eigenen
  Index. Ein Profilwechsel erfordert ein erneutes Einbetten der Dokumente.
- Die Verdrahtung des `azure`-Profils ist getestet (offline, mit Dummy-Zugangsdaten);
  ein echter Lauf erfordert Azure-Zugangsdaten und ist daher CI-übersprungen.

### Qualität & Evaluation

Die Antwortqualität wird mit **echten Zahlen** gemessen und kann den Build
absichern – nichts wird geschätzt oder fest verdrahtet. Ein Golden-Datensatz und
ein kleiner Beispiel-Korpus liegen im Verzeichnis `eval/` und sind eigenständig.

```bash
cd backend
mvn -Peval test      # benötigt das lokale Ollama; schreibt eval/report.json
```

Gemessen werden **Trefferquote@k**, **MRR**, **Kontext-Precision/Recall**
(Retrieval) sowie **Faithfulness** (Spring AI `FactCheckingEvaluator`) und
**Antwort-Relevanz** (`RelevancyEvaluator`) für die Generierung, dazu die
**Abstaining-Korrektheit** für Fragen ohne Beleg. Mit dem lokalen Modell
`qwen2.5:3b-instruct` gemessen: Faithfulness ≈ 1,0, Trefferquote@k = 1,0, MRR =
1,0, Kontext-Recall ≈ 0,95. (Die Antwort-Relevanz fällt mit dem schwachen
3B-Bewertungsmodell niedriger und schwankt – daher kein Gate-Kriterium.)

Der Build bricht ab, wenn `MI_EVAL_MIN_FAITHFULNESS` (Standard 0,90) oder
`MI_EVAL_MIN_HITRATE` (Standard 0,80) unterschritten werden. Dass das Gate echt
ist, lässt sich beweisen: `MI_RAG_SIMILARITY_THRESHOLD=1.0 mvn -Peval test`
liefert keine Treffer → Trefferquote 0 → Build schlägt fehl.

### Observability (Langfuse)

Jede Chat-Anfrage erzeugt einen **Trace** mit den einzelnen Retrieval-Hops,
Tool-/Modell-Aufrufen, Token-Zahlen, Latenz pro Schritt und geschätzten Kosten –
sichtbar im **selbst-gehosteten Langfuse**. `docker compose up` startet Langfuse
gleich mit (Postgres, ClickHouse, Redis, MinIO, web, worker); Dashboard unter
http://localhost:3000 (Demo-Login `admin@mittelstandgpt.local` /
`changeme-langfuse`). Die Telemetrie verlässt den Server **nicht**.

- **DSGVO:** Prompt- und Antwort-**Inhalte** werden nur im Profil `dev`
  exportiert, im Profil `prod` **nie** (nur Spans, Token, Latenz, Kosten). In der
  Compose-Vorgabe ist `local,dev` aktiv; für den Produktivbetrieb `…,prod` setzen.
- **Metriken:** Token- und Kostenzähler unter
  http://localhost:8080/actuator/prometheus (`mittelstandgpt_tokens`,
  `mittelstandgpt_cost_total`). Modellpreise via `MI_COST_INPUT_PER_1K` /
  `MI_COST_OUTPUT_PER_1K` (Standard 0 – das lokale Modell ist kostenlos).
- Der Langfuse-Stack ist ressourcenintensiv; die Kern-App hängt nicht von ihm ab
  und läuft auch ohne ihn (der Trace-Export schlägt dann still fehl).

### MCP-Server (Wissensbasis für Claude Desktop/Code)

Die Retrieval-Werkzeuge der Wissensbasis werden zusätzlich über das **Model
Context Protocol (MCP)** als SSE/HTTP-Server unter
`http://localhost:8080/mcp/sse` bereitgestellt. Damit lässt sich dieselbe
Wissensbasis direkt aus MCP-Clients wie **Claude Desktop** oder **Claude Code**
nutzen (`searchKnowledgeBase`, `listDocuments`, `searchWithinDocument`). Es kommen
exakt dieselben `@Tool`-Methoden wie im Agenten zum Einsatz, sodass MCP nie vom
Produktionsverhalten abweicht. Abschaltbar mit `MI_MCP_ENABLED=false`.

### Datenschutz / DSGVO / On-Premise

- **Keine Cloud, keine externen Aufrufe.** LLM, Embeddings und Vektor-DB laufen
  vollständig lokal in Docker. Dokumente und Anfragen verlassen den Server nicht.
- Die Schriftart ist **selbst gehostet** (kein Google-Fonts-CDN – in Deutschland ein
  bekanntes DSGVO-Thema).
- Qdrant-**Telemetrie ist deaktiviert** (`QDRANT__TELEMETRY_DISABLED=true`).
- Einzige ausgehende Verbindung: der **einmalige Modell-Download** beim ersten Start
  (von der Ollama-Registry). Danach ist kein Internet mehr nötig; für einen komplett
  luftdichten Betrieb kann die Ollama-Registry gespiegelt oder das Volume vorab
  befüllt werden.

### Projektstruktur

```
.
├── docker-compose.yml      # ollama, ollama-init, qdrant, backend, frontend
├── .env.example            # konfigurierbare Modelle / Collection
├── backend/                # Java 21 + Spring Boot 3.4 + Spring AI
│   └── src/main/java/com/mittelstandgpt/
│       ├── chat/           # Agentische RAG-Schleife + Streaming (/api/chat)
│       ├── document/       # Ingestion + Upload (/api/documents)
│       └── controller/     # Health-Endpoints
└── frontend/               # React + TS + Vite + Tailwind
    └── src/{components,hooks,lib}
```

### Fehlersuche

- **Container starten, aber erreichen sich nicht** (Anfragen laufen in einen Timeout):
  meist blockiert die Host-Firewall die `FORWARD`-Kette für Docker-Bridges. Sicher­
  stellen, dass Docker weitergeleitete Pakete zwischen Containern zulässt (z. B.
  `iptables -P FORWARD ACCEPT` bzw. eine entsprechende nftables-Regel) oder den
  Docker-Daemon neu starten.
- **Erste Antwort dauert lange**: Beim ersten Aufruf lädt Ollama das Modell in den
  Speicher; danach geht es deutlich schneller.

---

## 🇬🇧 English

### What it is

MittelstandGPT answers questions about **your own documents**. Uploaded files are
split into chunks, embedded as vectors and stored in Qdrant. For each question the
most relevant chunks are retrieved and passed to a local language model as context.
The model answers **only from those documents** and cites its sources (file + page);
if the answer isn't in the documents, it says so.

Retrieval is **agentic and self-correcting** (Corrective-RAG): instead of a single
top-k lookup, the system runs a **bounded loop** of _search → grade relevance and
sufficiency → reformulate → search again_, answering only once the gathered context
is enough — so questions whose answer spans **multiple documents** are handled too,
each with the sources actually used. The hop count is bounded (`MI_RAG_MAX_HOPS`,
default 3).

Everything runs locally in Docker — **no cloud, no external API calls** — so it is
suitable for GDPR-sensitive, on-premise use.

### Quick start

Requires only **Docker** and **Docker Compose**:

```bash
docker compose up --build
```

On the **first run**, the `ollama-init` service downloads the models
(`qwen2.5:3b-instruct` ≈ 2 GB, `nomic-embed-text` ≈ 0.3 GB) into a Docker volume; the
backend waits until they are ready. Then open **http://localhost:5173**.

### Configuration

All variables are optional (sensible defaults). Copy `.env.example` to `.env` to
override:

| Variable                 | Default               | Purpose                      |
| ------------------------ | --------------------- | ---------------------------- |
| `OLLAMA_CHAT_MODEL`      | `qwen2.5:3b-instruct` | Chat / generation model      |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text`    | Embedding model              |
| `QDRANT_COLLECTION`      | `mittelstandgpt`      | Qdrant collection name       |
| `MI_RAG_MAX_HOPS`        | `3`                   | Max retrieve/correct hops    |
| `MI_RAG_TOPK`            | `4`                   | Chunks fetched per search    |
| `MI_RAG_GRADING_ENABLED` | `true`                | Relevance/sufficiency grading|

**Swapping the model:** set `OLLAMA_CHAT_MODEL` and restart `docker compose up`. If
you change the **embedding** model, the vector dimension usually changes — use a new
`QDRANT_COLLECTION` (or reset the Qdrant volume) and re-upload your documents.

### Backend: local or Azure (profiles)

The backend is **provider-portable**: the same agent, eval and observability code
runs either fully local or on Azure, selected purely by a Spring profile (business
logic depends only on the Spring AI `VectorStore` + `ChatClient` interfaces — no
provider `if/else`).

- `local` (default): Ollama + Qdrant, fully offline.
- `azure`: Azure OpenAI + Azure AI Search — set `SPRING_PROFILES_ACTIVE=azure` plus
  the `AZURE_OPENAI_*` / `AZURE_AISEARCH_*` env vars.

**Data-residency trade-off:** the `azure` profile sends content to Azure OpenAI
(choose an EU region); `local` keeps everything on-premises. The two embedding models
differ in vector dimension, so each profile uses its own index and switching profiles
requires re-embedding. The azure wiring is tested offline with dummy credentials; a
live run needs Azure credentials and is CI-skipped.

### REST API

| Method | Endpoint              | Description                                  |
| ------ | --------------------- | -------------------------------------------- |
| POST   | `/api/documents`      | Upload a document (multipart) — parse, chunk, embed, store |
| GET    | `/api/documents`      | List ingested documents                      |
| POST   | `/api/chat`           | Ask a question → `{ answer, sources }`       |
| POST   | `/api/chat/stream`    | Same, streamed as Server-Sent Events         |
| GET    | `/api/health`         | Liveness                                     |
| GET    | `/api/health/ai`      | Connectivity to Ollama + Qdrant              |

### Evaluation

Answer quality is measured with **real numbers** and can gate the build — never
estimated or hardcoded. A golden dataset and a small fixture corpus live in
`eval/` and run offline:

```bash
cd backend && mvn -Peval test   # needs local Ollama; writes eval/report.json
```

Retrieval metrics (hit-rate@k, MRR, context precision/recall) plus generation
faithfulness (Spring AI `FactCheckingEvaluator`), answer relevance
(`RelevancyEvaluator`) and abstention correctness. Measured with the local
`qwen2.5:3b-instruct`: faithfulness ≈ 1.0, hit-rate@k = 1.0, MRR = 1.0, context
recall ≈ 0.95 (answer relevance is lower and noisy with the small judge model, so
it is not a gate metric). The build fails below `MI_EVAL_MIN_FAITHFULNESS`
(default 0.90) or `MI_EVAL_MIN_HITRATE` (default 0.80). The gate is real:
`MI_RAG_SIMILARITY_THRESHOLD=1.0 mvn -Peval test` breaks retrieval → hit-rate 0 →
build fails.

### Observability (Langfuse)

Every chat request produces one **trace** with the retrieval hops, tool/model
calls, token counts, per-stage latency and estimated cost, viewable in
**self-hosted Langfuse**. `docker compose up` also starts Langfuse (Postgres,
ClickHouse, Redis, MinIO, web, worker); dashboard at http://localhost:3000 (demo
login `admin@mittelstandgpt.local` / `changeme-langfuse`). Telemetry never leaves
the server. Prompt/response **content** is exported only under the `dev` profile,
never under `prod` (GDPR) — compose defaults to `local,dev`. Token and cost
metrics are at http://localhost:8080/actuator/prometheus
(`mittelstandgpt_tokens`, `mittelstandgpt_cost_total`); model prices via
`MI_COST_INPUT_PER_1K` / `MI_COST_OUTPUT_PER_1K`. The core app does not depend on
Langfuse and runs without it (trace export then fails silently).

### MCP server (knowledge base for Claude Desktop/Code)

The knowledge-base retrieval tools are also served over the **Model Context
Protocol** as an SSE/HTTP server at `http://localhost:8080/mcp/sse`, so the same
knowledge base is usable directly from MCP clients such as **Claude Desktop** /
**Claude Code** (`searchKnowledgeBase`, `listDocuments`, `searchWithinDocument`).
It reuses the exact `@Tool` methods the agent uses, so MCP never diverges from
production behaviour. Disable with `MI_MCP_ENABLED=false`.

### Privacy / GDPR

No cloud and no external calls at runtime: the LLM, embeddings and vector DB all run
locally. The web font is self-hosted (no Google Fonts CDN), and Qdrant telemetry is
disabled. The only outbound traffic is the one-time model download on first start.
