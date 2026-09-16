"use strict";


document.addEventListener(
    "DOMContentLoaded",
    initializePublicTournamentShowcase
);


/* =====================================================
   STATE
   ===================================================== */

let publicScoreboardLoaded =
    false;


let recentTournaments =
    [];


let selectedRecentTournamentId =
    null;


let victoryAnimation =
    null;


let activePublicTab =
    "RECENT";


/* =====================================================
   START
   ===================================================== */

async function initializePublicTournamentShowcase() {

    const section =
        document.getElementById(
            "publicTournamentSection"
        );


    /*
     * index.html has not been updated yet.
     *
     * Until the public tournament section exists,
     * safely do nothing.
     */
    if (!section) {

        return;
    }


    wirePublicTabs();


    /*
     * Planning Poker already uses:
     *
     * joinScreen
     * roomScreen
     *
     * Public tournament content should be visible only
     * on the home/create/join screen.
     */
    observePlanningPokerScreen();


    /*
     * Connect to the animation created in:
     *
     * pickleball-victory-animation.js
     */
    initializeVictoryAnimation();


    /*
     * Recent Tournament is the default tab,
     * so load it immediately.
     */
    await loadRecentTournaments();
}


/* =====================================================
   PUBLIC SECTION VISIBILITY
   ===================================================== */

function observePlanningPokerScreen() {

    const joinScreen =
        document.getElementById(
            "joinScreen"
        );


    const publicSection =
        document.getElementById(
            "publicTournamentSection"
        );


    if (
        !joinScreen
        ||
        !publicSection
    ) {

        return;
    }


    function syncVisibility() {

        const joinVisible =
            window
                .getComputedStyle(
                    joinScreen
                )
                .display !==
            "none";


        /*
         * User is on Planning Poker home screen:
         *
         * show tournament section.
         *
         * User joined a room:
         *
         * hide tournament section.
         */
        publicSection
            .classList
            .toggle(
                "hidden",
                !joinVisible
            );


        syncVictoryAnimation();
    }


    syncVisibility();


    /*
     * Your Planning Poker code changes joinScreen using
     * inline style.display.
     *
     * MutationObserver lets us react automatically.
     */
    const observer =
        new MutationObserver(
            syncVisibility
        );


    observer.observe(
        joinScreen,
        {
            attributes:
                true,

            attributeFilter: [
                "style",
                "class"
            ]
        }
    );
}


/* =====================================================
   TOP TWO BUTTONS
   ===================================================== */

function wirePublicTabs() {

    const recentButton =
        document.getElementById(
            "recentTournamentTab"
        );


    const scoreboardButton =
        document.getElementById(
            "overallScoreboardTab"
        );


    if (recentButton) {

        recentButton.addEventListener(
            "click",
            function () {

                activatePublicTab(
                    "RECENT"
                );
            }
        );
    }


    if (scoreboardButton) {

        scoreboardButton.addEventListener(
            "click",
            async function () {

                activatePublicTab(
                    "SCOREBOARD"
                );


                /*
                 * We don't need to load lifetime scoreboard
                 * until the user actually opens it.
                 */
                if (!publicScoreboardLoaded) {

                    await loadOverallScoreboard();
                }
            }
        );
    }
}


/* =====================================================
   TAB SWITCHING
   ===================================================== */

function activatePublicTab(
    tab
) {

    activePublicTab =
        tab;


    const recentSelected =
        tab ===
        "RECENT";


    toggleClass(
        "recentTournamentTab",
        "active",
        recentSelected
    );


    toggleClass(
        "overallScoreboardTab",
        "active",
        !recentSelected
    );


    toggleClass(
        "recentTournamentPanel",
        "hidden",
        !recentSelected
    );


    toggleClass(
        "overallScoreboardPanel",
        "hidden",
        recentSelected
    );


    /*
     * Accessibility state.
     */
    setAriaSelected(
        "recentTournamentTab",
        recentSelected
    );


    setAriaSelected(
        "overallScoreboardTab",
        !recentSelected
    );


    syncVictoryAnimation();
}


/* =====================================================
   RECENT TOURNAMENTS
   ===================================================== */

