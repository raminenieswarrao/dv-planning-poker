"use strict";


document.addEventListener(
    "DOMContentLoaded",
    initializeDashboard
);


async function initializeDashboard() {

    updateDateTime();


    setInterval(
        updateDateTime,
        1000
    );


    try {

        const response =
            await fetch(
                "/api/auth/me",
                {
                    method: "GET",

                    credentials:
                        "same-origin",

                    headers: {
                        "Accept":
                            "application/json"
                    }
                }
            );


        if (!response.ok) {

            redirectToLogin();

            return;
        }


        const data =
            await response.json();


        if (
            !data ||
            data.authenticated !== true ||
            !data.user
        ) {

            redirectToLogin();

            return;
        }


        renderDashboard(
            data
        );


        await loadRecentTournament();

    } catch (error) {

        redirectToLogin();
    }
}


/* =====================================================
   RENDER DASHBOARD
   ===================================================== */

function renderDashboard(
    data
) {

    const profile =
        data.profile ||
        {};


    const user =
        data.user ||
        {};


    const name =
        typeof profile.name ===
        "string" &&
        profile.name.trim()
            ? profile.name.trim()
            : createNameFromEmail(
                user.email
            );


    const displayName =
        formatDisplayName(
            name
        );


    const email =
        typeof user.email ===
        "string"
            ? user.email
            : "";


    const role =
        typeof profile.role ===
        "string"
            ? profile.role
                .trim()
                .toUpperCase()
            : "USER";


    document
        .getElementById(
            "welcomeTitle"
        )
        .textContent =
        "Welcome back, " +
        displayName;


    document
        .getElementById(
            "profileName"
        )
        .textContent =
        displayName;


    document
        .getElementById(
            "profileEmail"
        )
        .textContent =
        email;


    document
        .getElementById(
            "profileRole"
        )
        .textContent =
        role;


    document
        .getElementById(
            "roleBadge"
        )
        .textContent =
        role;


    document
        .getElementById(
            "profileInitials"
        )
        .textContent =
        getInitials(
            name
        );


    if (
        role ===
        "ADMIN"
    ) {

        document
            .getElementById(
                "adminSection"
            )
            .classList
            .remove(
                "dashboard-hidden"
            );
    }


    document
        .getElementById(
            "dashboardLoading"
        )
        .style.display =
        "none";


    document
        .getElementById(
            "dashboardContent"
        )
        .classList
        .remove(
            "dashboard-hidden"
        );
}


/* =====================================================
   RECENT TOURNAMENT
   ===================================================== */

