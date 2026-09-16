"use strict";


/* =====================================================
   STATE
   ===================================================== */

let currentTournament =
    null;

let currentParticipants =
    [];

let participantIds =
    new Set();

let currentMatches =
    [];

let currentStandings = {
    standings: [],
    winner: null,
    tiedForFirst: false,
    completedMatches: 0
};

let matchesCanEdit =
    false;

let editingMatchId =
    null;

let userSearchTimer =
    null;

let userSearchRequestId =
    0;


/* =====================================================
   INITIALIZE
   ===================================================== */

document.addEventListener(
    "DOMContentLoaded",
    initializeTournamentPage
);


async function initializeTournamentPage() {

    updateDateTime();

    setInterval(
        updateDateTime,
        1000
    );


    const params =
        new URLSearchParams(
            window.location.search
        );


    const tournamentId =
        params.get(
            "id"
        );


    if (!tournamentId) {

        window.location.replace(
            "/tournaments.html"
        );

        return;
    }


    const loaded =
        await loadTournament(
            tournamentId
        );


    if (!loaded) {

        hideLoading();

        showDetailPage();

        return;
    }


    await loadParticipants(
        tournamentId
    );


    if (
        String(
            currentTournament.gameType || ""
        )
            .toUpperCase() ===
        "PICKLEBALL"
    ) {

        await loadMatches(
            tournamentId
        );


        await loadStandings(
            tournamentId
        );
    }


    hideLoading();

    showDetailPage();


    if (
        params.get(
            "edit"
        ) ===
        "true"
        &&
        currentTournament.canEdit
    ) {

        openEditModal();
    }
}


/* =====================================================
   PAGE
   ===================================================== */

function hideLoading() {

    const loading =
        document.getElementById(
            "loadingState"
        );


    if (loading) {

        loading.style.display =
            "none";
    }
}


function showDetailPage() {

    const page =
        document.getElementById(
            "detailPage"
        );


    if (page) {

        page.classList.remove(
            "page-hidden"
        );
    }
}


/* =====================================================
   LOAD TOURNAMENT
   ===================================================== */

async function loadTournament(
    tournamentId
) {

    try {

        const response =
            await fetch(
                "/api/tournaments/"
                +
                encodeURIComponent(
                    tournamentId
                ),
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

            window.location.replace(
                "/login.html"
            );

            return false;
        }


        const data =
            await readJson(
                response
            );


        if (!response.ok) {

            showPageError(
                getMessage(
                    data,
                    "Unable to load tournament."
                )
            );

            return false;
        }


        if (
            !data ||
            !data.tournament
        ) {

            showPageError(
                "Tournament not found."
            );

            return false;
        }


        currentTournament =
            data.tournament;


        renderTournament();


        return true;

    } catch (error) {

        showPageError(
            "Unable to load tournament."
        );

        return false;
    }
}


/* =====================================================
   RENDER TOURNAMENT
   ===================================================== */

function renderTournament() {

    if (!currentTournament) {

        return;
    }


    const tournament =
        currentTournament;


    renderGameHero(
        tournament.gameType,
        tournament.name
    );


    setText(
        "tournamentName",
        tournament.name ||
        "Tournament"
    );


    setText(
        "gameLabel",
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
        "Tournament"
    );


    setText(
        "statusBadge",
        humanize(
            tournament.status
        )
    );


    setText(
        "tournamentDate",
        formatDateTime(
            tournament.scheduledAt
        )
    );


    setText(
        "tournamentLocation",
        tournament.location ||
        "Not set"
    );


    setText(
        "tournamentGame",
        humanize(
            tournament.gameType
        )
        ||
        "-"
    );


    setText(
        "tournamentFormat",
        humanize(
            tournament.format
        )
        ||
        "-"
    );


    setText(
        "tournamentDescription",
        tournament.description ||
        "No description provided."
    );


    toggleHidden(
        "editTournamentButton",
        !tournament.canEdit
    );


    toggleHidden(
        "participantManagement",
        !tournament.canEdit
    );


    toggleHidden(
        "deleteTournamentButton",
        !tournament.canDelete
    );


    const pickleball =
        String(
            tournament.gameType || ""
        )
            .toUpperCase() ===
        "PICKLEBALL";


    toggleHidden(
        "matchesSection",
        !pickleball
    );


    toggleHidden(
        "standingsSection",
        !pickleball
    );
}


/* =====================================================
   HERO
   ===================================================== */

function renderGameHero(
    gameType,
    tournamentName
) {

    const hero =
        document.getElementById(
            "gameHero"
        );

    const picture =
        document.getElementById(
            "gameHeroPicture"
        );

    const avif =
        document.getElementById(
            "gameHeroAvif"
        );

    const image =
        document.getElementById(
            "gameHeroImage"
        );

    const fallback =
        document.getElementById(
            "gameHeroFallback"
        );

    const label =
        document.getElementById(
            "gameHeroLabel"
        );


    if (
        !hero ||
        !picture ||
        !image ||
        !fallback ||
        !label
    ) {

        return;
    }


    hero.classList.remove(
        "hidden"
    );


    picture.classList.add(
        "hidden"
    );


    fallback.classList.add(
        "hidden"
    );


    image.removeAttribute(
        "src"
    );


    if (avif) {

        avif.removeAttribute(
            "srcset"
        );
    }


    const normalized =
        String(
            gameType || ""
        )
            .trim()
            .toUpperCase();


    label.textContent =
        humanize(
            normalized
        )
            .toUpperCase()
        ||
        "TOURNAMENT";


    if (
        normalized ===
        "PICKLEBALL"
    ) {

        if (avif) {

            avif.srcset =
                "/images/games/pickleball/pickleball-logo.avif";
        }


        image.src =
            "/images/games/pickleball/pickleball-logo.webp";


        image.alt =
            (
                tournamentName ||
                "Pickleball tournament"
            )
            +
            " Pickleball";


        picture.classList.remove(
            "hidden"
        );


        return;
    }


    const icons = {

        PING_PONG:
            "🏓",

        BADMINTON:
            "🏸",

        BASKETBALL:
            "🏀",

        CRICKET:
            "🏏"
    };


    fallback.textContent =
        icons[
            normalized
        ]
        ||
        "🏆";


    fallback.classList.remove(
        "hidden"
    );
}


/* =====================================================
   LOAD PARTICIPANTS
   ===================================================== */

async function loadParticipants(
    tournamentId
) {

    try {

        const response =
            await fetch(
                "/api/tournaments/"
                +
                encodeURIComponent(
                    tournamentId
                )
                +
                "/participants",
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

            window.location.replace(
                "/login.html"
            );

            return;
        }


        const data =
            await readJson(
                response
            );


        if (!response.ok) {

            showPageError(
                getMessage(
                    data,
                    "Unable to load participants."
                )
            );

            return;
        }


        currentParticipants =
            Array.isArray(
                data.participants
            )
                ? data.participants
                : [];


        participantIds =
            new Set(
                currentParticipants
                    .map(
                        participant =>
                            participant.userId
                    )
                    .filter(Boolean)
            );


        renderParticipants();

    } catch (error) {

        showPageError(
            "Unable to load participants."
        );
    }
}


/* =====================================================
   RENDER PARTICIPANTS
   ===================================================== */

