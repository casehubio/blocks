package io.casehub.blocks.conversation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record ConversationRendererConfig(
        Map<String, String> statusEmoji,
        Map<Priority, String> priorityLabel,
        Map<String, String> entryTypeLabel,
        Map<String, String> roleLabel,
        Set<String> resolvedStatuses,
        Set<String> escalatedStatuses,
        Map<io.casehub.qhorus.api.message.MessageType, String> messageTypeLabel,
        boolean groupByTopic,
        boolean showObligationChain,
        boolean showEpistemicStatus,
        boolean showConvergenceSignal,
        boolean showProgress) {

    public ConversationRendererConfig {
        statusEmoji       = Map.copyOf(statusEmoji);
        priorityLabel     = Map.copyOf(priorityLabel);
        entryTypeLabel    = Map.copyOf(entryTypeLabel);
        roleLabel         = Map.copyOf(roleLabel);
        resolvedStatuses  = Set.copyOf(resolvedStatuses);
        escalatedStatuses = Set.copyOf(escalatedStatuses);
        messageTypeLabel  = Map.copyOf(messageTypeLabel);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                       .statusEmoji(new java.util.HashMap<>(statusEmoji))
                       .priorityLabel(new java.util.HashMap<>(priorityLabel))
                       .entryTypeLabel(new java.util.HashMap<>(entryTypeLabel))
                       .roleLabel(new java.util.HashMap<>(roleLabel))
                       .resolvedStatuses(new java.util.HashSet<>(resolvedStatuses))
                       .escalatedStatuses(new java.util.HashSet<>(escalatedStatuses))
                       .messageTypeLabel(new java.util.HashMap<>(messageTypeLabel))
                       .groupByTopic(groupByTopic)
                       .showObligationChain(showObligationChain)
                       .showEpistemicStatus(showEpistemicStatus)
                       .showConvergenceSignal(showConvergenceSignal)
                       .showProgress(showProgress);
    }

    public static final class Builder {
        private Map<String, String>                                    statusEmoji           = Map.of();
        private Map<Priority, String>                                  priorityLabel         = Map.of();
        private Map<String, String>                                    entryTypeLabel        = Map.of();
        private Map<String, String>                                    roleLabel             = Map.of();
        private Set<String>                                            resolvedStatuses      = Set.of();
        private Set<String>                                            escalatedStatuses     = Set.of();
        private Map<io.casehub.qhorus.api.message.MessageType, String> messageTypeLabel      = Map.of();
        private boolean                                                groupByTopic          = false;
        private boolean                                                showObligationChain   = false;
        private boolean                                                showEpistemicStatus   = false;
        private boolean                                                showConvergenceSignal = false;
        private boolean                                                showProgress          = false;

        private Builder()                                                                                        {}

        public Builder statusEmoji(Map<String, String> statusEmoji)                                              {
                                                                                                                     this.statusEmoji = new HashMap<>(statusEmoji);
                                                                                                                     return this;
                                                                                                                 }

        public Builder priorityLabel(Map<Priority, String> priorityLabel)                                        {
                                                                                                                     this.priorityLabel = new HashMap<>(priorityLabel);
                                                                                                                     return this;
                                                                                                                 }

        public Builder entryTypeLabel(Map<String, String> entryTypeLabel)                                        {
                                                                                                                     this.entryTypeLabel = new HashMap<>(entryTypeLabel);
                                                                                                                     return this;
                                                                                                                 }

        public Builder roleLabel(Map<String, String> roleLabel)                                                  {
                                                                                                                     this.roleLabel = new HashMap<>(roleLabel);
                                                                                                                     return this;
                                                                                                                 }

        public Builder resolvedStatuses(Set<String> resolvedStatuses)                                            {
                                                                                                                     this.resolvedStatuses = new HashSet<>(resolvedStatuses);
                                                                                                                     return this;
                                                                                                                 }

        public Builder escalatedStatuses(Set<String> escalatedStatuses)                                          {
                                                                                                                     this.escalatedStatuses = new HashSet<>(escalatedStatuses);
                                                                                                                     return this;
                                                                                                                 }

        public Builder messageTypeLabel(Map<io.casehub.qhorus.api.message.MessageType, String> messageTypeLabel) {
                                                                                                                     this.messageTypeLabel = new java.util.HashMap<>(messageTypeLabel);
                                                                                                                     return this;
                                                                                                                 }

        public Builder groupByTopic(boolean groupByTopic)                                                        {
                                                                                                                     this.groupByTopic = groupByTopic;
                                                                                                                     return this;
                                                                                                                 }

        public Builder showObligationChain(boolean showObligationChain)                                          {
                                                                                                                     this.showObligationChain = showObligationChain;
                                                                                                                     return this;
                                                                                                                 }

        public Builder showEpistemicStatus(boolean showEpistemicStatus)                                          {
                                                                                                                     this.showEpistemicStatus = showEpistemicStatus;
                                                                                                                     return this;
                                                                                                                 }

        public Builder showConvergenceSignal(boolean showConvergenceSignal)                                      {
                                                                                                                     this.showConvergenceSignal = showConvergenceSignal;
                                                                                                                     return this;
                                                                                                                 }

        public Builder showProgress(boolean showProgress)                                                        {
                                                                                                                     this.showProgress = showProgress;
                                                                                                                     return this;
                                                                                                                 }

        public ConversationRendererConfig build() {
            return new ConversationRendererConfig(
                    statusEmoji, priorityLabel, entryTypeLabel, roleLabel,
                    resolvedStatuses, escalatedStatuses, messageTypeLabel,
                    groupByTopic, showObligationChain, showEpistemicStatus, showConvergenceSignal,
                    showProgress);
        }
    }
}
