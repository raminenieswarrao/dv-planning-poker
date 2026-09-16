package com.dv.dvplanningpoker.tournament;

import com.dv.dvplanningpoker.security.PublicTextSafetyValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * Tournament data access through Supabase PostgREST.
 *
 * IMPORTANT:
 *
 * The browser never supplies a Supabase token directly.
 * Spring receives the authenticated token through the
 * HttpOnly cookie/session flow and forwards it here.
 *
 * All authenticated requests continue to respect Supabase RLS.
 *
 * Public-facing tournament text is validated server-side before
 * being written to Supabase.
 */
@Service
public class SupabaseTournamentService {

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(
                    10
            );


    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(
                    15
            );


    private static final int MAX_SEARCH_RESULTS =
            10;


    private static final Set<String> ALLOWED_STATUSES =
            Set.of(
                    "DRAFT",
                    "REGISTRATION_OPEN",
                    "REGISTRATION_CLOSED",
                    "IN_PROGRESS",
                    "COMPLETED",
                    "CANCELLED"
            );


    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String supabaseUrl;

    private final String publishableKey;

    private final PublicTextSafetyValidator
            publicTextSafetyValidator;


    public SupabaseTournamentService(
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
       CREATE TOURNAMENT
       ========================================================= */

    public Tournament createTournament(
            String accessToken,
            String authenticatedUserId,
            CreateTournamentCommand command) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        String userId =
                validateUuid(
                        authenticatedUserId,
                        "Invalid authenticated user."
                );


        if (command == null) {

            throw new IllegalArgumentException(
                    "Tournament information is required."
            );
        }


        String name =
                validateName(
                        command.name()
                );


        String gameType =
                validateGameType(
                        command.gameType()
                );


        String format =
                normalizeOptional(
                        command.format(),
                        30,
                        "Tournament format is too long."
                );


        String description =
                normalizePublicOptional(
                        command.description(),
                        2000,
                        "Tournament description",
                        "Tournament description is too long."
                );


        String location =
                normalizePublicOptional(
                        command.location(),
                        200,
                        "Tournament location",
                        "Tournament location is too long."
                );


        String status =
                normalizeStatus(
                        command.status()
                );


        ObjectNode body =
                objectMapper
                        .createObjectNode();


        body.put(
                "name",
                name
        );


        body.put(
                "game_type",
                gameType
        );


        putNullable(
                body,
                "format",
                format
        );


        body.put(
                "status",
                status
        );


        putNullable(
                body,
                "description",
                description
        );


        putNullable(
                body,
                "location",
                location
        );


        putNullableDateTime(
                body,
                "scheduled_at",
                command.scheduledAt()
        );


        putNullableDateTime(
                body,
                "registration_end_at",
                command.registrationEndAt()
        );


        /*
         * CRITICAL:
         *
         * created_by comes from the authenticated session,
         * never from browser input.
         */
        body.put(
                "created_by",
                userId
        );


        JsonNode response =
                sendRequest(
                        "POST",
                        "/rest/v1/tournaments"
                                +
                                "?select=*",
                        accessToken,
                        body,
                        true
                );


        JsonNode row =
                firstArrayRow(
                        response
                );


        if (row == null) {

            throw new TournamentServiceException(
                    502,
                    "Tournament was created but could not be returned."
            );
        }


        return parseTournament(
                row,
                userId,
                true
        );
    }


    /* =========================================================
       ONGOING TOURNAMENTS
       ========================================================= */

    public List<Tournament> getOngoingTournaments(
            String accessToken,
            String authenticatedUserId) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        String userId =
                validateUuid(
                        authenticatedUserId,
                        "Invalid authenticated user."
                );


        String path =
                "/rest/v1/tournaments"
                        +
                        "?status=in.(REGISTRATION_OPEN,REGISTRATION_CLOSED,IN_PROGRESS)"
                        +
                        "&select=*"
                        +
                        "&order=scheduled_at.asc.nullslast,created_at.desc";


        JsonNode response =
                sendRequest(
                        "GET",
                        path,
                        accessToken,
                        null,
                        false
                );


        Set<String> membership =
                getCurrentUserTournamentMembership(
                        accessToken,
                        userId
                );


        List<Tournament> result =
                new ArrayList<>();