function renderParticipants() {

    const list =
        document.getElementById(
            "participantList"
        );

    const empty =
        document.getElementById(
            "participantEmptyState"
        );


    if (!list) {

        return;
    }


    list.innerHTML =
        "";


    setText(
        "participantCount",
        String(
            currentParticipants.length
        )
    );


    if (
        currentParticipants.length ===
        0
    ) {

        if (empty) {

            empty.classList.remove(
                "hidden"
            );
        }


        return;
    }


    if (empty) {

        empty.classList.add(
            "hidden"
        );
    }


    currentParticipants.forEach(
        participant => {

            const row =
                document.createElement(
                    "div"
                );


            row.className =
                "participant-row";


            const avatar =
                document.createElement(
                    "div"
                );


            avatar.className =
                "participant-avatar";


            avatar.textContent =
                getInitials(
                    participant.name
                );


            const info =
                document.createElement(
                    "div"
                );


            info.className =
                "participant-info";


            const name =
                document.createElement(
                    "div"
                );


            name.className =
                "participant-name";


            name.textContent =
                formatDisplayName(
                    participant.name
                );


            const isCreator =
                participant.userId ===
                currentTournament.createdBy;


            if (isCreator) {

                const creator =
                    document.createElement(
                        "span"
                    );


                creator.className =
                    "creator-label";


                creator.textContent =
                    "CREATOR";


                name.appendChild(
                    creator
                );
            }


            const status =
                document.createElement(
                    "div"
                );


            status.className =
                "participant-status";


            status.textContent =
                humanize(
                    participant.status
                );


            info.appendChild(
                name
            );


            info.appendChild(
                status
            );


            row.appendChild(
                avatar
            );


            row.appendChild(
                info
            );


            if (
                currentTournament.canEdit
                &&
                !isCreator
            ) {

                const remove =
                    document.createElement(
                        "button"
                    );


                remove.type =
                    "button";


                remove.className =
                    "remove-user-button";


                remove.textContent =
                    "Remove";


                remove.addEventListener(
                    "click",
                    function () {

                        removeParticipant(
                            participant.userId,
                            participant.name
                        );
                    }
                );


                row.appendChild(
                    remove
                );
            }


            list.appendChild(
                row
            );
        }
    );
}


/* =====================================================
   LIVE USER SEARCH
   ===================================================== */

const searchUserButton =
    document.getElementById(
        "searchUserButton"
    );


const userSearchInput =
    document.getElementById(
        "userSearchInput"
    );


if (searchUserButton) {

    searchUserButton.addEventListener(
        "click",
        function () {

            clearTimeout(
                userSearchTimer
            );


            searchUsers();
        }
    );
}


if (userSearchInput) {

    userSearchInput.addEventListener(
        "input",
        function () {

            clearTimeout(
                userSearchTimer
            );


            userSearchRequestId++;


            setSearchLoading(
                false
            );


            clearSearch();


            const value =
                userSearchInput
                    .value
                    .trim();


            if (!value) {

                return;
            }


            if (
                value.length < 2
            ) {

                showSearchMessage(
                    "Type at least 2 characters."
                );

                return;
            }


            userSearchTimer =
                setTimeout(
                    function () {

                        searchUsers(
                            value
                        );
                    },
                    350
                );
        }
    );


    userSearchInput.addEventListener(
        "keydown",
        function (event) {

            if (
                event.key ===
                "Enter"
            ) {

                event.preventDefault();


                clearTimeout(
                    userSearchTimer
                );


                searchUsers();
            }
        }
    );
}


/* =====================================================
   SEARCH USERS
   ===================================================== */

async function searchUsers(
    suppliedName
) {

    if (!userSearchInput) {

        return;
    }


    const name =
        typeof suppliedName ===
        "string"
            ? suppliedName.trim()
            : userSearchInput.value.trim();


    clearSearch();


    if (
        name.length < 2
    ) {

        showSearchMessage(
            "Enter at least 2 characters."
        );

        return;
    }


    const requestId =
        ++userSearchRequestId;


    setSearchLoading(
        true
    );


    try {

        const response =
            await fetch(
                "/api/tournaments/users/search?name="
                +
                encodeURIComponent(
                    name
                ),
                {
                    credentials:
                        "same-origin",

                    headers: {
                        "Accept":
                            "application/json"
                    }
                }
            );


        if (
            requestId !==
            userSearchRequestId
        ) {

            return;
        }


        if (
            response.status ===
            401
        ) {

            window.location.replace(
                "/login.html"
            );

            return;
        }


        const data =
            await readJson(
                response
            );


        if (
            requestId !==
            userSearchRequestId
        ) {

            return;
        }


        if (!response.ok) {

            showSearchMessage(
                getMessage(
                    data,
                    "Unable to search users."
                )
            );

            return;
        }


        const users =
            Array.isArray(
                data.users
            )
                ? data.users
                : [];


        if (
            users.length ===
            0
        ) {

            showSearchMessage(
                "Can't find this user."
            );

            return;
        }


        renderSearchResults(
            users
        );

    } catch (error) {

        if (
            requestId ===
            userSearchRequestId
        ) {

            showSearchMessage(
                "Unable to search users."
            );
        }

    } finally {

        if (
            requestId ===
            userSearchRequestId
        ) {

            setSearchLoading(
                false
            );
        }
    }
}


/* =====================================================
   SEARCH RESULTS
   ===================================================== */

function renderSearchResults(
    users
) {

    const container =
        document.getElementById(
            "searchResults"
        );


    if (!container) {

        return;
    }


    container.innerHTML =
        "";


    users.forEach(
        user => {

            const row =
                document.createElement(
                    "div"
                );


            row.className =
                "search-result";


            const avatar =
                document.createElement(
                    "div"
                );


            avatar.className =
                "participant-avatar";


            avatar.textContent =
                getInitials(
                    user.name
                );


            const info =
                document.createElement(
                    "div"
                );


            info.className =
                "search-result-info";


            const name =
                document.createElement(
                    "div"
                );


            name.className =
                "search-result-name";


            name.textContent =
                formatDisplayName(
                    user.name
                );


            info.appendChild(
                name
            );


            const button =
                document.createElement(
                    "button"
                );


            button.type =
                "button";


            button.className =
                "add-user-button";


            if (
                participantIds.has(
                    user.id
                )
            ) {

                button.textContent =
                    "Already Added";


                button.disabled =
                    true;

            } else {

                button.textContent =
                    "+ Add";


                button.addEventListener(
                    "click",
                    function () {

                        addParticipant(
                            user.id,
                            button
                        );
                    }
                );
            }


            row.appendChild(
                avatar
            );


            row.appendChild(
                info
            );


            row.appendChild(
                button
            );


            container.appendChild(
                row
            );
        }
    );
}


function setSearchLoading(
    loading
) {

    if (!searchUserButton) {

        return;
    }


    searchUserButton.disabled =
        loading;


    searchUserButton.textContent =
        loading
            ? "Searching..."
            : "Search";
}


function clearSearch() {

    setText(
        "searchMessage",
        ""
    );


    const results =
        document.getElementById(
            "searchResults"
        );


    if (results) {

        results.innerHTML =
            "";
    }
}


function showSearchMessage(
    message
) {

    setText(
        "searchMessage",
        message
    );
}


/* =====================================================
   ADD PARTICIPANT
   ===================================================== */

