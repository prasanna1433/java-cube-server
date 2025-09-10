package com.example.cubeserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import jakarta.annotation.PostConstruct;

@Service
public class CubeClient {
    private WebClient webClient;
    private String endpoint;
    private String apiSecret;
    private Map<String, Object> tokenPayload;
    private String token;
    private final Logger logger = LoggerFactory.getLogger(CubeClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cube.api.endpoint}")
    private String endpointProp;
    @Value("${cube.api.secret}")
    private String apiSecretProp;
    @Value("${cube.api.token-payload}")
    private String tokenPayloadProp;

    @PostConstruct
    public void init() {
        this.endpoint = endpointProp;
        this.apiSecret = apiSecretProp;
        try {
            this.tokenPayload = objectMapper.readValue(tokenPayloadProp, Map.class);
        } catch (Exception e) {
            logger.error("Failed to parse token payload: {}", e.getMessage());
            this.tokenPayload = Map.of();
        }
        this.webClient = WebClient.builder().baseUrl(endpoint).build();
        refreshToken();
    }

    private void refreshToken() {
        this.token = Jwts.builder()
                .setClaims(tokenPayload)
                .signWith(SignatureAlgorithm.HS256, apiSecret.getBytes())
                .compact();
    }

    public Mono<Map> request(String route, Map<String, Object> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        if ("load".equals(route)) {
            // Use POST for load, send params as JSON body
            return webClient.post()
                    .uri("/load")
                    .headers(h -> h.addAll(headers))
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        logger.error("Request failed: {}", e.getMessage());
                        return Mono.just(Map.of("error", "Request failed: " + e.getMessage()));
                    });
        } else {
            // Use GET for other routes
            return webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/" + route);
                        params.forEach((k, v) -> uriBuilder.queryParam(k, v instanceof String ? v : toJson(v)));
                        return uriBuilder.build();
                    })
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        logger.error("Request failed: {}", e.getMessage());
                        return Mono.just(Map.of("error", "Request failed: " + e.getMessage()));
                    });
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    public Mono<Map> describe() {
        return request("meta", Map.of());
    }

    public Mono<Map> query(Map<String, Object> query) {
        return request("load", Map.of("query", query));
    }

}