async function loadRecentTournaments() {

    setRecentState(
        "LOADING"
    );


    try {

        const response =
            await fetch(
                "/api/public/tournaments/recent",
                {
                    method:
                        "GET",

                    credentials:
                        "same-origin",

                    headers: {

                        "Accept":
                            "application/json"
                    }
                }
            );


        const data =
            await readJson(
                response
            );


        if (
            !response.ok
            ||
            !data
            ||
            data.success !==
            true
        ) {

            setRecentState(
                "ERROR"
            );


            return;
        }


        const allTournaments =
            Array.isArray(
                data.tournaments
            )
                ?
                data.tournaments
                :
                [];


        /*
         * IMPORTANT:
         *
         * We don't display all three days together.
         *
         * We look for:
         *
         * Today
         * Yesterday
         * 2 Days Ago
         *
         * Then display every tournament from the
         * most recent day that has results.
         *
         *
         * Example:
         *
         * Today:
         *   3 completed tournaments
         *
         * Display all 3.
         *
         *
         * Today:
         *   none
         *
         * Yesterday:
         *   2
         *
         * Display yesterday's 2.
         */
        recentTournaments =
            selectMostRecentAvailableDay(
                allTournaments
            );


        if (
            recentTournaments.length ===
            0
        ) {

            setRecentState(
                "EMPTY"
            );


            return;
        }


        setRecentState(
            "READY"
        );


        renderRecentTournamentTable();


        /*
         * Most recent completed tournament is automatically
         * selected.
         */
        selectRecentTournament(
            recentTournaments[0].id
        );


    } catch (error) {

        setRecentState(
            "ERROR"
        );
    }
}


/* =====================================================
   SELECT MOST RECENT AVAILABLE DAY
   ===================================================== */

function selectMostRecentAvailableDay(
    tournaments
) {

    const eligible =
        tournaments
            .map(
                tournament => {

                    const completedAt =
                        parseDate(
                            tournament.completedAt
                        );


                    return {

                        tournament,

                        completedAt,

                        dayDifference:
                            completedAt
                                ?
                                localCalendarDayDifference(
                                    completedAt
                                )
                                :
                                null
                    };
                }
            )
            .filter(
                item =>
                    item.completedAt
                    &&
                    item.dayDifference !==
                    null
                    &&
                    item.dayDifference >=
                    0
                    &&
                    item.dayDifference <=
                    2
            );


    if (
        eligible.length ===
        0
    ) {

        return [];
    }


    /*
     * 0 = Today
     * 1 = Yesterday
     * 2 = Two days ago
     */
    const mostRecentDayDifference =
        Math.min(
            ...eligible.map(
                item =>
                    item.dayDifference
            )
        );


    return eligible
        .filter(
            item =>
                item.dayDifference ===
                mostRecentDayDifference
        )
        .sort(
            (
                first,
                second
            ) =>
                second.completedAt.getTime()
                -
                first.completedAt.getTime()
        )
        .map(
            item =>
                item.tournament
        );
}


/* =====================================================
   CALENDAR DAY DIFFERENCE
   ===================================================== */

function localCalendarDayDifference(
    date
) {

    const now =
        new Date();


    const today =
        new Date(
            now.getFullYear(),
            now.getMonth(),
            now.getDate()
        );


    const target =
        new Date(
            date.getFullYear(),
            date.getMonth(),
            date.getDate()
        );


    return Math.round(
        (
            today.getTime()
            -
            target.getTime()
        )
        /
        86400000
    );
}


/* =====================================================
   RECENT TOURNAMENT TABLE
   ===================================================== */

