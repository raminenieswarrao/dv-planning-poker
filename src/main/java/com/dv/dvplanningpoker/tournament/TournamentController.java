package com.dv.dvplanningpoker.tournament;

import com.dv.dvplanningpoker.auth.AuthSessionResolver;
import com.dv.dvplanningpoker.auth.AuthSessionResolver.ResolvedSession;
import com.dv.dvplanningpoker.tournament.SupabaseTournamentService.CreateTournamentCommand;
import com.dv.dvplanningpoker.tournament.SupabaseTournamentService.Tournament;
import com.dv.dvplanningpoker.tournament.SupabaseTournamentService.TournamentParticipant;
import com.dv.dvplanningpoker.tournament.SupabaseTournamentService.TournamentServiceException;
import com.dv.dvplanningpoker.tournament.SupabaseTournamentService.UpdateTournamentCommand;
import com.dv.dvplanningpoker.tournament.SupabaseTournamentService.UserSearchResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Tournament API.
 *
 * Authentication is resolved from the secure HttpOnly
 * Supabase session cookies.
 *
 * The browser never sends:
 *
 * - created_by
 * - authenticated user id
 * - Supabase access token
 *
 * Those values are resolved server-side.
 */
@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final SupabaseTournamentService tournamentService;

    private final AuthSessionResolver authSessionResolver;


    public TournamentController(
            SupabaseTournamentService tournamentService,
            AuthSessionResolver authSessionResolver) {

        this.tournamentService =
                tournamentService;

        this.authSessionResolver =
                authSessionResolver;
    }


    /* =========================================================
       CREATE TOURNAMENT
       ========================================================= */

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTournament(
            @RequestBody CreateTournamentRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            if (requestBody == null) {

                return badRequest(
                        "Tournament information is required."
                );
            }

            CreateTournamentCommand command =
                    new CreateTournamentCommand(
                            requestBody.name(),
                            requestBody.gameType(),
                            requestBody.format(),
                            requestBody.description(),
                            requestBody.location(),
                            parseDateTime(
                                    requestBody.scheduledAt(),
                                    "Invalid tournament date."
                            ),
                            parseDateTime(
                                    requestBody.registrationEndAt(),
                                    "Invalid registration end date."
                            ),
                            requestBody.status()
                    );

            /*
             * CRITICAL:
             *
             * Creator comes from authenticated Supabase user.
             * Browser cannot choose another created_by UUID.
             */
            Tournament tournament =
                    tournamentService.createTournament(
                            session.accessToken(),
                            session.user().id(),
                            command
                    );

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "message",
                    "Tournament created successfully."
            );

            result.put(
                    "tournament",
                    tournament
            );

            return ResponseEntity
                    .status(
                            HttpStatus.CREATED
                    )
                    .body(
                            result
                    );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to create tournament."
                    )
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       ONGOING TOURNAMENTS
       ========================================================= */

    @GetMapping("/ongoing")
    public ResponseEntity<Map<String, Object>> getOngoingTournaments(
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            List<Tournament> tournaments =
                    tournamentService.getOngoingTournaments(
                            session.accessToken(),
                            session.user().id()
                    );

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "tournaments",
                    tournaments
            );

            result.put(
                    "count",
                    tournaments.size()
            );

            return ResponseEntity.ok(
                    result
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       RECENT COMPLETED TOURNAMENT
       ========================================================= */

    @GetMapping("/recent-completed")
    public ResponseEntity<Map<String, Object>> getRecentCompletedTournament(
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            Tournament tournament =
                    tournamentService.getRecentCompletedTournament(
                            session.accessToken(),
                            session.user().id()
                    );

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "hasTournament",
                    tournament != null
            );

            result.put(
                    "tournament",
                    tournament
            );

            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to load recent tournament."
                    )
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       GET ONE TOURNAMENT
       ========================================================= */

    @GetMapping("/{tournamentId}")
    public ResponseEntity<Map<String, Object>> getTournament(
            @PathVariable String tournamentId,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            Tournament tournament =
                    tournamentService.getTournament(
                            session.accessToken(),
                            session.user().id(),
                            tournamentId
                    );

            if (tournament == null) {

                return notFound(
                        "Tournament not found."
                );
            }

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "tournament",
                    tournament
            );

            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Invalid tournament."
                    )
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       SEARCH EXISTING USERS
       ========================================================= */

    @GetMapping("/users/search")
    public ResponseEntity<Map<String, Object>> searchUsers(
            @RequestParam(name = "name") String name,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            List<UserSearchResult> users =
                    tournamentService.searchUsers(
                            session.accessToken(),
                            name
                    );

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "users",
                    users
            );

            result.put(
                    "count",
                    users.size()
            );

            /*
             * This gives the frontend the exact message
             * requested when no registered profile matches.
             */
            if (users.isEmpty()) {

                result.put(
                        "message",
                        "Can't find this user."
                );
            }

            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Invalid user search."
                    )
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       PARTICIPANTS
       ========================================================= */

    @GetMapping("/{tournamentId}/participants")
    public ResponseEntity<Map<String, Object>> getParticipants(
            @PathVariable String tournamentId,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            /*
             * First ensure this user is allowed to see
             * the tournament itself.
             */
            Tournament tournament =
                    tournamentService.getTournament(
                            session.accessToken(),
                            session.user().id(),
                            tournamentId
                    );

            if (tournament == null) {

                return notFound(
                        "Tournament not found."
                );
            }

            List<TournamentParticipant> participants =
                    tournamentService.getParticipants(
                            session.accessToken(),
                            tournamentId
                    );

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "participants",
                    participants
            );

            result.put(
                    "count",
                    participants.size()
            );

            result.put(
                    "canEdit",
                    tournament.canEdit()
            );

            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Invalid tournament."
                    )
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       ADD PARTICIPANT
       ========================================================= */

    @PostMapping("/{tournamentId}/participants")
    public ResponseEntity<Map<String, Object>> addParticipant(
            @PathVariable String tournamentId,
            @RequestBody AddParticipantRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            if (requestBody == null ||
                    requestBody.userId() == null ||
                    requestBody.userId().isBlank()) {

                return badRequest(
                        "Please select a user."
                );
            }

            /*
             * Explicit server-side permission check.
             *
             * RLS also enforces this at the database level,
             * giving us two layers of protection.
             */
            Tournament tournament =
                    tournamentService.getTournament(
                            session.accessToken(),
                            session.user().id(),
                            tournamentId
                    );

            if (tournament == null) {

                return notFound(
                        "Tournament not found."
                );
            }

            if (!tournament.canEdit()) {

                return forbidden(
                        "You are not a member of this tournament."
                );
            }

            TournamentParticipant participant =
                    tournamentService.addParticipant(
                            session.accessToken(),
                            tournamentId,
                            requestBody.userId()
                    );

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "message",
                    "Participant added successfully."
            );

            result.put(
                    "participant",
                    participant
            );

            return ResponseEntity
                    .status(
                            HttpStatus.CREATED
                    )
                    .body(
                            result
                    );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to add participant."
                    )
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       REMOVE PARTICIPANT
       ========================================================= */

    @DeleteMapping(
            "/{tournamentId}/participants/{userId}"
    )
    public ResponseEntity<Map<String, Object>> removeParticipant(
            @PathVariable String tournamentId,
            @PathVariable String userId,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            Tournament tournament =
                    tournamentService.getTournament(
                            session.accessToken(),
                            session.user().id(),
                            tournamentId
                    );

            if (tournament == null) {

                return notFound(
                        "Tournament not found."
                );
            }

            if (!tournament.canEdit()) {

                return forbidden(
                        "You are not a member of this tournament."
                );
            }

            tournamentService.removeParticipant(
                    session.accessToken(),
                    tournamentId,
                    userId
            );

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "message",
                    "Participant removed successfully."
            );

            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to remove participant."
                    )
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       UPDATE TOURNAMENT
       ========================================================= */

    @PatchMapping("/{tournamentId}")
    public ResponseEntity<Map<String, Object>> updateTournament(
            @PathVariable String tournamentId,
            @RequestBody JsonNode body,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            Tournament existing =
                    tournamentService.getTournament(
                            session.accessToken(),
                            session.user().id(),
                            tournamentId
                    );

            if (existing == null) {

                return notFound(
                        "Tournament not found."
                );
            }

            if (!existing.canEdit()) {

                return forbidden(
                        "You are not a member of this tournament."
                );
            }

            UpdateTournamentCommand command =
                    buildUpdateCommand(
                            body
                    );

            Tournament tournament =
                    tournamentService.updateTournament(
                            session.accessToken(),
                            session.user().id(),
                            tournamentId,
                            command
                    );

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "message",
                    "Tournament updated successfully."
            );

            result.put(
                    "tournament",
                    tournament
            );

            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to update tournament."
                    )
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       DELETE TOURNAMENT
       ========================================================= */

    @DeleteMapping("/{tournamentId}")
    public ResponseEntity<Map<String, Object>> deleteTournament(
            @PathVariable String tournamentId,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                requireSession(
                        request,
                        response
                );

        if (session == null) {

            return unauthorized();
        }

        try {

            Tournament tournament =
                    tournamentService.getTournament(
                            session.accessToken(),
                            session.user().id(),
                            tournamentId
                    );

            if (tournament == null) {

                return notFound(
                        "Tournament not found."
                );
            }

            /*
             * Members may edit.
             *
             * Only creator may delete the entire tournament.
             */
            if (!tournament.canDelete()) {

                return forbidden(
                        "Only the tournament creator can delete this tournament."
                );
            }

            tournamentService.deleteTournament(
                    session.accessToken(),
                    tournamentId
            );

            Map<String, Object> result =
                    successResponse();

            result.put(
                    "message",
                    "Tournament deleted successfully."
            );

            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to delete tournament."
                    )
            );

        } catch (TournamentServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       UPDATE REQUEST PARSING
       ========================================================= */

    private UpdateTournamentCommand buildUpdateCommand(
            JsonNode body) {

        if (body == null ||
                !body.isObject()) {

            throw new IllegalArgumentException(
                    "Tournament information is required."
            );
        }

        return new UpdateTournamentCommand(

                optionalText(
                        body,
                        "name"
                ),

                optionalText(
                        body,
                        "gameType"
                ),

                optionalText(
                        body,
                        "format"
                ),

                optionalText(
                        body,
                        "status"
                ),


                nullableText(
                        body,
                        "description"
                ),

                body.has(
                        "description"
                ),


                nullableText(
                        body,
                        "location"
                ),

                body.has(
                        "location"
                ),


                nullableDateTime(
                        body,
                        "scheduledAt",
                        "Invalid tournament date."
                ),

                body.has(
                        "scheduledAt"
                ),


                nullableDateTime(
                        body,
                        "registrationEndAt",
                        "Invalid registration end date."
                ),

                body.has(
                        "registrationEndAt"
                )
        );
    }


    private String optionalText(
            JsonNode body,
            String field) {

        if (!body.has(
                field
        )) {

            return null;
        }

        JsonNode value =
                body.get(
                        field
                );

        if (value == null ||
                value.isNull()) {

            return null;
        }

        if (!value.isTextual()) {

            throw new IllegalArgumentException(
                    "Invalid " +
                            field +
                            "."
            );
        }

        return value.asText();
    }


    private String nullableText(
            JsonNode body,
            String field) {

        if (!body.has(
                field
        )) {

            return null;
        }

        JsonNode value =
                body.get(
                        field
                );

        if (value == null ||
                value.isNull()) {

            return null;
        }

        if (!value.isTextual()) {

            throw new IllegalArgumentException(
                    "Invalid " +
                            field +
                            "."
            );
        }

        return value.asText();
    }


    private OffsetDateTime nullableDateTime(
            JsonNode body,
            String field,
            String errorMessage) {

        if (!body.has(
                field
        )) {

            return null;
        }

        JsonNode value =
                body.get(
                        field
                );

        if (value == null ||
                value.isNull()) {

            return null;
        }

        if (!value.isTextual()) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }

        return parseDateTime(
                value.asText(),
                errorMessage
        );
    }


    /* =========================================================
       DATE PARSING
       ========================================================= */

    private OffsetDateTime parseDateTime(
            String value,
            String errorMessage) {

        if (value == null ||
                value.isBlank()) {

            return null;
        }

        try {

            /*
             * Frontend will send ISO-8601 timestamps:
             *
             * 2026-09-18T22:00:00.000Z
             *
             * or
             *
             * 2026-09-18T18:00:00-04:00
             */
            return OffsetDateTime.parse(
                    value.trim()
            );

        } catch (DateTimeParseException ex) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }
    }


    /* =========================================================
       AUTH
       ========================================================= */

    private ResolvedSession requireSession(
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                authSessionResolver.resolve(
                        request,
                        response
                );

        if (session == null ||
                session.user() == null ||
                session.user().id() == null ||
                session.user().id().isBlank()) {

            return null;
        }

        return session;
    }


    /* =========================================================
       RESPONSE HELPERS
       ========================================================= */

    private Map<String, Object> successResponse() {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "success",
                true
        );

        return result;
    }


    private ResponseEntity<Map<String, Object>>
    unauthorized() {

        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "Please sign in."
        );
    }


    private ResponseEntity<Map<String, Object>>
    forbidden(
            String message) {

        return errorResponse(
                HttpStatus.FORBIDDEN,
                message
        );
    }


    private ResponseEntity<Map<String, Object>>
    notFound(
            String message) {

        return errorResponse(
                HttpStatus.NOT_FOUND,
                message
        );
    }


    private ResponseEntity<Map<String, Object>>
    badRequest(
            String message) {

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                message
        );
    }


    private ResponseEntity<Map<String, Object>>
    serverError() {

        return errorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Tournament service is temporarily unavailable."
        );
    }


    private ResponseEntity<Map<String, Object>>
    serviceError(
            TournamentServiceException ex) {

        int code =
                ex.getStatusCode();

        HttpStatus status;

        if (code == 400 ||
                code == 422) {

            status =
                    HttpStatus.BAD_REQUEST;

        } else if (code == 401) {

            status =
                    HttpStatus.UNAUTHORIZED;

        } else if (code == 403) {

            status =
                    HttpStatus.FORBIDDEN;

        } else if (code == 404) {

            status =
                    HttpStatus.NOT_FOUND;

        } else if (code == 409) {

            status =
                    HttpStatus.CONFLICT;

        } else if (code == 429) {

            status =
                    HttpStatus.TOO_MANY_REQUESTS;

        } else {

            status =
                    HttpStatus.SERVICE_UNAVAILABLE;
        }

        return errorResponse(
                status,
                safeMessage(
                        ex.getMessage(),
                        "Tournament request failed."
                )
        );
    }


    private ResponseEntity<Map<String, Object>>
    errorResponse(
            HttpStatus status,
            String message) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "success",
                false
        );

        result.put(
                "message",
                message
        );

        return ResponseEntity
                .status(
                        status
                )
                .body(
                        result
                );
    }


    private String safeMessage(
            String message,
            String fallback) {

        if (message == null ||
                message.isBlank() ||
                message.length() > 250) {

            return fallback;
        }

        return message;
    }


    /* =========================================================
       REQUEST TYPES
       ========================================================= */

    public record CreateTournamentRequest(
            String name,
            String gameType,
            String format,
            String description,
            String location,
            String scheduledAt,
            String registrationEndAt,
            String status) {
    }


    public record AddParticipantRequest(
            String userId) {
    }
}
