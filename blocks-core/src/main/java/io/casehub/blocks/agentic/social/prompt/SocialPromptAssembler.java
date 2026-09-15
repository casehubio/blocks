package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.AssembledPrompt;
import io.casehub.blocks.speech.ConversationTurn;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.speech.SpeechPromptAssembler;

import java.util.List;
import java.util.function.Supplier;

public class SocialPromptAssembler implements SpeechPromptAssembler {

    private static final System.Logger LOG = System.getLogger(SocialPromptAssembler.class.getName());

    private final SpeechPromptAssembler delegate;
    private final List<PromptSection> sections;
    private final String agentId;
    private final String tenantId;
    private final Supplier<String> subjectIdSupplier;

    public SocialPromptAssembler(SpeechPromptAssembler delegate, List<PromptSection> sections,
                                  String agentId, String tenantId,
                                  Supplier<String> subjectIdSupplier) {
        this.delegate = delegate;
        this.sections = List.copyOf(sections);
        this.agentId = agentId;
        this.tenantId = tenantId;
        this.subjectIdSupplier = subjectIdSupplier;
    }

    @Override
    public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history) {
        AssembledPrompt base = delegate.assemble(userMessage, history);
        var enriched = new StringBuilder(base.systemPrompt());
        var context = new PromptContext(agentId, tenantId, subjectIdSupplier.get());
        for (var section : sections) {
            try {
                String contribution = section.contribute(context);
                if (contribution != null) {
                    enriched.append("\n\n").append(contribution);
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "PromptSection failed: " + section.getClass().getSimpleName(), e);
            }
        }
        return new AssembledPrompt(enriched.toString(), base.userPrompt(), base.model());
    }
}