function renderRecentTournamentTable() {

    const body =
        document.getElementById(
            "recentTournamentTableBody"
        );


    if (!body) {

        return;
    }


    body.innerHTML =
        "";


    const firstTournamentDate =
        parseDate(
            recentTournaments[0]
                .completedAt
        );


    setText(
        "recentTournamentDayLabel",

        firstTournamentDate
            ?
            dayLabel(
                firstTournamentDate
            )
            :
            "Recent"
    );


    setText(
        "recentTournamentCount",
        String(
            recentTournaments.length
        )
    );


    setText(
        "recentTournamentCountText",

        recentTournaments.length ===
        1
            ?
            "1 completed tournament"
            :
            recentTournaments.length
            +
            " completed tournaments"
    );


    recentTournaments.forEach(
        tournament => {

            const row =
                document.createElement(
                    "tr"
                );


            row.className =
                "recent-tournament-row";


            row.tabIndex =
                0;


            row.dataset.tournamentId =
                tournament.id;


            row.innerHTML = `

                <td>

                    ${escapeHtml(
                        formatShortTime(
                            tournament.completedAt
                        )
                    )}

                </td>


                <td>

                    <span class="recent-name">

                        ${escapeHtml(
                            tournament.name
                            ||
                            "Pickleball Tournament"
                        )}

                    </span>

                </td>


                <td>

                    <span class="recent-format-badge">

                        ${escapeHtml(
                            humanize(
                                tournament.format
                            )
                        )}

                    </span>

                </td>


                <td>

                    ${escapeHtml(
                        recentResultLabel(
                            tournament
                        )
                    )}

                </td>

            `;


            row.addEventListener(
                "click",
                function () {

                    selectRecentTournament(
                        tournament.id
                    );
                }
            );


            /*
             * Keyboard accessibility.
             */
            row.addEventListener(
                "keydown",
                function (event) {

                    if (
                        event.key ===
                        "Enter"
                        ||
                        event.key ===
                        " "
                    ) {

                        event.preventDefault();


                        selectRecentTournament(
                            tournament.id
                        );
                    }
                }
            );


            body.appendChild(
                row
            );
        }
    );
}


/* =====================================================
   SELECT RECENT TOURNAMENT
   ===================================================== */

function selectRecentTournament(
    tournamentId
) {

    const tournament =
        recentTournaments
            .find(
                item =>
                    item.id ===
                    tournamentId
            );


    if (!tournament) {

        return;
    }


    selectedRecentTournamentId =
        tournamentId;


    /*
     * Highlight selected row.
     */
    document
        .querySelectorAll(
            ".recent-tournament-row"
        )
        .forEach(
            row => {

                row.classList.toggle(
                    "selected",

                    row.dataset.tournamentId ===
                    tournamentId
                );
            }
        );


    renderRecentTournamentHighlight(
        tournament
    );
}


/* =====================================================
   RECENT TOURNAMENT DETAIL
   ===================================================== */

function renderRecentTournamentHighlight(
    tournament
) {

    setText(
        "recentHighlightTitle",

        tournament.name
        ||
        "Pickleball Tournament"
    );


    setText(
        "recentHighlightMeta",

        [
            humanize(
                tournament.format
            ),

            formatCompletedDateTime(
                tournament.completedAt
            )
        ]
            .filter(
                Boolean
            )
            .join(
                " • "
            )
    );


    renderScoreStrip(
        tournament.gameScores
    );


    const resultType =
        String(
            tournament.resultType
            ||
            ""
        )
            .toUpperCase();


    const winner =
        tournament.winner
        ||
        null;


    const runnerUp =
        tournament.runnerUp
        ||
        null;


    /* =================================================
       WINNER EXISTS
       ================================================= */

    if (
        resultType ===
        "WINNER"
        &&
        winner
    ) {

        renderWinnerSummary(
            winner,
            runnerUp
        );


        const singles =
            String(
                tournament.format
                ||
                ""
            )
                .toUpperCase()
            ===
            "SINGLES";


        const winnerName =
            competitorName(
                winner
            );


        const runnerUpName =
            competitorName(
                runnerUp
            );


        /*
         * =================================================
         * SINGLES
         *
         * Show uploaded winner-vs-loser animation.
         * =================================================
         */
        if (
            singles
            &&
            winnerName
            &&
            runnerUpName
        ) {

            toggleClass(
                "victoryAnimationShell",
                "hidden",
                false
            );


            toggleClass(
                "teamResultPanel",
                "hidden",
                true
            );


            toggleClass(
                "tieResultPanel",
                "hidden",
                true
            );


            if (victoryAnimation) {

                victoryAnimation
                    .setNames(
                        winnerName,
                        runnerUpName
                    );
            }


        /*
         * =================================================
         * DOUBLES
         *
         * For now show winning team panel.
         *
         * Later we can build the two-player celebration
         * animation separately.
         * =================================================
         */

        } else {

            toggleClass(
                "victoryAnimationShell",
                "hidden",
                true
            );


            toggleClass(
                "tieResultPanel",
                "hidden",
                true
            );


            renderTeamWinnerPanel(
                winner,
                runnerUp
            );
        }


    /* =================================================
       FIRST PLACE TIE
       ================================================= */

    } else if (
        resultType ===
        "TIE"
    ) {

        renderTieSummary(
            tournament.tiedForFirst
        );


    /* =================================================
       COMPLETED TOURNAMENT BUT NO VALID MATCH RESULT
       ================================================= */

    } else {

        renderNoResultSummary();
    }


    syncVictoryAnimation();
}


