"use strict";


const page =
    document.getElementById(
        "tournamentPage"
    );

const loadingState =
    document.getElementById(
        "loadingState"
    );

const tournamentList =
    document.getElementById(
        "tournamentList"
    );

const tournamentCount =
    document.getElementById(
        "tournamentCount"
    );

const emptyState =
    document.getElementById(
        "emptyState"
    );

const pageMessage =
    document.getElementById(
        "pageMessage"
    );

const modal =
    document.getElementById(
        "createModal"
    );

const openCreateButton =
    document.getElementById(
        "openCreateButton"
    );

const closeCreateButton =
    document.getElementById(
        "closeCreateButton"
    );

const cancelCreateButton =
    document.getElementById(
        "cancelCreateButton"
    );

const createForm =
    document.getElementById(
        "createTournamentForm"
    );

const submitCreateButton =
    document.getElementById(
        "submitCreateButton"
    );

const createMessage =
    document.getElementById(
        "createMessage"
    );


document.addEventListener(
    "DOMContentLoaded",
    initializePage
);


/* =====================================================
   INITIALIZE
   ===================================================== */

async function initializePage() {

    updateDateTime();

    setInterval(
        updateDateTime,
        1000
    );

    const authenticated =
        await verifyAuthentication();

    if (!authenticated) {

        window.location.replace(
            "/login.html"
        );

        return;
    }

    await loadTournaments();

    loadingState.style.display =
        "none";

    page.classList.remove(
        "page-hidden"
    );
}


/* =====================================================
   AUTH
   ===================================================== */

async function verifyAuthentication() {

    try {

        const response =
            await fetch(
                "/api/auth/me",
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

        if (!response.ok) {

            return false;
        }

        const data =
            await response.json();

        return Boolean(
            data &&
            data.authenticated === true
        );

    } catch (error) {

        return false;
    }
}


/* =====================================================
   LOAD TOURNAMENTS
   ===================================================== */

async function loadTournaments() {

    clearPageMessage();

    try {

        const response =
            await fetch(
                "/api/tournaments/ongoing",
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
                    "Unable to load tournaments."
                )
            );

            renderTournaments(
                []
            );

            return;
        }

        const tournaments =
            Array.isArray(
                data.tournaments
            )
                ? data.tournaments
                : [];

        renderTournaments(
            tournaments
        );

    } catch (error) {

        showPageError(
            "Unable to load tournaments."
        );

        renderTournaments(
            []
        );
    }
}


/* =====================================================
   RENDER TOURNAMENTS
   ===================================================== */

function renderTournaments(
    tournaments
) {

    tournamentList.innerHTML =
        "";

    tournamentCount.textContent =
        String(
            tournaments.length
        );

    emptyState.style.display =
        tournaments.length === 0
            ? "block"
            : "none";

    tournaments.forEach(
        tournament => {

            const card =
                document.createElement(
                    "article"
                );

            card.className =
                "tournament-card";


            const game =
                humanize(
                    tournament.gameType
                );

            const format =
                humanize(
                    tournament.format
                );

            const date =
                formatDateTime(
                    tournament.scheduledAt
                );

            const location =
                tournament.location ||
                "Location not set";

            const name =
                tournament.name ||
                "Tournament";

            const status =
                humanize(
                    tournament.status
                );

            const tournamentId =
                tournament.id ||
                "";

            const mediaHtml =
                getGameMediaHtml(
                    tournament.gameType,
                    name
                );


            card.innerHTML = `

                ${mediaHtml}

                <div class="tournament-card-body">

                    <div class="tournament-card-top">

                        <div class="tournament-title-area">

                            <div class="tournament-game">

                                ${escapeHtml(game)}

                                ${
                                    format
                                        ? " • " +
                                          escapeHtml(format)
                                        : ""
                                }

                            </div>

                            <h3 class="tournament-name">

                                ${escapeHtml(name)}

                            </h3>

                        </div>

                        <span class="status-badge">

                            ${escapeHtml(status)}

                        </span>

                    </div>


                    <div class="tournament-meta">

                        <div class="meta-line">

                            <span class="meta-icon">
                                📅
                            </span>

                            <span>
                                ${escapeHtml(date)}
                            </span>

                        </div>

                        <div class="meta-line">

                            <span class="meta-icon">
                                📍
                            </span>

                            <span>
                                ${escapeHtml(location)}
                            </span>

                        </div>

                    </div>


                    <div class="tournament-actions">

                        <a
                            class="view-button"
                            href="/tournament.html?id=${encodeURIComponent(
                                tournamentId
                            )}">

                            View Tournament

                        </a>

                        ${
                            tournament.canEdit
                                ? `
                                    <a
                                        class="edit-button"
                                        href="/tournament.html?id=${encodeURIComponent(
                                            tournamentId
                                        )}&edit=true">

                                        Edit

                                    </a>
                                  `
                                : ""
                        }

                    </div>

                </div>
            `;

            tournamentList.appendChild(
                card
            );
        }
    );
}


