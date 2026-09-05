package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.InnerLifeOrchestrator;
import io.casehub.blocks.agentic.social.InnerLifeTick;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.speech.AssembledPrompt;
import io.casehub.blocks.speech.SpeechPromptAssembler;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentRegistry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialAvatarCognitionTest {

    private SocialAvatarCognition cognition;
    private MoodOrchestrator mood;
    private DriveOrchestrator drives;
    private MentalModelOrchestrator mentalModel;
    private UserModelOrchestrator userModel;
    private StrategyLearningOrchestrator strategy;
    private InnerLifeOrchestrator innerLife;
    private AgentRegistry registry;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        cognition = new SocialAvatarCognition();
        mood = mock(MoodOrchestrator.class);
        drives = mock(DriveOrchestrator.class);
        mentalModel = mock(MentalModelOrchestrator.class);
        userModel = mock(UserModelOrchestrator.class);
        strategy = mock(StrategyLearningOrchestrator.class);
        innerLife = mock(InnerLifeOrchestrator.class);
        registry = mock(AgentRegistry.class);

        var narrativeInstance = (Instance<NarrativeOrchestrator>) mock(Instance.class);
        when(narrativeInstance.isResolvable()).thenReturn(false);
        var goalInstance = (Instance<GoalProposalOrchestrator>) mock(Instance.class);
        when(goalInstance.isResolvable()).thenReturn(false);
        var innerLifeInstance = (Instance<InnerLifeOrchestrator>) mock(Instance.class);
        when(innerLifeInstance.isResolvable()).thenReturn(true);
        when(innerLifeInstance.get()).thenReturn(innerLife);
        var registryInstance = (Instance<AgentRegistry>) mock(Instance.class);
        when(registryInstance.isResolvable()).thenReturn(true);
        when(registryInstance.get()).thenReturn(registry);

        var moodField = SocialAvatarCognition.class.getDeclaredField("mood");
        moodField.setAccessible(true); moodField.set(cognition, mood);
        var drivesField = SocialAvatarCognition.class.getDeclaredField("drives");
        drivesField.setAccessible(true); drivesField.set(cognition, drives);
        var mmField = SocialAvatarCognition.class.getDeclaredField("mentalModel");
        mmField.setAccessible(true); mmField.set(cognition, mentalModel);
        var umField = SocialAvatarCognition.class.getDeclaredField("userModel");
        umField.setAccessible(true); umField.set(cognition, userModel);
        var stField = SocialAvatarCognition.class.getDeclaredField("strategy");
        stField.setAccessible(true); stField.set(cognition, strategy);
        var narField = SocialAvatarCognition.class.getDeclaredField("narrative");
        narField.setAccessible(true); narField.set(cognition, narrativeInstance);
        var goalField = SocialAvatarCognition.class.getDeclaredField("goals");
        goalField.setAccessible(true); goalField.set(cognition, goalInstance);
        var ilField = SocialAvatarCognition.class.getDeclaredField("innerLife");
        ilField.setAccessible(true); ilField.set(cognition, innerLifeInstance);
        var regField = SocialAvatarCognition.class.getDeclaredField("agentRegistry");
        regField.setAccessible(true); regField.set(cognition, registryInstance);
    }

    @Test
    void wrapAssemblerReturnsSocialPromptAssembler() {
        SpeechPromptAssembler base = (msg, hist) -> new AssembledPrompt("base", msg);
        var wrapped = cognition.wrapAssembler(base, "a1", "t1", () -> null);
        assertThat(wrapped).isInstanceOf(SocialPromptAssembler.class);
        var result = wrapped.assemble("test", List.of());
        assertThat(result.systemPrompt()).startsWith("base");
    }

    @Test
    void tickCallsAgentScopedOrchestrators() {
        cognition.tick("a1", "t1", Set.of());
        verify(mood).tick("a1", "t1");
        verify(strategy).tick("a1", "t1");
    }

    @Test
    void tickCallsSubjectScopedOrchestratorsForEachSubject() {
        cognition.tick("a1", "t1", Set.of("user1", "user2"));
        verify(userModel).tick("a1", "user1", "t1");
        verify(userModel).tick("a1", "user2", "t1");
        verify(mentalModel).tick("a1", "user1", "t1");
        verify(mentalModel).tick("a1", "user2", "t1");
    }

    @Test
    void recordInteractionDispatchesSubjectScopedSignals() {
        cognition.recordInteraction("a1", "t1", "user1", "hello", "hi there");
        verify(userModel).record(any(), eq("a1"), eq("user1"), eq("t1"));
        verify(mentalModel).record(any(), eq("a1"), eq("user1"), eq("t1"));
        verify(strategy).record(any(), eq("a1"), eq("user1"), eq("t1"));
    }

    @Test
    void recordInteractionSkipsSubjectScopedWhenNoSubject() {
        cognition.recordInteraction("a1", "t1", null, "hello", "hi there");
        verify(userModel, never()).record(any(), any(), any(), any());
        verify(mentalModel, never()).record(any(), any(), any(), any());
        verify(strategy, never()).record(any(), any(), any(), any());
    }

    @Test
    void recordInteractionResetsProactiveCounter() {
        var descriptor = mock(AgentDescriptor.class);
        when(registry.findById("a1", "t1")).thenReturn(Optional.of(descriptor));
        cognition.recordInteraction("a1", "t1", "user1", "hello", "hi");
        verify(innerLife).observeResponse(descriptor);
    }

    @Test
    void evaluateProactiveReturnsContentOnInitiated() {
        var descriptor = mock(AgentDescriptor.class);
        when(registry.findById("a1", "t1")).thenReturn(Optional.of(descriptor));
        when(innerLife.tick(descriptor, "ctx"))
                .thenReturn(new InnerLifeTick.Initiated("Hi!", null, 0.9));
        var result = cognition.evaluateProactive("a1", "t1", "ctx");
        assertThat(result).isEqualTo("Hi!");
    }

    @Test
    void evaluateProactiveReturnsNullOnSilent() {
        var descriptor = mock(AgentDescriptor.class);
        when(registry.findById("a1", "t1")).thenReturn(Optional.of(descriptor));
        when(innerLife.tick(descriptor, "ctx"))
                .thenReturn(new InnerLifeTick.Silent(null));
        assertThat(cognition.evaluateProactive("a1", "t1", "ctx")).isNull();
    }

    @Test
    void recordInteractionIsolatesFailures() {
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(userModel).record(any(), any(), any(), any());
        cognition.recordInteraction("a1", "t1", "user1", "hello", "hi");
        verify(mentalModel).record(any(), eq("a1"), eq("user1"), eq("t1"));
    }
}
