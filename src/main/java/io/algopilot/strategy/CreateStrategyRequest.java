package io.algopilot.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStrategyRequest(@NotBlank String name, @NotNull JsonNode definition, @NotBlank String changeReason) {}