/* =====================================================
   GAME IMAGES
   ===================================================== */

function getGameMediaHtml(
    gameType,
    tournamentName
) {

    switch (
        String(
            gameType || ""
        ).toUpperCase()
    ) {

        case "PICKLEBALL":

            return `

                <div class="tournament-media pickleball-media">

                    <picture>

                        <source
                            srcset="/images/games/pickleball/pickleball-logo.avif"
                            type="image/avif">

                        <img
                            class="game-image"
                            src="/images/games/pickleball/pickleball-logo.webp"
                            alt="${escapeHtml(
                                tournamentName
                            )} Pickleball"
                            loading="lazy">

                    </picture>

                    <div class="media-overlay">

                        <span class="game-media-label">
                            PICKLEBALL
                        </span>

                    </div>

                </div>
            `;


        case "PING_PONG":

            return getFallbackMedia(
                "🏓",
                "Ping Pong"
            );


        case "BADMINTON":

            return getFallbackMedia(
                "🏸",
                "Badminton"
            );


        case "BASKETBALL":

            return getFallbackMedia(
                "🏀",
                "Basketball"
            );


        case "CRICKET":

            return getFallbackMedia(
                "🏏",
                "Cricket"
            );


        default:

            return getFallbackMedia(
                "🏆",
                "Tournament"
            );
    }
}


function getFallbackMedia(
    icon,
    label
) {

    return `

        <div class="tournament-media fallback-media">

            <div class="fallback-game-icon">
                ${escapeHtml(icon)}
            </div>

            <div class="media-overlay">

                <span class="game-media-label">
                    ${escapeHtml(
                        label.toUpperCase()
                    )}
                </span>

            </div>

        </div>
    `;
}


/* =====================================================
   CREATE MODAL
   ===================================================== */

openCreateButton.addEventListener(
    "click",
    openCreateModal
);


closeCreateButton.addEventListener(
    "click",
    closeCreateModal
);


cancelCreateButton.addEventListener(
    "click",
    closeCreateModal
);


modal.addEventListener(
    "click",
    function (event) {

        if (
            event.target === modal
        ) {

            closeCreateModal();
        }
    }
);


document.addEventListener(
    "keydown",
    function (event) {

        if (
            event.key ===
            "Escape" &&
            !modal.classList.contains(
                "hidden"
            )
        ) {

            closeCreateModal();
        }
    }
);


function openCreateModal() {

    clearCreateMessage();

    modal.classList.remove(
        "hidden"
    );

    document.body.style.overflow =
        "hidden";

    const nameInput =
        document.getElementById(
            "tournamentName"
        );

    if (nameInput) {

        nameInput.focus();
    }
}


function closeCreateModal() {

    modal.classList.add(
        "hidden"
    );

    document.body.style.overflow =
        "";

    createForm.reset();

    clearCreateMessage();
}


/* =====================================================
   CREATE TOURNAMENT
   ===================================================== */