async function addParticipant(
    userId,
    button
) {

    if (
        !currentTournament ||
        !currentTournament.id
    ) {

        return;
    }


    if (button) {

        button.disabled =
            true;


        button.textContent =
            "Adding...";
    }


    try {

        const response =
            await fetch(
                "/api/tournaments/"
                +
                encodeURIComponent(
                    currentTournament.id
                )
                +
                "/participants",
                {
                    method:
                        "POST",

                    credentials:
                        "same-origin",

                    headers: {
                        "Content-Type":
                            "application/json",

                        "Accept":
                            "application/json"
                    },

                    body:
                        JSON.stringify({
                            userId:
                                userId
                        })
                }
            );


        const data =
            await readJson(
                response
            );


        if (!response.ok) {

            showSearchMessage(
                getMessage(
                    data,
                    "Unable to add participant."
                )
            );


            if (button) {

                button.disabled =
                    false;


                button.textContent =
                    "+ Add";
            }


            return;
        }


        if (userSearchInput) {

            userSearchInput.value =
                "";
        }


        clearTimeout(
            userSearchTimer
        );


        userSearchRequestId++;


        clearSearch();


        await loadParticipants(
            currentTournament.id
        );


        showPageSuccess(
            "Participant added successfully."
        );

    } catch (error) {

        showSearchMessage(
            "Unable to add participant."
        );
    }
}


/* =====================================================
   REMOVE PARTICIPANT
   ===================================================== */

