package com.dv.dvplanningpoker.pickleball;

import com.dv.dvplanningpoker.auth.AuthSessionResolver;
import com.dv.dvplanningpoker.auth.AuthSessionResolver.ResolvedSession;
import com.dv.dvplanningpoker.pickleball.SupabasePickleballService.CreateMatchCommand;
import com.dv.dvplanningpoker.pickleball.SupabasePickleballService.GameScoreInput;
import com.dv.dvplanningpoker.pickleball.SupabasePickleballService.PickleballMatch;
import com.dv.dvplanningpoker.pickleball.SupabasePickleballService.PickleballServiceException;
import com.dv.dvplanningpoker.pickleball.SupabasePickleballService.TournamentStandings;
import com.dv.dvplanningpoker.pickleball.SupabasePickleballService.UpdateMatchCommand;
import com.dv.dvplanningpoker.tournament.SupabaseTournamentService;
import com.dv.dvplanningpoker.tournament.SupabaseTournamentService.Tournament;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Pickleball match and score API.
 *
 * Access rules:
 *
 * - Logged-in users who can view a tournament may view matches.
 * - Tournament members may create/edit/delete matches.
 * - Non-members may only view matches.
 * - Match players must already belong to the tournament.
 *
 * Winner information is NEVER accepted from the browser.
 *
 * SupabasePickleballService calculates:
 *
 * - team1GamesWon
 * - team2GamesWon
 * - winnerSide
 * - completed/in-progress status
 */
@RestController
@RequestMapping(
        "/api/tournaments/{tournamentId}/pickleball"
)
public class PickleballController {

    private final SupabasePickleballService pickleballService;

    private final SupabaseTournamentService tournamentService;

    private final AuthSessionResolver authSessionResolver;


    public PickleballController(
            SupabasePickleballService pickleballService,
            SupabaseTournamentService tournamentService,
            AuthSessionResolver authSessionResolver) {

        this.pickleballService =
                pickleballService;

        this.tournamentService =
                tournamentService;

        this.authSessionResolver =
                authSessionResolver;
    }


    /* =========================================================
       LIST MATCHES
       ========================================================= */

    @GetMapping("/matches")
    public ResponseEntity<Map<String, Object>> getMatches(
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
                    requirePickleballTournament(
                            session,
                            tournamentId
                    );


            if (tournament == null) {

                return notFound(
                        "Tournament not found."
                );
            }


            List<PickleballMatch> matches =
                    pickleballService.getMatches(
                            session.accessToken(),
                            tournamentId
                    );


            Map<String, Object> result =
                    successResponse();


            result.put(
                    "matches",
                    matches
            );


            result.put(
                    "count",
                    matches.size()
            );


            /*
             * Frontend uses this to decide whether to show:
             *
             * + Create Match
             * Edit Score
             * Delete Match
             */
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

        } catch (PickleballServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }



    /* =========================================================
       TOURNAMENT STANDINGS
       ========================================================= */

