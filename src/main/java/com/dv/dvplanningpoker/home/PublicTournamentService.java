package com.dv.dvplanningpoker.home;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * Read-only public tournament service.
 *
 * Used by the unauthenticated public home page.
 *
 * SECURITY:
 *
 * - Uses Supabase SERVER secret key.
 * - Secret key never goes to the browser.
 * - Real user UUIDs are not returned publicly.
 * - Real tournament UUIDs are not returned publicly.
 * - Avatar URLs are not returned publicly.
 * - Existing authenticated tournament APIs remain unchanged.
 */
@Service
public class PublicTournamentService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PublicTournamentService.class
            );


    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(
                    10
            );


    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(
                    15
            );


    /*
     * Browser finally displays only:
     *
     * Today
     * Yesterday
     * 2 Days Ago
     *
     * Four days are retrieved to safely cover timezone
     * differences between UTC and browser-local time.
     */
    private static final int RECENT_LOOKBACK_DAYS =
            4;


    private static final int MAX_RECENT_TOURNAMENTS =
            30;


    private static final int MAX_SCOREBOARD_MATCHES =
            5000;


    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String supabaseUrl;

    private final String secretKey;


    public PublicTournamentService(
            ObjectMapper objectMapper,
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.secret-key:}") String secretKey) {

        this.objectMapper =
                objectMapper;


        this.supabaseUrl =
                normalizeBaseUrl(
                        supabaseUrl
                );


        this.secretKey =
                secretKey == null
                        ? ""
                        : secretKey.trim();


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
       RECENT COMPLETED TOURNAMENTS
       ========================================================= */

    public List<RecentTournament>
    getRecentCompletedTournaments() {

        requireConfigured();


        OffsetDateTime threshold =
                OffsetDateTime
                        .now(
                                ZoneOffset.UTC
                        )
                        .minusDays(
                                RECENT_LOOKBACK_DAYS
                        );


        String path =
                "/rest/v1/tournaments"
                        +
                        "?status=eq.COMPLETED"
                        +
                        "&game_type=eq.PICKLEBALL"
                        +
                        "&completed_at=gte."
                        +
                        encodeQueryValue(
                                threshold.toString()
                        )
                        +
                        "&select=id,name,game_type,format,completed_at"
                        +
                        "&order=completed_at.desc"
                        +
                        "&limit="
                        +
                        MAX_RECENT_TOURNAMENTS;


        JsonNode tournamentRows =
                sendGet(
                        path
                );


        if (
                tournamentRows == null
                        ||
                        !tournamentRows.isArray()
                        ||
                        tournamentRows.isEmpty()
        ) {

            return List.of();
        }


        List<TournamentRow> tournaments =
                new ArrayList<>();


        List<String> tournamentIds =
                new ArrayList<>();


        for (JsonNode row : tournamentRows) {

            String id =
                    textOrNull(
                            row,
                            "id"
                    );


            String completedAt =
                    textOrNull(
                            row,
                            "completed_at"
                    );


            if (
                    id == null
                            ||
                            completedAt == null
            ) {

                continue;
            }


            TournamentRow tournament =
                    new TournamentRow(
                            id,

                            textOrDefault(
                                    row,
                                    "name",
                                    "Pickleball Tournament"
                            ),

                            textOrDefault(
                                    row,
                                    "game_type",
                                    "PICKLEBALL"
                            ),

                            textOrDefault(
                                    row,
                                    "format",
                                    "SINGLES"
                            ),

                            completedAt
                    );


            tournaments.add(
                    tournament
            );


            tournamentIds.add(
                    id
            );
        }


        if (tournaments.isEmpty()) {

            return List.of();
        }


        List<MatchRow> matches =
                loadMatchesForTournaments(
                        tournamentIds
                );


        Map<String, List<MatchRow>>
                matchesByTournament =
                new HashMap<>();


        for (MatchRow match : matches) {

            matchesByTournament
                    .computeIfAbsent(
                            match.tournamentId(),
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(
                            match
                    );
        }


        Set<String> playerIds =
                collectPlayerIds(
                        matches
                );


        Map<String, PlayerProfile> profiles =
                loadProfiles(
                        playerIds
                );


        List<RecentTournament> result =
                new ArrayList<>();


        /*
         * IMPORTANT:
         *
         * The real tournament UUID is used internally only.
         *
         * Browser receives:
         *
         * recent-1
         * recent-2
         * recent-3
         *
         * This keeps the existing frontend selection logic
         * working without exposing internal UUIDs.
         */
        for (
                int index = 0;
                index < tournaments.size();
                index++
        ) {

            TournamentRow tournament =
                    tournaments.get(
                            index
                    );


            String publicId =
                    "recent-"
                            +
                            (
                                    index + 1
                            );


            List<MatchRow> tournamentMatches =
                    matchesByTournament
                            .getOrDefault(
                                    tournament.id(),
                                    List.of()
                            );


            result.add(
                    buildRecentTournament(
                            publicId,
                            tournament,
                            tournamentMatches,
                            profiles
                    )
            );
        }


        return result;
    }


    /* =========================================================
       OVERALL SCOREBOARD
       ========================================================= */

    public OverallScoreboard
    getOverallScoreboard() {

        requireConfigured();


        String path =
                "/rest/v1/pickle_game_score"
                        +
                        "?status=eq.COMPLETED"
                        +
                        "&winner_side=not.is.null"
                        +
                        "&select="
                        +
                        matchSelectFields()
                        +
                        "&order=completed_at.asc.nullslast,created_at.asc"
                        +
                        "&limit="
                        +
                        MAX_SCOREBOARD_MATCHES;


        JsonNode rows =
                sendGet(
                        path
                );


        List<MatchRow> matches =
                parseMatchRows(
                        rows
                );


        Set<String> playerIds =
                collectPlayerIds(
                        matches
                );


        Map<String, PlayerProfile> profiles =
                loadProfiles(
                        playerIds
                );


        Map<String, PlayerAccumulator> singles =
                new HashMap<>();


        Map<String, PlayerAccumulator> doubles =
                new HashMap<>();


        int completedSinglesMatches =
                0;


        int completedDoublesMatches =
                0;


        for (MatchRow match : matches) {

            String format =
                    normalizeFormat(
                            match.matchFormat()
                    );


            /* =================================================
               SINGLES
               ================================================= */

            if ("SINGLES".equals(format)) {

                if (
                        match.team1Player1Id() == null
                                ||
                                match.team2Player1Id() == null
                ) {

                    continue;
                }


                completedSinglesMatches++;


                applyPlayerMatch(
                        singles,
                        profiles,

                        List.of(
                                match.team1Player1Id()
                        ),

                        List.of(
                                match.team2Player1Id()
                        ),

                        match
                );


                /* =============================================
                   DOUBLES
                   ============================================= */

            } else if ("DOUBLES".equals(format)) {

                if (
                        match.team1Player1Id() == null
                                ||
                                match.team1Player2Id() == null
                                ||
                                match.team2Player1Id() == null
                                ||
                                match.team2Player2Id() == null
                ) {

                    continue;
                }


                completedDoublesMatches++;


                /*
                 * Overall Doubles scoreboard remains
                 * INDIVIDUAL PLAYER based.
                 *
                 * If:
                 *
                 * Kumar + Eswar
                 *
                 * defeat:
                 *
                 * Sai + Philip
                 *
                 * Then:
                 *
                 * Kumar   +1 MP +1 W
                 * Eswar   +1 MP +1 W
                 * Sai     +1 MP +1 L
                 * Philip  +1 MP +1 L
                 */
                applyPlayerMatch(
                        doubles,
                        profiles,

                        List.of(
                                match.team1Player1Id(),
                                match.team1Player2Id()
                        ),

                        List.of(
                                match.team2Player1Id(),
                                match.team2Player2Id()
                        ),

                        match
                );
            }
        }


        return new OverallScoreboard(
                buildScoreboardRows(
                        singles
                ),

                buildScoreboardRows(
                        doubles
                ),

                completedSinglesMatches,

                completedDoublesMatches
        );
    }


    /* =========================================================
       BUILD RECENT TOURNAMENT
       ========================================================= */

    private RecentTournament buildRecentTournament(
            String publicId,
            TournamentRow tournament,
            List<MatchRow> matches,
            Map<String, PlayerProfile> profiles) {

        String format =
                normalizeFormat(
                        tournament.format()
                );


        List<MatchRow> usableMatches =
                matches
                        .stream()
                        .filter(
                                match ->
                                        format.equals(
                                                normalizeFormat(
                                                        match.matchFormat()
                                                )
                                        )
                        )
                        .toList();


        if ("DOUBLES".equals(format)) {

            return buildRecentDoublesTournament(
                    publicId,
                    tournament,
                    usableMatches,
                    profiles
            );
        }


        return buildRecentSinglesTournament(
                publicId,
                tournament,
                usableMatches,
                profiles
        );
    }


    /* =========================================================
       RECENT SINGLES
       ========================================================= */

    private RecentTournament
    buildRecentSinglesTournament(
            String publicId,
            TournamentRow tournament,
            List<MatchRow> matches,
            Map<String, PlayerProfile> profiles) {

        Map<String, CompetitionAccumulator> accumulators =
                new HashMap<>();


        for (MatchRow match : matches) {

            String player1 =
                    match.team1Player1Id();


            String player2 =
                    match.team2Player1Id();


            if (
                    player1 == null
                            ||
                            player2 == null
                            ||
                            match.winnerSide() == null
            ) {

                continue;
            }


            CompetitionAccumulator side1 =
                    accumulators
                            .computeIfAbsent(
                                    player1,
                                    ignored ->
                                            CompetitionAccumulator
                                                    .forSinglePlayer(
                                                            playerProfile(
                                                                    profiles,
                                                                    player1
                                                            )
                                                    )
                            );


            CompetitionAccumulator side2 =
                    accumulators
                            .computeIfAbsent(
                                    player2,
                                    ignored ->
                                            CompetitionAccumulator
                                                    .forSinglePlayer(
                                                            playerProfile(
                                                                    profiles,
                                                                    player2
                                                            )
                                                    )
                            );


            applyCompetitionMatch(
                    side1,
                    side2,
                    match
            );
        }


        return tournamentResultFromAccumulators(
                publicId,
                tournament,
                matches,
                accumulators
        );
    }


    /* =========================================================
       RECENT DOUBLES
       ========================================================= */

    private RecentTournament
    buildRecentDoublesTournament(
            String publicId,
            TournamentRow tournament,
            List<MatchRow> matches,
            Map<String, PlayerProfile> profiles) {

        Map<String, CompetitionAccumulator> accumulators =
                new HashMap<>();


        for (MatchRow match : matches) {

            if (
                    match.team1Player1Id() == null
                            ||
                            match.team1Player2Id() == null
                            ||
                            match.team2Player1Id() == null
                            ||
                            match.team2Player2Id() == null
                            ||
                            match.winnerSide() == null
            ) {

                continue;
            }


            List<PlayerProfile> team1Players =
                    sortedTeamPlayers(
                            playerProfile(
                                    profiles,
                                    match.team1Player1Id()
                            ),

                            playerProfile(
                                    profiles,
                                    match.team1Player2Id()
                            )
                    );


            List<PlayerProfile> team2Players =
                    sortedTeamPlayers(
                            playerProfile(
                                    profiles,
                                    match.team2Player1Id()
                            ),

                            playerProfile(
                                    profiles,
                                    match.team2Player2Id()
                            )
                    );


            String team1Key =
                    teamKey(
                            team1Players
                    );


            String team2Key =
                    teamKey(
                            team2Players
                    );


            CompetitionAccumulator side1 =
                    accumulators
                            .computeIfAbsent(
                                    team1Key,
                                    ignored ->
                                            new CompetitionAccumulator(
                                                    team1Players
                                            )
                            );


            CompetitionAccumulator side2 =
                    accumulators
                            .computeIfAbsent(
                                    team2Key,
                                    ignored ->
                                            new CompetitionAccumulator(
                                                    team2Players
                                            )
                            );


            applyCompetitionMatch(
                    side1,
                    side2,
                    match
            );
        }


        return tournamentResultFromAccumulators(
                publicId,
                tournament,
                matches,
                accumulators
        );
    }


    /* =========================================================
       TOURNAMENT RESULT
       ========================================================= */

    private RecentTournament
    tournamentResultFromAccumulators(
            String publicId,
            TournamentRow tournament,
            List<MatchRow> matches,
            Map<String, CompetitionAccumulator> accumulators) {

        List<CompetitionAccumulator> ordered =
                new ArrayList<>(
                        accumulators.values()
                );


        ordered.sort(
                competitionComparator()
        );


        if (ordered.isEmpty()) {

            return new RecentTournament(
                    publicId,
                    tournament.name(),
                    tournament.gameType(),
                    normalizeFormat(
                            tournament.format()
                    ),
                    tournament.completedAt(),
                    "NO_RESULT",
                    null,
                    null,
                    List.of(),
                    matches.size(),
                    singleMatchScores(
                            matches
                    )
            );
        }


        CompetitionAccumulator first =
                ordered.get(
                        0
                );


        List<CompetitionAccumulator> tiedFirst =
                ordered
                        .stream()
                        .filter(
                                candidate ->
                                        sameCompetitionRank(
                                                first,
                                                candidate
                                        )
                        )
                        .toList();


        if (
                tiedFirst.size() >
                        1
        ) {

            return new RecentTournament(
                    publicId,
                    tournament.name(),
                    tournament.gameType(),
                    normalizeFormat(
                            tournament.format()
                    ),
                    tournament.completedAt(),
                    "TIE",
                    null,
                    null,

                    tiedFirst
                            .stream()
                            .map(
                                    this::toResultSide
                            )
                            .toList(),

                    matches.size(),

                    singleMatchScores(
                            matches
                    )
            );
        }


        ResultSide winner =
                toResultSide(
                        first
                );


        ResultSide runnerUp =
                ordered.size() >
                        1
                        ?
                        toResultSide(
                                ordered.get(
                                        1
                                )
                        )
                        :
                        null;


        return new RecentTournament(
                publicId,
                tournament.name(),
                tournament.gameType(),
                normalizeFormat(
                        tournament.format()
                ),
                tournament.completedAt(),
                "WINNER",
                winner,
                runnerUp,
                List.of(),
                matches.size(),
                singleMatchScores(
                        matches
                )
        );
    }


    /* =========================================================
       SINGLE-MATCH SCORES
       ========================================================= */

    private List<GameScoreView> singleMatchScores(
            List<MatchRow> matches) {

        if (
                matches == null
                        ||
                        matches.size() !=
                                1
        ) {

            return List.of();
        }


        return matches
                .get(
                        0
                )
                .gameScores();
    }


    /* =========================================================
       APPLY TOURNAMENT MATCH
       ========================================================= */

    private void applyCompetitionMatch(
            CompetitionAccumulator side1,
            CompetitionAccumulator side2,
            MatchRow match) {

        side1.matchesPlayed++;

        side2.matchesPlayed++;


        side1.gamesWon +=
                match.team1GamesWon();


        side1.gamesLost +=
                match.team2GamesWon();


        side2.gamesWon +=
                match.team2GamesWon();


        side2.gamesLost +=
                match.team1GamesWon();


        if (
                Integer.valueOf(
                                1
                        )
                        .equals(
                                match.winnerSide()
                        )
        ) {

            side1.matchesWon++;

            side2.matchesLost++;


        } else if (
                Integer.valueOf(
                                2
                        )
                        .equals(
                                match.winnerSide()
                        )
        ) {

            side2.matchesWon++;

            side1.matchesLost++;
        }
    }


    /* =========================================================
       TOURNAMENT RANKING
       ========================================================= */

    private Comparator<CompetitionAccumulator>
    competitionComparator() {

        return Comparator
                .comparingInt(
                        CompetitionAccumulator::matchesWon
                )
                .reversed()

                .thenComparing(
                        Comparator
                                .comparingInt(
                                        CompetitionAccumulator
                                                ::gameDifferential
                                )
                                .reversed()
                )

                .thenComparing(
                        accumulator ->
                                accumulator
                                        .displayName()
                                        .toLowerCase(
                                                Locale.ROOT
                                        )
                );
    }


    private boolean sameCompetitionRank(
            CompetitionAccumulator first,
            CompetitionAccumulator second) {

        return first.matchesWon
                ==
                second.matchesWon

                &&

                first.gameDifferential()
                        ==
                        second.gameDifferential();
    }


    private ResultSide toResultSide(
            CompetitionAccumulator accumulator) {

        /*
         * Public response deliberately contains names only.
         *
         * Internal UUIDs remain server-side.
         */
        return new ResultSide(

                accumulator.players
                        .stream()
                        .map(
                                profile ->
                                        new PlayerView(
                                                profile.name()
                                        )
                        )
                        .toList(),

                accumulator.matchesPlayed,

                accumulator.matchesWon,

                accumulator.matchesLost,

                accumulator.gamesWon,

                accumulator.gamesLost,

                accumulator.gameDifferential()
        );
    }


    /* =========================================================
       OVERALL PLAYER MATCH
       ========================================================= */

    private void applyPlayerMatch(
            Map<String, PlayerAccumulator> accumulators,
            Map<String, PlayerProfile> profiles,
            List<String> team1Players,
            List<String> team2Players,
            MatchRow match) {

        List<PlayerAccumulator> side1 =
                playerAccumulators(
                        accumulators,
                        profiles,
                        team1Players
                );


        List<PlayerAccumulator> side2 =
                playerAccumulators(
                        accumulators,
                        profiles,
                        team2Players
                );


        for (PlayerAccumulator player : side1) {

            player.matchesPlayed++;


            player.gamesWon +=
                    match.team1GamesWon();


            player.gamesLost +=
                    match.team2GamesWon();
        }


        for (PlayerAccumulator player : side2) {

            player.matchesPlayed++;


            player.gamesWon +=
                    match.team2GamesWon();


            player.gamesLost +=
                    match.team1GamesWon();
        }


        if (
                Integer.valueOf(
                                1
                        )
                        .equals(
                                match.winnerSide()
                        )
        ) {

            side1.forEach(
                    player ->
                            player.matchesWon++
            );


            side2.forEach(
                    player ->
                            player.matchesLost++
            );


        } else if (
                Integer.valueOf(
                                2
                        )
                        .equals(
                                match.winnerSide()
                        )
        ) {

            side2.forEach(
                    player ->
                            player.matchesWon++
            );


            side1.forEach(
                    player ->
                            player.matchesLost++
            );
        }
    }


    private List<PlayerAccumulator> playerAccumulators(
            Map<String, PlayerAccumulator> accumulators,
            Map<String, PlayerProfile> profiles,
            List<String> playerIds) {

        List<PlayerAccumulator> result =
                new ArrayList<>();


        for (String playerId : playerIds) {

            PlayerProfile profile =
                    playerProfile(
                            profiles,
                            playerId
                    );


            PlayerAccumulator accumulator =
                    accumulators
                            .computeIfAbsent(
                                    playerId,
                                    ignored ->
                                            new PlayerAccumulator(
                                                    profile
                                            )
                            );


            result.add(
                    accumulator
            );
        }


        return result;
    }


    /* =========================================================
       BUILD OVERALL SCOREBOARD
       ========================================================= */

    private List<ScoreboardRow> buildScoreboardRows(
            Map<String, PlayerAccumulator> accumulators) {

        List<PlayerAccumulator> ordered =
                new ArrayList<>(
                        accumulators.values()
                );


        /*
         * Overall ranking:
         *
         * 1. Match wins
         * 2. Win percentage
         * 3. Game differential
         */
        ordered.sort(
                Comparator
                        .comparingInt(
                                PlayerAccumulator::matchesWon
                        )
                        .reversed()

                        .thenComparing(
                                Comparator
                                        .comparingDouble(
                                                PlayerAccumulator
                                                        ::winRate
                                        )
                                        .reversed()
                        )

                        .thenComparing(
                                Comparator
                                        .comparingInt(
                                                PlayerAccumulator
                                                        ::gameDifferential
                                        )
                                        .reversed()
                        )

                        .thenComparing(
                                accumulator ->
                                        accumulator
                                                .profile
                                                .name()
                                                .toLowerCase(
                                                        Locale.ROOT
                                                )
                        )
        );


        List<ScoreboardRow> result =
                new ArrayList<>();


        PlayerAccumulator previous =
                null;


        int rank =
                0;


        for (
                int index = 0;
                index < ordered.size();
                index++
        ) {

            PlayerAccumulator current =
                    ordered.get(
                            index
                    );


            if (
                    previous == null
                            ||
                            !sameScoreboardRank(
                                    previous,
                                    current
                            )
            ) {

                rank =
                        index + 1;
            }


            /*
             * Public scoreboard contains no UUIDs
             * and no avatar URLs.
             */
            result.add(
                    new ScoreboardRow(
                            rank,

                            current.profile.name(),

                            current.matchesPlayed,

                            current.matchesWon,

                            current.matchesLost,

                            current.gamesWon,

                            current.gamesLost,

                            current.gameDifferential(),

                            roundPercentage(
                                    current.winRate()
                            )
                    )
            );


            previous =
                    current;
        }


        return result;
    }


    private boolean sameScoreboardRank(
            PlayerAccumulator first,
            PlayerAccumulator second) {

        return first.matchesWon
                ==
                second.matchesWon

                &&

                Double.compare(
                        first.winRate(),
                        second.winRate()
                )
                        ==
                        0

                &&

                first.gameDifferential()
                        ==
                        second.gameDifferential();
    }


    private double roundPercentage(
            double rate) {

        return Math.round(
                rate
                        *
                        1000.0
        )
                /
                10.0;
    }


    /* =========================================================
       LOAD MATCHES
       ========================================================= */

    private List<MatchRow>
    loadMatchesForTournaments(
            List<String> tournamentIds) {

        if (
                tournamentIds == null
                        ||
                        tournamentIds.isEmpty()
        ) {

            return List.of();
        }


        String path =
                "/rest/v1/pickle_game_score"
                        +
                        "?tournament_id=in.("
                        +
                        String.join(
                                ",",
                                tournamentIds
                        )
                        +
                        ")"
                        +
                        "&status=eq.COMPLETED"
                        +
                        "&winner_side=not.is.null"
                        +
                        "&select="
                        +
                        matchSelectFields()
                        +
                        "&order=tournament_id.asc,match_number.asc"
                        +
                        "&limit="
                        +
                        MAX_SCOREBOARD_MATCHES;


        return parseMatchRows(
                sendGet(
                        path
                )
        );
    }


    private String matchSelectFields() {

        return "id,"
                +
                "tournament_id,"
                +
                "match_number,"
                +
                "match_format,"
                +
                "team1_player1_id,"
                +
                "team1_player2_id,"
                +
                "team2_player1_id,"
                +
                "team2_player2_id,"
                +
                "game_scores,"
                +
                "team1_games_won,"
                +
                "team2_games_won,"
                +
                "winner_side,"
                +
                "completed_at,"
                +
                "created_at";
    }


    /* =========================================================
       PARSE MATCHES
       ========================================================= */

    private List<MatchRow> parseMatchRows(
            JsonNode rows) {

        if (
                rows == null
                        ||
                        !rows.isArray()
        ) {

            return List.of();
        }


        List<MatchRow> result =
                new ArrayList<>();


        for (JsonNode row : rows) {

            String tournamentId =
                    textOrNull(
                            row,
                            "tournament_id"
                    );


            Integer winnerSide =
                    integerOrNull(
                            row,
                            "winner_side"
                    );


            if (
                    tournamentId == null
                            ||
                            winnerSide == null
            ) {

                continue;
            }


            result.add(
                    new MatchRow(

                            textOrNull(
                                    row,
                                    "id"
                            ),

                            tournamentId,

                            integerOrDefault(
                                    row,
                                    "match_number",
                                    0
                            ),

                            textOrDefault(
                                    row,
                                    "match_format",
                                    "SINGLES"
                            ),

                            textOrNull(
                                    row,
                                    "team1_player1_id"
                            ),

                            textOrNull(
                                    row,
                                    "team1_player2_id"
                            ),

                            textOrNull(
                                    row,
                                    "team2_player1_id"
                            ),

                            textOrNull(
                                    row,
                                    "team2_player2_id"
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

                            winnerSide
                    )
            );
        }


        return result;
    }


    /* =========================================================
       GAME SCORES
       ========================================================= */

    private List<GameScoreView> parseGameScores(
            JsonNode node) {

        if (
                node == null
                        ||
                        !node.isArray()
        ) {

            return List.of();
        }


        List<GameScoreView> result =
                new ArrayList<>();


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
                    team1 == null
                            ||
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
                    new GameScoreView(

                            gameNumber == null
                                    ?
                                    fallbackGameNumber
                                    :
                                    gameNumber,

                            team1,

                            team2
                    )
            );


            fallbackGameNumber++;
        }


        return result;
    }


    /* =========================================================
       PLAYER IDS
       ========================================================= */

    private Set<String> collectPlayerIds(
            List<MatchRow> matches) {

        Set<String> result =
                new HashSet<>();


        for (MatchRow match : matches) {

            addIfPresent(
                    result,
                    match.team1Player1Id()
            );


            addIfPresent(
                    result,
                    match.team1Player2Id()
            );


            addIfPresent(
                    result,
                    match.team2Player1Id()
            );


            addIfPresent(
                    result,
                    match.team2Player2Id()
            );
        }


        return result;
    }


    /* =========================================================
       PLAYER PROFILES
       ========================================================= */

    private Map<String, PlayerProfile> loadProfiles(
            Set<String> ids) {

        Map<String, PlayerProfile> result =
                new HashMap<>();


        if (
                ids == null
                        ||
                        ids.isEmpty()
        ) {

            return result;
        }


        /*
         * avatar_url is intentionally NOT retrieved.
         *
         * Public homepage needs only the player's display name.
         */
        String path =
                "/rest/v1/profiles"
                        +
                        "?id=in.("
                        +
                        String.join(
                                ",",
                                ids
                        )
                        +
                        ")"
                        +
                        "&select=id,name"
                        +
                        "&limit=5000";


        JsonNode rows =
                sendGet(
                        path
                );


        if (
                rows == null
                        ||
                        !rows.isArray()
        ) {

            return result;
        }


        for (JsonNode row : rows) {

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
                    new PlayerProfile(
                            id,
                            name
                    )
            );
        }


        return result;
    }


    private PlayerProfile playerProfile(
            Map<String, PlayerProfile> profiles,
            String id) {

        PlayerProfile profile =
                profiles.get(
                        id
                );


        if (profile != null) {

            return profile;
        }


        return new PlayerProfile(
                id,
                "Unknown User"
        );
    }


    /* =========================================================
       TEAM HELPERS
       ========================================================= */

    private List<PlayerProfile> sortedTeamPlayers(
            PlayerProfile first,
            PlayerProfile second) {

        List<PlayerProfile> players =
                new ArrayList<>(
                        List.of(
                                first,
                                second
                        )
                );


        players.sort(
                Comparator.comparing(
                        PlayerProfile::id
                )
        );


        return players;
    }


    private String teamKey(
            List<PlayerProfile> players) {

        return players
                .stream()
                .map(
                        PlayerProfile::id
                )
                .sorted()
                .reduce(
                        (
                                first,
                                second
                        ) ->
                                first
                                        +
                                        "|"
                                        +
                                        second
                )
                .orElse(
                        ""
                );
    }


    /* =========================================================
       SUPABASE HTTP
       ========================================================= */

    private JsonNode sendGet(
            String path) {

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
                                    secretKey
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            )
                            .GET();


            /*
             * Legacy service_role JWT keys may additionally
             * be supplied as Bearer tokens.
             *
             * New sb_secret_* keys remain in the apikey header.
             */
            if (
                    secretKey.startsWith(
                            "eyJ"
                    )
            ) {

                builder.header(
                        "Authorization",
                        "Bearer "
                                +
                                secretKey
                );
            }


            HttpResponse<String> response =
                    httpClient.send(
                            builder.build(),

                            HttpResponse
                                    .BodyHandlers
                                    .ofString()
                    );


            if (
                    response.statusCode() < 200
                            ||
                            response.statusCode() >= 300
            ) {

                /*
                 * SECURITY:
                 *
                 * Do not log:
                 *
                 * - database response body
                 * - table details
                 * - complete query
                 * - secret key
                 */
                LOGGER.warn(
                        "Public tournament data request failed with HTTP {}.",
                        response.statusCode()
                );


                throw new PublicTournamentServiceException(
                        response.statusCode(),
                        "Public tournament request failed."
                );
            }


            return parseBody(
                    response.body()
            );


        } catch (
                PublicTournamentServiceException ex
        ) {

            throw ex;


        } catch (
                InterruptedException ex
        ) {

            Thread
                    .currentThread()
                    .interrupt();


            LOGGER.warn(
                    "Public tournament data request was interrupted."
            );


            throw new PublicTournamentServiceException(
                    503,
                    "Public tournament service is temporarily unavailable."
            );


        } catch (
                IOException
                |
                IllegalArgumentException ex
        ) {

            LOGGER.warn(
                    "Public tournament data request failed."
            );


            throw new PublicTournamentServiceException(
                    503,
                    "Public tournament service is temporarily unavailable."
            );
        }
    }


    /* =========================================================
       RESPONSE PARSING
       ========================================================= */

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

            LOGGER.warn(
                    "Public tournament response could not be parsed."
            );


            throw new PublicTournamentServiceException(
                    502,
                    "Public tournament service returned an invalid response."
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
                        secretKey.isBlank()
        ) {

            LOGGER.error(
                    "Public tournament service is not configured."
            );


            throw new PublicTournamentServiceException(
                    503,
                    "Public tournament service is not configured."
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


    private String normalizeFormat(
            String value) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            return "SINGLES";
        }


        String normalized =
                value
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        return "DOUBLES".equals(
                normalized
        )
                ?
                "DOUBLES"
                :
                "SINGLES";
    }


    private String encodeQueryValue(
            String value) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }


    private void addIfPresent(
            Set<String> values,
            String value) {

        if (
                value != null
                        &&
                        !value.isBlank()
        ) {

            values.add(
                    value
            );
        }
    }


    /* =========================================================
       JSON HELPERS
       ========================================================= */

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
                ?
                defaultValue
                :
                value;
    }


    /* =========================================================
       INTERNAL DATABASE TYPES
       ========================================================= */

    private record TournamentRow(

            String id,

            String name,

            String gameType,

            String format,

            String completedAt) {

    }


    private record MatchRow(

            String id,

            String tournamentId,

            int matchNumber,

            String matchFormat,

            String team1Player1Id,

            String team1Player2Id,

            String team2Player1Id,

            String team2Player2Id,

            List<GameScoreView> gameScores,

            int team1GamesWon,

            int team2GamesWon,

            Integer winnerSide) {

    }


    /*
     * UUID remains internal.
     *
     * It is required only to correctly correlate profile rows
     * and calculate statistics.
     */
    private record PlayerProfile(

            String id,

            String name) {

    }


    /* =========================================================
       TOURNAMENT ACCUMULATOR
       ========================================================= */

    private static final class CompetitionAccumulator {

        private final List<PlayerProfile> players;

        private int matchesPlayed;

        private int matchesWon;

        private int matchesLost;

        private int gamesWon;

        private int gamesLost;


        private CompetitionAccumulator(
                List<PlayerProfile> players) {

            this.players =
                    List.copyOf(
                            players
                    );
        }


        private static CompetitionAccumulator
        forSinglePlayer(
                PlayerProfile profile) {

            return new CompetitionAccumulator(
                    List.of(
                            profile
                    )
            );
        }


        private int matchesWon() {

            return matchesWon;
        }


        private int gameDifferential() {

            return gamesWon
                    -
                    gamesLost;
        }


        private String displayName() {

            return players
                    .stream()
                    .map(
                            PlayerProfile::name
                    )
                    .reduce(
                            (
                                    first,
                                    second
                            ) ->
                                    first
                                            +
                                            " & "
                                            +
                                            second
                    )
                    .orElse(
                            ""
                    );
        }
    }


    /* =========================================================
       OVERALL PLAYER ACCUMULATOR
       ========================================================= */

    private static final class PlayerAccumulator {

        private final PlayerProfile profile;

        private int matchesPlayed;

        private int matchesWon;

        private int matchesLost;

        private int gamesWon;

        private int gamesLost;


        private PlayerAccumulator(
                PlayerProfile profile) {

            this.profile =
                    profile;
        }


        private int matchesWon() {

            return matchesWon;
        }


        private double winRate() {

            if (
                    matchesPlayed <=
                            0
            ) {

                return 0;
            }


            return (
                    double
                    )
                    matchesWon
                    /
                    matchesPlayed;
        }


        private int gameDifferential() {

            return gamesWon
                    -
                    gamesLost;
        }
    }


    /* =========================================================
       PUBLIC RESPONSE TYPES
       ========================================================= */

    /**
     * Public player representation.
     *
     * No:
     *
     * UUID
     * email
     * avatar URL
     * gender
     * role
     */
    public record PlayerView(

            String name) {

    }


    public record GameScoreView(

            int game,

            int team1,

            int team2) {

    }


    public record ResultSide(

            List<PlayerView> players,

            int matchesPlayed,

            int matchesWon,

            int matchesLost,

            int gamesWon,

            int gamesLost,

            int gameDifferential) {

    }


    /**
     * id is deliberately a synthetic ID such as:
     *
     * recent-1
     *
     * NOT the real tournament UUID.
     */
    public record RecentTournament(

            String id,

            String name,

            String gameType,

            String format,

            String completedAt,

            String resultType,

            ResultSide winner,

            ResultSide runnerUp,

            List<ResultSide> tiedForFirst,

            int completedMatches,

            List<GameScoreView> gameScores) {

    }


    /**
     * Public scoreboard representation.
     *
     * No user UUID.
     * No avatar URL.
     */
    public record ScoreboardRow(

            int rank,

            String name,

            int matchesPlayed,

            int matchesWon,

            int matchesLost,

            int gamesWon,

            int gamesLost,

            int gameDifferential,

            double winPercentage) {

    }


    public record OverallScoreboard(

            List<ScoreboardRow> singles,

            List<ScoreboardRow> doubles,

            int completedSinglesMatches,

            int completedDoublesMatches) {

    }


    /* =========================================================
       EXCEPTION
       ========================================================= */

    public static class PublicTournamentServiceException
            extends RuntimeException {

        private final int statusCode;


        public PublicTournamentServiceException(
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