# RagRetrievalWorkerFactory — Design Spec

**Issue:** casehubio/blocks#293
**Parent:** casehubio/neocortex#371
**Date:** 2026-09-21

## Problem

Every CaseHub domain app (SOC, AML, Clinical) that needs RAG-backed retrieval
in its engine workflows writes the same ~15 lines of boilerplate:

1. Accept case context as `Map<String, Object>`
2. Extract a query via a domain-specific strategy
3. Call `CaseContextRetriever.retrieve()` with corpora and max results
4. Map `RetrievedChunk` results via `CaseContextRetriever.toMap()`
5. Return as `WorkerResult`

SOC shipped this as `RuleRagRetrievalWorker` + `SocRagRetrieveService`. AML and
Clinical would duplicate the pattern. A reusable factory in blocks eliminates
that duplication.

## Solution

A static factory class `RagRetrievalWorkerFactory` in `io.casehub.blocks.rag`
that produces a configured `Worker` from:

- A `QueryExtractionStrategy` (neocortex `rag-api`) — domain-specific query extraction
- A `CaseContextRetriever` (neocortex `rag-api`) — the retrieval engine
- A `List<CorpusRef>` — corpus configuration
- Worker metadata (name, capability name)
- Optional `maxResults` (default: 10)

### API

```java
package io.casehub.blocks.rag;

public final class RagRetrievalWorkerFactory {

    private static final int DEFAULT_MAX_RESULTS = 10;

    private RagRetrievalWorkerFactory() {}

    public static Worker create(String name, String capabilityName,
                                CaseContextRetriever retriever,
                                QueryExtractionStrategy strategy,
                                List<CorpusRef> corpora, int maxResults) {
        return Worker.builder()
                .name(name)
                .capabilityName(capabilityName)
                .function((Map<String, Object> input) -> {
                    List<RetrievedChunk> chunks =
                        retriever.retrieve(input, strategy, corpora, maxResults);
                    String summary = chunks.isEmpty()
                        ? "No relevant documents found"
                        : chunks.size() + " relevant chunk(s) retrieved";
                    return WorkerResult.of(Map.of(
                        "retrievedChunks", chunks.stream()
                            .map(CaseContextRetriever::toMap).toList(),
                        "summary", summary));
                })
                .build();
    }

    public static Worker create(String name, String capabilityName,
                                CaseContextRetriever retriever,
                                QueryExtractionStrategy strategy,
                                List<CorpusRef> corpora) {
        return create(name, capabilityName, retriever, strategy,
                      corpora, DEFAULT_MAX_RESULTS);
    }
}
```

### Output shape

The Worker function returns `WorkerResult<Map<String, Object>>` with:

```json
{
  "retrievedChunks": [
    {
      "content": "...",
      "sourceDocumentId": "...",
      "relevanceScore": 0.87,
      ...metadata fields
    }
  ],
  "summary": "3 relevant chunk(s) retrieved"
}
```

This matches the SOC reference implementation output.

## Module and dependency

**Module:** `blocks` (main module)
**Package:** `io.casehub.blocks.rag`
**New dependency:** `casehub-neocortex-rag-api` with `<scope>provided</scope>`

The `provided` scope follows the established pattern for optional capabilities
in blocks (`neocortex-memory-api`, `platform-agent-api`, `engine-ledger`).
Consumers must explicitly declare `rag-api` in their own pom.xml.

## Error handling

- `CaseContextRetriever.retrieve(Map, QueryExtractionStrategy, ...)` already
  handles null query return (returns empty list) and per-corpus failures
  (catches, logs, skips)
- No additional error handling needed in the factory — the retriever is
  the error boundary

## Testing

Plain JUnit 5 + Mockito (no Quarkus runtime — consistent with all blocks tests):

1. **Happy path** — mock `CaseContextRetriever`, verify Worker function calls
   retrieve with correct args, returns mapped chunks + summary
2. **Empty results** — strategy returns valid query, retriever returns empty list,
   verify "No relevant documents found" summary
3. **Null query from strategy** — verify retriever handles gracefully (empty result)
4. **Default maxResults** — verify 2-arg overload passes 10 to retriever
5. **Worker metadata** — verify name and capabilityName are set correctly

## Consumer migration (SOC)

SOC replaces `RuleRagRetrievalWorker` + `SocRagRetrieveService` with:

```java
// Before (SOC-specific, ~60 lines across 2 files)
Worker worker = RuleRagRetrievalWorker.create(socRagRetrieveService);

// After (~3 lines)
Worker worker = RagRetrievalWorkerFactory.create(
    "rule-rag-retrieval", "rag-retrieval",
    contextRetriever,
    SocQueryExtraction::extract,   // extracted from SocRagRetrieveService.buildQueryText()
    List.of(new CorpusRef(REFERENCE_TENANT, CORPUS_NAME)));
```

SOC's `buildQueryText()` method becomes a standalone `QueryExtractionStrategy`
lambda or method reference. The hardcoded tenant/corpus move to configuration.

## Scope

- `RagRetrievalWorkerFactory` — static factory class
- Tests for the factory
- pom.xml change (add `rag-api` as provided)
- CLAUDE.md update (add rag package to Key Directories)

NOT in scope: SOC migration (separate issue in SOC repo).

## References

- casehubio/soc `app/src/main/java/io/casehub/soc/worker/RuleRagRetrievalWorker.java` — reference implementation
- casehubio/soc `app/src/main/java/io/casehub/soc/engine/rag/SocRagRetrieveService.java` — domain-specific retrieval service
- casehubio/neocortex `rag-api` — `QueryExtractionStrategy`, `CaseContextRetriever`, `CorpusRef`, `RetrievalQuery`, `RetrievedChunk`
- casehubio/worker `api` — `Worker`, `WorkerResult`
- casehubio/neocortex#371 — provides QueryExtractionStrategy SPI
- CLAUDE.md Blocks Scope Criteria — criterion 3 (foundational platform integration)