    @GetMapping("/standings")
    public ResponseEntity<Map<String, Object>> getStandings(
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
                    requirePickleballTournament(
                            session,
                            tournamentId
                    );


            if (tournament == null) {

                return notFound(
                        "Tournament not found."
                );
            }


            TournamentStandings standings =
                    pickleballService.getStandings(
                            session.accessToken(),
                            tournamentId
                    );


            Map<String, Object> result =
                    successResponse();


            result.put(
                    "standings",
                    standings.standings()
            );


            result.put(
                    "winner",
                    standings.winner()
            );


            result.put(
                    "tiedForFirst",
                    standings.tiedForFirst()
            );


            result.put(
                    "completedMatches",
                    standings.completedMatches()
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

        } catch (PickleballServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       GET ONE MATCH
       ========================================================= */

    @GetMapping("/matches/{matchId}")
    public ResponseEntity<Map<String, Object>> getMatch(
            @PathVariable String tournamentId,
            @PathVariable String matchId,
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
                    requirePickleballTournament(
                            session,
                            tournamentId
                    );


            if (tournament == null) {

                return notFound(
                        "Tournament not found."
                );
            }


            PickleballMatch match =
                    pickleballService.getMatch(
                            session.accessToken(),
                            tournamentId,
                            matchId
                    );


            if (match == null) {

                return notFound(
                        "Match not found."
                );
            }


            Map<String, Object> result =
                    successResponse();


            result.put(
                    "match",
                    match
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
                            "Invalid match."
                    )
            );

        } catch (PickleballServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       CREATE MATCH
       ========================================================= */

    @PostMapping("/matches")
    public ResponseEntity<Map<String, Object>> createMatch(
            @PathVariable String tournamentId,
            @RequestBody MatchRequest requestBody,
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
                        "Match information is required."
                );
            }


            Tournament tournament =
                    requirePickleballTournament(
                            session,
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


            CreateMatchCommand command =
                    new CreateMatchCommand(

                            requestBody.roundName(),

                            requestBody.matchNumber(),

                            requestBody.matchFormat(),

                            requestBody.bestOfGames(),

                            requestBody.pointsToWin(),

                            requestBody.winBy(),

                            requestBody.team1Player1Id(),

                            requestBody.team1Player2Id(),

                            requestBody.team2Player1Id(),

                            requestBody.team2Player2Id(),

                            convertGameScores(
                                    requestBody.gameScores()
                            ),

                            requestBody.status(),

                            parseDateTime(
                                    requestBody.scheduledAt()
                            )
                    );


            PickleballMatch match =
                    pickleballService.createMatch(
                            session.accessToken(),
                            tournamentId,
                            command
                    );


            Map<String, Object> result =
                    successResponse();


            result.put(
                    "message",
                    "Match created successfully."
            );


            result.put(
                    "match",
                    match
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
                            "Unable to create match."
                    )
            );

        } catch (PickleballServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       UPDATE MATCH / SCORE
       ========================================================= */

    @PatchMapping("/matches/{matchId}")
    public ResponseEntity<Map<String, Object>> updateMatch(
            @PathVariable String tournamentId,
            @PathVariable String matchId,
            @RequestBody MatchRequest requestBody,
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
                        "Match information is required."
                );
            }


