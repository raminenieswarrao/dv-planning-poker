"use strict";


document.addEventListener(
    "DOMContentLoaded",
    initializeAuthNavigation
);


/* =====================================================
   INITIALIZE
   ===================================================== */

async function initializeAuthNavigation() {

    const header =
        document.querySelector(
            ".header"
        );


    const clock =
        document.querySelector(
            ".header-clock"
        );


    if (!header) {
        return;
    }


    header.classList.add(
        "auth-header-ready"
    );


    const nav =
        document.createElement(
            "div"
        );


    nav.id =
        "authNavigation";


    nav.className =
        "auth-nav";


    const loading =
        document.createElement(
            "div"
        );


    loading.className =
        "auth-nav-loading";


    nav.appendChild(
        loading
    );


    if (
        clock &&
        clock.parentNode === header
    ) {

        header.insertBefore(
            nav,
            clock
        );

    } else {

        header.appendChild(
            nav
        );
    }


    await refreshAuthNavigation(
        nav
    );
}


/* =====================================================
   SESSION
   ===================================================== */

async function refreshAuthNavigation(
    nav
) {

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

            renderGuestNavigation(
                nav
            );

            return;
        }


        const data =
            await response.json();


        if (
            data &&
            data.authenticated === true &&
            data.user
        ) {

            renderAuthenticatedNavigation(
                nav,
                data.user,
                data.profile
            );

        } else {

            renderGuestNavigation(
                nav
            );
        }

    } catch (error) {

        /*
         * Planning Poker must remain available even if
         * authentication/profile loading temporarily fails.
         */
        renderGuestNavigation(
            nav
        );
    }
}


/* =====================================================
   GUEST
   ===================================================== */

function renderGuestNavigation(
    nav
) {

    nav.innerHTML =
        "";


    const login =
        document.createElement(
            "a"
        );


    login.href =
        "/login.html";


    login.className =
        "auth-nav-link auth-login-link";


    login.textContent =
        "Login";


    const signup =
        document.createElement(
            "a"
        );


    signup.href =
        "/signup.html";


    signup.className =
        "auth-nav-link auth-signup-link";


    signup.textContent =
        "Sign Up";


    nav.appendChild(
        login
    );


    nav.appendChild(
        signup
    );
}


/* =====================================================
   AUTHENTICATED
   ===================================================== */

function renderAuthenticatedNavigation(
    nav,
    user,
    profile
) {

    nav.innerHTML =
        "";


    const email =
        user &&
        typeof user.email ===
        "string"
            ? user.email
            : "";


    const profileName =
        profile &&
        typeof profile.name ===
        "string" &&
        profile.name.trim()
            ? profile.name.trim()
            : "";


    const role =
        profile &&
        typeof profile.role ===
        "string"
            ? profile.role.trim().toUpperCase()
            : "USER";


    const displayName =
        profileName ||
        createDisplayNameFromEmail(
            email
        );


    const wrapper =
        document.createElement(
            "div"
        );


    wrapper.className =
        "auth-account";


    const button =
        document.createElement(
            "button"
        );


    button.type =
        "button";


    button.className =
        "auth-account-button";


    button.setAttribute(
        "aria-haspopup",
        "true"
    );


    button.setAttribute(
        "aria-expanded",
        "false"
    );


    const avatar =
        document.createElement(
            "span"
        );


    avatar.className =
        "auth-avatar";


    avatar.textContent =
        getInitials(
            displayName
        );


    const accountText =
        document.createElement(
            "span"
        );


    accountText.className =
        "auth-account-text";


    accountText.textContent =
        formatDisplayName(
            displayName
        );


    const chevron =
        document.createElement(
            "span"
        );


    chevron.className =
        "auth-chevron";


    chevron.textContent =
        "▼";


    button.appendChild(
        avatar
    );


    button.appendChild(
        accountText
    );


    button.appendChild(
        chevron
    );


    const menu =
        buildAccountMenu(
            displayName,
            email,
            role
        );


    button.addEventListener(
        "click",
        function (event) {

            event.stopPropagation();


            const opening =
                menu.classList.contains(
                    "hidden"
                );


            menu.classList.toggle(
                "hidden"
            );


            button.setAttribute(
                "aria-expanded",
                opening
                    ? "true"
                    : "false"
            );
        }
    );


    wrapper.appendChild(
        button
    );


    wrapper.appendChild(
        menu
    );


    nav.appendChild(
        wrapper
    );


    document.addEventListener(
        "click",
        function (event) {

            if (
                !wrapper.contains(
                    event.target
                )
            ) {

                closeAccountMenu(
                    menu,
                    button
                );
            }
        }
    );


    document.addEventListener(
        "keydown",
        function (event) {

            if (
                event.key ===
                "Escape"
            ) {

                closeAccountMenu(
                    menu,
                    button
                );
            }
        }
    );
}


