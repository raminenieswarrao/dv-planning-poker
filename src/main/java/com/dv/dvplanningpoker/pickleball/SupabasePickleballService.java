package com.dv.dvplanningpoker.pickleball;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * Handles Pickleball matches and scoring.
 *
 * Important security / integrity rules:
 *
 * - Supabase user access token is used for every request.
 * - RLS determines whether the current user may modify a match.
 * - tournament_id comes from the server/controller path.
 * - player UUIDs are validated.
 * - database trigger confirms players belong to the tournament.
 * - browser never supplies games-won totals.
 * - browser never supplies winner_side.
 * - Spring calculates winner information from game_scores.
 */
@Service
public class SupabasePickleballService {

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(10);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(15);

    private static final Set<Integer> ALLOWED_BEST_OF =
            Set.of(
                    1,
                    3,
                    5
            );

    private static final Set<String> ALLOWED_FORMATS =
            Set.of(
                    "SINGLES",
                    "DOUBLES"
            );

    private static final Set<String> ALLOWED_REQUEST_STATUSES =
            Set.of(
                    "SCHEDULED",
                    "IN_PROGRESS",
                    "COMPLETED",
                    "CANCELLED"
            );

    private static final int MAX_SCORE =
            250;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String supabaseUrl;

    private final String publishableKey;


    public SupabasePickleballService(
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


    /* =========================================================
       CREATE MATCH
       ========================================================= */

    public PickleballMatch createMatch(
            String accessToken,
            String tournamentId,
            CreateMatchCommand command) {

        requireConfigured();

        validateAccessToken(
                accessToken
        );

        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );

        if (command == null) {

            throw new IllegalArgumentException(
                    "Match information is required."
            );
        }


        MatchData matchData =
                validateAndBuildMatchData(
                        command.roundName(),
                        command.matchNumber(),
                        command.matchFormat(),
                        command.bestOfGames(),
                        command.pointsToWin(),
                        command.winBy(),
                        command.team1Player1Id(),
                        command.team1Player2Id(),
                        command.team2Player1Id(),
                        command.team2Player2Id(),
                        command.gameScores(),
                        command.status(),
                        command.scheduledAt()
                );


        ObjectNode body =
                buildMatchBody(
                        validTournamentId,
                        matchData,
                        null
                );


        JsonNode response =
                sendRequest(
                        "POST",
                        "/rest/v1/pickle_game_score?select=*",
                        accessToken,
                        body,
                        true
                );


        JsonNode row =
                firstArrayRow(
                        response
                );


        if (row == null) {

            throw new PickleballServiceException(
                    502,
                    "Match was created but could not be returned."
            );
        }


        return hydrateMatch(
                accessToken,
                row
        );
    }


    /* =========================================================
       GET MATCHES
       ========================================================= */

    public List<PickleballMatch> getMatches(
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
                "/rest/v1/pickle_game_score"
                        +
                        "?tournament_id=eq."
                        +
                        validTournamentId
                        +
                        "&select=*"
                        +
                        "&order=match_number.asc";


        JsonNode response =
                sendRequest(
                        "GET",
                        path,
                        accessToken,
                        null,
                        false
                );


        if (
                response == null ||
                        !response.isArray()
        ) {

            return List.of();
        }


        Map<String, PlayerProfile> profiles =
                loadProfilesForMatches(
                        accessToken,
                        response
                );


        List<PickleballMatch> result =
                new ArrayList<>();


        for (JsonNode row : response) {

            result.add(
                    parseMatch(
                            row,
                            profiles
                    )
            );
        }


        return result;
    }



    /* =========================================================
       TOURNAMENT STANDINGS
       ========================================================= */