            Tournament tournament =
                    requirePickleballTournament(
                            session,
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


            /*
             * Make sure match belongs to this tournament.
             */
            PickleballMatch existing =
                    pickleballService.getMatch(
                            session.accessToken(),
                            tournamentId,
                            matchId
                    );


            if (existing == null) {

                return notFound(
                        "Match not found."
                );
            }


            UpdateMatchCommand command =
                    new UpdateMatchCommand(

                            requestBody.roundName(),

                            requestBody.matchNumber(),

                            requestBody.matchFormat(),

                            requestBody.bestOfGames(),

                            requestBody.pointsToWin(),

                            requestBody.winBy(),

                            requestBody.team1Player1Id(),

                            requestBody.team1Player2Id(),

                            requestBody.team2Player1Id(),

                            requestBody.team2Player2Id(),

                            convertGameScores(
                                    requestBody.gameScores()
                            ),

                            requestBody.status(),

                            parseDateTime(
                                    requestBody.scheduledAt()
                            )
                    );


            PickleballMatch match =
                    pickleballService.updateMatch(
                            session.accessToken(),
                            tournamentId,
                            matchId,
                            command
                    );


            Map<String, Object> result =
                    successResponse();


            result.put(
                    "message",
                    "Match updated successfully."
            );


            result.put(
                    "match",
                    match
            );


            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to update match."
                    )
            );

        } catch (PickleballServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       DELETE MATCH
       ========================================================= */

    @DeleteMapping("/matches/{matchId}")
    public ResponseEntity<Map<String, Object>> deleteMatch(
            @PathVariable String tournamentId,
            @PathVariable String matchId,
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
                    requirePickleballTournament(
                            session,
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


            PickleballMatch existing =
                    pickleballService.getMatch(
                            session.accessToken(),
                            tournamentId,
                            matchId
                    );


            if (existing == null) {

                return notFound(
                        "Match not found."
                );
            }


            pickleballService.deleteMatch(
                    session.accessToken(),
                    tournamentId,
                    matchId
            );


            Map<String, Object> result =
                    successResponse();


            result.put(
                    "message",
                    "Match deleted successfully."
            );


            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to delete match."
                    )
            );

        } catch (PickleballServiceException ex) {

            return serviceError(
                    ex
            );

        } catch (Exception ex) {

            return serverError();
        }
    }


    /* =========================================================
       TOURNAMENT VALIDATION
       ========================================================= */

    private Tournament requirePickleballTournament(
            ResolvedSession session,
            String tournamentId) {

        Tournament tournament =
                tournamentService.getTournament(
                        session.accessToken(),
                        session.user().id(),
                        tournamentId
                );


        if (tournament == null) {

            return null;
        }


        if (
                tournament.gameType() == null ||
                        !"PICKLEBALL".equalsIgnoreCase(
                                tournament.gameType()
                        )
        ) {

            throw new IllegalArgumentException(
                    "This tournament is not a Pickleball tournament."
            );
        }


        return tournament;
    }


    /* =========================================================
       SCORE CONVERSION
       ========================================================= */

    private List<GameScoreInput> convertGameScores(
            List<GameScoreRequest> scores) {

        if (
                scores == null ||
                        scores.isEmpty()
        ) {

            return List.of();
        }


        /*
         * Defensive upper limit.
         *
         * Service validation will also ensure this does not
         * exceed bestOfGames.
         */
        if (
                scores.size() > 5
        ) {

            throw new IllegalArgumentException(
                    "A Pickleball match cannot contain more than 5 games."
            );
        }


        List<GameScoreInput> result =
                new ArrayList<>();


        for (GameScoreRequest score : scores) {

            if (score == null) {

                throw new IllegalArgumentException(
                        "Invalid game score."
                );
            }


            result.add(
                    new GameScoreInput(
                            score.team1(),
                            score.team2()
                    )
            );
        }


        return result;
    }


    /* =========================================================
       DATE PARSING
       ========================================================= */

    private OffsetDateTime parseDateTime(
            String value) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            return null;
        }


        try {

            return OffsetDateTime.parse(
                    value.trim()
            );

        } catch (DateTimeParseException ex) {

            throw new IllegalArgumentException(
                    "Invalid match date."
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


        if (
                session == null ||
                        session.user() == null ||
                        session.user().id() == null ||
                        session.user().id().isBlank()
        ) {

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
                "Pickleball service is temporarily unavailable."
        );
    }


    private ResponseEntity<Map<String, Object>>
    serviceError(
            PickleballServiceException ex) {

        int code =
                ex.getStatusCode();


        HttpStatus status;


        if (
                code == 400 ||
                        code == 422
        ) {

            status =
                    HttpStatus.BAD_REQUEST;

        } else if (
                code == 401
        ) {

            status =
                    HttpStatus.UNAUTHORIZED;

        } else if (
                code == 403
        ) {

            status =
                    HttpStatus.FORBIDDEN;

        } else if (
                code == 404
        ) {

            status =
                    HttpStatus.NOT_FOUND;

        } else if (
                code == 409
        ) {

            status =
                    HttpStatus.CONFLICT;

        } else if (
                code == 429
        ) {

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
                        "Pickleball request failed."
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

        if (
                message == null ||
                        message.isBlank() ||
                        message.length() > 250
        ) {

            return fallback;
        }


        return message;
    }


    /* =========================================================
       REQUEST BODY
       ========================================================= */

    /**
     * Full match body.
     *
     * We intentionally do NOT accept:
     *
     * winnerSide
     * team1GamesWon
     * team2GamesWon
     *
     * Those are calculated on the server.
     */
    public record MatchRequest(
            String roundName,
            Integer matchNumber,
            String matchFormat,
            Integer bestOfGames,
            Integer pointsToWin,
            Integer winBy,

            String team1Player1Id,
            String team1Player2Id,

            String team2Player1Id,
            String team2Player2Id,

            List<GameScoreRequest> gameScores,

            String status,
            String scheduledAt) {
    }


    public record GameScoreRequest(
            Integer team1,
            Integer team2) {
    }
}
