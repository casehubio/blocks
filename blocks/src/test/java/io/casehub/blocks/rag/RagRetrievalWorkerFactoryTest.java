package io.casehub.blocks.rag;

import io.casehub.neocortex.rag.CaseContextRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.QueryExtractionStrategy;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerFunction;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrievalWorkerFactoryTest {

    private final CaseContextRetriever retriever = mock(CaseContextRetriever.class);

    @SuppressWarnings("unchecked")
    private WorkerResult<Map<String, Object>> invoke(Worker worker, Map<String, Object> input) {
        var sync = (WorkerFunction.Sync<Map<String, Object>, Map<String, Object>>) worker.function();
        return sync.fn().apply(input, null);
    }

    @Test
    void happyPathReturnsChunksAndSummary() {
        var chunk = new RetrievedChunk("threat intel content", "doc-1", 0.87,
                Map.of("source", "attck"));
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> RetrievalQuery.of("test query");

        when(retriever.retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(5)))
                .thenReturn(List.of(chunk));

        Worker worker = RagRetrievalWorkerFactory.create(
                "rag-worker", "rag-retrieval", retriever, strategy, corpora, 5);

        var result = invoke(worker, Map.of("alert", "suspicious activity"));

        assertThat(result.output()).containsKey("retrievedChunks");
        assertThat(result.output()).containsEntry("summary", "1 relevant chunk(s) retrieved");

        @SuppressWarnings("unchecked")
        var chunks = (List<Map<String, Object>>) result.output().get("retrievedChunks");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).containsEntry("content", "threat intel content");
        assertThat(chunks.get(0)).containsEntry("sourceDocumentId", "doc-1");
        assertThat(chunks.get(0)).containsEntry("relevanceScore", 0.87);
    }

    @Test
    void emptyResultsReturnNoDocumentsSummary() {
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> RetrievalQuery.of("no match query");

        when(retriever.retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(10)))
                .thenReturn(List.of());

        Worker worker = RagRetrievalWorkerFactory.create(
                "rag-worker", "rag-retrieval", retriever, strategy, corpora, 10);

        var result = invoke(worker, Map.of());

        assertThat(result.output()).containsEntry("summary", "No relevant documents found");

        @SuppressWarnings("unchecked")
        var chunks = (List<Map<String, Object>>) result.output().get("retrievedChunks");
        assertThat(chunks).isEmpty();
    }

    @Test
    void defaultMaxResultsIsTen() {
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> RetrievalQuery.of("query");

        when(retriever.retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(10)))
                .thenReturn(List.of());

        Worker worker = RagRetrievalWorkerFactory.create(
                "rag-worker", "rag-retrieval", retriever, strategy, corpora);

        invoke(worker, Map.of());

        verify(retriever).retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(10));
    }

    @Test
    void workerMetadataIsSet() {
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> RetrievalQuery.of("query");

        Worker worker = RagRetrievalWorkerFactory.create(
                "my-rag-worker", "my-rag-cap", retriever, strategy, corpora);

        assertThat(worker.name()).isEqualTo("my-rag-worker");
        assertThat(worker.capabilities()).containsExactly("my-rag-cap");
    }

    @Test
    void nullQueryFromStrategyReturnsEmpty() {
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> null;

        when(retriever.retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(10)))
                .thenReturn(List.of());

        Worker worker = RagRetrievalWorkerFactory.create(
                "rag-worker", "rag-retrieval", retriever, strategy, corpora);

        var result = invoke(worker, Map.of());

        assertThat(result.output()).containsEntry("summary", "No relevant documents found");
    }
}