/* =====================================================
   WINNER / RUNNER-UP SUMMARY
   ===================================================== */

function renderWinnerSummary(
    winner,
    runnerUp
) {

    const container =
        document.getElementById(
            "recentResultSummary"
        );


    if (!container) {

        return;
    }


    container.innerHTML = `

        ${competitorCard(
            winner,
            "🏆 Winner",
            true
        )}


        <div class="result-versus">
            VS
        </div>


        ${competitorCard(
            runnerUp,
            "Runner-Up",
            false
        )}

    `;
}


/* =====================================================
   COMPETITOR CARD
   ===================================================== */

function competitorCard(
    competitor,
    label,
    winner
) {

    if (!competitor) {

        return `

            <div class="result-person-card">

                <div class="result-label">

                    ${escapeHtml(
                        label
                    )}

                </div>


                <div class="result-name">
                    -
                </div>

            </div>

        `;
    }


    return `

        <div class="
            result-person-card
            ${winner ? "winner" : ""}
        ">

            <div class="result-label">

                ${escapeHtml(
                    label
                )}

            </div>


            <div class="result-name">

                ${escapeHtml(
                    competitorName(
                        competitor
                    )
                )}

            </div>


            <div class="result-record">

                ${Number(
                    competitor.matchesWon
                    ||
                    0
                )}

                W

                •

                ${Number(
                    competitor.matchesLost
                    ||
                    0
                )}

                L

                •

                ${Number(
                    competitor.gamesWon
                    ||
                    0
                )}

                Games Won

            </div>

        </div>

    `;
}


/* =====================================================
   DOUBLES WINNER
   ===================================================== */

function renderTeamWinnerPanel(
    winner,
    runnerUp
) {

    const panel =
        document.getElementById(
            "teamResultPanel"
        );


    if (!panel) {

        return;
    }


    const winnerName =
        competitorName(
            winner
        );


    const runnerUpName =
        competitorName(
            runnerUp
        );


    panel.innerHTML = `

        <div class="team-result-icon">
            🏆
        </div>


        <div class="result-label">
            Winning Team
        </div>


        <strong>

            ${escapeHtml(
                winnerName
            )}

        </strong>


        ${
            runnerUpName

                ? `

                    <span>

                        Runner-Up:
                        ${escapeHtml(
                            runnerUpName
                        )}

                    </span>

                  `

                :
                ""
        }

    `;


    panel
        .classList
        .remove(
            "hidden"
        );
}


/* =====================================================
   TIE RESULT
   ===================================================== */

function renderTieSummary(
    tiedForFirst
) {

    const tied =
        Array.isArray(
            tiedForFirst
        )
            ?
            tiedForFirst
            :
            [];


    /*
     * Remove normal winner cards.
     */
    const summary =
        document.getElementById(
            "recentResultSummary"
        );


    if (summary) {

        summary.innerHTML =
            "";
    }


    toggleClass(
        "victoryAnimationShell",
        "hidden",
        true
    );


    toggleClass(
        "teamResultPanel",
        "hidden",
        true
    );


    const panel =
        document.getElementById(
            "tieResultPanel"
        );


    if (!panel) {

        return;
    }


    const names =
        tied
            .map(
                competitorName
            )
            .filter(
                Boolean
            )
            .join(
                " • "
            );


    panel.innerHTML = `

        <div class="tie-result-icon">
            🤝
        </div>


        <strong>
            First Place Tie
        </strong>


        <span>

            ${escapeHtml(
                names
                ||
                "Tournament finished tied."
            )}

        </span>

    `;


    panel
        .classList
        .remove(
            "hidden"
        );
}


/* =====================================================
   NO RESULT
   ===================================================== */

