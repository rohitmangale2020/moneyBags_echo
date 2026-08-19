package com.training.platform.risk.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ModelScoringClient {
    private final RestClient restClient;

    public ModelScoringClient(RestClient modelScoringRestClient) { this.restClient = modelScoringRestClient; }

    public ModelScoreResponse score(ModelScoreRequest request) {
        ModelScoreResponse response = restClient.post().uri("/score").body(request)
                .retrieve().body(ModelScoreResponse.class);
        if (response == null) throw new IllegalStateException("Model scoring service returned an empty response");
        return response;
    }
}
