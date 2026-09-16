package com.dv.dvplanningpoker.auth;

import com.dv.dvplanningpoker.security.PublicTextSafetyValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Handles communication between the Spring Boot application
 * and Supabase Auth.
 *
 * Security:
 *
 * - Passwords are never stored by this application.
 * - Supabase Auth performs password hashing and verification.
 * - The Supabase publishable key is supplied through an
 *   environment variable.
 * - Access/refresh tokens are returned to our controller layer.
 *   The controller stores them in secure HttpOnly cookies.
 * - Tokens are NOT stored in localStorage.
 * - No Supabase secret/service-role key is required here.
 * - Public profile names are validated server-side before
 *   being sent to Supabase.
 */
@Service
public class SupabaseAuthService {

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(10);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(15);

    private static final int MIN_PASSWORD_LENGTH =
            8;

    private static final int MAX_PASSWORD_LENGTH =
            128;

    private static final int MIN_NAME_LENGTH =
            3;

    private static final int MAX_NAME_LENGTH =
            100;

    private static final int MAX_EMAIL_LENGTH =
            254;

    private static final Set<String> ALLOWED_GENDERS =
            Set.of(
                    "MALE",
                    "FEMALE",
                    "OTHER",
                    "PREFER_NOT_TO_SAY"
            );

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String supabaseUrl;

    private final String publishableKey;

    private final PublicTextSafetyValidator
            publicTextSafetyValidator;


    public SupabaseAuthService(
            ObjectMapper objectMapper,
            PublicTextSafetyValidator publicTextSafetyValidator,
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.publishable-key:}") String publishableKey) {

        this.objectMapper =
                objectMapper;

        this.publicTextSafetyValidator =
                publicTextSafetyValidator;

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


    /* =========================================================
       REGISTER
       ========================================================= */

    /**
     * Creates a new Supabase Auth user.
     *
     * name and gender are placed into user metadata.
     *
     * Database flow:
     *
     * auth.users
     *      ↓
     * public.handle_new_user()
     *      ↓
     * public.profiles
     */
    public RegistrationResult register(
            String rawEmail,
            String rawPassword,
            String rawName,
            String rawGender) {

        requireConfigured();

        String email =
                validateEmail(
                        rawEmail
                );

        String password =
                validatePassword(
                        rawPassword
                );

        String name =
                validateName(
                        rawName
                );

        String gender =
                validateGender(
                        rawGender
                );

        ObjectNode metadata =
                objectMapper
                        .createObjectNode();

        metadata.put(
                "name",
                name
        );

        metadata.put(
                "gender",
                gender
        );

        ObjectNode body =
                objectMapper
                        .createObjectNode();

        body.put(
                "email",
                email
        );

        body.put(
                "password",
                password
        );

        body.set(
                "data",
                metadata
        );

        JsonNode response =
                sendJsonRequest(
                        "POST",
                        "/auth/v1/signup",
                        null,
                        body
                );

        JsonNode userNode =
                response.get(
                        "user"
                );

        if (
                userNode == null
                        ||
                        userNode.isNull()
        ) {

            userNode =
                    response;
        }

        AuthUser user =
                parseUser(
                        userNode
                );

        AuthSession session =
                parseSessionOrNull(
                        response
                );

        boolean confirmationRequired =
                session == null;

        return new RegistrationResult(
                user,
                session,
                confirmationRequired
        );
    }


    /* =========================================================
       LOGIN
       ========================================================= */

    public AuthSession login(
            String rawEmail,
            String rawPassword) {

        requireConfigured();

        String email =
                validateEmail(
                        rawEmail
                );

        String password =
                requirePasswordForLogin(
                        rawPassword
                );

        ObjectNode body =
                objectMapper
                        .createObjectNode();

        body.put(
                "email",
                email
        );

        body.put(
                "password",
                password
        );

        JsonNode response =
                sendJsonRequest(
                        "POST",
                        "/auth/v1/token?grant_type=password",
                        null,
                        body
                );

        AuthSession session =
                parseSessionOrNull(
                        response
                );

        if (session == null) {

            throw new SupabaseAuthException(
                    401,
                    "Unable to sign in."
            );
        }

        return session;
    }


    /* =========================================================
       REFRESH SESSION
       ========================================================= */

