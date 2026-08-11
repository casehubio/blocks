package io.casehub.blocks.channel.summary;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ChannelSummariser implements SummaryUpdateHook {

    private static final System.Logger LOG =
            System.getLogger(ChannelSummariser.class.getName());

    private final ContentSummariser<Message> delegate;

    @Inject
    public ChannelSummariser(ContentSummariser<Message> delegate) {
        this.delegate = delegate;
    }

    @Override
    public SummaryResult update(SummaryUpdateContext context) {
        if (context.recentMessages() == null || context.recentMessages().isEmpty()) {
            return context.previousResult() != null
                    ? context.previousResult()
                    : SummaryResult.ofText("");
        }
        try {
            return Uni.createFrom()
                    .completionStage(delegate.summarise(
                            context.recentMessages(), context.previousResult()))
                    .await().indefinitely();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Summarisation failed for channel " + context.channelName()
                            + " (" + context.recentMessages().size() + " messages)", e);
            throw e;
        }
    }
}