        if (!response.isArray()) {

            return result;
        }


        for (JsonNode row : response) {

            String tournamentId =
                    textOrNull(
                            row,
                            "id"
                    );


            String creatorId =
                    textOrNull(
                            row,
                            "created_by"
                    );


            boolean creator =
                    userId.equals(
                            creatorId
                    );


            boolean member =
                    tournamentId != null
                            &&
                            membership.contains(
                                    tournamentId
                            );


            boolean canEdit =
                    creator
                            ||
                            member;


            Tournament tournament =
                    parseTournament(
                            row,
                            userId,
                            canEdit
                    );


            result.add(
                    tournament
            );
        }


        return result;
    }


    /* =========================================================
       RECENT COMPLETED TOURNAMENT
       ========================================================= */

    public Tournament getRecentCompletedTournament(
            String accessToken,
            String authenticatedUserId) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        String userId =
                validateUuid(
                        authenticatedUserId,
                        "Invalid authenticated user."
                );


        String path =
                "/rest/v1/tournaments"
                        +
                        "?status=eq.COMPLETED"
                        +
                        "&select=*"
                        +
                        "&order=completed_at.desc.nullslast,updated_at.desc,created_at.desc"
                        +
                        "&limit=1";


        JsonNode response =
                sendRequest(
                        "GET",
                        path,
                        accessToken,
                        null,
                        false
                );


        JsonNode row =
                firstArrayRow(
                        response
                );


        if (row == null) {

            return null;
        }


        String tournamentId =
                textOrNull(
                        row,
                        "id"
                );


        String creatorId =
                textOrNull(
                        row,
                        "created_by"
                );


        Set<String> membership =
                getCurrentUserTournamentMembership(
                        accessToken,
                        userId
                );


        boolean creator =
                userId.equals(
                        creatorId
                );


        boolean member =
                tournamentId != null
                        &&
                        membership.contains(
                                tournamentId
                        );


        return parseTournament(
                row,
                userId,
                creator
                        ||
                        member
        );
    }


    /* =========================================================
       GET ONE TOURNAMENT
       ========================================================= */

    public Tournament getTournament(
            String accessToken,
            String authenticatedUserId,
            String tournamentId) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        String userId =
                validateUuid(
                        authenticatedUserId,
                        "Invalid authenticated user."
                );


        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );


        String path =
                "/rest/v1/tournaments"
                        +
                        "?id=eq."
                        +
                        validTournamentId
                        +
                        "&select=*"
                        +
                        "&limit=1";


        JsonNode response =
                sendRequest(
                        "GET",
                        path,
                        accessToken,
                        null,
                        false
                );


        JsonNode row =
                firstArrayRow(
                        response
                );


        if (row == null) {

            return null;
        }


        Set<String> membership =
                getCurrentUserTournamentMembership(
                        accessToken,
                        userId
                );


        String creatorId =
                textOrNull(
                        row,
                        "created_by"
                );


        boolean canEdit =
                userId.equals(
                        creatorId
                )
                        ||
                        membership.contains(
                                validTournamentId
                        );


        return parseTournament(
                row,
                userId,
                canEdit
        );
    }


    /* =========================================================
       SEARCH REGISTERED USERS
       ========================================================= */

    public List<UserSearchResult> searchUsers(
            String accessToken,
            String rawName) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        if (rawName == null) {

            return List.of();
        }


        String name =
                rawName
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        if (name.length() < 2) {

            return List.of();
        }


        if (name.length() > 100) {

            throw new IllegalArgumentException(
                    "Search name is too long."
            );
        }


        String encodedName =
                encodeQueryValue(
                        name
                );


        String path =
                "/rest/v1/profiles"
                        +
                        "?name=ilike.*"
                        +
                        encodedName
                        +
                        "*"
                        +
                        "&select=id,name,avatar_url"
                        +
                        "&order=name.asc"
                        +
                        "&limit="
                        +
                        MAX_SEARCH_RESULTS;


        JsonNode response =
                sendRequest(
                        "GET",
                        path,
                        accessToken,
                        null,
                        false
                );


        List<UserSearchResult> result =
                new ArrayList<>();


        if (!response.isArray()) {

            return result;
        }


        for (JsonNode row : response) {

            String id =
                    textOrNull(
                            row,
                            "id"
                    );


            String resultName =
                    textOrNull(
                            row,
                            "name"
                    );


            if (
                    id == null
                            ||
                            resultName == null
            ) {

                continue;
            }


            result.add(
                    new UserSearchResult(
                            id,
                            resultName,
                            textOrNull(
                                    row,
                                    "avatar_url"
                            )
                    )
            );
        }


        return result;
    }


    /* =========================================================
       ADD PARTICIPANT
       ========================================================= */

    public TournamentParticipant addParticipant(
            String accessToken,
            String tournamentId,
            String userId) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );


        String validUserId =
                validateUuid(
                        userId,
                        "Invalid user."
                );


        UserSearchResult existingUser =
                getUserById(
                        accessToken,
                        validUserId
                );


        if (existingUser == null) {

            throw new TournamentServiceException(
                    404,
                    "Can't find this user."
            );
        }


        ObjectNode body =
                objectMapper
                        .createObjectNode();


        body.put(
                "tournament_id",
                validTournamentId
        );


        body.put(
                "user_id",
                validUserId
        );


        body.put(
                "status",
                "CONFIRMED"
        );


        JsonNode response =
                sendRequest(
                        "POST",
                        "/rest/v1/tournament_participants"
                                +
                                "?select=*",
                        accessToken,
                        body,
                        true
                );


        JsonNode row =
                firstArrayRow(
                        response
                );


        if (row == null) {

            throw new TournamentServiceException(
                    502,
                    "Unable to add participant."
            );
        }


        return new TournamentParticipant(
                textOrNull(
                        row,
                        "id"
                ),
                validTournamentId,
                validUserId,
                existingUser.name(),
                existingUser.avatarUrl(),
                textOrDefault(
                        row,
                        "status",
                        "CONFIRMED"
                ),
                integerOrNull(
                        row,
                        "seed_number"
                ),
                textOrNull(
                        row,
                        "registered_at"
                )
        );
    }


    /* =========================================================
       PARTICIPANTS
       ========================================================= */

    public List<TournamentParticipant> getParticipants(
            String accessToken,
            String tournamentId) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );


        String path =
                "/rest/v1/tournament_participants"
                        +
                        "?tournament_id=eq."
                        +
                        validTournamentId
                        +
                        "&select=id,tournament_id,user_id,status,seed_number,registered_at"
                        +
                        "&order=registered_at.asc";


        JsonNode response =
                sendRequest(
                        "GET",
                        path,
                        accessToken,
                        null,
                        false
                );


        if (
                !response.isArray()
                        ||
                        response.isEmpty()
        ) {

            return List.of();
        }


        List<String> userIds =
                new ArrayList<>();


        for (JsonNode row : response) {

            String userId =
                    textOrNull(
                            row,
                            "user_id"
                    );


            if (userId != null) {

                userIds.add(
                        userId
                );
            }
        }


        Map<String, UserSearchResult> profiles =
                getUsersByIds(
                        accessToken,
                        userIds
                );


        List<TournamentParticipant> result =
                new ArrayList<>();


        for (JsonNode row : response) {

            String userId =
                    textOrNull(
                            row,
                            "user_id"
                    );


            if (userId == null) {

                continue;
            }


            UserSearchResult profile =
                    profiles.get(
                            userId
                    );


            result.add(
                    new TournamentParticipant(
                            textOrNull(
                                    row,
                                    "id"
                            ),
                            validTournamentId,
                            userId,
                            profile == null
                                    ? "Unknown User"
                                    : profile.name(),
                            profile == null
                                    ? null
                                    : profile.avatarUrl(),
                            textOrDefault(
                                    row,
                                    "status",
                                    "REGISTERED"
                            ),
                            integerOrNull(
                                    row,
                                    "seed_number"
                            ),
                            textOrNull(
                                    row,
                                    "registered_at"
                            )
                    )
            );
        }


        return result;
    }


    /* =========================================================
       REMOVE PARTICIPANT
       ========================================================= */

    public void removeParticipant(
            String accessToken,
            String tournamentId,
            String userId) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );


        String validUserId =
                validateUuid(
                        userId,
                        "Invalid user."
                );


        String path =
                "/rest/v1/tournament_participants"
                        +
                        "?tournament_id=eq."
                        +
                        validTournamentId
                        +
                        "&user_id=eq."
                        +
                        validUserId;


        sendRequest(
                "DELETE",
                path,
                accessToken,
                null,
                false
        );
    }


    /* =========================================================
       UPDATE TOURNAMENT
       ========================================================= */

    public Tournament updateTournament(
            String accessToken,
            String authenticatedUserId,
            String tournamentId,
            UpdateTournamentCommand command) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        String userId =
                validateUuid(
                        authenticatedUserId,
                        "Invalid authenticated user."
                );


        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );


        if (command == null) {

            throw new IllegalArgumentException(
                    "Tournament information is required."
            );
        }


        ObjectNode body =
                objectMapper
                        .createObjectNode();


        if (command.name() != null) {

            body.put(
                    "name",
                    validateName(
                            command.name()
                    )
            );
        }


        if (command.gameType() != null) {

            body.put(
                    "game_type",
                    validateGameType(
                            command.gameType()
                    )
            );
        }


        if (command.format() != null) {

            putNullable(
                    body,
                    "format",
                    normalizeOptional(
                            command.format(),
                            30,
                            "Tournament format is too long."
                    )
            );
        }


        if (command.status() != null) {

            body.put(
                    "status",
                    normalizeStatus(
                            command.status()
                    )
            );
        }


        if (command.descriptionProvided()) {

            putNullable(
                    body,
                    "description",
                    normalizePublicOptional(
                            command.description(),
                            2000,
                            "Tournament description",
                            "Tournament description is too long."
                    )
            );
        }


        if (command.locationProvided()) {

            putNullable(
                    body,
                    "location",
                    normalizePublicOptional(
                            command.location(),
                            200,
                            "Tournament location",
                            "Tournament location is too long."
                    )
            );
        }


        if (command.scheduledAtProvided()) {

            putNullableDateTime(
                    body,
                    "scheduled_at",
                    command.scheduledAt()
            );
        }


        if (command.registrationEndAtProvided()) {

            putNullableDateTime(
                    body,
                    "registration_end_at",
                    command.registrationEndAt()
            );
        }


        if (body.isEmpty()) {

            throw new IllegalArgumentException(
                    "No tournament changes were provided."
            );
        }


        /*
         * Ownership is never accepted from the browser.
         */
        body.put(
                "updated_at",
                OffsetDateTime
                        .now()
                        .toString()
        );


        String path =
                "/rest/v1/tournaments"
                        +
                        "?id=eq."
                        +
                        validTournamentId
                        +
                        "&select=*";


        JsonNode response =
                sendRequest(
                        "PATCH",
                        path,
                        accessToken,
                        body,
                        true
                );


        JsonNode row =
                firstArrayRow(
                        response
                );


        if (row == null) {

            throw new TournamentServiceException(
                    404,
                    "Tournament not found or you do not have permission to edit it."
            );
        }


        return parseTournament(
                row,
                userId,
                true
        );
    }


    /* =========================================================
       DELETE TOURNAMENT
       ========================================================= */

    public void deleteTournament(
            String accessToken,
            String tournamentId) {

        requireConfigured();


        validateAccessToken(
                accessToken
        );


        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );


        String path =
                "/rest/v1/tournaments"
                        +
                        "?id=eq."
                        +
                        validTournamentId;


        sendRequest(
                "DELETE",
                path,
                accessToken,
                null,
                false
        );
    }


    /* =========================================================
       CURRENT USER MEMBERSHIP
       ========================================================= */

    private Set<String> getCurrentUserTournamentMembership(
            String accessToken,
            String userId) {

        String path =
                "/rest/v1/tournament_participants"
                        +
                        "?user_id=eq."
                        +
                        userId
                        +
                        "&status=in.(REGISTERED,CONFIRMED)"
                        +
                        "&select=tournament_id";


        JsonNode response =
                sendRequest(
                        "GET",
                        path,
                        accessToken,
                        null,
                        false
                );


        Set<String> result =
                new HashSet<>();


        if (!response.isArray()) {

            return result;
        }


        for (JsonNode row : response) {

            String tournamentId =
                    textOrNull(
                            row,
                            "tournament_id"
                    );


            if (tournamentId != null) {

                result.add(
                        tournamentId
                );
            }
        }


        return result;
    }


    /* =========================================================
       PROFILE HELPERS
       ========================================================= */

    private UserSearchResult getUserById(
            String accessToken,
            String userId) {

        String path =
                "/rest/v1/profiles"
                        +
                        "?id=eq."
                        +
                        userId
                        +
                        "&select=id,name,avatar_url"
                        +
                        "&limit=1";


        JsonNode response =
                sendRequest(
                        "GET",
                        path,
                        accessToken,
                        null,
                        false
                );


        JsonNode row =
                firstArrayRow(
                        response
                );


        if (row == null) {

            return null;
        }


        String id =
                textOrNull(
                        row,
                        "id"
                );


        String name =
                textOrNull(
                        row,
                        "name"
                );


        if (
                id == null
                        ||
                        name == null
        ) {

            return null;
        }


        return new UserSearchResult(
                id,
                name,
                textOrNull(
                        row,
                        "avatar_url"
                )
        );
    }


    private Map<String, UserSearchResult> getUsersByIds(
            String accessToken,
            List<String> userIds) {

        Map<String, UserSearchResult> result =
                new HashMap<>();


        if (
                userIds == null
                        ||
                        userIds.isEmpty()
        ) {

            return result;
        }


        String joinedIds =
                String.join(
                        ",",
                        userIds
                );


        String path =
                "/rest/v1/profiles"
                        +
                        "?id=in.("
                        +
                        joinedIds
                        +
                        ")"
                        +
                        "&select=id,name,avatar_url";


        JsonNode response =
                sendRequest(
                        "GET",
                        path,
                        accessToken,
                        null,
                        false
                );


        if (!response.isArray()) {

            return result;
        }


        for (JsonNode row : response) {

            String id =
                    textOrNull(
                            row,
                            "id"
                    );


            String name =
                    textOrNull(
                            row,
                            "name"
                    );


            if (
                    id == null
                            ||
                            name == null
            ) {

                continue;
            }


            result.put(
                    id,
                    new UserSearchResult(
                            id,
                            name,
                            textOrNull(
                                    row,
                                    "avatar_url"
                            )
                    )
            );
        }


        return result;
    }


    /* =========================================================
       HTTP
       ========================================================= */

    private JsonNode sendRequest(
            String method,
            String path,
            String accessToken,
            JsonNode body,
            boolean returnRepresentation) {

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
                                    "Authorization",
                                    "Bearer "
                                            +
                                            accessToken
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            );


            if (returnRepresentation) {

                builder.header(
                        "Prefer",
                        "return=representation"
                );
            }


            if (body != null) {

                String jsonBody =
                        objectMapper
                                .writeValueAsString(
                                        body
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


                    case "PATCH" ->
                            builder.method(
                                    "PATCH",
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


                    case "DELETE" ->
                            builder.DELETE();


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


            JsonNode json =
                    parseBody(
                            response.body()
                    );


            if (
                    response.statusCode() < 200
                            ||
                            response.statusCode() >= 300
            ) {

                throw new TournamentServiceException(
                        response.statusCode(),
                        extractSafeErrorMessage(
                                json
                        )
                );
            }


            return json;


        } catch (
                TournamentServiceException ex
        ) {

            throw ex;


        } catch (
                InterruptedException ex
        ) {

            Thread
                    .currentThread()
                    .interrupt();


            throw new TournamentServiceException(
                    503,
                    "Tournament service is temporarily unavailable."
            );


        } catch (
                IOException
                |
                IllegalArgumentException ex
        ) {

            throw new TournamentServiceException(
                    503,
                    "Tournament service is temporarily unavailable."
            );
        }
    }


    private JsonNode parseBody(
            String body) {

        if (
                body == null
                        ||
                        body.isBlank()
        ) {

            return objectMapper
                    .createArrayNode();
        }


        try {

            return objectMapper
                    .readTree(
                            body
                    );


        } catch (Exception ex) {

            return objectMapper
                    .createArrayNode();
        }
    }


    /* =========================================================
       TOURNAMENT PARSING
       ========================================================= */

    private Tournament parseTournament(
            JsonNode row,
            String authenticatedUserId,
            boolean canEdit) {

        String creatorId =
                textOrNull(
                        row,
                        "created_by"
                );


        boolean canDelete =
                authenticatedUserId != null
                        &&
                        authenticatedUserId.equals(
                                creatorId
                        );


        return new Tournament(
                textOrNull(
                        row,
                        "id"
                ),
                textOrNull(
                        row,
                        "name"
                ),
                textOrNull(
                        row,
                        "game_type"
                ),
                textOrNull(
                        row,
                        "format"
                ),
                textOrNull(
                        row,
                        "status"
                ),
                textOrNull(
                        row,
                        "description"
                ),
                textOrNull(
                        row,
                        "location"
                ),
                textOrNull(
                        row,
                        "scheduled_at"
                ),
                textOrNull(
                        row,
                        "registration_end_at"
                ),
                creatorId,
                textOrNull(
                        row,
                        "created_at"
                ),
                textOrNull(
                        row,
                        "updated_at"
                ),
                canEdit,
                canDelete
        );
    }


    /* =========================================================
       VALIDATION
       ========================================================= */

    private String validateName(
            String rawName) {

        if (rawName == null) {

            throw new IllegalArgumentException(
                    "Tournament name is required."
            );
        }


        /*
         * Validate the original input BEFORE whitespace
         * normalization so hidden/control characters cannot
         * disappear before inspection.
         */
        publicTextSafetyValidator.validate(
                rawName,
                "Tournament name"
        );


        String name =
                rawName
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        if (name.length() < 3) {

            throw new IllegalArgumentException(
                    "Tournament name must be at least 3 characters."
            );
        }


        if (name.length() > 150) {

            throw new IllegalArgumentException(
                    "Tournament name must be 150 characters or fewer."
            );
        }


        /*
         * Validate final normalized display value as well.
         */
        publicTextSafetyValidator.validate(
                name,
                "Tournament name"
        );


        return name;
    }


    private String validateGameType(
            String rawGameType) {

        if (
                rawGameType == null
                        ||
                        rawGameType.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Game type is required."
            );
        }


        String gameType =
                rawGameType
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        if (gameType.length() > 50) {

            throw new IllegalArgumentException(
                    "Game type is too long."
            );
        }


        return gameType;
    }


    private String normalizeStatus(
            String rawStatus) {

        if (
                rawStatus == null
                        ||
                        rawStatus.isBlank()
        ) {

            return "REGISTRATION_OPEN";
        }


        String status =
                rawStatus
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        if (
                !ALLOWED_STATUSES.contains(
                        status
                )
        ) {

            throw new IllegalArgumentException(
                    "Invalid tournament status."
            );
        }


        return status;
    }


    /**
     * Generic optional text used for controlled/internal values
     * such as tournament format.
     */
    private String normalizeOptional(
            String value,
            int maxLength,
            String errorMessage) {

        if (value == null) {

            return null;
        }


        String normalized =
                value
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        if (normalized.isBlank()) {

            return null;
        }


        if (
                normalized.length() >
                        maxLength
        ) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }


        return normalized;
    }


    /**
     * Optional PUBLIC-FACING text.
     *
     * Used for:
     *
     * - description
     * - location
     *
     * Performs:
     *
     * - public-content validation on original text
     * - whitespace normalization
     * - length validation
     * - public-content validation on final value
     */
    private String normalizePublicOptional(
            String value,
            int maxLength,
            String fieldLabel,
            String lengthErrorMessage) {

        if (value == null) {

            return null;
        }


        /*
         * Validate original value first.
         */
        publicTextSafetyValidator.validate(
                value,
                fieldLabel
        );


        String normalized =
                value
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        if (normalized.isBlank()) {

            return null;
        }


        if (
                normalized.length() >
                        maxLength
        ) {

            throw new IllegalArgumentException(
                    lengthErrorMessage
            );
        }


        /*
         * Validate final value too.
         */
        publicTextSafetyValidator.validate(
                normalized,
                fieldLabel
        );


        return normalized;
    }


    private String validateUuid(
            String rawValue,
            String errorMessage) {

        if (
                rawValue == null
                        ||
                        rawValue.isBlank()
        ) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }


        try {

            return UUID
                    .fromString(
                            rawValue.trim()
                    )
                    .toString();


        } catch (
                IllegalArgumentException ex
        ) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }
    }


    private void validateAccessToken(
            String accessToken) {

        if (
                accessToken == null
                        ||
                        accessToken.isBlank()
                        ||
                        accessToken.length() > 8192
        ) {

            throw new TournamentServiceException(
                    401,
                    "Please sign in."
            );
        }
    }


    /* =========================================================
       JSON HELPERS
       ========================================================= */

    private void putNullable(
            ObjectNode node,
            String field,
            String value) {

        if (value == null) {

            node.putNull(
                    field
            );


        } else {

            node.put(
                    field,
                    value
            );
        }
    }


    private void putNullableDateTime(
            ObjectNode node,
            String field,
            OffsetDateTime value) {

        if (value == null) {

            node.putNull(
                    field
            );


        } else {

            node.put(
                    field,
                    value.toString()
            );
        }
    }


    private JsonNode firstArrayRow(
            JsonNode node) {

        if (
                node == null
                        ||
                        !node.isArray()
                        ||
                        node.isEmpty()
        ) {

            return null;
        }


        return node.get(
                0
        );
    }


    private String textOrNull(
            JsonNode node,
            String field) {

        if (node == null) {

            return null;
        }


        JsonNode value =
                node.get(
                        field
                );


        if (
                value == null
                        ||
                        value.isNull()
        ) {

            return null;
        }


        String result =
                value.asText();


        return result == null
                ||
                result.isBlank()
                ?
                null
                :
                result;
    }


    private String textOrDefault(
            JsonNode node,
            String field,
            String defaultValue) {

        String value =
                textOrNull(
                        node,
                        field
                );


        return value == null
                ?
                defaultValue
                :
                value;
    }


    private Integer integerOrNull(
            JsonNode node,
            String field) {

        if (node == null) {

            return null;
        }


        JsonNode value =
                node.get(
                        field
                );


        if (
                value == null
                        ||
                        value.isNull()
                        ||
                        !value.isNumber()
        ) {

            return null;
        }


        return value.asInt();
    }


    /* =========================================================
       ERROR HANDLING
       ========================================================= */

    private String extractSafeErrorMessage(
            JsonNode json) {

        String message =
                firstText(
                        json,
                        "message",
                        "details"
                );


        if (
                message == null
                        ||
                        message.isBlank()
                        ||
                        message.length() > 250
        ) {

            return "Tournament request failed.";
        }


        String lower =
                message.toLowerCase(
                        Locale.ROOT
                );


        if (
                lower.contains(
                        "duplicate"
                )
        ) {

            return "This user is already in the tournament.";
        }


        return message;
    }


    private String firstText(
            JsonNode node,
            String... fields) {

        if (node == null) {

            return null;
        }


        for (String field : fields) {

            String value =
                    textOrNull(
                            node,
                            field
                    );


            if (value != null) {

                return value;
            }
        }


        return null;
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


    private String encodeQueryValue(
            String value) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }


    /* =========================================================
       COMMAND TYPES
       ========================================================= */

    public record CreateTournamentCommand(
            String name,
            String gameType,
            String format,
            String description,
            String location,
            OffsetDateTime scheduledAt,
            OffsetDateTime registrationEndAt,
            String status) {
    }


    public record UpdateTournamentCommand(
            String name,
            String gameType,
            String format,
            String status,

            String description,
            boolean descriptionProvided,

            String location,
            boolean locationProvided,

            OffsetDateTime scheduledAt,
            boolean scheduledAtProvided,

            OffsetDateTime registrationEndAt,
            boolean registrationEndAtProvided) {
    }


    /* =========================================================
       RESPONSE TYPES
       ========================================================= */

    public record Tournament(
            String id,
            String name,
            String gameType,
            String format,
            String status,
            String description,
            String location,
            String scheduledAt,
            String registrationEndAt,
            String createdBy,
            String createdAt,
            String updatedAt,
            boolean canEdit,
            boolean canDelete) {
    }


    public record UserSearchResult(
            String id,
            String name,
            String avatarUrl) {
    }


    public record TournamentParticipant(
            String id,
            String tournamentId,
            String userId,
            String name,
            String avatarUrl,
            String status,
            Integer seedNumber,
            String registeredAt) {
    }


    /* =========================================================
       EXCEPTION
       ========================================================= */

    public static class TournamentServiceException
            extends RuntimeException {

        private final int statusCode;


        public TournamentServiceException(
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