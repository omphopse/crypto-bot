package io.algopilot.bot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeployBotRequest(@NotBlank String name, @NotNull UUID strategyVersionId, @NotNull Broker broker, @NotNull ExecutionMode executionMode) {}
