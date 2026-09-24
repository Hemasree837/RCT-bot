package com.example.resume_chatbot.telegram;

import com.example.resume_chatbot.config.TelegramProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBot {

    private final TelegramProperties properties;
    private final TelegramUpdateHandler updateHandler;

    private final RestClient restClient = RestClient.builder().build();
    private ExecutorService pollingExecutor;
    private volatile boolean running = false;
    private long lastUpdateId = 0;

    @PostConstruct
    public void start() {
        if (!properties.isConfigured()) {
            log.warn("=========================================================================");
            log.warn("TELEGRAM BOT TOKEN IS NOT CONFIGURED!");
            log.warn("The application is running in STANDBY / REST API mode.");
            log.warn("To connect with Telegram, provide your bot token from @BotFather via:");
            log.warn("export TELEGRAM_BOT_TOKEN=\"your-bot-token-here\"");
            log.warn("All REST endpoints remain active at http://localhost:8080/api/resume/...");
            log.warn("=========================================================================");
            return;
        }

        if (!properties.isPollingEnabled()) {
            log.info("Telegram polling is disabled in configuration.");
            return;
        }

        log.info("Starting Telegram Bot long-polling daemon for bot @{}...", properties.getUsername());
        running = true;
        pollingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "telegram-polling-thread");
            t.setDaemon(true);
            return t;
        });

        pollingExecutor.submit(this::pollLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (pollingExecutor != null) {
            log.info("Stopping Telegram Bot long-polling executor...");
            pollingExecutor.shutdownNow();
            try {
                if (!pollingExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    pollingExecutor.shutdownNow();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void pollLoop() {
        log.info("Telegram Bot long-polling loop active.");
        while (running) {
            try {
                pollUpdates();
            } catch (Exception e) {
                if (!running) break;
                log.error("Error in Telegram polling loop: {}. Retrying in 5 seconds...", e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void pollUpdates() {
        String url = String.format("https://api.telegram.org/bot%s/getUpdates?offset=%d&timeout=%d",
                properties.getToken(),
                lastUpdateId + 1,
                properties.getPollingTimeoutSeconds());

        Map<?, ?> response = restClient.get()
                .uri(url)
                .retrieve()
                .body(Map.class);

        if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
            List<?> updates = (List<?>) response.get("result");
            if (updates != null && !updates.isEmpty()) {
                for (Object item : updates) {
                    if (item instanceof Map<?, ?> updateMap) {
                        Number updateId = (Number) updateMap.get("update_id");
                        if (updateId != null) {
                            lastUpdateId = Math.max(lastUpdateId, updateId.longValue());
                        }
                        processIncomingUpdate((Map<String, Object>) updateMap);
                    }
                }
            }
        }
    }

    /**
     * Processes an individual update, sending responses back to the user.
     */
    public void processIncomingUpdate(Map<String, Object> update) {
        try {
            // Send typing indicator if message exists
            if (update.containsKey("message")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = (Map<String, Object>) update.get("message");
                if (msg.containsKey("chat")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chat = (Map<String, Object>) msg.get("chat");
                    if (chat.containsKey("id")) {
                        sendChatAction(Long.valueOf(chat.get("id").toString()), "typing");
                    }
                }
            }

            TelegramUpdateHandler.TelegramResponse response = updateHandler.processUpdate(update);
            if (response != null) {
                sendMessage(response.chatId(), response.text(), response.replyMarkup());
            }
        } catch (Exception e) {
            log.error("Failed to process update: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends an HTML-formatted message to a Telegram chat.
     */
    public void sendMessage(Long chatId, String text, Map<String, Object> replyMarkup) {
        if (!properties.isConfigured()) {
            log.info("[Standby Mode] Message intended for chat {}: {}", chatId, text);
            return;
        }

        try {
            String url = "https://api.telegram.org/bot" + properties.getToken() + "/sendMessage";
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            payload.put("parse_mode", "HTML");
            payload.put("disable_web_page_preview", false);

            if (replyMarkup != null) {
                payload.put("reply_markup", replyMarkup);
            }

            restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

        } catch (Exception e) {
            log.error("Failed to send Telegram message to chat {}: {}", chatId, e.getMessage());
        }
    }

    /**
     * Sends a chat action (e.g. typing) to indicate bot activity.
     */
    public void sendChatAction(Long chatId, String action) {
        if (!properties.isConfigured()) return;
        try {
            String url = "https://api.telegram.org/bot" + properties.getToken() + "/sendChatAction";
            restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(Map.of("chat_id", chatId, "action", action))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // Chat actions are non-critical
        }
    }
}