createForm.addEventListener(
    "submit",
    async function (event) {

        event.preventDefault();

        clearCreateMessage();


        const name =
            document
                .getElementById(
                    "tournamentName"
                )
                .value
                .trim()
                .replace(
                    /\s+/g,
                    " "
                );


        const gameType =
            document
                .getElementById(
                    "gameType"
                )
                .value;


        const format =
            document
                .getElementById(
                    "tournamentFormat"
                )
                .value;


        const scheduledValue =
            document
                .getElementById(
                    "scheduledAt"
                )
                .value;


        const location =
            document
                .getElementById(
                    "location"
                )
                .value
                .trim()
                .replace(
                    /\s+/g,
                    " "
                );


        const description =
            document
                .getElementById(
                    "description"
                )
                .value
                .trim();


        if (
            name.length < 3
        ) {

            showCreateError(
                "Tournament name must be at least 3 characters."
            );

            return;
        }


        if (
            name.length > 150
        ) {

            showCreateError(
                "Tournament name must be 150 characters or fewer."
            );

            return;
        }


        if (!gameType) {

            showCreateError(
                "Please select a game."
            );

            return;
        }


        let scheduledAt =
            null;


        if (scheduledValue) {

            const parsed =
                new Date(
                    scheduledValue
                );

            if (
                Number.isNaN(
                    parsed.getTime()
                )
            ) {

                showCreateError(
                    "Please enter a valid tournament date."
                );

                return;
            }

            scheduledAt =
                parsed.toISOString();
        }


        setCreateLoading(
            true
        );


        try {

            const response =
                await fetch(
                    "/api/tournaments",
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
                                name:
                                    name,

                                gameType:
                                    gameType,

                                format:
                                    format ||
                                    null,

                                description:
                                    description ||
                                    null,

                                location:
                                    location ||
                                    null,

                                scheduledAt:
                                    scheduledAt,

                                registrationEndAt:
                                    null,

                                status:
                                    "REGISTRATION_OPEN"
                            })
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

                showCreateError(
                    getMessage(
                        data,
                        "Unable to create tournament."
                    )
                );

                return;
            }


            closeCreateModal();

            await loadTournaments();

            showPageSuccess(
                "Tournament created successfully."
            );

        } catch (error) {

            showCreateError(
                "Unable to create tournament."
            );

        } finally {

            setCreateLoading(
                false
            );
        }
    }
);


/* =====================================================
   CREATE FORM STATE
   ===================================================== */

function setCreateLoading(
    loading
) {

    submitCreateButton.disabled =
        loading;

    const controls =
        createForm.querySelectorAll(
            "input, select, textarea"
        );

    controls.forEach(
        control => {

            control.disabled =
                loading;
        }
    );

    submitCreateButton.textContent =
        loading
            ? "Creating..."
            : "Create Tournament";

    cancelCreateButton.disabled =
        loading;

    closeCreateButton.disabled =
        loading;
}


/* =====================================================
   CREATE MESSAGE
   ===================================================== */

function clearCreateMessage() {

    createMessage.textContent =
        "";

    createMessage.className =
        "form-message";
}


function showCreateError(
    message
) {

    createMessage.textContent =
        message;

    createMessage.className =
        "form-message error";
}


/* =====================================================
   PAGE MESSAGES
   ===================================================== */

function clearPageMessage() {

    pageMessage.textContent =
        "";

    pageMessage.className =
        "page-message";
}


function showPageError(
    message
) {

    pageMessage.textContent =
        message;

    pageMessage.className =
        "page-message error";
}


function showPageSuccess(
    message
) {

    pageMessage.textContent =
        message;

    pageMessage.className =
        "page-message success";

    setTimeout(
        function () {

            if (
                pageMessage.classList.contains(
                    "success"
                )
            ) {

                clearPageMessage();
            }
        },
        3500
    );
}


/* =====================================================
   HELPERS
   ===================================================== */

function humanize(
    value
) {

    if (
        typeof value !==
        "string" ||
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


function formatDateTime(
    value
) {

    if (!value) {

        return "Date not set";
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

        return "Date not set";
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


function escapeHtml(
    value
) {

    const element =
        document.createElement(
            "div"
        );

    element.textContent =
        String(
            value ?? ""
        );

    return element.innerHTML;
}


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
        data &&
        typeof data.message ===
        "string"
    ) {

        const message =
            data.message.trim();

        if (
            message &&
            message.length <= 250
        ) {

            return message;
        }
    }

    return fallback;
}


/* =====================================================
   CLOCK
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