function renderNoResultSummary() {

    const summary =
        document.getElementById(
            "recentResultSummary"
        );


    if (summary) {

        summary.innerHTML = `

            <div class="result-person-card">

                <div class="result-label">
                    Result
                </div>


                <div class="result-name">
                    No completed match result
                </div>

            </div>

        `;
    }


    toggleClass(
        "victoryAnimationShell",
        "hidden",
        true
    );


    toggleClass(
        "teamResultPanel",
        "hidden",
        true
    );


    toggleClass(
        "tieResultPanel",
        "hidden",
        true
    );
}


/* =====================================================
   SINGLE-MATCH GAME SCORES
   ===================================================== */

function renderScoreStrip(
    scores
) {

    const container =
        document.getElementById(
            "recentScoreStrip"
        );


    if (!container) {

        return;
    }


    container.innerHTML =
        "";


    if (
        !Array.isArray(
            scores
        )
        ||
        scores.length ===
        0
    ) {

        container.style.display =
            "none";


        return;
    }


    container.style.display =
        "flex";


    scores.forEach(
        score => {

            const chip =
                document.createElement(
                    "div"
                );


            chip.className =
                "public-score-chip";


            chip.innerHTML = `

                <span>

                    Game
                    ${Number(
                        score.game
                        ||
                        0
                    )}

                </span>


                <strong>

                    ${Number(
                        score.team1
                        ||
                        0
                    )}

                    -

                    ${Number(
                        score.team2
                        ||
                        0
                    )}

                </strong>

            `;


            container.appendChild(
                chip
            );
        }
    );
}


/* =====================================================
   RESULT LABEL FOR RECENT TABLE
   ===================================================== */

function recentResultLabel(
    tournament
) {

    const type =
        String(
            tournament.resultType
            ||
            ""
        )
            .toUpperCase();


    if (
        type ===
        "TIE"
    ) {

        return "Tie";
    }


    if (
        type !==
        "WINNER"
        ||
        !tournament.winner
    ) {

        return "Completed";
    }


    return competitorName(
        tournament.winner
    );
}


/* =====================================================
   OVERALL SCOREBOARD
   ===================================================== */

async function loadOverallScoreboard() {

    setScoreboardState(
        "LOADING"
    );


    try {

        const response =
            await fetch(
                "/api/public/pickleball/scoreboard",
                {
                    method:
                        "GET",

                    credentials:
                        "same-origin",

                    headers: {

                        "Accept":
                            "application/json"
                    }
                }
            );


        const data =
            await readJson(
                response
            );


        if (
            !response.ok
            ||
            !data
            ||
            data.success !==
            true
            ||
            !data.scoreboard
        ) {

            setScoreboardState(
                "ERROR"
            );


            return;
        }


        renderOverallScoreboard(
            data.scoreboard
        );


        publicScoreboardLoaded =
            true;


        setScoreboardState(
            "READY"
        );


    } catch (error) {

        setScoreboardState(
            "ERROR"
        );
    }
}


/* =====================================================
   RENDER BOTH SCOREBOARDS
   ===================================================== */

function renderOverallScoreboard(
    scoreboard
) {

    renderScoreboardRows(
        "singlesScoreboardBody",

        Array.isArray(
            scoreboard.singles
        )
            ?
            scoreboard.singles
            :
            []
    );


    renderScoreboardRows(
        "doublesScoreboardBody",

        Array.isArray(
            scoreboard.doubles
        )
            ?
            scoreboard.doubles
            :
            []
    );


    const singlesMatches =
        Number(
            scoreboard.completedSinglesMatches
            ||
            0
        );


    const doublesMatches =
        Number(
            scoreboard.completedDoublesMatches
            ||
            0
        );


    setText(
        "singlesMatchCount",

        singlesMatches ===
        1
            ?
            "1 completed match"
            :
            singlesMatches
            +
            " completed matches"
    );


    setText(
        "doublesMatchCount",

        doublesMatches ===
        1
            ?
            "1 completed match"
            :
            doublesMatches
            +
            " completed matches"
    );
}


/* =====================================================
   SCOREBOARD ROWS
   ===================================================== */