async function removeParticipant(
    userId,
    name
) {

    const confirmed =
        window.confirm(
            "Remove "
            +
            formatDisplayName(
                name
            )
            +
            " from this tournament?"
        );


    if (!confirmed) {

        return;
    }


    try {

        const response =
            await fetch(
                "/api/tournaments/"
                +
                encodeURIComponent(
                    currentTournament.id
                )
                +
                "/participants/"
                +
                encodeURIComponent(
                    userId
                ),
                {
                    method:
                        "DELETE",

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


        if (!response.ok) {

            showPageError(
                getMessage(
                    data,
                    "Unable to remove participant."
                )
            );

            return;
        }


        await loadParticipants(
            currentTournament.id
        );


        showPageSuccess(
            "Participant removed successfully."
        );

    } catch (error) {

        showPageError(
            "Unable to remove participant."
        );
    }
}


/* =====================================================
   MATCH API
   ===================================================== */

function matchApiBase() {

    return "/api/tournaments/"
        +
        encodeURIComponent(
            currentTournament.id
        )
        +
        "/pickleball/matches";
}


/* =====================================================
   LOAD MATCHES
   ===================================================== */

async function loadMatches() {

    try {

        const response =
            await fetch(
                matchApiBase(),
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

            window.location.replace(
                "/login.html"
            );

            return;
        }


        const data =
            await readJson(
                response
            );


        if (!response.ok) {

            showPageError(
                getMessage(
                    data,
                    "Unable to load matches."
                )
            );

            return;
        }


        currentMatches =
            Array.isArray(
                data.matches
            )
                ? data.matches
                : [];


        matchesCanEdit =
            Boolean(
                data.canEdit
                &&
                currentTournament.canEdit
            );


        renderMatches();

    } catch (error) {

        showPageError(
            "Unable to load matches."
        );
    }
}


/* =====================================================
   RENDER MATCHES
   ===================================================== */

function renderMatches() {

    const list =
        document.getElementById(
            "matchesList"
        );


    const empty =
        document.getElementById(
            "matchesEmptyState"
        );


    const createButton =
        document.getElementById(
            "createMatchButton"
        );


    if (!list) {

        return;
    }


    list.innerHTML =
        "";


    setText(
        "matchCount",
        String(
            currentMatches.length
        )
    );


    if (createButton) {

        createButton.classList.toggle(
            "hidden",
            !matchesCanEdit
        );
    }


    if (
        currentMatches.length ===
        0
    ) {

        if (empty) {

            empty.style.display =
                "flex";
        }


        return;
    }


    if (empty) {

        empty.style.display =
            "none";
    }


    currentMatches.forEach(
        match => {

            list.appendChild(
                buildMatchCard(
                    match
                )
            );
        }
    );
}


/* =====================================================
   MATCH CARD
   ===================================================== */

function buildMatchCard(
    match
) {

    const card =
        document.createElement(
            "article"
        );


    card.className =
        "match-card";


    /* HEADER */

    const header =
        document.createElement(
            "div"
        );


    header.className =
        "match-card-header";


    const title =
        document.createElement(
            "div"
        );


    title.className =
        "match-card-title";


    const round =
        document.createElement(
            "div"
        );


    round.className =
        "match-round";


    round.textContent =
        match.roundName ||
        humanize(
            match.matchFormat
        );


    const heading =
        document.createElement(
            "h3"
        );


    heading.textContent =
        "Match "
        +
        match.matchNumber;


    title.appendChild(
        round
    );


    title.appendChild(
        heading
    );


    const status =
        document.createElement(
            "span"
        );


    status.className =
        "match-status "
        +
        statusClass(
            match.status
        );


    status.textContent =
        humanize(
            match.status
        );


    header.appendChild(
        title
    );


    header.appendChild(
        status
    );


    card.appendChild(
        header
    );


    /* BODY */

    const body =
        document.createElement(
            "div"
        );


    body.className =
        "match-body";


    const teams =
        document.createElement(
            "div"
        );


    teams.className =
        "match-teams";


    teams.appendChild(
        buildTeamDisplay(
            "Team 1",
            teamName(
                match.team1Player1,
                match.team1Player2
            ),
            match.team1GamesWon,
            match.winnerSide === 1
        )
    );


    const vs =
        document.createElement(
            "div"
        );


    vs.className =
        "match-vs";


    vs.textContent =
        "VS";


    teams.appendChild(
        vs
    );


    teams.appendChild(
        buildTeamDisplay(
            "Team 2",
            teamName(
                match.team2Player1,
                match.team2Player2
            ),
            match.team2GamesWon,
            match.winnerSide === 2
        )
    );


    body.appendChild(
        teams
    );


    /* GAME SCORES */

    const scores =
        Array.isArray(
            match.gameScores
        )
            ? match.gameScores
            : [];


    if (
        scores.length > 0
    ) {

        const scoreContainer =
            document.createElement(
                "div"
            );


        scoreContainer.className =
            "match-game-scores";


        scores.forEach(
            score => {

                const chip =
                    document.createElement(
                        "div"
                    );


                chip.className =
                    "game-score-chip";


                const label =
                    document.createElement(
                        "div"
                    );


                label.className =
                    "game-score-title";


                label.textContent =
                    "Game "
                    +
                    score.game;


                const value =
                    document.createElement(
                        "div"
                    );


                value.className =
                    "game-score-value";


                value.textContent =
                    score.team1
                    +
                    " - "
                    +
                    score.team2;


                chip.appendChild(
                    label
                );


                chip.appendChild(
                    value
                );


                scoreContainer.appendChild(
                    chip
                );
            }
        );


        body.appendChild(
            scoreContainer
        );

    } else {

        const noScore =
            document.createElement(
                "div"
            );


        noScore.className =
            "match-no-score";


        noScore.textContent =
            "No game scores entered yet.";


        body.appendChild(
            noScore
        );
    }


    /* WINNER */

    if (
        match.winnerSide ===
        1
        ||
        match.winnerSide ===
        2
    ) {

        const winner =
            document.createElement(
                "div"
            );


        winner.className =
            "match-winner";


        const winnerName =
            match.winnerSide === 1
                ? teamName(
                    match.team1Player1,
                    match.team1Player2
                )
                : teamName(
                    match.team2Player1,
                    match.team2Player2
                );


        winner.textContent =
            "🏆 Winner: "
            +
            winnerName;


        body.appendChild(
            winner
        );
    }


    /* META */

    const meta =
        document.createElement(
            "div"
        );


    meta.className =
        "match-meta";


    [
        humanize(
            match.matchFormat
        ),

        "Best of "
        +
        match.bestOfGames,

        match.pointsToWin
        +
        " points",

        "Win by "
        +
        match.winBy
    ]
        .forEach(
            value => {

                const item =
                    document.createElement(
                        "span"
                    );


                item.className =
                    "match-meta-item";


                item.textContent =
                    value;


                meta.appendChild(
                    item
                );
            }
        );


    if (
        match.scheduledAt
    ) {

        const scheduled =
            document.createElement(
                "span"
            );


        scheduled.className =
            "match-meta-item";


        scheduled.textContent =
            formatDateTime(
                match.scheduledAt
            );


        meta.appendChild(
            scheduled
        );
    }


    body.appendChild(
        meta
    );


    /* ACTIONS */

    if (
        matchesCanEdit
    ) {

        const actions =
            document.createElement(
                "div"
            );


        actions.className =
            "match-actions";


        const edit =
            document.createElement(
                "button"
            );


        edit.type =
            "button";


        edit.className =
            "match-edit-button";


        edit.textContent =
            scores.length === 0
                ? "Enter Score / Edit"
                : "Edit Match & Score";


        edit.addEventListener(
            "click",
            function () {

                openEditMatchModal(
                    match
                );
            }
        );


        const remove =
            document.createElement(
                "button"
            );


        remove.type =
            "button";


        remove.className =
            "match-delete-button";


        remove.textContent =
            "Delete Match";


        remove.addEventListener(
            "click",
            function () {

                deleteMatch(
                    match
                );
            }
        );


        actions.appendChild(
            edit
        );


        actions.appendChild(
            remove
        );


        body.appendChild(
            actions
        );
    }


    card.appendChild(
        body
    );


    return card;
}


/* =====================================================
   TEAM DISPLAY
   ===================================================== */

function buildTeamDisplay(
    label,
    name,
    gamesWon,
    winner
) {

    const element =
        document.createElement(
            "div"
        );


    element.className =
        "match-team";


    if (winner) {

        element.classList.add(
            "winner"
        );
    }


    const teamLabel =
        document.createElement(
            "div"
        );


    teamLabel.className =
        "match-team-label";


    teamLabel.textContent =
        label;


    const teamNameElement =
        document.createElement(
            "div"
        );


    teamNameElement.className =
        "match-team-name";


    teamNameElement.textContent =
        name;


    const won =
        document.createElement(
            "div"
        );


    won.className =
        "match-team-games";


    won.textContent =
        String(
            gamesWon ?? 0
        );


    element.appendChild(
        teamLabel
    );


    element.appendChild(
        teamNameElement
    );


    element.appendChild(
        won
    );


    return element;
}



/* =====================================================
   STANDINGS API
   ===================================================== */

function standingsApiUrl() {

    return "/api/tournaments/"
        +
        encodeURIComponent(
            currentTournament.id
        )
        +
        "/pickleball/standings";
}


/* =====================================================
   LOAD STANDINGS
   ===================================================== */

async function loadStandings() {

    if (
        !currentTournament ||
        !currentTournament.id
    ) {

        return;
    }


    try {

        const response =
            await fetch(
                standingsApiUrl(),
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

            window.location.replace(
                "/login.html"
            );

            return;
        }


        const data =
            await readJson(
                response
            );


        if (!response.ok) {

            showPageError(
                getMessage(
                    data,
                    "Unable to load tournament standings."
                )
            );

            return;
        }


        currentStandings = {
            standings:
                Array.isArray(
                    data.standings
                )
                    ? data.standings
                    : [],

            winner:
                data.winner ||
                null,

            tiedForFirst:
                Boolean(
                    data.tiedForFirst
                ),

            completedMatches:
                Number(
                    data.completedMatches || 0
                )
        };


        renderStandings();

    } catch (error) {

        showPageError(
            "Unable to load tournament standings."
        );
    }
}


/* =====================================================
   RENDER STANDINGS
   ===================================================== */

function renderStandings() {

    const tableBody =
        document.getElementById(
            "standingsTableBody"
        );

    const tableWrap =
        document.getElementById(
            "standingsTableWrap"
        );

    const empty =
        document.getElementById(
            "standingsEmptyState"
        );

    const summary =
        document.getElementById(
            "standingsSummary"
        );

    const leaderCard =
        document.getElementById(
            "standingsLeaderCard"
        );

    const leaderIcon =
        document.getElementById(
            "standingsLeaderIcon"
        );

    const tieMessage =
        document.getElementById(
            "standingsTieMessage"
        );


    if (!tableBody) {

        return;
    }


    const standings =
        Array.isArray(
            currentStandings.standings
        )
            ? currentStandings.standings
            : [];


    const completedMatches =
        Number(
            currentStandings.completedMatches || 0
        );


    setText(
        "completedMatchCount",
        String(
            completedMatches
        )
    );


    setText(
        "standingsCompletedMatches",
        String(
            completedMatches
        )
    );


    tableBody.innerHTML =
        "";


    if (tieMessage) {

        tieMessage.textContent =
            "";

        tieMessage.classList.add(
            "hidden"
        );
    }


    if (leaderCard) {

        leaderCard.classList.remove(
            "final-winner"
        );
    }


    if (
        completedMatches === 0 ||
        standings.length === 0
    ) {

        if (empty) {

            empty.classList.remove(
                "hidden"
            );
        }


        if (tableWrap) {

            tableWrap.classList.add(
                "hidden"
            );
        }


        if (summary) {

            summary.classList.add(
                "hidden"
            );
        }


        return;
    }


    if (empty) {

        empty.classList.add(
            "hidden"
        );
    }


    if (tableWrap) {

        tableWrap.classList.remove(
            "hidden"
        );
    }


    if (summary) {

        summary.classList.remove(
            "hidden"
        );
    }


    const tournamentCompleted =
        String(
            currentTournament.status || ""
        )
            .toUpperCase() ===
        "COMPLETED";


    if (
        currentStandings.tiedForFirst
    ) {

        if (leaderIcon) {

            leaderIcon.textContent =
                "🤝";
        }


        setText(
            "standingsLeaderLabel",
            tournamentCompleted
                ? "Final Standings"
                : "Current Standings"
        );


        setText(
            "standingsLeaderName",
            "First place is tied"
        );


        if (tieMessage) {

            tieMessage.textContent =
                tournamentCompleted
                    ? "The tournament finished with first place tied. No single winner is being declared."
                    : "First place is currently tied. Additional completed matches may break the tie.";

            tieMessage.classList.remove(
                "hidden"
            );
        }

    } else if (
        currentStandings.winner
    ) {

        if (tournamentCompleted) {

            if (leaderIcon) {

                leaderIcon.textContent =
                    "🏆";
            }


            if (leaderCard) {

                leaderCard.classList.add(
                    "final-winner"
                );
            }


            setText(
                "standingsLeaderLabel",
                "Tournament Winner"
            );

        } else {

            if (leaderIcon) {

                leaderIcon.textContent =
                    "🏅";
            }


            setText(
                "standingsLeaderLabel",
                "Current Leader"
            );
        }


        setText(
            "standingsLeaderName",
            formatDisplayName(
                currentStandings.winner.name
            )
        );
    }


    standings.forEach(
        standing => {

            tableBody.appendChild(
                buildStandingRow(
                    standing
                )
            );
        }
    );
}


/* =====================================================
   BUILD STANDING ROW
   ===================================================== */

function buildStandingRow(
    standing
) {

    const row =
        document.createElement(
            "tr"
        );


    if (
        Number(
            standing.rank
        ) === 1
    ) {

        row.classList.add(
            "rank-one"
        );
    }


    const rankCell =
        document.createElement(
            "td"
        );


    const rank =
        document.createElement(
            "span"
        );


    rank.className =
        "standings-rank";


    if (
        Number(
            standing.rank
        ) === 1
    ) {

        rank.classList.add(
            "first"
        );
    }


    rank.textContent =
        Number(
            standing.rank
        ) === 1
            ? "🏆 1"
            : String(
                standing.rank ?? "-"
            );


    rankCell.appendChild(
        rank
    );


    row.appendChild(
        rankCell
    );


    const playerCell =
        document.createElement(
            "td"
        );


    const player =
        document.createElement(
            "div"
        );


    player.className =
        "standings-player";


    const avatar =
        document.createElement(
            "div"
        );


    avatar.className =
        "standings-player-avatar";


    avatar.textContent =
        getInitials(
            standing.name
        );


    const name =
        document.createElement(
            "div"
        );


    name.className =
        "standings-player-name";


    name.textContent =
        formatDisplayName(
            standing.name
        );


    player.appendChild(
        avatar
    );


    player.appendChild(
        name
    );


    playerCell.appendChild(
        player
    );


    row.appendChild(
        playerCell
    );


    row.appendChild(
        buildStandingStatCell(
            standing.matchesPlayed
        )
    );


    row.appendChild(
        buildStandingStatCell(
            standing.matchesWon,
            "win"
        )
    );


    row.appendChild(
        buildStandingStatCell(
            standing.matchesLost,
            "loss"
        )
    );


    row.appendChild(
        buildStandingStatCell(
            standing.gamesWon
        )
    );


    row.appendChild(
        buildStandingStatCell(
            standing.gamesLost
        )
    );


    const differential =
        Number(
            standing.gameDifferential || 0
        );


    const differentialCell =
        buildStandingStatCell(
            differential > 0
                ? "+" + differential
                : differential
        );


    const differentialSpan =
        differentialCell.querySelector(
            ".standings-stat"
        );


    if (differentialSpan) {

        differentialSpan.classList.add(
            "standings-differential"
        );


        if (
            differential > 0
        ) {

            differentialSpan.classList.add(
                "positive"
            );

        } else if (
            differential < 0
        ) {

            differentialSpan.classList.add(
                "negative"
            );
        }
    }


    row.appendChild(
        differentialCell
    );


    return row;
}


function buildStandingStatCell(
    value,
    extraClass
) {

    const cell =
        document.createElement(
            "td"
        );


    const stat =
        document.createElement(
            "span"
        );


    stat.className =
        "standings-stat";


    if (extraClass) {

        stat.classList.add(
            extraClass
        );
    }


    stat.textContent =
        String(
            value ?? 0
        );


    cell.appendChild(
        stat
    );


    return cell;
}

/* =====================================================
   MATCH MODAL ELEMENTS
   ===================================================== */

const createMatchButton =
    document.getElementById(
        "createMatchButton"
    );


const matchModal =
    document.getElementById(
        "matchModal"
    );


const matchForm =
    document.getElementById(
        "matchForm"
    );


const closeMatchButton =
    document.getElementById(
        "closeMatchButton"
    );


const cancelMatchButton =
    document.getElementById(
        "cancelMatchButton"
    );


const saveMatchButton =
    document.getElementById(
        "saveMatchButton"
    );


const addGameScoreButton =
    document.getElementById(
        "addGameScoreButton"
    );


const matchFormat =
    document.getElementById(
        "matchFormat"
    );


const bestOfGames =
    document.getElementById(
        "bestOfGames"
    );


if (createMatchButton) {

    createMatchButton.addEventListener(
        "click",
        openCreateMatchModal
    );
}


if (closeMatchButton) {

    closeMatchButton.addEventListener(
        "click",
        closeMatchModal
    );
}


if (cancelMatchButton) {

    cancelMatchButton.addEventListener(
        "click",
        closeMatchModal
    );
}


if (matchModal) {

    matchModal.addEventListener(
        "click",
        function (event) {

            if (
                event.target ===
                matchModal
            ) {

                closeMatchModal();
            }
        }
    );
}


if (matchForm) {

    matchForm.addEventListener(
        "submit",
        saveMatch
    );
}


if (matchFormat) {

    matchFormat.addEventListener(
        "change",
        updateMatchFormatFields
    );
}


if (bestOfGames) {

    bestOfGames.addEventListener(
        "change",
        enforceScoreRowLimit
    );
}


if (addGameScoreButton) {

    addGameScoreButton.addEventListener(
        "click",
        function () {

            addGameScoreRow();
        }
    );
}


/* =====================================================
   CREATE MATCH
   ===================================================== */

function openCreateMatchModal() {

    editingMatchId =
        null;


    resetMatchForm();


    setText(
        "matchModalTitle",
        "Create Match"
    );


    if (saveMatchButton) {

        saveMatchButton.textContent =
            "Create Match";
    }


    const number =
        nextMatchNumber();


    document
        .getElementById(
            "matchNumber"
        )
        .value =
        number;


    document
        .getElementById(
            "matchRoundName"
        )
        .value =
        "Round 1";


    const tournamentFormat =
        String(
            currentTournament.format || ""
        )
            .toUpperCase();


    document
        .getElementById(
            "matchFormat"
        )
        .value =
        (
            tournamentFormat ===
            "DOUBLES"
        )
            ? "DOUBLES"
            : "SINGLES";


    updateMatchFormatFields();


    populatePlayerSelects();


    openMatchModal();
}


/* =====================================================
   EDIT MATCH
   ===================================================== */

function openEditMatchModal(
    match
) {

    editingMatchId =
        match.id;


    resetMatchForm();


    setText(
        "matchModalTitle",
        "Edit Match & Score"
    );


    if (saveMatchButton) {

        saveMatchButton.textContent =
            "Save Changes";
    }


    document
        .getElementById(
            "matchRoundName"
        )
        .value =
        match.roundName ||
        "";


    document
        .getElementById(
            "matchNumber"
        )
        .value =
        match.matchNumber;


    document
        .getElementById(
            "matchFormat"
        )
        .value =
        match.matchFormat ||
        "SINGLES";


    document
        .getElementById(
            "bestOfGames"
        )
        .value =
        match.bestOfGames ||
        3;


    document
        .getElementById(
            "pointsToWin"
        )
        .value =
        match.pointsToWin ||
        11;


    document
        .getElementById(
            "winBy"
        )
        .value =
        match.winBy ||
        2;


    document
        .getElementById(
            "matchStatus"
        )
        .value =
        match.status ||
        "SCHEDULED";


    document
        .getElementById(
            "matchScheduledAt"
        )
        .value =
        toLocalDateTimeInput(
            match.scheduledAt
        );


    populatePlayerSelects();


    setSelectValue(
        "team1Player1",
        match.team1Player1?.id
    );


    setSelectValue(
        "team1Player2",
        match.team1Player2?.id
    );


    setSelectValue(
        "team2Player1",
        match.team2Player1?.id
    );


    setSelectValue(
        "team2Player2",
        match.team2Player2?.id
    );


    updateMatchFormatFields(
        false
    );


    const scores =
        Array.isArray(
            match.gameScores
        )
            ? match.gameScores
            : [];


    scores.forEach(
        score => {

            addGameScoreRow(
                score
            );
        }
    );


    updateAddScoreButton();


    openMatchModal();
}


/* =====================================================
   MATCH MODAL OPEN/CLOSE
   ===================================================== */

function openMatchModal() {

    clearMatchMessage();


    matchModal.classList.remove(
        "hidden"
    );


    document.body.style.overflow =
        "hidden";
}


function closeMatchModal() {

    if (matchModal) {

        matchModal.classList.add(
            "hidden"
        );
    }


    document.body.style.overflow =
        "";


    editingMatchId =
        null;
}


/* =====================================================
   RESET MATCH FORM
   ===================================================== */

function resetMatchForm() {

    matchForm.reset();


    document
        .getElementById(
            "bestOfGames"
        )
        .value =
        "3";


    document
        .getElementById(
            "pointsToWin"
        )
        .value =
        "11";


    document
        .getElementById(
            "winBy"
        )
        .value =
        "2";


    document
        .getElementById(
            "matchStatus"
        )
        .value =
        "SCHEDULED";


    document
        .getElementById(
            "gameScoresContainer"
        )
        .innerHTML =
        "";


    clearMatchMessage();


    updateAddScoreButton();
}


/* =====================================================
   PLAYER SELECTS
   ===================================================== */

function populatePlayerSelects() {

    [
        "team1Player1",
        "team1Player2",
        "team2Player1",
        "team2Player2"
    ]
        .forEach(
            selectId => {

                const select =
                    document.getElementById(
                        selectId
                    );


                if (!select) {

                    return;
                }


                select.innerHTML =
                    "";


                const placeholder =
                    document.createElement(
                        "option"
                    );


                placeholder.value =
                    "";


                placeholder.textContent =
                    "Select player";


                select.appendChild(
                    placeholder
                );


                currentParticipants.forEach(
                    participant => {

                        const option =
                            document.createElement(
                                "option"
                            );


                        option.value =
                            participant.userId;


                        option.textContent =
                            formatDisplayName(
                                participant.name
                            );


                        select.appendChild(
                            option
                        );
                    }
                );
            }
        );
}


/* =====================================================
   SINGLES / DOUBLES
   ===================================================== */

function updateMatchFormatFields(
    clearSecondPlayers = true
) {

    const format =
        document
            .getElementById(
                "matchFormat"
            )
            .value;


    const doubles =
        format ===
        "DOUBLES";


    toggleHidden(
        "team1Player2Group",
        !doubles
    );


    toggleHidden(
        "team2Player2Group",
        !doubles
    );


    if (
        !doubles
        &&
        clearSecondPlayers
    ) {

        setSelectValue(
            "team1Player2",
            ""
        );


        setSelectValue(
            "team2Player2",
            ""
        );
    }
}


/* =====================================================
   GAME SCORE ROWS
   ===================================================== */

function addGameScoreRow(
    existingScore
) {

    const container =
        document.getElementById(
            "gameScoresContainer"
        );


    const limit =
        Number(
            document
                .getElementById(
                    "bestOfGames"
                )
                .value
        );


    if (
        container.children.length >=
        limit
    ) {

        showMatchError(
            "This match allows a maximum of "
            +
            limit
            +
            " game scores."
        );

        return;
    }


    const row =
        document.createElement(
            "div"
        );


    row.className =
        "game-score-entry";


    const number =
        document.createElement(
            "div"
        );


    number.className =
        "game-score-number";


    const gameNumber =
        container.children.length
        +
        1;


    number.textContent =
        "Game "
        +
        gameNumber;


    const team1 =
        document.createElement(
            "input"
        );


    team1.type =
        "number";


    team1.min =
        "0";


    team1.max =
        "250";


    team1.className =
        "score-team1";


    team1.placeholder =
        "Team 1";


    if (
        existingScore &&
        existingScore.team1 != null
    ) {

        team1.value =
            existingScore.team1;
    }


    const divider =
        document.createElement(
            "div"
        );


    divider.className =
        "game-score-divider";


    divider.textContent =
        "-";


    const team2 =
        document.createElement(
            "input"
        );


    team2.type =
        "number";


    team2.min =
        "0";


    team2.max =
        "250";


    team2.className =
        "score-team2";


    team2.placeholder =
        "Team 2";


    if (
        existingScore &&
        existingScore.team2 != null
    ) {

        team2.value =
            existingScore.team2;
    }


    const remove =
        document.createElement(
            "button"
        );


    remove.type =
        "button";


    remove.className =
        "remove-score-button";


    remove.textContent =
        "×";


    remove.title =
        "Remove game score";


    remove.addEventListener(
        "click",
        function () {

            row.remove();


            renumberScoreRows();


            updateAddScoreButton();
        }
    );


    row.appendChild(
        number
    );


    row.appendChild(
        team1
    );


    row.appendChild(
        divider
    );


    row.appendChild(
        team2
    );


    row.appendChild(
        remove
    );


    container.appendChild(
        row
    );


    updateAddScoreButton();
}


/* =====================================================
   SCORE ROW LIMIT
   ===================================================== */

function enforceScoreRowLimit() {

    const container =
        document.getElementById(
            "gameScoresContainer"
        );


    const limit =
        Number(
            document
                .getElementById(
                    "bestOfGames"
                )
                .value
        );


    while (
        container.children.length >
        limit
    ) {

        container.lastElementChild.remove();
    }


    renumberScoreRows();


    updateAddScoreButton();
}


function updateAddScoreButton() {

    if (!addGameScoreButton) {

        return;
    }


    const count =
        document
            .getElementById(
                "gameScoresContainer"
            )
            .children
            .length;


    const limit =
        Number(
            document
                .getElementById(
                    "bestOfGames"
                )
                .value
        );


    addGameScoreButton.disabled =
        count >= limit;
}


function renumberScoreRows() {

    const rows =
        document
            .querySelectorAll(
                ".game-score-entry"
            );


    rows.forEach(
        (
            row,
            index
        ) => {

            const label =
                row.querySelector(
                    ".game-score-number"
                );


            if (label) {

                label.textContent =
                    "Game "
                    +
                    (
                        index + 1
                    );
            }
        }
    );
}


/* =====================================================
   COLLECT GAME SCORES
   ===================================================== */

function collectGameScores() {

    const rows =
        document
            .querySelectorAll(
                ".game-score-entry"
            );


    const scores =
        [];


    for (
        const row
        of rows
    ) {

        const team1Input =
            row.querySelector(
                ".score-team1"
            );


        const team2Input =
            row.querySelector(
                ".score-team2"
            );


        const team1Value =
            team1Input.value.trim();


        const team2Value =
            team2Input.value.trim();


        /*
         * Completely blank row can be ignored.
         */
        if (
            !team1Value &&
            !team2Value
        ) {

            continue;
        }


        if (
            !team1Value ||
            !team2Value
        ) {

            throw new Error(
                "Enter both Team 1 and Team 2 scores for every game."
            );
        }


        const team1 =
            Number(
                team1Value
            );


        const team2 =
            Number(
                team2Value
            );


        if (
            !Number.isInteger(
                team1
            )
            ||
            !Number.isInteger(
                team2
            )
            ||
            team1 < 0
            ||
            team2 < 0
        ) {

            throw new Error(
                "Game scores must be positive whole numbers or zero."
            );
        }


        scores.push({
            team1:
                team1,

            team2:
                team2
        });
    }


    return scores;
}


/* =====================================================
   SAVE MATCH
   ===================================================== */

async function saveMatch(
    event
) {

    event.preventDefault();


    clearMatchMessage();


    const format =
        document
            .getElementById(
                "matchFormat"
            )
            .value;


    const team1Player1Id =
        valueOf(
            "team1Player1"
        );


    const team1Player2Id =
        valueOf(
            "team1Player2"
        );


    const team2Player1Id =
        valueOf(
            "team2Player1"
        );


    const team2Player2Id =
        valueOf(
            "team2Player2"
        );


    const players =
        format ===
        "DOUBLES"
            ? [
                team1Player1Id,
                team1Player2Id,
                team2Player1Id,
                team2Player2Id
            ]
            : [
                team1Player1Id,
                team2Player1Id
            ];


    if (
        players.some(
            player =>
                !player
        )
    ) {

        showMatchError(
            format ===
            "DOUBLES"
                ? "Please select all four players."
                : "Please select both players."
        );

        return;
    }


    if (
        new Set(
            players
        )
            .size !==
        players.length
    ) {

        showMatchError(
            "Each player in the match must be different."
        );

        return;
    }


    const matchNumber =
        Number(
            valueOf(
                "matchNumber"
            )
        );


    if (
        !Number.isInteger(
            matchNumber
        )
        ||
        matchNumber <= 0
    ) {

        showMatchError(
            "Enter a valid match number."
        );

        return;
    }


    let gameScores;


    try {

        gameScores =
            collectGameScores();

    } catch (error) {

        showMatchError(
            error.message
        );

        return;
    }


    let scheduledAt =
        null;


    const scheduledValue =
        valueOf(
            "matchScheduledAt"
        );


    if (scheduledValue) {

        const date =
            new Date(
                scheduledValue
            );


        if (
            Number.isNaN(
                date.getTime()
            )
        ) {

            showMatchError(
                "Enter a valid scheduled date."
            );

            return;
        }


        scheduledAt =
            date.toISOString();
    }


    const payload = {

        roundName:
            valueOf(
                "matchRoundName"
            )
            ||
            null,

        matchNumber:
            matchNumber,

        matchFormat:
            format,

        bestOfGames:
            Number(
                valueOf(
                    "bestOfGames"
                )
            ),

        pointsToWin:
            Number(
                valueOf(
                    "pointsToWin"
                )
            ),

        winBy:
            Number(
                valueOf(
                    "winBy"
                )
            ),

        team1Player1Id:
            team1Player1Id,

        team1Player2Id:
            format ===
            "DOUBLES"
                ? team1Player2Id
                : null,

        team2Player1Id:
            team2Player1Id,

        team2Player2Id:
            format ===
            "DOUBLES"
                ? team2Player2Id
                : null,

        gameScores:
            gameScores,

        status:
            valueOf(
                "matchStatus"
            ),

        scheduledAt:
            scheduledAt
    };


    setMatchSaving(
        true
    );


    try {

        const editing =
            Boolean(
                editingMatchId
            );


        const url =
            editing
                ? matchApiBase()
                  +
                  "/"
                  +
                  encodeURIComponent(
                      editingMatchId
                  )
                : matchApiBase();


        const response =
            await fetch(
                url,
                {
                    method:
                        editing
                            ? "PATCH"
                            : "POST",

                    credentials:
                        "same-origin",

                    headers: {
                        "Content-Type":
                            "application/json",

                        "Accept":
                            "application/json"
                    },

                    body:
                        JSON.stringify(
                            payload
                        )
                }
            );


        const data =
            await readJson(
                response
            );


        if (!response.ok) {

            showMatchError(
                getMessage(
                    data,
                    editing
                        ? "Unable to update match."
                        : "Unable to create match."
                )
            );

            return;
        }


        closeMatchModal();


        await loadMatches();


        await loadStandings();


        showPageSuccess(
            editing
                ? "Match updated successfully."
                : "Match created successfully."
        );

    } catch (error) {

        showMatchError(
            "Unable to save match."
        );

    } finally {

        setMatchSaving(
            false
        );
    }
}


/* =====================================================
   DELETE MATCH
   ===================================================== */

async function deleteMatch(
    match
) {

    const confirmed =
        window.confirm(
            "Delete Match "
            +
            match.matchNumber
            +
            "? This cannot be undone."
        );


    if (!confirmed) {

        return;
    }


    try {

        const response =
            await fetch(
                matchApiBase()
                +
                "/"
                +
                encodeURIComponent(
                    match.id
                ),
                {
                    method:
                        "DELETE",

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


        if (!response.ok) {

            showPageError(
                getMessage(
                    data,
                    "Unable to delete match."
                )
            );

            return;
        }


        await loadMatches();


        await loadStandings();


        showPageSuccess(
            "Match deleted successfully."
        );

    } catch (error) {

        showPageError(
            "Unable to delete match."
        );
    }
}


/* =====================================================
   MATCH FORM HELPERS
   ===================================================== */

function setMatchSaving(
    saving
) {

    if (!saveMatchButton) {

        return;
    }


    saveMatchButton.disabled =
        saving;


    saveMatchButton.textContent =
        saving
            ? "Saving..."
            : (
                editingMatchId
                    ? "Save Changes"
                    : "Create Match"
            );
}


function clearMatchMessage() {

    const element =
        document.getElementById(
            "matchMessage"
        );


    if (!element) {

        return;
    }


    element.textContent =
        "";


    element.className =
        "form-message";
}


function showMatchError(
    message
) {

    const element =
        document.getElementById(
            "matchMessage"
        );


    if (!element) {

        return;
    }


    element.textContent =
        message;


    element.className =
        "form-message error";
}


function nextMatchNumber() {

    if (
        currentMatches.length ===
        0
    ) {

        return 1;
    }


    return Math.max(
        ...currentMatches.map(
            match =>
                Number(
                    match.matchNumber || 0
                )
        )
    )
    +
    1;
}


/* =====================================================
   EDIT TOURNAMENT EVENTS
   ===================================================== */

const editTournamentButton =
    document.getElementById(
        "editTournamentButton"
    );


const closeEditButton =
    document.getElementById(
        "closeEditButton"
    );


const cancelEditButton =
    document.getElementById(
        "cancelEditButton"
    );


const editTournamentForm =
    document.getElementById(
        "editTournamentForm"
    );


const deleteTournamentButton =
    document.getElementById(
        "deleteTournamentButton"
    );


const editModal =
    document.getElementById(
        "editModal"
    );


if (editTournamentButton) {

    editTournamentButton.addEventListener(
        "click",
        openEditModal
    );
}


if (closeEditButton) {

    closeEditButton.addEventListener(
        "click",
        closeEditModal
    );
}


if (cancelEditButton) {

    cancelEditButton.addEventListener(
        "click",
        closeEditModal
    );
}


if (editTournamentForm) {

    editTournamentForm.addEventListener(
        "submit",
        saveTournament
    );
}


if (deleteTournamentButton) {

    deleteTournamentButton.addEventListener(
        "click",
        deleteTournament
    );
}


if (editModal) {

    editModal.addEventListener(
        "click",
        function (event) {

            if (
                event.target ===
                editModal
            ) {

                closeEditModal();
            }
        }
    );
}


/* =====================================================
   ESCAPE KEY
   ===================================================== */

document.addEventListener(
    "keydown",
    function (event) {

        if (
            event.key !==
            "Escape"
        ) {

            return;
        }


        if (
            matchModal
            &&
            !matchModal.classList.contains(
                "hidden"
            )
        ) {

            closeMatchModal();

            return;
        }


        if (
            editModal
            &&
            !editModal.classList.contains(
                "hidden"
            )
        ) {

            closeEditModal();
        }
    }
);


/* =====================================================
   EDIT TOURNAMENT MODAL
   ===================================================== */

function openEditModal() {

    if (
        !currentTournament ||
        !currentTournament.canEdit
    ) {

        return;
    }


    setInputValue(
        "editName",
        currentTournament.name
    );


    setInputValue(
        "editGameType",
        currentTournament.gameType ||
        "PICKLEBALL"
    );


    setInputValue(
        "editFormat",
        currentTournament.format
    );


    setInputValue(
        "editStatus",
        currentTournament.status ||
        "REGISTRATION_OPEN"
    );


    setInputValue(
        "editScheduledAt",
        toLocalDateTimeInput(
            currentTournament.scheduledAt
        )
    );


    setInputValue(
        "editLocation",
        currentTournament.location
    );


    setInputValue(
        "editDescription",
        currentTournament.description
    );


    clearEditMessage();


    editModal.classList.remove(
        "hidden"
    );


    document.body.style.overflow =
        "hidden";
}


function closeEditModal() {

    editModal.classList.add(
        "hidden"
    );


    document.body.style.overflow =
        "";
}


/* =====================================================
   SAVE TOURNAMENT
   ===================================================== */

async function saveTournament(
    event
) {

    event.preventDefault();


    clearEditMessage();


    const name =
        valueOf(
            "editName"
        )
            .trim()
            .replace(
                /\s+/g,
                " "
            );


    if (
        name.length < 3
    ) {

        showEditError(
            "Tournament name must be at least 3 characters."
        );

        return;
    }


    let scheduledAt =
        null;


    const scheduledValue =
        valueOf(
            "editScheduledAt"
        );


    if (scheduledValue) {

        const date =
            new Date(
                scheduledValue
            );


        if (
            Number.isNaN(
                date.getTime()
            )
        ) {

            showEditError(
                "Please enter a valid date."
            );

            return;
        }


        scheduledAt =
            date.toISOString();
    }


    const saveButton =
        document.getElementById(
            "saveEditButton"
        );


    saveButton.disabled =
        true;


    saveButton.textContent =
        "Saving...";


    try {

        const response =
            await fetch(
                "/api/tournaments/"
                +
                encodeURIComponent(
                    currentTournament.id
                ),
                {
                    method:
                        "PATCH",

                    credentials:
                        "same-origin",

                    headers: {
                        "Content-Type":
                            "application/json",

                        "Accept":
                            "application/json"
                    },

                    body:
                        JSON.stringify({

                            name:
                                name,

                            gameType:
                                valueOf(
                                    "editGameType"
                                ),

                            format:
                                valueOf(
                                    "editFormat"
                                ),

                            status:
                                valueOf(
                                    "editStatus"
                                ),

                            scheduledAt:
                                scheduledAt,

                            location:
                                valueOf(
                                    "editLocation"
                                )
                                    .trim(),

                            description:
                                valueOf(
                                    "editDescription"
                                )
                                    .trim()
                        })
                }
            );


        const data =
            await readJson(
                response
            );


        if (!response.ok) {

            showEditError(
                getMessage(
                    data,
                    "Unable to update tournament."
                )
            );

            return;
        }


        currentTournament =
            data.tournament;


        renderTournament();


        renderStandings();


        closeEditModal();


        showPageSuccess(
            "Tournament updated successfully."
        );

    } catch (error) {

        showEditError(
            "Unable to update tournament."
        );

    } finally {

        saveButton.disabled =
            false;


        saveButton.textContent =
            "Save Changes";
    }
}


/* =====================================================
   DELETE TOURNAMENT
   ===================================================== */

async function deleteTournament() {

    if (
        !currentTournament ||
        !currentTournament.canDelete
    ) {

        return;
    }


    const confirmed =
        window.confirm(
            "Delete this tournament? This cannot be undone."
        );


    if (!confirmed) {

        return;
    }


    try {

        const response =
            await fetch(
                "/api/tournaments/"
                +
                encodeURIComponent(
                    currentTournament.id
                ),
                {
                    method:
                        "DELETE",

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


        if (!response.ok) {

            showPageError(
                getMessage(
                    data,
                    "Unable to delete tournament."
                )
            );

            return;
        }


        window.location.replace(
            "/tournaments.html"
        );

    } catch (error) {

        showPageError(
            "Unable to delete tournament."
        );
    }
}


/* =====================================================
   EDIT MESSAGE
   ===================================================== */

function clearEditMessage() {

    const element =
        document.getElementById(
            "editMessage"
        );


    if (!element) {

        return;
    }


    element.textContent =
        "";


    element.className =
        "form-message";
}


function showEditError(
    message
) {

    const element =
        document.getElementById(
            "editMessage"
        );


    if (!element) {

        return;
    }


    element.textContent =
        message;


    element.className =
        "form-message error";
}


/* =====================================================
   PAGE MESSAGES
   ===================================================== */

function showPageError(
    message
) {

    const element =
        document.getElementById(
            "pageMessage"
        );


    if (!element) {

        return;
    }


    element.textContent =
        message;


    element.className =
        "page-message error";
}


function showPageSuccess(
    message
) {

    const element =
        document.getElementById(
            "pageMessage"
        );


    if (!element) {

        return;
    }


    element.textContent =
        message;


    element.className =
        "page-message success";


    setTimeout(
        function () {

            element.textContent =
                "";


            element.className =
                "page-message";
        },
        3000
    );
}


/* =====================================================
   GENERIC HELPERS
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
            value ?? "";
    }
}


function toggleHidden(
    id,
    hidden
) {

    const element =
        document.getElementById(
            id
        );


    if (element) {

        element.classList.toggle(
            "hidden",
            hidden
        );
    }
}


function setInputValue(
    id,
    value
) {

    const element =
        document.getElementById(
            id
        );


    if (element) {

        element.value =
            value ?? "";
    }
}


function setSelectValue(
    id,
    value
) {

    const element =
        document.getElementById(
            id
        );


    if (element) {

        element.value =
            value ?? "";
    }
}


function valueOf(
    id
) {

    const element =
        document.getElementById(
            id
        );


    return element
        ? element.value
        : "";
}


function teamName(
    player1,
    player2
) {

    const names =
        [];


    if (
        player1 &&
        player1.name
    ) {

        names.push(
            formatDisplayName(
                player1.name
            )
        );
    }


    if (
        player2 &&
        player2.name
    ) {

        names.push(
            formatDisplayName(
                player2.name
            )
        );
    }


    return names.length
        ? names.join(
            " / "
        )
        : "Unknown";
}


function statusClass(
    status
) {

    switch (
        String(
            status || ""
        ).toUpperCase()
    ) {

        case "COMPLETED":
            return "completed";

        case "IN_PROGRESS":
            return "in-progress";

        case "CANCELLED":
            return "cancelled";

        default:
            return "";
    }
}


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


function formatDisplayName(
    value
) {

    if (
        typeof value !==
        "string"
        ||
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
                part.slice(1)
        )
        .join(
            " "
        );
}


function getInitials(
    value
) {

    if (
        typeof value !==
        "string"
        ||
        !value.trim()
    ) {

        return "U";
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


/* =====================================================
   DATE
   ===================================================== */

function formatDateTime(
    value
) {

    if (!value) {

        return "Not set";
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

        return "Not set";
    }


    return new Intl.DateTimeFormat(
        undefined,
        {
            month:
                "short",

            day:
                "numeric",

            year:
                "numeric",

            hour:
                "numeric",

            minute:
                "2-digit"
        }
    )
        .format(
            date
        );
}


function toLocalDateTimeInput(
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


    const localDate =
        new Date(
            date.getTime()
            -
            date.getTimezoneOffset()
            *
            60000
        );


    return localDate
        .toISOString()
        .slice(
            0,
            16
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


function getMessage(
    data,
    fallback
) {

    if (
        data
        &&
        typeof data.message ===
        "string"
        &&
        data.message.trim()
    ) {

        return data.message.trim();
    }


    return fallback;
}


/* =====================================================
   CLOCK
   ===================================================== */

function updateDateTime() {

    const now =
        new Date();


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


    const timezone =
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
        timezone
            ? timezone.value
            : "";
}