package io.casehub.blocks.rag;

import io.casehub.neocortex.rag.CaseContextRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.QueryExtractionStrategy;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;

import java.util.List;
import java.util.Map;

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
