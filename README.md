# MittelstandGPT

Selbst-gehosteter, **DSGVO-konformer RAG-Wissensassistent**. Dokumente (PDF,
DOCX, TXT) hochladen und in natürlicher Sprache Fragen stellen — Antworten kommen
vom **lokalen LLM inkl. Quellenangaben**. Es verlässt **kein einziges Byte** den
Server: kein OpenAI, keine Cloud.

> Vollständige Dokumentation folgt in Phase 7. Dies ist der Stand nach **Phase 0**.

## Schnellstart

```bash
docker compose up --build
```

Danach erreichbar:

| Dienst             | URL                                    |
| ------------------ | -------------------------------------- |
| Frontend (UI)      | http://localhost:5173                  |
| Backend (REST API) | http://localhost:8080/api/health       |
| Backend Health     | http://localhost:8080/actuator/health  |
| Qdrant Dashboard   | http://localhost:6333/dashboard        |

Ollama läuft nur intern (`http://ollama:11434` im Docker-Netz, kein Host-Port).

## Architektur

```
Browser (React/Vite)  →  Backend (Spring Boot)  →  Ollama (LLM + Embeddings)
                                                 →  Qdrant (Vektor-DB)
```

Vier Container, ein Netzwerk, alles lokal:

- **ollama** — lokales LLM (`qwen2.5:3b-instruct`) und Embeddings (`nomic-embed-text`)
- **qdrant** — Vektor-Datenbank
- **backend** — Java 21 + Spring Boot 3.4 (RAG-Orchestrierung)
- **frontend** — React + TypeScript + Vite + Tailwind

## Konfiguration

Modelle und Endpunkte sind über Umgebungsvariablen konfigurierbar
(siehe [.env.example](.env.example)). Zum Tauschen des Modells:

```bash
cp .env.example .env
# OLLAMA_CHAT_MODEL=... anpassen
```

## Datenschutz

Alle Komponenten laufen on-premise in Docker. Es bestehen keine ausgehenden
Verbindungen zu Cloud-Diensten — die hochgeladenen Dokumente und Anfragen
verbleiben vollständig auf dem eigenen Server.
