package com.training.platform.security.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** GPT-OSS assistant through an OpenAI-compatible local runtime such as Ollama. */
@RestController
@RequestMapping("/auth/gpt-oss")
public class GptOssAssistantController {
    private static final String SYSTEM_PROMPT = """
            You are MoneyBags Banking Assistant for authenticated bank employees and customers.
            Give concise, safe banking guidance in plain language. Do not claim to perform actions, approve KYC,
            move money, expose credentials, or invent customer information. Ask the employee to
            use approved platform workflows for customer changes, KYC, accounts, and transactions.

            Format every answer for a busy bank employee:
            - Use plain text only: never use Markdown, tables, pipes, asterisks, HTML tags, or code blocks.
            - Start with a one-sentence summary.
            - Use at most three short labelled sections, followed by bullet points beginning with "- ".
            - Give no more than six bullets in total and keep each bullet to one sentence.
            - For a customer-360 request, provide a short checklist of what to review, not a large template.
            """;

    private final RestClient modelClient;
    private final String apiKey;
    private final String model;

    public GptOssAssistantController(@Value("${gpt-oss.base-url:http://localhost:11434/v1}") String baseUrl,
                                     @Value("${gpt-oss.api-key:}") String apiKey,
                                     @Value("${gpt-oss.model:gpt-oss:20b}") String model) {
        // Do not use the application's load-balanced RestClient.Builder here:
        // localhost is the Ollama runtime, not a Eureka service ID.
        this.modelClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @PostMapping("/chat")
    public AssistantResponse chat(@Valid @RequestBody AssistantRequest request) {
        try {
            RestClient.RequestBodySpec call = modelClient.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                call.header("Authorization", "Bearer " + apiKey);
            }
            GptOssResponse response = call.body(Map.of("model", model, "stream", false, "max_tokens", 400, "messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", request.message()))))
                    .retrieve().body(GptOssResponse.class);
            String answer = response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null || response.choices().get(0).message().content() == null
                    ? "GPT-OSS did not return a response. Please try again."
                    : response.choices().get(0).message().content().trim();
            return new AssistantResponse("GPT_OSS_ASSISTANT", answer,
                    new Policy("GUIDANCE_ONLY", "GPT-OSS provides guidance; approved MoneyBags workflows perform actions."));
        } catch (RuntimeException exception) {
            throw new AssistantUnavailableException("GPT-OSS is unavailable. Start the configured runtime and verify GPT_OSS_BASE_URL and GPT_OSS_MODEL.");
        }
    }

    public record AssistantRequest(@NotBlank String message, Long customerId, String transactionId, String accountId, String module) { }
    public record AssistantResponse(String intent, String answer, List<Object> evidence, List<String> nextSteps,
                                    List<Object> recommendations, Policy policy) {
        public AssistantResponse(String intent, String answer, Policy policy) {
            this(intent, answer, List.of(), List.of(), List.of(), policy);
        }
    }
    public record Policy(String decision, String rationale) { }
    public record GptOssResponse(List<Choice> choices) { }
    public record Choice(Message message) { }
    public record Message(String content) { }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    static class AssistantUnavailableException extends RuntimeException {
        AssistantUnavailableException(String message) { super(message); }
    }
}