    public AuthSession refreshSession(
            String refreshToken) {

        requireConfigured();

        if (
                refreshToken == null
                        ||
                        refreshToken.isBlank()
                        ||
                        refreshToken.length() > 4096
        ) {

            throw new SupabaseAuthException(
                    401,
                    "Your session has expired. Please sign in again."
            );
        }

        ObjectNode body =
                objectMapper
                        .createObjectNode();

        body.put(
                "refresh_token",
                refreshToken
        );

        JsonNode response =
                sendJsonRequest(
                        "POST",
                        "/auth/v1/token?grant_type=refresh_token",
                        null,
                        body
                );

        AuthSession session =
                parseSessionOrNull(
                        response
                );

        if (session == null) {

            throw new SupabaseAuthException(
                    401,
                    "Your session has expired. Please sign in again."
            );
        }

        return session;
    }


    /* =========================================================
       CURRENT USER
       ========================================================= */

    public AuthUser getCurrentUser(
            String accessToken) {

        requireConfigured();

        validateAccessToken(
                accessToken
        );

        JsonNode response =
                sendJsonRequest(
                        "GET",
                        "/auth/v1/user",
                        accessToken,
                        null
                );

        return parseUser(
                response
        );
    }


    /* =========================================================
       CHANGE PASSWORD
       ========================================================= */

    public AuthUser changePassword(
            String accessToken,
            String rawNewPassword) {

        requireConfigured();

        validateAccessToken(
                accessToken
        );

        String newPassword =
                validatePassword(
                        rawNewPassword
                );

        ObjectNode body =
                objectMapper
                        .createObjectNode();

        body.put(
                "password",
                newPassword
        );

        JsonNode response =
                sendJsonRequest(
                        "PUT",
                        "/auth/v1/user",
                        accessToken,
                        body
                );

        return parseUser(
                response
        );
    }


    /* =========================================================
       LOGOUT
       ========================================================= */

    public void logout(
            String accessToken) {

        requireConfigured();

        if (
                accessToken == null
                        ||
                        accessToken.isBlank()
        ) {

            return;
        }

        /*
         * Logout failures should not prevent the controller
         * from clearing this application's HttpOnly cookies.
         */
        try {

            sendJsonRequest(
                    "POST",
                    "/auth/v1/logout?scope=local",
                    accessToken,
                    null
            );

        } catch (
                SupabaseAuthException ignored
        ) {

            /*
             * Cookie cleanup will still occur.
             */
        }
    }


    /* =========================================================
       HTTP
       ========================================================= */

