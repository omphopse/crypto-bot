package io.algopilot.research.security;

import io.algopilot.research.model.SecurityStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PromptInjectionDetector {

  private static final List<Pattern> INJECTION_PATTERNS = List.of(
      Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
      Pattern.compile("(?i)system\\s+prompt|developer\\s+instruction|system\\s+message"),
      Pattern.compile("(?i)reveal\\s+(your\\s+)?(prompt|system|credentials|keys)"),
      Pattern.compile("(?i)show\\s+(your\\s+)?(secret|token|api_key|password)"),
      Pattern.compile("(?i)bypass\\s+(safety|risk|filter|controls)"),
      Pattern.compile("(?i)disable\\s+(safety|risk|limits)"),
      Pattern.compile("(?i)execute\\s+(order|trade|command|transaction)\\s+immediately"),
      Pattern.compile("(?i)<script[\\s\\S]*?>[\\s\\S]*?<\\/script>"),
      Pattern.compile("(?i)<\\|im_start\\|>|<\\|im_end\\|>"),
      Pattern.compile("(?i)\\{\\{.*(prompt|config|env).*\\}\\}")
  );

  public record Analysis(SecurityStatus status, List<String> flaggedRules) {
    public boolean isSafe() { return status == SecurityStatus.CLEAN; }
  }

  public Analysis analyze(String text) {
    if (text == null || text.isBlank()) {
      return new Analysis(SecurityStatus.CLEAN, List.of());
    }

    List<String> matches = new ArrayList<>();
    for (Pattern p : INJECTION_PATTERNS) {
      if (p.matcher(text).find()) {
        matches.add(p.pattern());
      }
    }

    if (matches.size() >= 2) {
      return new Analysis(SecurityStatus.BLOCKED, matches);
    } else if (matches.size() == 1) {
      return new Analysis(SecurityStatus.SUSPICIOUS, matches);
    } else {
      return new Analysis(SecurityStatus.CLEAN, List.of());
    }
  }
}