    public TournamentStandings getStandings(
            String accessToken,
            String tournamentId) {

        List<PickleballMatch> matches =
                getMatches(
                        accessToken,
                        tournamentId
                );


        Map<String, StandingsAccumulator> accumulators =
                new HashMap<>();


        int completedMatches =
                0;


        for (PickleballMatch match : matches) {

            if (
                    match == null ||
                            !"COMPLETED".equalsIgnoreCase(
                                    match.status()
                            ) ||
                            match.winnerSide() == null ||
                            !"SINGLES".equalsIgnoreCase(
                                    match.matchFormat()
                            )
            ) {

                continue;
            }


            PlayerSummary team1Player =
                    match.team1Player1();

            PlayerSummary team2Player =
                    match.team2Player1();


            if (
                    team1Player == null ||
                            team2Player == null ||
                            team1Player.id() == null ||
                            team2Player.id() == null
            ) {

                continue;
            }


            completedMatches++;


            StandingsAccumulator team1 =
                    accumulators.computeIfAbsent(
                            team1Player.id(),
                            ignored ->
                                    new StandingsAccumulator(
                                            team1Player.id(),
                                            team1Player.name(),
                                            team1Player.avatarUrl()
                                    )
                    );


            StandingsAccumulator team2 =
                    accumulators.computeIfAbsent(
                            team2Player.id(),
                            ignored ->
                                    new StandingsAccumulator(
                                            team2Player.id(),
                                            team2Player.name(),
                                            team2Player.avatarUrl()
                                    )
                    );


            team1.matchesPlayed++;
            team2.matchesPlayed++;


            team1.gamesWon +=
                    match.team1GamesWon();

            team1.gamesLost +=
                    match.team2GamesWon();


            team2.gamesWon +=
                    match.team2GamesWon();

            team2.gamesLost +=
                    match.team1GamesWon();


            if (
                    match.winnerSide() ==
                            1
            ) {

                team1.matchesWon++;
                team2.matchesLost++;

            } else if (
                    match.winnerSide() ==
                            2
            ) {

                team2.matchesWon++;
                team1.matchesLost++;
            }
        }


        List<StandingsAccumulator> ordered =
                new ArrayList<>(
                        accumulators.values()
                );


        ordered.sort(
                Comparator
                        .comparingInt(
                                StandingsAccumulator::matchesWon
                        )
                        .reversed()
                        .thenComparing(
                                Comparator
                                        .comparingInt(
                                                StandingsAccumulator::gameDifferential
                                        )
                                        .reversed()
                        )
                        .thenComparing(
                                accumulator ->
                                        accumulator.name == null
                                                ? ""
                                                : accumulator.name.toLowerCase(
                                                Locale.ROOT
                                        )
                        )
        );


        List<StandingRow> standings =
                new ArrayList<>();


        int previousWins =
                Integer.MIN_VALUE;

        int previousDifferential =
                Integer.MIN_VALUE;

        int currentRank =
                0;


        for (
                int index = 0;
                index < ordered.size();
                index++
        ) {

            StandingsAccumulator accumulator =
                    ordered.get(
                            index
                    );


            if (
                    index == 0 ||
                            accumulator.matchesWon != previousWins ||
                            accumulator.gameDifferential() != previousDifferential
            ) {

                currentRank =
                        index + 1;
            }


            standings.add(
                    new StandingRow(
                            currentRank,
                            accumulator.userId,
                            accumulator.name,
                            accumulator.avatarUrl,
                            accumulator.matchesPlayed,
                            accumulator.matchesWon,
                            accumulator.matchesLost,
                            accumulator.gamesWon,
                            accumulator.gamesLost,
                            accumulator.gameDifferential()
                    )
            );


            previousWins =
                    accumulator.matchesWon;

            previousDifferential =
                    accumulator.gameDifferential();
        }


        StandingRow winner =
                null;

        boolean tiedForFirst =
                false;


        if (
                !standings.isEmpty()
        ) {

            if (
                    standings.size() == 1
            ) {

                winner =
                        standings.get(
                                0
                        );

            } else {

                StandingRow first =
                        standings.get(
                                0
                        );

                StandingRow second =
                        standings.get(
                                1
                        );


                tiedForFirst =
                        first.matchesWon() ==
                                second.matchesWon()
                                &&
                                first.gameDifferential() ==
                                        second.gameDifferential();


                if (!tiedForFirst) {

                    winner =
                            first;
                }
            }
        }


        return new TournamentStandings(
                standings,
                winner,
                tiedForFirst,
                completedMatches
        );
    }


    /* =========================================================
       GET ONE MATCH
       ========================================================= */

    public PickleballMatch getMatch(
            String accessToken,
            String tournamentId,
            String matchId) {

        requireConfigured();

        validateAccessToken(
                accessToken
        );

        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );

        String validMatchId =
                validateUuid(
                        matchId,
                        "Invalid match."
                );


        String path =
                "/rest/v1/pickle_game_score"
                        +
                        "?id=eq."
                        +
                        validMatchId
                        +
                        "&tournament_id=eq."
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


