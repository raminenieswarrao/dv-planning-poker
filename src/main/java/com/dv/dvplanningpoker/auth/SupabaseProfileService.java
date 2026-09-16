package com.dv.dvplanningpoker.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Reads authenticated user profile information from
 * public.profiles through Supabase REST.
 *
 * The authenticated user's access token is forwarded to
 * Supabase, therefore Row Level Security remains active.
 */
@Service
public class SupabaseProfileService {

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(10);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String supabaseUrl;

    private final String publishableKey;

    public SupabaseProfileService(
            ObjectMapper objectMapper,
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.publishable-key:}") String publishableKey) {

        this.objectMapper =
                objectMapper;

        this.supabaseUrl =
                normalizeBaseUrl(
                        supabaseUrl
                );

        this.publishableKey =
                publishableKey == null
                        ? ""
                        : publishableKey.trim();

        this.httpClient =
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                CONNECT_TIMEOUT
                        )
                        .followRedirects(
                                HttpClient.Redirect.NEVER
                        )
                        .build();
    }

    public UserProfile getProfile(
            String accessToken,
            String userId) {

        requireConfigured();

        if (accessToken == null ||
                accessToken.isBlank()) {

            throw new IllegalArgumentException(
                    "Access token is required."
            );
        }

        if (userId == null ||
                userId.isBlank()) {

            throw new IllegalArgumentException(
                    "User id is required."
            );
        }

        /*
         * userId comes from Supabase Auth itself.
         *
         * It is expected to be a UUID and therefore safe
         * to use in this PostgREST equality filter.
         */
        String path =
                "/rest/v1/profiles"
                        + "?id=eq."
                        + userId
                        + "&select=id,name,gender,avatar_url,role";

        try {

            HttpRequest request =
                    HttpRequest
                            .newBuilder()
                            .uri(
                                    URI.create(
                                            supabaseUrl +
                                                    path
                                    )
                            )
                            .timeout(
                                    REQUEST_TIMEOUT
                            )
                            .header(
                                    "apikey",
                                    publishableKey
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " +
                                            accessToken
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            )
                            .GET()
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse
                                    .BodyHandlers
                                    .ofString()
                    );

            if (response.statusCode() < 200 ||
                    response.statusCode() >= 300) {

                throw new ProfileServiceException(
                        "Unable to load profile."
                );
            }

            JsonNode root =
                    objectMapper.readTree(
                            response.body()
                    );

            if (root == null ||
                    !root.isArray() ||
                    root.isEmpty()) {

                return null;
            }

            JsonNode profile =
                    root.get(
                            0
                    );

            return new UserProfile(
                    textOrNull(
                            profile,
                            "id"
                    ),
                    textOrNull(
                            profile,
                            "name"
                    ),
                    textOrNull(
                            profile,
                            "gender"
                    ),
                    textOrNull(
                            profile,
                            "avatar_url"
                    ),
                    textOrNull(
                            profile,
                            "role"
                    )
            );

        } catch (InterruptedException ex) {

            Thread
                    .currentThread()
                    .interrupt();

            throw new ProfileServiceException(
                    "Unable to load profile."
            );

        } catch (IOException |
                 IllegalArgumentException ex) {

            throw new ProfileServiceException(
                    "Unable to load profile."
            );
        }
    }

    private String textOrNull(
            JsonNode node,
            String fieldName) {

        if (node == null) {
            return null;
        }

        JsonNode value =
                node.get(
                        fieldName
                );

        if (value == null ||
                value.isNull()) {

            return null;
        }

        String result =
                value.asText();

        return result == null ||
                result.isBlank()
                ? null
                : result;
    }

    private void requireConfigured() {

        if (supabaseUrl.isBlank() ||
                publishableKey.isBlank()) {

            throw new IllegalStateException(
                    "Supabase is not configured."
            );
        }
    }

    private String normalizeBaseUrl(
            String value) {

        if (value == null) {
            return "";
        }

        String normalized =
                value.trim();

        while (normalized.endsWith(
                "/"
        )) {

            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1
                    );
        }

        return normalized;
    }

    public record UserProfile(
            String id,
            String name,
            String gender,
            String avatarUrl,
            String role) {
    }

    public static class ProfileServiceException
            extends RuntimeException {

        public ProfileServiceException(
                String message) {

            super(
                    message
            );
        }
    }
}