    private JsonNode sendJsonRequest(
            String method,
            String path,
            String accessToken,
            JsonNode requestBody) {

        try {

            HttpRequest.Builder builder =
                    HttpRequest
                            .newBuilder()
                            .uri(
                                    URI.create(
                                            supabaseUrl
                                                    +
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
                                    "Accept",
                                    "application/json"
                            );

            if (
                    accessToken != null
                            &&
                            !accessToken.isBlank()
            ) {

                builder.header(
                        "Authorization",
                        "Bearer "
                                +
                                accessToken
                );
            }

            if (requestBody != null) {

                String jsonBody =
                        objectMapper
                                .writeValueAsString(
                                        requestBody
                                );

                builder.header(
                        "Content-Type",
                        "application/json"
                );

                switch (method) {

                    case "POST" ->
                            builder.POST(
                                    HttpRequest
                                            .BodyPublishers
                                            .ofString(
                                                    jsonBody
                                            )
                            );

                    case "PUT" ->
                            builder.PUT(
                                    HttpRequest
                                            .BodyPublishers
                                            .ofString(
                                                    jsonBody
                                            )
                            );

                    default ->
                            throw new IllegalArgumentException(
                                    "Unsupported HTTP method."
                            );
                }

            } else {

                switch (method) {

                    case "GET" ->
                            builder.GET();

                    case "POST" ->
                            builder.POST(
                                    HttpRequest
                                            .BodyPublishers
                                            .noBody()
                            );

                    default ->
                            throw new IllegalArgumentException(
                                    "Unsupported HTTP method."
                            );
                }
            }

            HttpResponse<String> response =
                    httpClient.send(
                            builder.build(),
                            HttpResponse
                                    .BodyHandlers
                                    .ofString()
                    );

            String responseBody =
                    response.body();

            JsonNode json =
                    parseResponseBody(
                            responseBody
                    );

            int statusCode =
                    response.statusCode();

            if (
                    statusCode < 200
                            ||
                            statusCode >= 300
            ) {

                throw new SupabaseAuthException(
                        statusCode,
                        extractSafeErrorMessage(
                                json
                        )
                );
            }

            return json;

        } catch (
                SupabaseAuthException ex
        ) {

            throw ex;

        } catch (
                InterruptedException ex
        ) {

            Thread
                    .currentThread()
                    .interrupt();

            throw new SupabaseAuthException(
                    503,
                    "Authentication service is temporarily unavailable."
            );

        } catch (
                IOException
                |
                IllegalArgumentException ex
        ) {

            throw new SupabaseAuthException(
                    503,
                    "Authentication service is temporarily unavailable."
            );
        }
    }


    private JsonNode parseResponseBody(
            String body) {

        if (
                body == null
                        ||
                        body.isBlank()
        ) {

            return objectMapper
                    .createObjectNode();
        }

        try {

            return objectMapper
                    .readTree(
                            body
                    );

        } catch (
                Exception ex
        ) {

            /*
             * Never expose malformed upstream responses.
             */
            return objectMapper
                    .createObjectNode();
        }
    }


    /* =========================================================
       RESPONSE PARSING
       ========================================================= */

    private AuthSession parseSessionOrNull(
            JsonNode response) {

        if (response == null) {

            return null;
        }

        String accessToken =
                textOrNull(
                        response,
                        "access_token"
                );

        String refreshToken =
                textOrNull(
                        response,
                        "refresh_token"
                );

        if (
                accessToken == null
                        ||
                        refreshToken == null
        ) {

            return null;
        }

        long expiresIn =
                longOrDefault(
                        response,
                        "expires_in",
                        3600L
                );

        String tokenType =
                textOrNull(
                        response,
                        "token_type"
                );

        JsonNode userNode =
                response.get(
                        "user"
                );

        AuthUser user =
                userNode == null
                        ||
                        userNode.isNull()
                        ?
                        null
                        :
                        parseUser(
                                userNode
                        );

        return new AuthSession(
                accessToken,
                refreshToken,
                expiresIn,
                tokenType == null
                        ?
                        "bearer"
                        :
                        tokenType,
                user
        );
    }


    private AuthUser parseUser(
            JsonNode userNode) {

        if (
                userNode == null
                        ||
                        userNode.isNull()
        ) {

            throw new SupabaseAuthException(
                    502,
                    "Authentication service returned an invalid response."
            );
        }

        String id =
                textOrNull(
                        userNode,
                        "id"
                );

        String email =
                textOrNull(
                        userNode,
                        "email"
                );

        if (id == null) {

            throw new SupabaseAuthException(
                    502,
                    "Authentication service returned an invalid response."
            );
        }

        boolean emailConfirmed =
                hasTextValue(
                        userNode,
                        "email_confirmed_at"
                )
                        ||
                        hasTextValue(
                                userNode,
                                "confirmed_at"
                        );

        return new AuthUser(
                id,
                email,
                emailConfirmed
        );
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

        if (
                value == null
                        ||
                        value.isNull()
                        ||
                        !value.isTextual()
        ) {

            return null;
        }

        String text =
                value.asText();

        return text == null
                ||
                text.isBlank()
                ?
                null
                :
                text;
    }


    private long longOrDefault(
            JsonNode node,
            String fieldName,
            long defaultValue) {

        if (node == null) {

            return defaultValue;
        }

        JsonNode value =
                node.get(
                        fieldName
                );

        if (
                value == null
                        ||
                        value.isNull()
                        ||
                        !value.isNumber()
        ) {

            return defaultValue;
        }

        return value.asLong();
    }


    private boolean hasTextValue(
            JsonNode node,
            String fieldName) {

        return textOrNull(
                node,
                fieldName
        ) != null;
    }


    /* =========================================================
       VALIDATION
       ========================================================= */

    private String validateEmail(
            String rawEmail) {

        if (rawEmail == null) {

            throw new IllegalArgumentException(
                    "Email is required."
            );
        }

        String email =
                rawEmail
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (
                email.isBlank()
                        ||
                        email.length() >
                                MAX_EMAIL_LENGTH
                        ||
                        !email.contains(
                                "@"
                        )
                        ||
                        email.startsWith(
                                "@"
                        )
                        ||
                        email.endsWith(
                                "@"
                        )
        ) {

            throw new IllegalArgumentException(
                    "Please enter a valid email address."
            );
        }

        return email;
    }


    private String validatePassword(
            String password) {

        if (
                password == null
                        ||
                        password.length() <
                                MIN_PASSWORD_LENGTH
        ) {

            throw new IllegalArgumentException(
                    "Password must be at least 8 characters."
            );
        }

        if (
                password.length() >
                        MAX_PASSWORD_LENGTH
        ) {

            throw new IllegalArgumentException(
                    "Password is too long."
            );
        }

        return password;
    }


    /**
     * Login deliberately does not disclose password policy.
     */
    private String requirePasswordForLogin(
            String password) {

        if (
                password == null
                        ||
                        password.isBlank()
                        ||
                        password.length() >
                                MAX_PASSWORD_LENGTH
        ) {

            throw new IllegalArgumentException(
                    "Invalid email or password."
            );
        }

        return password;
    }


    private String validateName(
            String rawName) {

        if (rawName == null) {

            throw new IllegalArgumentException(
                    "Name is required."
            );
        }


        /*
         * =====================================================
         * SECURITY / PUBLIC CONTENT VALIDATION
         * =====================================================
         *
         * Validate the ORIGINAL text before whitespace
         * normalization.
         *
         * This means zero-width characters, control characters,
         * markup and disguised offensive content cannot simply
         * disappear during normalization before inspection.
         */
        publicTextSafetyValidator.validate(
                rawName,
                "Name"
        );


        String name =
                rawName
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        if (
                name.length() <
                        MIN_NAME_LENGTH
        ) {

            throw new IllegalArgumentException(
                    "Name must be at least 3 characters."
            );
        }


        if (
                name.length() >
                        MAX_NAME_LENGTH
        ) {

            throw new IllegalArgumentException(
                    "Name must be 100 characters or fewer."
            );
        }


        /*
         * Validate the final normalized display value as well.
         */
        publicTextSafetyValidator.validate(
                name,
                "Name"
        );


        return name;
    }


    private String validateGender(
            String rawGender) {

        if (
                rawGender == null
                        ||
                        rawGender.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Please select a gender."
            );
        }

        String gender =
                rawGender
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                !ALLOWED_GENDERS.contains(
                        gender
                )
        ) {

            throw new IllegalArgumentException(
                    "Invalid gender selection."
            );
        }

