package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummariserTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);

    // --- ofSync contract ---

    @Test
    void ofSync_wrapsResultInCompletedFuture() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));

        var batch = List.of(new LevelEvent<>("a", 1, LEVEL), new LevelEvent<>("b", 2, LEVEL));
        CompletionStage<List<Integer>> result = summariser.summarise(batch);

        assertThat(result.toCompletableFuture().isDone()).isTrue();
        assertThat(result.toCompletableFuture().join()).containsExactly(2);
    }

    @Test
    void ofSync_emptyBatch_returnsEmptyList() {
        Summariser<String, String> summariser = Summariser.ofSync(batch -> List.of());

        CompletionStage<List<String>> result = summariser.summarise(List.of());

        assertThat(result.toCompletableFuture().join()).isEmpty();
    }

    @Test
    void ofSync_syncExceptionPropagatesImmediately() {
        Summariser<String, String> summariser = Summariser.ofSync(batch -> {
            throw new IllegalStateException("sync failure");
        });

        assertThatThrownBy(() -> summariser.summarise(List.of(new LevelEvent<>("a", 1, LEVEL))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("sync failure");
    }

    // --- Async contract ---

    @Test
    void asyncSummariser_completesAsynchronously() {
        var future = new CompletableFuture<List<Integer>>();
        Summariser<String, Integer> summariser = batch -> future;

        CompletionStage<List<Integer>> result = summariser.summarise(
            List.of(new LevelEvent<>("a", 1, LEVEL)));

        assertThat(result.toCompletableFuture().isDone()).as("not yet completed").isFalse();

        future.complete(List.of(42));

        assertThat(result.toCompletableFuture().isDone()).isTrue();
        assertThat(result.toCompletableFuture().join()).containsExactly(42);
    }

    @Test
    void asyncSummariser_failurePropagatesToStage() {
        Summariser<String, String> summariser = batch ->
            CompletableFuture.failedFuture(new RuntimeException("async failure"));

        CompletionStage<List<String>> result = summariser.summarise(
            List.of(new LevelEvent<>("a", 1, LEVEL)));

        assertThatThrownBy(() -> result.toCompletableFuture().join())
            .hasCauseInstanceOf(RuntimeException.class)
            .hasMessageContaining("async failure");
    }

    // --- SyncSummariser as lambda ---

    @Test
    void syncSummariser_usableAsLambda() {
        Summariser.SyncSummariser<String, String> sync = batch ->
            batch.stream().map(e -> e.payload().toUpperCase()).toList();

        Summariser<String, String> wrapped = Summariser.ofSync(sync);

        var result = wrapped.summarise(List.of(
            new LevelEvent<>("hello", 1, LEVEL),
            new LevelEvent<>("world", 2, LEVEL)
        )).toCompletableFuture().join();

        assertThat(result).containsExactly("HELLO", "WORLD");
    }
}