function renderScoreboardRows(
    bodyId,
    rows
) {

    const body =
        document.getElementById(
            bodyId
        );


    if (!body) {

        return;
    }


    body.innerHTML =
        "";


    if (
        rows.length ===
        0
    ) {

        const emptyRow =
            document.createElement(
                "tr"
            );


        emptyRow.innerHTML = `

            <td colspan="9">
                No completed matches yet.
            </td>

        `;


        body.appendChild(
            emptyRow
        );


        return;
    }


    rows.forEach(
        row => {

            const tableRow =
                document.createElement(
                    "tr"
                );


            const difference =
                Number(
                    row.gameDifferential
                    ||
                    0
                );


            let differenceClass =
                "";


            if (
                difference >
                0
            ) {

                differenceClass =
                    "positive";


            } else if (
                difference <
                0
            ) {

                differenceClass =
                    "negative";
            }


            tableRow.innerHTML = `

                <td>

                    <span class="score-rank">

                        ${rankDisplay(
                            row.rank
                        )}

                    </span>

                </td>


                <td class="player-cell">

                    <span class="score-player-name">

                        ${escapeHtml(
                            row.name
                            ||
                            "Player"
                        )}

                    </span>

                </td>


                <td>

                    ${Number(
                        row.matchesPlayed
                        ||
                        0
                    )}

                </td>


                <td>

                    <strong>

                        ${Number(
                            row.matchesWon
                            ||
                            0
                        )}

                    </strong>

                </td>


                <td>

                    ${Number(
                        row.matchesLost
                        ||
                        0
                    )}

                </td>


                <td class="optional-mobile-stat">

                    ${Number(
                        row.gamesWon
                        ||
                        0
                    )}

                </td>


                <td class="optional-mobile-stat">

                    ${Number(
                        row.gamesLost
                        ||
                        0
                    )}

                </td>


                <td class="
                    score-diff
                    ${differenceClass}
                ">

                    ${signedNumber(
                        difference
                    )}

                </td>


                <td class="score-win-rate">

                    ${formatWinPercentage(
                        row.winPercentage
                    )}

                </td>

            `;


            body.appendChild(
                tableRow
            );
        }
    );
}


/* =====================================================
   RANK DISPLAY
   ===================================================== */

function rankDisplay(
    rank
) {

    const value =
        Number(
            rank
            ||
            0
        );


    if (
        value ===
        1
    ) {

        return "🏆 1";
    }


    if (
        value ===
        2
    ) {

        return "🥈 2";
    }


    if (
        value ===
        3
    ) {

        return "🥉 3";
    }


    return String(
        value
    );
}


/* =====================================================
   ANIMATION INITIALIZATION
   ===================================================== */

function initializeVictoryAnimation() {

    const canvas =
        document.getElementById(
            "victoryCanvas"
        );


    if (
        !canvas
        ||
        !window.DVPickleballVictory
    ) {

        return;
    }


    victoryAnimation =
        window
            .DVPickleballVictory
            .create(
                canvas
            );
}


/* =====================================================
   START / STOP ANIMATION
   ===================================================== */

function syncVictoryAnimation() {

    if (!victoryAnimation) {

        return;
    }


    const publicSection =
        document.getElementById(
            "publicTournamentSection"
        );


    const animationShell =
        document.getElementById(
            "victoryAnimationShell"
        );


    const shouldRun =
        activePublicTab ===
        "RECENT"

        &&

        publicSection

        &&

        !publicSection
            .classList
            .contains(
                "hidden"
            )

        &&

        animationShell

        &&

        !animationShell
            .classList
            .contains(
                "hidden"
            );


    if (shouldRun) {

        victoryAnimation
            .start();


    } else {

        victoryAnimation
            .stop();
    }
}


/* =====================================================
   RECENT PANEL STATES
   ===================================================== */

function setRecentState(
    state
) {

    toggleClass(
        "recentTournamentLoading",
        "hidden",
        state !==
        "LOADING"
    );


    toggleClass(
        "recentTournamentEmpty",
        "hidden",
        state !==
        "EMPTY"
    );


    toggleClass(
        "recentTournamentError",
        "hidden",
        state !==
        "ERROR"
    );


    toggleClass(
        "recentTournamentContent",
        "hidden",
        state !==
        "READY"
    );
}


/* =====================================================
   SCOREBOARD PANEL STATES
   ===================================================== */

function setScoreboardState(
    state
) {

    toggleClass(
        "scoreboardLoading",
        "hidden",
        state !==
        "LOADING"
    );


    toggleClass(
        "scoreboardError",
        "hidden",
        state !==
        "ERROR"
    );


    toggleClass(
        "scoreboardContent",
        "hidden",
        state !==
        "READY"
    );
}


