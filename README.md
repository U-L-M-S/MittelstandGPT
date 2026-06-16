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
   (PDF/DOCX/TXT)         │  Retrieval → Prompt → LLM    │
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
| Backend / API  | Java 21 + Spring Boot 3.4 + Spring AI                        |
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

| Variable                 | Vorgabe              | Bedeutung                         |
| ------------------------ | -------------------- | --------------------------------- |
| `OLLAMA_CHAT_MODEL`      | `qwen2.5:3b-instruct`| Sprachmodell für die Antworten    |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text`   | Modell für die Vektor-Einbettung  |
| `QDRANT_COLLECTION`      | `mittelstandgpt`     | Name der Qdrant-Collection        |

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
│       ├── chat/           # RAG-Query + Streaming (/api/chat)
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

**Swapping the model:** set `OLLAMA_CHAT_MODEL` and restart `docker compose up`. If
you change the **embedding** model, the vector dimension usually changes — use a new
`QDRANT_COLLECTION` (or reset the Qdrant volume) and re-upload your documents.

### REST API

| Method | Endpoint              | Description                                  |
| ------ | --------------------- | -------------------------------------------- |
| POST   | `/api/documents`      | Upload a document (multipart) — parse, chunk, embed, store |
| GET    | `/api/documents`      | List ingested documents                      |
| POST   | `/api/chat`           | Ask a question → `{ answer, sources }`       |
| POST   | `/api/chat/stream`    | Same, streamed as Server-Sent Events         |
| GET    | `/api/health`         | Liveness                                     |
| GET    | `/api/health/ai`      | Connectivity to Ollama + Qdrant              |

### Privacy / GDPR

No cloud and no external calls at runtime: the LLM, embeddings and vector DB all run
locally. The web font is self-hosted (no Google Fonts CDN), and Qdrant telemetry is
disabled. The only outbound traffic is the one-time model download on first start.
```
