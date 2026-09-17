package com.dv.dvplanningpoker.profile;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and updates the authenticated user's profile through
 * the Supabase REST API.
 *
 * The user's Supabase access token is forwarded with every
 * request so Row Level Security remains active.
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
            @Value("${supabase.publishable-key:}")
            String publishableKey) {

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

    /* =====================================================
       LOAD PROFILE
       ===================================================== */

    public UserProfile getProfile(
            String accessToken,
            String userId) {

        validateCredentials(
                accessToken,
                userId
        );

        HttpRequest request =
                baseRequest(
                        accessToken,
                        profileUri(
                                userId
                        )
                )
                        .GET()
                        .build();

        HttpResponse<String> response =
                send(
                        request,
                        "Unable to load profile."
                );

        validateResponse(
                response,
                "Unable to load profile."
        );

        return readProfile(
                response.body()
        );
    }

    /* =====================================================
   UPDATE PROFILE
   ===================================================== */

    public UserProfile updateProfile(
            String accessToken,
            String userId,
            String name,
            String gender) {

        validateCredentials(
                accessToken,
                userId
        );


        String normalizedName =
                normalizeName(
                        name
                );


        String normalizedGender =
                normalizeGender(
                        gender
                );


        Map<String, Object> update =
                new LinkedHashMap<>();


        update.put(
                "name",
                normalizedName
        );


        update.put(
                "gender",
                normalizedGender
        );


        update.put(
                "updated_at",
                java.time.OffsetDateTime
                        .now()
                        .toString()
        );


        return patchProfile(
                accessToken,
                userId,
                update,
                "Unable to update profile."
        );
    }

    /* =====================================================
   UPDATE BUILT-IN AVATAR
   ===================================================== */

    public UserProfile updateAvatar(
            String accessToken,
            String userId,
            String avatarUrl) {

        validateCredentials(
                accessToken,
                userId
        );

        String normalizedAvatarUrl =
                validateBuiltInAvatarUrl(
                        avatarUrl
                );

        Map<String, Object> update =
                new LinkedHashMap<>();

        update.put(
                "avatar_url",
                normalizedAvatarUrl
        );

        update.put(
                "updated_at",
                java.time.OffsetDateTime
                        .now()
                        .toString()
        );

        return patchProfile(
                accessToken,
                userId,
                update,
                "Unable to update profile picture."
        );
    }


/* =====================================================
   REMOVE AVATAR
   ===================================================== */

    public UserProfile removeAvatar(
            String accessToken,
            String userId) {

        validateCredentials(
                accessToken,
                userId
        );

        Map<String, Object> update =
                new LinkedHashMap<>();

        /*
         * A null database value tells the frontend to display
         * the user's initials again.
         */
        update.put(
                "avatar_url",
                null
        );

        update.put(
                "updated_at",
                java.time.OffsetDateTime
                        .now()
                        .toString()
        );

        return patchProfile(
                accessToken,
                userId,
                update,
                "Unable to remove profile picture."
        );
    }

    /* =====================================================
       HTTP HELPERS
       ===================================================== */

    private HttpRequest.Builder baseRequest(
            String accessToken,
            URI uri) {

        return HttpRequest
                .newBuilder()
                .uri(
                        uri
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
                );
    }

    private HttpResponse<String> send(
            HttpRequest request,
            String failureMessage) {

        try {

            return httpClient.send(
                    request,
                    HttpResponse
                            .BodyHandlers
                            .ofString()
            );

        } catch (InterruptedException ex) {

            Thread
                    .currentThread()
                    .interrupt();

            throw new ProfileServiceException(
                    failureMessage
            );

        } catch (IOException ex) {

            throw new ProfileServiceException(
                    failureMessage
            );
        }
    }

    private void validateResponse(
            HttpResponse<String> response,
            String fallbackMessage) {

        int statusCode =
                response.statusCode();

        if (statusCode >= 200 &&
                statusCode < 300) {

            return;
        }

        String message =
                switch (statusCode) {

                    case 400, 422 ->
                            "Profile information is invalid.";

                    case 401, 403 ->
                            "You are not authorized to access this profile.";

                    case 404 ->
                            "Profile was not found.";

                    case 409 ->
                            "The profile could not be updated because of a conflict.";

                    case 429 ->
                            "Too many profile requests. Please try again shortly.";

                    default ->
                            fallbackMessage;
                };

        throw new ProfileServiceException(
                message,
                statusCode
        );
    }

    private UserProfile patchProfile(
            String accessToken,
            String userId,
            Map<String, Object> update,
            String failureMessage) {

        String body;

        try {

            body =
                    objectMapper.writeValueAsString(
                            update
                    );

        } catch (Exception ex) {

            throw new ProfileServiceException(
                    "Unable to prepare profile update."
            );
        }


        HttpRequest request =
                baseRequest(
                        accessToken,
                        profileUri(
                                userId
                        )
                )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "Prefer",
                                "return=representation"
                        )
                        .method(
                                "PATCH",
                                HttpRequest
                                        .BodyPublishers
                                        .ofString(
                                                body
                                        )
                        )
                        .build();


        HttpResponse<String> response =
                send(
                        request,
                        failureMessage
                );


        validateResponse(
                response,
                failureMessage
        );


        UserProfile profile =
                readProfile(
                        response.body()
                );


        if (profile == null) {

            throw new ProfileServiceException(
                    "Profile was not found.",
                    404
            );
        }


        return profile;
    }

    /* =====================================================
       PROFILE MAPPING
       ===================================================== */

    private UserProfile readProfile(
            String responseBody) {

        try {

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
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
                    ),
                    textOrNull(
                            profile,
                            "created_at"
                    ),
                    textOrNull(
                            profile,
                            "updated_at"
                    )
            );

        } catch (Exception ex) {

            throw new ProfileServiceException(
                    "Unable to read profile information."
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

    /* =====================================================
       VALIDATION
       ===================================================== */

    private void validateCredentials(
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

        try {

            UUID.fromString(
                    userId
            );

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException(
                    "User id is invalid."
            );
        }
    }

    private String normalizeName(
            String name) {

        if (name == null) {

            throw new IllegalArgumentException(
                    "Name is required."
            );
        }


        String normalized =
                name
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        if (normalized.length() < 2) {

            throw new IllegalArgumentException(
                    "Name must contain at least 2 characters."
            );
        }


        if (normalized.length() > 100) {

            throw new IllegalArgumentException(
                    "Name cannot exceed 100 characters."
            );
        }


        /*
         * Allow Unicode letters, accented-letter marks,
         * spaces, apostrophes, periods and hyphens.
         *
         * This rejects HTML characters such as:
         *
         * < > / = " ;
         */
        if (!normalized.matches(
                "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]{1,99}$"
        )) {

            throw new IllegalArgumentException(
                    "Name can contain only letters, spaces, apostrophes, periods and hyphens."
            );
        }


        /*
         * Reject suspicious repeated punctuation.
         */
        if (normalized.contains(
                ".."
        ) ||
                normalized.contains(
                        "--"
                ) ||
                normalized.contains(
                        "''"
                )) {

            throw new IllegalArgumentException(
                    "Name contains invalid repeated punctuation."
            );
        }


        return normalized;
    }

    private String normalizeGender(
            String gender) {

        if (gender == null ||
                gender.isBlank()) {

            return null;
        }


        String normalized =
                gender
                        .trim()
                        .replace(
                                "-",
                                "_"
                        )
                        .replace(
                                " ",
                                "_"
                        )
                        .toUpperCase();


        return switch (normalized) {

            case "MALE" ->
                    "MALE";

            case "FEMALE" ->
                    "FEMALE";

            case "NON_BINARY",
                 "NONBINARY" ->
                    "NON_BINARY";

            case "OTHER" ->
                    "OTHER";

            default ->
                    throw new IllegalArgumentException(
                            "Please select a valid gender."
                    );
        };
    }

    private String validateBuiltInAvatarUrl(
            String avatarUrl) {

        if (avatarUrl == null ||
                avatarUrl.isBlank()) {

            throw new IllegalArgumentException(
                    "Avatar selection is required."
            );
        }


        String normalized =
                avatarUrl.trim();


        /*
         * Defense in depth:
         *
         * Only locally bundled PNG avatars can be saved here.
         * External URLs, path traversal, query parameters and
         * arbitrary static files are rejected.
         */
        if (!normalized.matches(
                "^/images/avatars/(men|women)/avatar-[0-9]{2}\\.png$"
        )) {

            throw new IllegalArgumentException(
                    "Avatar selection is invalid."
            );
        }


        return normalized;
    }
    /* =====================================================
       CONFIGURATION
       ===================================================== */

    private URI profileUri(
            String userId) {

        String path =
                "/rest/v1/profiles"
                        + "?id=eq."
                        + userId
                        + "&select=id,name,gender,"
                        + "avatar_url,role,"
                        + "created_at,updated_at";

        return URI.create(
                supabaseUrl +
                        path
        );
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
}