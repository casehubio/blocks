package io.casehub.blocks.channel;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks cumulative LLM context window usage for a channel session.
 * Thread-safe via atomic counters.
 */
public class ContextTracker {

    private final AtomicLong serverContributionChars = new AtomicLong(0);
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private volatile Double agentReportedPercent;

    public void addContribution(long chars) {
        serverContributionChars.addAndGet(chars);
        messageCount.incrementAndGet();
    }

    public void addInitialContribution(long chars) {
        serverContributionChars.addAndGet(chars);
    }

    public void reportAgentUsage(double percent) {
        if (Double.isNaN(percent) || Double.isInfinite(percent)) return;
        this.agentReportedPercent = percent < 0 ? 0.0 : percent;
    }

    public ContextSnapshot snapshot(long windowSizeChars, double thresholdPercent) {
        long contribution = serverContributionChars.get();
        Double agentPct = agentReportedPercent;
        double effective = agentPct != null ? agentPct
                : (windowSizeChars > 0 ? (double) contribution / windowSizeChars * 100.0 : 0.0);
        return new ContextSnapshot(
                contribution, windowSizeChars, agentPct,
                messageCount.get(), effective,
                effective >= thresholdPercent
        );
    }
}