        return hydrateMatch(
                accessToken,
                row
        );
    }


    /* =========================================================
       UPDATE MATCH
       ========================================================= */

    public PickleballMatch updateMatch(
            String accessToken,
            String tournamentId,
            String matchId,
            UpdateMatchCommand command) {

        requireConfigured();

        validateAccessToken(
                accessToken
        );

        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );

        String validMatchId =
                validateUuid(
                        matchId,
                        "Invalid match."
                );


        if (command == null) {

            throw new IllegalArgumentException(
                    "Match information is required."
            );
        }


        JsonNode existing =
                getRawMatch(
                        accessToken,
                        validTournamentId,
                        validMatchId
                );


        if (existing == null) {

            throw new PickleballServiceException(
                    404,
                    "Match not found."
            );
        }


        MatchData matchData =
                validateAndBuildMatchData(
                        command.roundName(),
                        command.matchNumber(),
                        command.matchFormat(),
                        command.bestOfGames(),
                        command.pointsToWin(),
                        command.winBy(),
                        command.team1Player1Id(),
                        command.team1Player2Id(),
                        command.team2Player1Id(),
                        command.team2Player2Id(),
                        command.gameScores(),
                        command.status(),
                        command.scheduledAt()
                );


        ObjectNode body =
                buildMatchBody(
                        validTournamentId,
                        matchData,
                        existing
                );


        body.put(
                "updated_at",
                OffsetDateTime
                        .now()
                        .toString()
        );


        String path =
                "/rest/v1/pickle_game_score"
                        +
                        "?id=eq."
                        +
                        validMatchId
                        +
                        "&tournament_id=eq."
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

            throw new PickleballServiceException(
                    404,
                    "Match not found or you do not have permission to edit it."
            );
        }


        return hydrateMatch(
                accessToken,
                row
        );
    }


    /* =========================================================
       DELETE MATCH
       ========================================================= */

    public void deleteMatch(
            String accessToken,
            String tournamentId,
            String matchId) {

        requireConfigured();

        validateAccessToken(
                accessToken
        );

        String validTournamentId =
                validateUuid(
                        tournamentId,
                        "Invalid tournament."
                );

        String validMatchId =
                validateUuid(
                        matchId,
                        "Invalid match."
                );


        String path =
                "/rest/v1/pickle_game_score"
                        +
                        "?id=eq."
                        +
                        validMatchId
                        +
                        "&tournament_id=eq."
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
       RAW MATCH
       ========================================================= */

    private JsonNode getRawMatch(
            String accessToken,
            String tournamentId,
            String matchId) {

        String path =
                "/rest/v1/pickle_game_score"
                        +
                        "?id=eq."
                        +
                        matchId
                        +
                        "&tournament_id=eq."
                        +
                        tournamentId
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


        return firstArrayRow(
                response
        );
    }


    /* =========================================================
       VALIDATE / BUILD MATCH
       ========================================================= */

    private MatchData validateAndBuildMatchData(
            String rawRoundName,
            Integer rawMatchNumber,
            String rawMatchFormat,
            Integer rawBestOfGames,
            Integer rawPointsToWin,
            Integer rawWinBy,
            String rawTeam1Player1Id,
            String rawTeam1Player2Id,
            String rawTeam2Player1Id,
            String rawTeam2Player2Id,
            List<GameScoreInput> rawGameScores,
            String rawStatus,
            OffsetDateTime scheduledAt) {

        String roundName =
                normalizeOptional(
                        rawRoundName,
                        50,
                        "Round name is too long."
                );


        int matchNumber =
                validateMatchNumber(
                        rawMatchNumber
                );


        String matchFormat =
                validateMatchFormat(
                        rawMatchFormat
                );


        int bestOfGames =
                validateBestOfGames(
                        rawBestOfGames
                );


        int pointsToWin =
                validatePointsToWin(
                        rawPointsToWin
                );


        int winBy =
                validateWinBy(
                        rawWinBy
                );


        String team1Player1Id =
                validateUuid(
                        rawTeam1Player1Id,
                        "Team 1 player 1 is required."
                );


        String team2Player1Id =
                validateUuid(
                        rawTeam2Player1Id,
                        "Team 2 player 1 is required."
                );


        String team1Player2Id =
                validateOptionalUuid(
                        rawTeam1Player2Id,
                        "Invalid Team 1 player 2."
                );


        String team2Player2Id =
                validateOptionalUuid(
                        rawTeam2Player2Id,
                        "Invalid Team 2 player 2."
                );


        validatePlayers(
                matchFormat,
                team1Player1Id,
                team1Player2Id,
                team2Player1Id,
                team2Player2Id
        );


        ScoreSummary scoreSummary =
                calculateScores(
                        rawGameScores,
                        bestOfGames,
                        pointsToWin,
                        winBy
                );


        String requestedStatus =
                normalizeRequestedStatus(
                        rawStatus
                );


        String finalStatus =
                determineStatus(
                        requestedStatus,
                        scoreSummary
                );


        Integer winnerSide =
                scoreSummary.winnerSide();


        /*
         * A cancelled match has no winner even if scores
         * were entered before cancellation.
         */
        if (
                "CANCELLED".equals(
                        finalStatus
                )
        ) {

            winnerSide =
                    null;
        }


        return new MatchData(
                roundName,
                matchNumber,
                matchFormat,
                bestOfGames,
                pointsToWin,
                winBy,
                team1Player1Id,
                team1Player2Id,
                team2Player1Id,
                team2Player2Id,
                scoreSummary.games(),
                scoreSummary.team1GamesWon(),
                scoreSummary.team2GamesWon(),
                winnerSide,
                finalStatus,
                scheduledAt
        );
    }


    /* =========================================================
       PLAYER VALIDATION
       ========================================================= */

    private void validatePlayers(
            String matchFormat,
            String team1Player1Id,
            String team1Player2Id,
            String team2Player1Id,
            String team2Player2Id) {

        if (
                "SINGLES".equals(
                        matchFormat
                )
        ) {

            if (
                    team1Player2Id != null ||
                            team2Player2Id != null
            ) {

                throw new IllegalArgumentException(
                        "Singles matches require exactly two players."
                );
            }


            if (
                    team1Player1Id.equals(
                            team2Player1Id
                    )
            ) {

                throw new IllegalArgumentException(
                        "A player cannot play against themselves."
                );
            }


            return;
        }


        if (
                team1Player2Id == null ||
                        team2Player2Id == null
        ) {

            throw new IllegalArgumentException(
                    "Doubles matches require four players."
            );
        }


        Set<String> players =
                new HashSet<>();


        players.add(
                team1Player1Id
        );

        players.add(
                team1Player2Id
        );

        players.add(
                team2Player1Id
        );

        players.add(
                team2Player2Id
        );


        if (
                players.size() != 4
        ) {

            throw new IllegalArgumentException(
                    "A doubles match requires four different players."
            );
        }
    }


    /* =========================================================
       SCORE CALCULATION
       ========================================================= */

    private ScoreSummary calculateScores(
            List<GameScoreInput> rawScores,
            int bestOfGames,
            int pointsToWin,
            int winBy) {

        List<GameScore> games =
                new ArrayList<>();


        if (
                rawScores == null ||
                        rawScores.isEmpty()
        ) {

            return new ScoreSummary(
                    games,
                    0,
                    0,
                    null
            );
        }


        if (
                rawScores.size() >
                        bestOfGames
        ) {

            throw new IllegalArgumentException(
                    "Too many games were supplied for this match."
            );
        }


        int winsNeeded =
                (
                        bestOfGames /
                                2
                )
                        +
                        1;


        int team1GamesWon =
                0;

        int team2GamesWon =
                0;


        for (
                int index = 0;
                index < rawScores.size();
                index++
        ) {

            /*
             * Once a side has already won the match,
             * another game must not exist.
             */
            if (
                    team1GamesWon >= winsNeeded ||
                            team2GamesWon >= winsNeeded
            ) {

                throw new IllegalArgumentException(
                        "The match already has a winner. Remove the extra game score."
                );
            }


            GameScoreInput input =
                    rawScores.get(
                            index
                    );


            if (input == null) {

                throw new IllegalArgumentException(
                        "Invalid game score."
                );
            }


            int team1 =
                    validateScoreValue(
                            input.team1(),
                            "Team 1 score is invalid."
                    );


            int team2 =
                    validateScoreValue(
                            input.team2(),
                            "Team 2 score is invalid."
                    );


            validateCompletedGame(
                    team1,
                    team2,
                    pointsToWin,
                    winBy
            );


            if (
                    team1 > team2
            ) {

                team1GamesWon++;

            } else {

                team2GamesWon++;
            }


            games.add(
                    new GameScore(
                            index + 1,
                            team1,
                            team2
                    )
            );
        }


        Integer winnerSide =
                null;


        if (
                team1GamesWon >=
                        winsNeeded
        ) {

            winnerSide =
                    1;

        } else if (
                team2GamesWon >=
                        winsNeeded
        ) {

            winnerSide =
                    2;
        }


        return new ScoreSummary(
                games,
                team1GamesWon,
                team2GamesWon,
                winnerSide
        );
    }


    /* =========================================================
       COMPLETED GAME VALIDATION
       ========================================================= */

    private void validateCompletedGame(
            int team1,
            int team2,
            int pointsToWin,
            int winBy) {

        if (
                team1 ==
                        team2
        ) {

            throw new IllegalArgumentException(
                    "A completed game cannot end in a tie."
            );
        }


        int winnerScore =
                Math.max(
                        team1,
                        team2
                );


        int loserScore =
                Math.min(
                        team1,
                        team2
                );


        if (
                winnerScore <
                        pointsToWin
        ) {

            throw new IllegalArgumentException(
                    "The winning score must be at least "
                            +
                            pointsToWin
                            +
                            "."
            );
        }


        if (
                winnerScore -
                        loserScore <
                        winBy
        ) {

            throw new IllegalArgumentException(
                    "The winner must win by at least "
                            +
                            winBy
                            +
                            " points."
            );
        }
    }


    /* =========================================================
       STATUS CALCULATION
       ========================================================= */

    private String determineStatus(
            String requestedStatus,
            ScoreSummary scoreSummary) {

        if (
                "CANCELLED".equals(
                        requestedStatus
                )
        ) {

            return "CANCELLED";
        }


        if (
                scoreSummary.winnerSide() != null
        ) {

            return "COMPLETED";
        }


        if (
                "COMPLETED".equals(
                        requestedStatus
                )
        ) {

            throw new IllegalArgumentException(
                    "The match cannot be completed until one side has won enough games."
            );
        }


        if (
                !scoreSummary.games().isEmpty()
        ) {

            return "IN_PROGRESS";
        }


        if (
                "IN_PROGRESS".equals(
                        requestedStatus
                )
        ) {

            return "IN_PROGRESS";
        }


        return "SCHEDULED";
    }


    /* =========================================================
       BUILD DATABASE BODY
       ========================================================= */

    private ObjectNode buildMatchBody(
            String tournamentId,
            MatchData matchData,
            JsonNode existing) {

        ObjectNode body =
                objectMapper
                        .createObjectNode();


        body.put(
                "tournament_id",
                tournamentId
        );


        putNullable(
                body,
                "round_name",
                matchData.roundName()
        );


        body.put(
                "match_number",
                matchData.matchNumber()
        );


        body.put(
                "match_format",
                matchData.matchFormat()
        );


        body.put(
                "best_of_games",
                matchData.bestOfGames()
        );


        body.put(
                "points_to_win",
                matchData.pointsToWin()
        );


        body.put(
                "win_by",
                matchData.winBy()
        );


        body.put(
                "team1_player1_id",
                matchData.team1Player1Id()
        );


        putNullable(
                body,
                "team1_player2_id",
                matchData.team1Player2Id()
        );


        body.put(
                "team2_player1_id",
                matchData.team2Player1Id()
        );


        putNullable(
                body,
                "team2_player2_id",
                matchData.team2Player2Id()
        );


        body.set(
                "game_scores",
                buildGameScoresJson(
                        matchData.gameScores()
                )
        );


        /*
         * These are calculated by Spring.
         *
         * The browser never controls them.
         */
        body.put(
                "team1_games_won",
                matchData.team1GamesWon()
        );


        body.put(
                "team2_games_won",
                matchData.team2GamesWon()
        );


        if (
                matchData.winnerSide() ==
                        null
        ) {

            body.putNull(
                    "winner_side"
            );

        } else {

            body.put(
                    "winner_side",
                    matchData.winnerSide()
            );
        }


        body.put(
                "status",
                matchData.status()
        );


        putNullableDateTime(
                body,
                "scheduled_at",
                matchData.scheduledAt()
        );


        OffsetDateTime now =
                OffsetDateTime.now();


        /*
         * STARTED_AT
         */
        if (
                "IN_PROGRESS".equals(
                        matchData.status()
                )
                        ||
                        "COMPLETED".equals(
                                matchData.status()
                        )
        ) {

            String previousStartedAt =
                    existing == null
                            ? null
                            : textOrNull(
                            existing,
                            "started_at"
                    );


            if (
                    previousStartedAt != null
            ) {

                body.put(
                        "started_at",
                        previousStartedAt
                );

            } else {

                body.put(
                        "started_at",
                        now.toString()
                );
            }

        } else if (
                "CANCELLED".equals(
                        matchData.status()
                )
        ) {

            String previousStartedAt =
                    existing == null
                            ? null
                            : textOrNull(
                            existing,
                            "started_at"
                    );


            putNullable(
                    body,
                    "started_at",
                    previousStartedAt
            );

        } else {

            body.putNull(
                    "started_at"
            );
        }


        /*
         * COMPLETED_AT
         */
        if (
                "COMPLETED".equals(
                        matchData.status()
                )
        ) {

            String previousCompletedAt =
                    existing == null
                            ? null
                            : textOrNull(
                            existing,
                            "completed_at"
                    );


            if (
                    previousCompletedAt != null
            ) {

                body.put(
                        "completed_at",
                        previousCompletedAt
                );

            } else {

                body.put(
                        "completed_at",
                        now.toString()
                );
            }

        } else {

            body.putNull(
                    "completed_at"
            );
        }


        return body;
    }


    /* =========================================================
       GAME SCORES JSON
       ========================================================= */

    private ArrayNode buildGameScoresJson(
            List<GameScore> scores) {

        ArrayNode array =
                objectMapper
                        .createArrayNode();


        if (scores == null) {

            return array;
        }


        for (GameScore score : scores) {

            ObjectNode game =
                    objectMapper
                            .createObjectNode();


            game.put(
                    "game",
                    score.game()
            );


            game.put(
                    "team1",
                    score.team1()
            );


            game.put(
                    "team2",
                    score.team2()
            );


            array.add(
                    game
            );
        }


        return array;
    }


    /* =========================================================
       HYDRATE MATCH WITH PLAYER NAMES
       ========================================================= */

    private PickleballMatch hydrateMatch(
            String accessToken,
            JsonNode row) {

        Set<String> ids =
                collectPlayerIds(
                        row
                );


        Map<String, PlayerProfile> profiles =
                getProfilesByIds(
                        accessToken,
                        ids
                );


        return parseMatch(
                row,
                profiles
        );
    }


    /* =========================================================
       LOAD PROFILES FOR MATCH LIST
       ========================================================= */

    private Map<String, PlayerProfile> loadProfilesForMatches(
            String accessToken,
            JsonNode matches) {

        Set<String> ids =
                new HashSet<>();


        for (JsonNode row : matches) {

            ids.addAll(
                    collectPlayerIds(
                            row
                    )
            );
        }


        return getProfilesByIds(
                accessToken,
                ids
        );
    }


    /* =========================================================
       COLLECT PLAYER IDS
       ========================================================= */

    private Set<String> collectPlayerIds(
            JsonNode row) {

        Set<String> ids =
                new HashSet<>();


        addIfPresent(
                ids,
                textOrNull(
                        row,
                        "team1_player1_id"
                )
        );


        addIfPresent(
                ids,
                textOrNull(
                        row,
                        "team1_player2_id"
                )
        );


        addIfPresent(
                ids,
                textOrNull(
                        row,
                        "team2_player1_id"
                )
        );


        addIfPresent(
                ids,
                textOrNull(
                        row,
                        "team2_player2_id"
                )
        );


        return ids;
    }


    private void addIfPresent(
            Set<String> values,
            String value) {

        if (
                value != null &&
                        !value.isBlank()
        ) {

            values.add(
                    value
            );
        }
    }


    /* =========================================================
       LOAD PLAYER PROFILES
       ========================================================= */

    private Map<String, PlayerProfile> getProfilesByIds(
            String accessToken,
            Set<String> ids) {

        Map<String, PlayerProfile> result =
                new HashMap<>();


        if (
                ids == null ||
                        ids.isEmpty()
        ) {

            return result;
        }


        String joined =
                String.join(
                        ",",
                        ids
                );


        String path =
                "/rest/v1/profiles"
                        +
                        "?id=in.("
                        +
                        joined
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


        if (
                response == null ||
                        !response.isArray()
        ) {

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
                    id == null ||
                            name == null
            ) {

                continue;
            }


            result.put(
                    id,
                    new PlayerProfile(
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
       PARSE MATCH
       ========================================================= */

    private PickleballMatch parseMatch(
            JsonNode row,
            Map<String, PlayerProfile> profiles) {

        String team1Player1Id =
                textOrNull(
                        row,
                        "team1_player1_id"
                );


        String team1Player2Id =
                textOrNull(
                        row,
                        "team1_player2_id"
                );


        String team2Player1Id =
                textOrNull(
                        row,
                        "team2_player1_id"
                );


        String team2Player2Id =
                textOrNull(
                        row,
                        "team2_player2_id"
                );


        return new PickleballMatch(
                textOrNull(
                        row,
                        "id"
                ),

                textOrNull(
                        row,
                        "tournament_id"
                ),

                textOrNull(
                        row,
                        "round_name"
                ),

                integerOrDefault(
                        row,
                        "match_number",
                        0
                ),

                textOrNull(
                        row,
                        "match_format"
                ),

                integerOrDefault(
                        row,
                        "best_of_games",
                        3
                ),

                integerOrDefault(
                        row,
                        "points_to_win",
                        11
                ),

                integerOrDefault(
                        row,
                        "win_by",
                        2
                ),

                playerSummary(
                        team1Player1Id,
                        profiles
                ),

                playerSummary(
                        team1Player2Id,
                        profiles
                ),

                playerSummary(
                        team2Player1Id,
                        profiles
                ),

                playerSummary(
                        team2Player2Id,
                        profiles
                ),

                parseGameScores(
                        row.get(
                                "game_scores"
                        )
                ),

                integerOrDefault(
                        row,
                        "team1_games_won",
                        0
                ),

                integerOrDefault(
                        row,
                        "team2_games_won",
                        0
                ),

                integerOrNull(
                        row,
                        "winner_side"
                ),

                textOrNull(
                        row,
                        "status"
                ),

                textOrNull(
                        row,
                        "scheduled_at"
                ),

                textOrNull(
                        row,
                        "started_at"
                ),

                textOrNull(
                        row,
                        "completed_at"
                ),

                textOrNull(
                        row,
                        "created_at"
                ),

                textOrNull(
                        row,
                        "updated_at"
                )
        );
    }


    /* =========================================================
       PLAYER SUMMARY
       ========================================================= */

    private PlayerSummary playerSummary(
            String userId,
            Map<String, PlayerProfile> profiles) {

        if (
                userId == null
        ) {

            return null;
        }


        PlayerProfile profile =
                profiles.get(
                        userId
                );


        if (
                profile == null
        ) {

            return new PlayerSummary(
                    userId,
                    "Unknown User",
                    null
            );
        }


        return new PlayerSummary(
                profile.id(),
                profile.name(),
                profile.avatarUrl()
        );
    }


    /* =========================================================
       PARSE GAME SCORES
       ========================================================= */

    private List<GameScore> parseGameScores(
            JsonNode node) {

        List<GameScore> result =
                new ArrayList<>();


        if (
                node == null ||
                        !node.isArray()
        ) {

            return result;
        }


        int fallbackGameNumber =
                1;


        for (JsonNode game : node) {

            Integer team1 =
                    integerOrNull(
                            game,
                            "team1"
                    );


            Integer team2 =
                    integerOrNull(
                            game,
                            "team2"
                    );


            if (
                    team1 == null ||
                            team2 == null
            ) {

                continue;
            }


            Integer gameNumber =
                    integerOrNull(
                            game,
                            "game"
                    );


            result.add(
                    new GameScore(
                            gameNumber == null
                                    ? fallbackGameNumber
                                    : gameNumber,
                            team1,
                            team2
                    )
            );


            fallbackGameNumber++;
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
                                    "Bearer "
                                            +
                                            accessToken
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            );


            if (
                    returnRepresentation
            ) {

                builder.header(
                        "Prefer",
                        "return=representation"
                );
            }


            if (
                    body != null
            ) {

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
                    response.statusCode() < 200 ||
                            response.statusCode() >= 300
            ) {

                throw new PickleballServiceException(
                        response.statusCode(),
                        extractSafeErrorMessage(
                                json
                        )
                );
            }


            return json;

        } catch (PickleballServiceException ex) {

            throw ex;

        } catch (InterruptedException ex) {

            Thread
                    .currentThread()
                    .interrupt();


            throw new PickleballServiceException(
                    503,
                    "Pickleball service is temporarily unavailable."
            );

        } catch (
                IOException |
                IllegalArgumentException ex
        ) {

            throw new PickleballServiceException(
                    503,
                    "Pickleball service is temporarily unavailable."
            );
        }
    }


    /* =========================================================
       RESPONSE BODY
       ========================================================= */

    private JsonNode parseBody(
            String body) {

        if (
                body == null ||
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
       SAFE SUPABASE ERRORS
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
                message == null ||
                        message.isBlank()
        ) {

            return "Pickleball request failed.";
        }


        String lower =
                message
                        .toLowerCase(
                                Locale.ROOT
                        );


        if (
                lower.contains(
                        "duplicate"
                )
                        ||
                        lower.contains(
                                "pickle_match_unique"
                        )
        ) {

            return "This match number already exists in the tournament.";
        }


        if (
                lower.contains(
                        "not a tournament participant"
                )
        ) {

            return "Every player must already be a participant in this tournament.";
        }


        if (
                lower.contains(
                        "four different players"
                )
        ) {

            return "A doubles match requires four different players.";
        }


        if (
                lower.contains(
                        "exactly two players"
                )
        ) {

            return "A singles match requires exactly two different players.";
        }


        if (
                lower.contains(
                        "cannot play against themselves"
                )
        ) {

            return "A player cannot play against themselves.";
        }


        if (
                lower.contains(
                        "pickleball matches can only"
                )
        ) {

            return "Matches can only be created here for a Pickleball tournament.";
        }


        /*
         * Do not return arbitrary PostgreSQL / PostgREST
         * implementation details to the browser.
         */
        return "Pickleball request failed.";
    }


    /* =========================================================
       VALIDATION
       ========================================================= */

    private int validateMatchNumber(
            Integer value) {

        if (
                value == null ||
                        value <= 0
        ) {

            throw new IllegalArgumentException(
                    "Match number must be greater than zero."
            );
        }


        if (
                value > 10_000
        ) {

            throw new IllegalArgumentException(
                    "Match number is too large."
            );
        }


        return value;
    }


    private String validateMatchFormat(
            String value) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Match format is required."
            );
        }


        String normalized =
                value
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        if (
                !ALLOWED_FORMATS.contains(
                        normalized
                )
        ) {

            throw new IllegalArgumentException(
                    "Match format must be SINGLES or DOUBLES."
            );
        }


        return normalized;
    }


    private int validateBestOfGames(
            Integer value) {

        int normalized =
                value == null
                        ? 3
                        : value;


        if (
                !ALLOWED_BEST_OF.contains(
                        normalized
                )
        ) {

            throw new IllegalArgumentException(
                    "Best of games must be 1, 3 or 5."
            );
        }


        return normalized;
    }


    private int validatePointsToWin(
            Integer value) {

        int normalized =
                value == null
                        ? 11
                        : value;


        if (
                normalized <= 0 ||
                        normalized > 100
        ) {

            throw new IllegalArgumentException(
                    "Points to win must be between 1 and 100."
            );
        }


        return normalized;
    }


    private int validateWinBy(
            Integer value) {

        int normalized =
                value == null
                        ? 2
                        : value;


        if (
                normalized <= 0 ||
                        normalized > 20
        ) {

            throw new IllegalArgumentException(
                    "Win by must be between 1 and 20."
            );
        }


        return normalized;
    }


    private int validateScoreValue(
            Integer value,
            String errorMessage) {

        if (
                value == null ||
                        value < 0 ||
                        value > MAX_SCORE
        ) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }


        return value;
    }


    private String normalizeRequestedStatus(
            String value) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            return "SCHEDULED";
        }


        String normalized =
                value
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        if (
                !ALLOWED_REQUEST_STATUSES.contains(
                        normalized
                )
        ) {

            throw new IllegalArgumentException(
                    "Invalid match status."
            );
        }


        return normalized;
    }


    private String normalizeOptional(
            String value,
            int maxLength,
            String errorMessage) {

        if (
                value == null
        ) {

            return null;
        }


        String normalized =
                value
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        if (
                normalized.isBlank()
        ) {

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


    private String validateUuid(
            String value,
            String errorMessage) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }


        try {

            return UUID
                    .fromString(
                            value.trim()
                    )
                    .toString();

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }
    }


    private String validateOptionalUuid(
            String value,
            String errorMessage) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            return null;
        }


        return validateUuid(
                value,
                errorMessage
        );
    }


    private void validateAccessToken(
            String accessToken) {

        if (
                accessToken == null ||
                        accessToken.isBlank() ||
                        accessToken.length() > 8192
        ) {

            throw new PickleballServiceException(
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

        if (
                value == null
        ) {

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

        if (
                value == null
        ) {

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
                node == null ||
                        !node.isArray() ||
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

        if (
                node == null
        ) {

            return null;
        }


        JsonNode value =
                node.get(
                        field
                );


        if (
                value == null ||
                        value.isNull()
        ) {

            return null;
        }


        String result =
                value.asText();


        return result == null ||
                result.isBlank()
                ? null
                : result;
    }


    private Integer integerOrNull(
            JsonNode node,
            String field) {

        if (
                node == null
        ) {

            return null;
        }


        JsonNode value =
                node.get(
                        field
                );


        if (
                value == null ||
                        value.isNull() ||
                        !value.isNumber()
        ) {

            return null;
        }


        return value.asInt();
    }


    private int integerOrDefault(
            JsonNode node,
            String field,
            int defaultValue) {

        Integer value =
                integerOrNull(
                        node,
                        field
                );


        return value == null
                ? defaultValue
                : value;
    }


    private String firstText(
            JsonNode node,
            String... fields) {

        if (
                node == null
        ) {

            return null;
        }


        for (String field : fields) {

            String value =
                    textOrNull(
                            node,
                            field
                    );


            if (
                    value != null
            ) {

                return value;
            }
        }


        return null;
    }


    /* =========================================================
       CONFIG
       ========================================================= */

    private void requireConfigured() {

        if (
                supabaseUrl.isBlank() ||
                        publishableKey.isBlank()
        ) {

            throw new IllegalStateException(
                    "Supabase is not configured."
            );
        }
    }


    private String normalizeBaseUrl(
            String value) {

        if (
                value == null
        ) {

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
       STANDINGS ACCUMULATOR
       ========================================================= */

    private static final class StandingsAccumulator {

        private final String userId;

        private final String name;

        private final String avatarUrl;

        private int matchesPlayed;

        private int matchesWon;

        private int matchesLost;

        private int gamesWon;

        private int gamesLost;


        private StandingsAccumulator(
                String userId,
                String name,
                String avatarUrl) {

            this.userId =
                    userId;

            this.name =
                    name;

            this.avatarUrl =
                    avatarUrl;
        }


        private int matchesWon() {

            return matchesWon;
        }


        private int gameDifferential() {

            return gamesWon -
                    gamesLost;
        }
    }


    /* =========================================================
       STANDINGS RESPONSE
       ========================================================= */

    public record StandingRow(
            int rank,
            String userId,
            String name,
            String avatarUrl,
            int matchesPlayed,
            int matchesWon,
            int matchesLost,
            int gamesWon,
            int gamesLost,
            int gameDifferential) {
    }


    public record TournamentStandings(
            List<StandingRow> standings,
            StandingRow winner,
            boolean tiedForFirst,
            int completedMatches) {
    }


    /* =========================================================
       CREATE COMMAND
       ========================================================= */

    public record CreateMatchCommand(
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
            List<GameScoreInput> gameScores,
            String status,
            OffsetDateTime scheduledAt) {
    }


    /* =========================================================
       UPDATE COMMAND
       ========================================================= */

    public record UpdateMatchCommand(
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
            List<GameScoreInput> gameScores,
            String status,
            OffsetDateTime scheduledAt) {
    }


    /* =========================================================
       INPUT SCORE
       ========================================================= */

    /**
     * Each score represents one COMPLETED Pickleball game.
     *
     * Example:
     *
     * {
     *     "team1": 11,
     *     "team2": 7
     * }
     *
     * Spring assigns game numbers automatically.
     */
    public record GameScoreInput(
            Integer team1,
            Integer team2) {
    }


    /* =========================================================
       INTERNAL MATCH DATA
       ========================================================= */

    private record MatchData(
            String roundName,
            int matchNumber,
            String matchFormat,
            int bestOfGames,
            int pointsToWin,
            int winBy,
            String team1Player1Id,
            String team1Player2Id,
            String team2Player1Id,
            String team2Player2Id,
            List<GameScore> gameScores,
            int team1GamesWon,
            int team2GamesWon,
            Integer winnerSide,
            String status,
            OffsetDateTime scheduledAt) {
    }


    /* =========================================================
       INTERNAL SCORE SUMMARY
       ========================================================= */

    private record ScoreSummary(
            List<GameScore> games,
            int team1GamesWon,
            int team2GamesWon,
            Integer winnerSide) {
    }


    /* =========================================================
       RESPONSE SCORE
       ========================================================= */

    public record GameScore(
            int game,
            int team1,
            int team2) {
    }


    /* =========================================================
       PLAYER PROFILE
       ========================================================= */

    private record PlayerProfile(
            String id,
            String name,
            String avatarUrl) {
    }


    /* =========================================================
       RESPONSE PLAYER
       ========================================================= */

    public record PlayerSummary(
            String id,
            String name,
            String avatarUrl) {
    }


    /* =========================================================
       RESPONSE MATCH
       ========================================================= */

    public record PickleballMatch(
            String id,
            String tournamentId,
            String roundName,
            int matchNumber,
            String matchFormat,
            int bestOfGames,
            int pointsToWin,
            int winBy,

            PlayerSummary team1Player1,
            PlayerSummary team1Player2,

            PlayerSummary team2Player1,
            PlayerSummary team2Player2,

            List<GameScore> gameScores,

            int team1GamesWon,
            int team2GamesWon,

            Integer winnerSide,

            String status,

            String scheduledAt,
            String startedAt,
            String completedAt,

            String createdAt,
            String updatedAt) {
    }


    /* =========================================================
       EXCEPTION
       ========================================================= */

    public static class PickleballServiceException
            extends RuntimeException {

        private final int statusCode;


        public PickleballServiceException(
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
