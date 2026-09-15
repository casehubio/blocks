package io.casehub.blocks.channel;

import io.casehub.qhorus.api.message.MessageDispatch;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * First-match handler routing for channel agent sub-task requests.
 * Routes a request to the first matching handler, invokes the agent provider,
 * and posts the result back to the channel.
 *
 * <p>Apps subclass this to provide error dispatch and instance registration.
 * The routing loop and agent invocation are generic.
 */
public class ChannelAgentDispatcher {

    private static final Logger LOG = Logger.getLogger(ChannelAgentDispatcher.class.getName());

    private final Function<AgentTask, String> agentProvider;
    private final Consumer<MessageDispatch> messageSink;
    private final Iterable<ChannelAgentHandler> handlers;
    private final String senderId;

    protected ChannelAgentDispatcher() {
        this.agentProvider = null;
        this.messageSink = null;
        this.handlers = List.of();
        this.senderId = null;
    }

    /**
     * @param agentProvider invokes the LLM — takes AgentTask, returns output string
     * @param messageSink   posts messages to the channel (typically messageService::dispatch)
     * @param handlers      handler beans, iterated in CDI discovery order
     * @param senderId      the instance ID used as sender for dispatched messages
     */
    public ChannelAgentDispatcher(Function<AgentTask, String> agentProvider,
                                  Consumer<MessageDispatch> messageSink,
                                  Iterable<ChannelAgentHandler> handlers,
                                  String senderId) {
        this.agentProvider = agentProvider;
        this.messageSink = messageSink;
        this.handlers = handlers;
        this.senderId = senderId;
    }

    public void dispatch(ChannelAgentRequest request) {
        ChannelAgentHandler handler = null;
        for (ChannelAgentHandler h : handlers) {
            if (h.handles(request)) {
                handler = h;
                break;
            }
        }

        if (handler == null) {
            LOG.warning("ChannelAgentDispatcher: no handler matched on channel "
                    + request.channelId() + " — dispatching error");
            onError(request, "No handler matched this sub-task request.");
            return;
        }

        try {
            AgentTask task = handler.prepareTask(request);
            String llmOutput = agentProvider.apply(task);
            try {
                MessageDispatch response = handler.buildResponse(
                        request.channelId(), senderId, llmOutput, request);
                messageSink.accept(response);
            } catch (AgentResultParseException e) {
                LOG.warning("ChannelAgentDispatcher: parse failure [" + request.correlationId()
                        + "]: " + e.getClass().getSimpleName() + " — " + e.getMessage());
                onError(request, "Sub-agent returned an unreadable result.");
            }
        } catch (Exception e) {
            LOG.warning("ChannelAgentDispatcher: sub-agent failed [" + request.correlationId()
                    + "]: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            onError(request, "Sub-agent analysis failed.");
        }
    }

    /**
     * Called when dispatch fails. Apps override to encode error messages
     * in their protocol format and post to the channel.
     */
    protected void onError(ChannelAgentRequest request, String reason) {
        LOG.warning("ChannelAgentDispatcher error [" + request.correlationId() + "]: " + reason);
    }

    protected Consumer<MessageDispatch> messageSink() { return messageSink; }

    protected String senderId() { return senderId; }
}