        return gender;
    }


    private void validateAccessToken(
            String accessToken) {

        if (
                accessToken == null
                        ||
                        accessToken.isBlank()
                        ||
                        accessToken.length() >
                                8192
        ) {

            throw new SupabaseAuthException(
                    401,
                    "Please sign in."
            );
        }
    }


    /* =========================================================
       CONFIGURATION
       ========================================================= */

    private void requireConfigured() {

        if (
                supabaseUrl.isBlank()
                        ||
                        publishableKey.isBlank()
        ) {

            throw new IllegalStateException(
                    "Supabase authentication is not configured."
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

        while (
                normalized.endsWith(
                        "/"
                )
        ) {

            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1
                    );
        }

        return normalized;
    }


    /* =========================================================
       SAFE ERROR HANDLING
       ========================================================= */

    private String extractSafeErrorMessage(
            JsonNode json) {

        String message =
                firstText(
                        json,
                        "message",
                        "msg",
                        "error_description",
                        "error"
                );

        if (
                message == null
                        ||
                        message.length() > 200
        ) {

            return "Authentication request failed.";
        }

        return message;
    }


    private String firstText(
            JsonNode node,
            String... fieldNames) {

        if (node == null) {

            return null;
        }

        for (
                String fieldName :
                fieldNames
        ) {

            String value =
                    textOrNull(
                            node,
                            fieldName
                    );

            if (value != null) {

                return value;
            }
        }

        return null;
    }


    /* =========================================================
       RESULT TYPES
       ========================================================= */

    public record AuthUser(
            String id,
            String email,
            boolean emailConfirmed) {
    }


    public record AuthSession(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType,
            AuthUser user) {
    }


    public record RegistrationResult(
            AuthUser user,
            AuthSession session,
            boolean emailConfirmationRequired) {
    }


    /* =========================================================
       EXCEPTION TYPE
       ========================================================= */

    public static class SupabaseAuthException
            extends RuntimeException {

        private final int statusCode;


        public SupabaseAuthException(
                int statusCode,
                String message) {

            super(
                    message
            );

            this.statusCode =
                    statusCode;
        }


        public int getStatusCode() {

            return statusCode;
        }
    }
}