async function loadRecentTournament() {

    try {

        const response =
            await fetch(
                "/api/tournaments/recent-completed",
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


        if (
            response.status ===
            401
        ) {

            redirectToLogin();

            return;
        }


        const data =
            await readJson(
                response
            );


        if (!response.ok) {

            renderRecentTournamentError();

            return;
        }


        const tournament =
            data &&
            data.tournament
                ? data.tournament
                : null;


        if (!tournament) {

            renderNoRecentTournament();

            return;
        }


        if (
            String(
                tournament.gameType ||
                ""
            )
                .toUpperCase() ===
            "PICKLEBALL"
            &&
            String(
                tournament.format ||
                ""
            )
                .toUpperCase() ===
            "SINGLES"
        ) {

            await loadRecentPickleballResult(
                tournament
            );

            return;
        }


        renderRecentTournamentWithoutStandings(
            tournament
        );

    } catch (error) {

        renderRecentTournamentError();
    }
}


/* =====================================================
   RECENT PICKLEBALL RESULT
   ===================================================== */

async function loadRecentPickleballResult(
    tournament
) {

    try {

        const response =
            await fetch(
                "/api/tournaments/"
                +
                encodeURIComponent(
                    tournament.id
                )
                +
                "/pickleball/standings",
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


        if (
            response.status ===
            401
        ) {

            redirectToLogin();

            return;
        }


        const data =
            await readJson(
                response
            );


        if (!response.ok) {

            renderRecentTournamentWithoutStandings(
                tournament
            );

            return;
        }


        renderRecentTournament(
            tournament,
            data
        );

    } catch (error) {

        renderRecentTournamentWithoutStandings(
            tournament
        );
    }
}


/* =====================================================
   RENDER RECENT RESULT
   ===================================================== */

function renderRecentTournament(
    tournament,
    standingsData
) {

    const container =
        document.getElementById(
            "recentTournamentContent"
        );


    if (!container) {

        return;
    }


    container.innerHTML =
        "";


    container.appendChild(
        buildRecentTournamentHeader(
            tournament
        )
    );


    const completedMatches =
        Number(
            standingsData &&
            standingsData.completedMatches
                ? standingsData.completedMatches
                : 0
        );


    if (
        completedMatches <= 0
    ) {

        container.appendChild(
            buildRecentNotice(
                "Tournament completed",
                "No completed Singles matches are available for the final result."
            )
        );


        configureRecentTournamentLink(
            tournament.id
        );

        return;
    }


    if (
        standingsData.tiedForFirst ===
        true
    ) {

        container.appendChild(
            buildRecentTieResult(
                standingsData.standings
            )
        );


        container.appendChild(
            buildRecentCompletedMatchCount(
                completedMatches
            )
        );


        configureRecentTournamentLink(
            tournament.id
        );

        return;
    }


    const winner =
        standingsData.winner;


    if (
        winner &&
        winner.name
    ) {

        container.appendChild(
            buildRecentWinnerResult(
                winner
            )
        );


        container.appendChild(
            buildRecentCompletedMatchCount(
                completedMatches
            )
        );


        configureRecentTournamentLink(
            tournament.id
        );

        return;
    }


    container.appendChild(
        buildRecentNotice(
            "Tournament completed",
            "The final result is not available yet."
        )
    );


    configureRecentTournamentLink(
        tournament.id
    );
}


function buildRecentTournamentHeader(
    tournament
) {

    const wrapper =
        document.createElement(
            "div"
        );


    wrapper.className =
        "recent-tournament-header";


    const game =
        document.createElement(
            "div"
        );


    game.className =
        "recent-game-label";


    game.textContent =
        [
            humanize(
                tournament.gameType
            ),

            humanize(
                tournament.format
            )
        ]
            .filter(Boolean)
            .join(
                " • "
            )
        ||
        "Tournament";


    const name =
        document.createElement(
            "div"
        );


    name.className =
        "recent-tournament-name";


    name.textContent =
        tournament.name ||
        "Tournament";


    const date =
        document.createElement(
            "div"
        );


    date.className =
        "recent-tournament-date";


    const dateValue =
        tournament.updatedAt ||
        tournament.scheduledAt ||
        tournament.createdAt;


    date.textContent =
        dateValue
            ? "Completed • "
              +
              formatShortDate(
                  dateValue
              )
            : "Completed";


    wrapper.appendChild(
        game
    );


    wrapper.appendChild(
        name
    );


    wrapper.appendChild(
        date
    );


    return wrapper;
}


function buildRecentWinnerResult(
    winner
) {

    const wrapper =
        document.createElement(
            "div"
        );


    wrapper.className =
        "recent-winner-box";


    const trophy =
        document.createElement(
            "div"
        );


    trophy.className =
        "recent-winner-trophy";


    trophy.textContent =
        "🏆";


    const details =
        document.createElement(
            "div"
        );


    details.className =
        "recent-winner-details";


    const label =
        document.createElement(
            "div"
        );


    label.className =
        "recent-result-label";


    label.textContent =
        "Tournament Winner";


    const name =
        document.createElement(
            "div"
        );


    name.className =
        "recent-winner-name";


    name.textContent =
        formatDisplayName(
            winner.name
        );


    const stats =
        document.createElement(
            "div"
        );


    stats.className =
        "recent-winner-stats";


    stats.textContent =
        Number(
            winner.matchesWon ||
            0
        )
        +
        " Wins"
        +
        " • "
        +
        Number(
            winner.matchesLost ||
            0
        )
        +
        " Losses"
        +
        " • "
        +
        Number(
            winner.gamesWon ||
            0
        )
        +
        " Games Won";


    details.appendChild(
        label
    );


    details.appendChild(
        name
    );


    details.appendChild(
        stats
    );


    wrapper.appendChild(
        trophy
    );


    wrapper.appendChild(
        details
    );


    return wrapper;
}


function buildRecentTieResult(
    standings
) {

    const wrapper =
        document.createElement(
            "div"
        );


    wrapper.className =
        "recent-tie-box";


    const icon =
        document.createElement(
            "div"
        );


    icon.className =
        "recent-tie-icon";


    icon.textContent =
        "🤝";


    const details =
        document.createElement(
            "div"
        );


    details.className =
        "recent-winner-details";


    const label =
        document.createElement(
            "div"
        );


    label.className =
        "recent-result-label";


    label.textContent =
        "Tournament Result";


    const title =
        document.createElement(
            "div"
        );


    title.className =
        "recent-winner-name";


    title.textContent =
        "First Place Tie";


    const names =
        document.createElement(
            "div"
        );


    names.className =
        "recent-winner-stats";


    const tiedNames =
        Array.isArray(
            standings
        )
            ? standings
                .filter(
                    row =>
                        Number(
                            row.rank
                        ) ===
                        1
                )
                .map(
                    row =>
                        formatDisplayName(
                            row.name
                        )
                )
            : [];


    names.textContent =
        tiedNames.length > 0
            ? tiedNames.join(
                " / "
            )
            : "Multiple players";


    details.appendChild(
        label
    );


    details.appendChild(
        title
    );


    details.appendChild(
        names
    );


    wrapper.appendChild(
        icon
    );


    wrapper.appendChild(
        details
    );


    return wrapper;
}


function buildRecentCompletedMatchCount(
    completedMatches
) {

    const element =
        document.createElement(
            "div"
        );


    element.className =
        "recent-completed-count";


    element.textContent =
        completedMatches
        +
        (
            completedMatches === 1
                ? " completed match"
                : " completed matches"
        );


    return element;
}


function buildRecentNotice(
    title,
    message
) {

    const wrapper =
        document.createElement(
            "div"
        );


    wrapper.className =
        "recent-result-notice";


    const strong =
        document.createElement(
            "strong"
        );


    strong.textContent =
        title;


    const span =
        document.createElement(
            "span"
        );


    span.textContent =
        message;


    wrapper.appendChild(
        strong
    );


    wrapper.appendChild(
        span
    );


    return wrapper;
}


/* =====================================================
   RECENT RESULT FALLBACKS
   ===================================================== */

function renderNoRecentTournament() {

    const container =
        document.getElementById(
            "recentTournamentContent"
        );


    if (!container) {

        return;
    }


    container.innerHTML =
        "";


    const empty =
        document.createElement(
            "div"
        );


    empty.className =
        "empty-state recent-empty-state";


    const strong =
        document.createElement(
            "strong"
        );


    strong.textContent =
        "No tournament results yet";


    const span =
        document.createElement(
            "span"
        );


    span.textContent =
        "The latest winner will appear here.";


    empty.appendChild(
        strong
    );


    empty.appendChild(
        span
    );


    container.appendChild(
        empty
    );


    hideRecentTournamentLink();
}


function renderRecentTournamentWithoutStandings(
    tournament
) {

    const container =
        document.getElementById(
            "recentTournamentContent"
        );


    if (!container) {

        return;
    }


    container.innerHTML =
        "";


    container.appendChild(
        buildRecentTournamentHeader(
            tournament
        )
    );


    container.appendChild(
        buildRecentNotice(
            "Tournament completed",
            "Detailed winner information is not available for this tournament format yet."
        )
    );


    configureRecentTournamentLink(
        tournament.id
    );
}


function renderRecentTournamentError() {

    const container =
        document.getElementById(
            "recentTournamentContent"
        );


    if (!container) {

        return;
    }


    container.innerHTML =
        "";


    container.appendChild(
        buildRecentNotice(
            "Unable to load recent result",
            "Tournament management is still available from the Tournaments page."
        )
    );


    hideRecentTournamentLink();
}


/* =====================================================
   RECENT TOURNAMENT LINK
   ===================================================== */

function configureRecentTournamentLink(
    tournamentId
) {

    const link =
        document.getElementById(
            "recentTournamentLink"
        );


    if (
        !link ||
        !tournamentId
    ) {

        return;
    }


    link.href =
        "/tournament.html?id="
        +
        encodeURIComponent(
            tournamentId
        );


    link.classList.remove(
        "dashboard-hidden"
    );
}


function hideRecentTournamentLink() {

    const link =
        document.getElementById(
            "recentTournamentLink"
        );


    if (!link) {

        return;
    }


    link.classList.add(
        "dashboard-hidden"
    );
}


/* =====================================================
   AUTH
   ===================================================== */

function redirectToLogin() {

    window.location.replace(
        "/login.html"
    );
}


/* =====================================================
   HTTP
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


/* =====================================================
   DATE / TIME
   ===================================================== */

function updateDateTime() {

    const dateElement =
        document.getElementById(
            "currentDate"
        );


    const timeElement =
        document.getElementById(
            "currentTime"
        );


    const timezoneElement =
        document.getElementById(
            "currentTimezone"
        );


    if (
        !dateElement ||
        !timeElement ||
        !timezoneElement
    ) {

        return;
    }


    const now =
        new Date();


    dateElement.textContent =
        new Intl.DateTimeFormat(
            undefined,
            {
                weekday:
                    "short",

                month:
                    "short",

                day:
                    "numeric",

                year:
                    "numeric"
            }
        )
            .format(
                now
            );


    timeElement.textContent =
        new Intl.DateTimeFormat(
            undefined,
            {
                hour:
                    "2-digit",

                minute:
                    "2-digit",

                second:
                    "2-digit",

                hour12:
                    true
            }
        )
            .format(
                now
            );


    const timezonePart =
        new Intl.DateTimeFormat(
            undefined,
            {
                timeZoneName:
                    "short"
            }
        )
            .formatToParts(
                now
            )
            .find(
                part =>
                    part.type ===
                    "timeZoneName"
            );


    timezoneElement.textContent =
        timezonePart
            ? timezonePart.value
            : "";
}


function formatShortDate(
    value
) {

    if (!value) {

        return "";
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

        return "";
    }


    return new Intl.DateTimeFormat(
        undefined,
        {
            month:
                "short",

            day:
                "numeric",

            year:
                "numeric"
        }
    )
        .format(
            date
        );
}


/* =====================================================
   TEXT HELPERS
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
                character.toUpperCase()
        );
}


/* =====================================================
   NAME HELPERS
   ===================================================== */

function formatDisplayName(
    value
) {

    if (
        typeof value !==
        "string" ||
        !value.trim()
    ) {

        return "User";
    }


    return value
        .trim()
        .toLowerCase()
        .split(
            /\s+/
        )
        .map(
            part =>
                part.charAt(0)
                    .toUpperCase()
                +
                part.slice(
                    1
                )
        )
        .join(
            " "
        );
}


function createNameFromEmail(
    email
) {

    if (
        typeof email !==
        "string"
    ) {

        return "User";
    }


    const value =
        email
            .split(
                "@"
            )[0];


    return value ||
        "User";
}


function getInitials(
    value
) {

    if (
        typeof value !==
        "string" ||
        !value.trim()
    ) {

        return "DV";
    }


    const parts =
        value
            .trim()
            .split(
                /\s+/
            );


    if (
        parts.length ===
        1
    ) {

        return parts[0]
            .substring(
                0,
                2
            )
            .toUpperCase();
    }


    return (
        parts[0][0]
        +
        parts[
            parts.length - 1
        ][0]
    )
        .toUpperCase();
}