/* =====================================================
   COMPETITOR NAME
   ===================================================== */

function competitorName(
    competitor
) {

    if (
        !competitor
        ||
        !Array.isArray(
            competitor.players
        )
    ) {

        return "";
    }


    return competitor.players
        .map(
            player => {

                if (
                    typeof player.name !==
                    "string"
                ) {

                    return "";
                }


                return player.name
                    .trim();
            }
        )
        .filter(
            Boolean
        )
        .join(
            " & "
        );
}


/* =====================================================
   HUMANIZE ENUM
   ===================================================== */

function humanize(
    value
) {

    if (
        typeof value !==
        "string"
        ||
        !value.trim()
    ) {

        return "";
    }


    return value
        .trim()
        .toLowerCase()
        .replace(
            /_/g,
            " "
        )
        .replace(
            /\b\w/g,
            character =>
                character
                    .toUpperCase()
        );
}


/* =====================================================
   DATE HELPERS
   ===================================================== */

function parseDate(
    value
) {

    if (!value) {

        return null;
    }


    const date =
        new Date(
            value
        );


    if (
        Number.isNaN(
            date.getTime()
        )
    ) {

        return null;
    }


    return date;
}


function dayLabel(
    date
) {

    const difference =
        localCalendarDayDifference(
            date
        );


    if (
        difference ===
        0
    ) {

        return "Today";
    }


    if (
        difference ===
        1
    ) {

        return "Yesterday";
    }


    if (
        difference ===
        2
    ) {

        return "2 Days Ago";
    }


    return new Intl.DateTimeFormat(
        undefined,
        {
            month:
                "short",

            day:
                "numeric"
        }
    )
        .format(
            date
        );
}


/* =====================================================
   TIME
   ===================================================== */

function formatShortTime(
    value
) {

    const date =
        value instanceof Date
            ?
            value
            :
            parseDate(
                value
            );


    if (!date) {

        return "-";
    }


    return new Intl.DateTimeFormat(
        undefined,
        {
            hour:
                "numeric",

            minute:
                "2-digit",

            hour12:
                true
        }
    )
        .format(
            date
        );
}


/* =====================================================
   COMPLETED DATE TEXT
   ===================================================== */

function formatCompletedDateTime(
    value
) {

    const date =
        parseDate(
            value
        );


    if (!date) {

        return "Completed";
    }


    return "Completed "
        +
        dayLabel(
            date
        )
        +
        " at "
        +
        formatShortTime(
            date
        );
}


/* =====================================================
   SIGNED DIFFERENTIAL
   ===================================================== */

function signedNumber(
    value
) {

    const number =
        Number(
            value
            ||
            0
        );


    if (
        number >
        0
    ) {

        return "+"
            +
            number;
    }


    return String(
        number
    );
}


/* =====================================================
   WIN %
   ===================================================== */

function formatWinPercentage(
    value
) {

    const number =
        Number(
            value
            ||
            0
        );


    if (
        Number.isInteger(
            number
        )
    ) {

        return number
            +
            "%";
    }


    return number
        .toFixed(
            1
        )
        +
        "%";
}


/* =====================================================
   SIMPLE DOM HELPERS
   ===================================================== */

function setText(
    id,
    value
) {

    const element =
        document.getElementById(
            id
        );


    if (element) {

        element.textContent =
            value
            ??
            "";
    }
}


function toggleClass(
    id,
    className,
    enabled
) {

    const element =
        document.getElementById(
            id
        );


    if (element) {

        element
            .classList
            .toggle(
                className,
                enabled
            );
    }
}


function setAriaSelected(
    id,
    selected
) {

    const element =
        document.getElementById(
            id
        );


    if (element) {

        element.setAttribute(
            "aria-selected",
            selected
                ?
                "true"
                :
                "false"
        );
    }
}


/* =====================================================
   ESCAPE HTML
   ===================================================== */

function escapeHtml(
    value
) {

    const div =
        document.createElement(
            "div"
        );


    div.textContent =
        String(
            value
            ??
            ""
        );


    return div.innerHTML;
}


/* =====================================================
   SAFE JSON
   ===================================================== */

async function readJson(
    response
) {

    try {

        return await response.json();


    } catch (error) {

        return null;
    }
}