/* =====================================================
   ACCOUNT MENU
   ===================================================== */

function buildAccountMenu(
    displayName,
    email,
    role
) {

    const menu =
        document.createElement(
            "div"
        );


    menu.className =
        "auth-menu hidden";


    const userInfo =
        document.createElement(
            "div"
        );


    userInfo.className =
        "auth-menu-user";


    const label =
        document.createElement(
            "span"
        );


    label.className =
        "auth-menu-label";


    label.textContent =
        "Signed in as";


    const nameElement =
        document.createElement(
            "span"
        );


    nameElement.className =
        "auth-menu-name";


    nameElement.textContent =
        formatDisplayName(
            displayName
        );


    const roleElement =
        document.createElement(
            "span"
        );


    roleElement.className =
        "auth-role-badge";


    roleElement.textContent =
        role ===
        "ADMIN"
            ? "ADMIN"
            : "USER";


    const emailElement =
        document.createElement(
            "span"
        );


    emailElement.className =
        "auth-menu-email";


    emailElement.textContent =
        email ||
        "Authenticated user";


    userInfo.appendChild(
        label
    );


    userInfo.appendChild(
        nameElement
    );


    userInfo.appendChild(
        roleElement
    );


    userInfo.appendChild(
        emailElement
    );


    const profile =
        createDisabledMenuItem(
            "Profile"
        );


    const tournaments =
        createDisabledMenuItem(
            "My Tournaments"
        );


    const password =
        createDisabledMenuItem(
            "Change Password"
        );


    const logout =
        document.createElement(
            "button"
        );


    logout.type =
        "button";


    logout.className =
        "auth-menu-item logout";


    logout.textContent =
        "Logout";


    logout.addEventListener(
        "click",
        logoutCurrentUser
    );


    menu.appendChild(
        userInfo
    );


    menu.appendChild(
        profile
    );


    menu.appendChild(
        tournaments
    );


    menu.appendChild(
        password
    );


    menu.appendChild(
        logout
    );


    return menu;
}


function createDisabledMenuItem(
    text
) {

    const item =
        document.createElement(
            "div"
        );


    item.className =
        "auth-menu-item disabled";


    item.textContent =
        text;


    return item;
}


function closeAccountMenu(
    menu,
    button
) {

    menu.classList.add(
        "hidden"
    );


    button.setAttribute(
        "aria-expanded",
        "false"
    );
}


/* =====================================================
   LOGOUT
   ===================================================== */

async function logoutCurrentUser() {

    try {

        await fetch(
            "/api/auth/logout",
            {
                method:
                    "POST",

                credentials:
                    "same-origin",

                headers: {
                    "Accept":
                        "application/json"
                }
            }
        );

    } catch (error) {

        /*
         * Refreshing the page still allows us to recover
         * if the request itself failed.
         */
    }


    window.location.replace(
        "/"
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

        return "Account";
    }


    /*
     * Profiles may currently contain uppercase names.
     *
     * ESWAR RAO RAMINENI
     *
     * becomes:
     *
     * Eswar Rao Ramineni
     */
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


function createDisplayNameFromEmail(
    email
) {

    if (
        typeof email !==
        "string"
    ) {

        return "Account";
    }


    const localPart =
        email
            .split(
                "@"
            )[0]
            .trim();


    if (!localPart) {

        return "Account";
    }


    const cleaned =
        localPart
            .replace(
                /[._-]+/g,
                " "
            )
            .trim();


    if (!cleaned) {

        return "Account";
    }


    return cleaned;
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