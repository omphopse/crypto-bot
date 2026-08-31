package io.algopilot.bot;
import java.util.UUID;
public class BotNotFoundException extends RuntimeException { public BotNotFoundException(UUID id) { super("BOT_NOT_FOUND:" + id); } }
