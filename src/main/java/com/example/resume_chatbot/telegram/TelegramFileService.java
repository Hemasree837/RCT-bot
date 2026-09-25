package com.example.resume_chatbot.telegram;

import com.example.resume_chatbot.config.TelegramProperties;
import com.example.resume_chatbot.exception.TelegramIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramFileService {

    private final TelegramProperties properties;
    private final RestClient restClient = RestClient.builder().build();

    /**
     * Downloads an uploaded document from Telegram servers directly into an in-memory InputStream.
     */
    public InputStream downloadTelegramFile(String fileId) {
        if (!properties.isConfigured()) {
            throw new TelegramIntegrationException("Telegram bot token is not configured.");
        }

        try {
            // Step 1: Call getFile to retrieve the relative file path
            String getFileUrl = "https://api.telegram.org/bot" + properties.getToken() + "/getFile?file_id=" + fileId;
            Map<?, ?> response = restClient.get()
                    .uri(getFileUrl)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
                throw new TelegramIntegrationException("Failed to get file metadata from Telegram: " + response);
            }

            Map<?, ?> result = (Map<?, ?>) response.get("result");
            String filePath = (String) result.get("file_path");

            if (filePath == null) {
                throw new TelegramIntegrationException("File path missing from Telegram response.");
            }

            // Step 2: Download file bytes directly
            String downloadUrl = "https://api.telegram.org/file/bot" + properties.getToken() + "/" + filePath;
            byte[] fileBytes = restClient.get()
                    .uri(downloadUrl)
                    .retrieve()
                    .body(byte[].class);

            if (fileBytes == null || fileBytes.length == 0) {
                throw new TelegramIntegrationException("Downloaded empty file from Telegram.");
            }

            log.info("Successfully downloaded file ({}) with {} bytes", filePath, fileBytes.length);
            return new ByteArrayInputStream(fileBytes);

        } catch (TelegramIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error downloading file from Telegram: {}", e.getMessage(), e);
            throw new TelegramIntegrationException("Failed to download file from Telegram: " + e.getMessage(), e);
        }
    }
}
