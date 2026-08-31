package io.casehub.engine.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.spi.judgment.CallerConfig;
import io.casehub.api.spi.judgment.EvidenceType;
import io.casehub.engine.agentic.judgment.PatternJudgmentConfig;
import org.junit.jupiter.api.Test;

class PatternJudgmentYamlTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final PatternWorkerFunctionProvider provider = new PatternWorkerFunctionProvider();

  @Test
  void parsesJudgmentBlockWithLlmCaller() {
    var node = mapper.createObjectNode();
    var pattern = node.putObject("pattern");
    pattern.put("type", "SUPERVISOR");
    var judgment = pattern.putObject("judgment");
    judgment.put("prompt", "Review the analysis");
    judgment.put("verifier", "schema-validation");
    var caller = judgment.putObject("caller");
    caller.put("type", "llm");
    caller.put("modelName", "claude-sonnet-4-20250514");

    var fn = (PatternWorkerFunction) provider.create(node);

    assertThat(fn.judgmentConfig()).isNotNull();
    assertThat(fn.judgmentConfig().prompt()).isEqualTo("Review the analysis");
    assertThat(fn.judgmentConfig().verifierStrategy()).isEqualTo("schema-validation");
    assertThat(fn.judgmentConfig().callerConfig()).isInstanceOf(CallerConfig.Llm.class);
    assertThat(((CallerConfig.Llm) fn.judgmentConfig().callerConfig()).modelName())
        .isEqualTo("claude-sonnet-4-20250514");
  }

  @Test
  void parsesEvidenceRequirements() {
    var node = mapper.createObjectNode();
    var pattern = node.putObject("pattern");
    pattern.put("type", "SUPERVISOR");
    var judgment = pattern.putObject("judgment");
    judgment.put("prompt", "Review");
    var caller = judgment.putObject("caller");
    caller.put("type", "llm");
    var evidence = judgment.putArray("evidence");
    var req = evidence.addObject();
    req.put("name", "rationale");
    req.put("type", "REASONING");
    req.put("required", true);

    var fn = (PatternWorkerFunction) provider.create(node);

    assertThat(fn.judgmentConfig().evidenceRequirements()).hasSize(1);
    assertThat(fn.judgmentConfig().evidenceRequirements().get(0).key()).isEqualTo("rationale");
    assertThat(fn.judgmentConfig().evidenceRequirements().get(0).type())
        .isEqualTo(EvidenceType.REASONING);
    assertThat(fn.judgmentConfig().evidenceRequirements().get(0).required()).isTrue();
  }

  @Test
  void noJudgmentBlock_returnsNullConfig() {
    var node = mapper.createObjectNode();
    var pattern = node.putObject("pattern");
    pattern.put("type", "SUPERVISOR");

    var fn = (PatternWorkerFunction) provider.create(node);

    assertThat(fn.judgmentConfig()).isNull();
  }

  @Test
  void parsesAfterStepFlag() {
    var node = mapper.createObjectNode();
    var pattern = node.putObject("pattern");
    pattern.put("type", "SEQUENCE");
    var judgment = pattern.putObject("judgment");
    judgment.put("prompt", "Quality gate");
    judgment.put("afterStep", true);
    var caller = judgment.putObject("caller");
    caller.put("type", "llm");

    var fn = (PatternWorkerFunction) provider.create(node);

    assertThat(fn.judgmentConfig().afterStep()).isTrue();
  }

  @Test
  void parsesJudgmentMode() {
    var node = mapper.createObjectNode();
    var pattern = node.putObject("pattern");
    pattern.put("type", "SUPERVISOR");
    var judgment = pattern.putObject("judgment");
    judgment.put("prompt", "Validate");
    judgment.put("mode", "post-step");
    var caller = judgment.putObject("caller");
    caller.put("type", "llm");

    var fn = (PatternWorkerFunction) provider.create(node);

    assertThat(fn.judgmentConfig().mode())
        .isEqualTo(PatternJudgmentConfig.JudgmentMode.POST_STEP);
  }

  @Test
  void parsesA2ACaller() {
    var node = mapper.createObjectNode();
    var pattern = node.putObject("pattern");
    pattern.put("type", "SUPERVISOR");
    var judgment = pattern.putObject("judgment");
    judgment.put("prompt", "Validate");
    var caller = judgment.putObject("caller");
    caller.put("type", "a2a");
    caller.put("endpoint", "https://validator.example.com");
    caller.put("skill", "validate");

    var fn = (PatternWorkerFunction) provider.create(node);

    assertThat(fn.judgmentConfig().callerConfig()).isInstanceOf(CallerConfig.A2A.class);
    var a2a = (CallerConfig.A2A) fn.judgmentConfig().callerConfig();
    assertThat(a2a.endpoint()).isEqualTo("https://validator.example.com");
    assertThat(a2a.skill()).isEqualTo("validate");
  }
}
