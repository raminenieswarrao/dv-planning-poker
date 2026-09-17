"use strict";


document.addEventListener(
    "DOMContentLoaded",
    initializeAuthNavigation
);


window.addEventListener(
    "dv-profile-updated",
    refreshNavigationAfterProfileUpdate
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


    let nav =
        document.getElementById(
            "authNavigation"
        );


    if (!nav) {

        nav =
            document.createElement(
                "div"
            );

        nav.id =
            "authNavigation";

        nav.className =
            "auth-nav";


        if (clock &&
                clock.parentNode === header) {

            header.insertBefore(
                nav,
                clock
            );

        } else {

            header.appendChild(
                nav
            );
        }
    }


    showNavigationLoading(
        nav
    );


    await refreshAuthNavigation(
        nav
    );
}


/* =====================================================
   PROFILE UPDATE
   ===================================================== */

async function refreshNavigationAfterProfileUpdate() {

    const nav =
        document.getElementById(
            "authNavigation"
        );


    if (!nav) {
        return;
    }


    showNavigationLoading(
        nav
    );


    await refreshAuthNavigation(
        nav
    );
}


function showNavigationLoading(
    nav
) {

    nav.innerHTML =
        "";


    const loading =
        document.createElement(
            "div"
        );


    loading.className =
        "auth-nav-loading";


    nav.appendChild(
        loading
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

                    cache:
                        "no-store",

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


        if (data &&
                data.authenticated === true &&
                data.user) {

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
        createNavigationLink(
            "Login",
            "/login.html",
            "auth-nav-link auth-login-link"
        );


    const signup =
        createNavigationLink(
            "Sign Up",
            "/signup.html",
            "auth-nav-link auth-signup-link"
        );


    nav.append(
        login,
        signup
    );
}


function createNavigationLink(
    text,
    href,
    className
) {

    const link =
        document.createElement(
            "a"
        );


    link.href =
        href;

    link.className =
        className;

    link.textContent =
        text;


    return link;
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
        stringValue(
            user?.email
        );


    const profileName =
        stringValue(
            profile?.name
        );


    const role =
        stringValue(
            profile?.role
        )
            .toUpperCase() ||
        "USER";


    const avatarUrl =
        safeAvatarUrl(
            profile?.avatarUrl
        );


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


    button.appendChild(
        createAccountAvatar(
            displayName,
            avatarUrl
        )
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

    chevron.setAttribute(
        "aria-hidden",
        "true"
    );


    button.append(
        accountText,
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
        event => {

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


    wrapper.append(
        button,
        menu
    );


    nav.appendChild(
        wrapper
    );


    document.addEventListener(
        "click",
        event => {

            if (wrapper.isConnected &&
                    !wrapper.contains(
                        event.target
                    )) {

                closeAccountMenu(
                    menu,
                    button
                );
            }
        }
    );


    document.addEventListener(
        "keydown",
        event => {

            if (wrapper.isConnected &&
                    event.key === "Escape") {

                closeAccountMenu(
                    menu,
                    button
                );
            }
        }
    );
}


/* =====================================================
   ACCOUNT AVATAR
   ===================================================== */

function createAccountAvatar(
    displayName,
    avatarUrl
) {

    const avatar =
        document.createElement(
            "span"
        );


    avatar.className =
        "auth-avatar";


    const initials =
        document.createElement(
            "span"
        );


    initials.className =
        "auth-avatar-initials";


    initials.textContent =
        getInitials(
            displayName
        );


    avatar.appendChild(
        initials
    );


    if (!avatarUrl) {

        return avatar;
    }


    const image =
        document.createElement(
            "img"
        );


    image.className =
        "auth-avatar-image";


    image.alt =
        "";


    image.src =
        avatarUrl;


    image.addEventListener(
        "load",
        () => {

            initials.classList.add(
                "hidden"
            );

            image.classList.add(
                "loaded"
            );
        }
    );


    image.addEventListener(
        "error",
        () => {

            image.remove();

            initials.classList.remove(
                "hidden"
            );
        }
    );


    avatar.appendChild(
        image
    );


    return avatar;
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
        role === "ADMIN"
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


    userInfo.append(
        label,
        nameElement,
        roleElement,
        emailElement
    );


    const profile =
        createMenuLink(
            "Profile",
            "/profile.html"
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


    menu.append(
        userInfo,
        profile,
        tournaments,
        password,
        logout
    );


    return menu;
}


function createMenuLink(
    text,
    href
) {

    const link =
        document.createElement(
            "a"
        );


    link.className =
        "auth-menu-item";

    link.href =
        href;

    link.textContent =
        text;


    return link;
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
         * Redirect regardless so the application can recover.
         */
    }


    window.location.replace(
        "/"
    );
}


/* =====================================================
   HELPERS
   ===================================================== */

function safeAvatarUrl(
    value
) {

    const normalized =
        stringValue(
            value
        );


    if (!normalized) {
        return "";
    }


    try {

        const url =
            new URL(
                normalized,
                window.location.origin
            );


        if (url.protocol !== "http:" &&
                url.protocol !== "https:") {

            return "";
        }


        return url.href;

    } catch (error) {

        return "";
    }
}


function stringValue(
    value
) {

    return typeof value === "string"
        ? value.trim()
        : "";
}


function formatDisplayName(
    value
) {

    const normalized =
        stringValue(
            value
        );


    if (!normalized) {
        return "Account";
    }


    return normalized
        .toLowerCase()
        .split(
            /\s+/
        )
        .map(
            part =>
                part.charAt(
                    0
                ).toUpperCase()
                +
                part.slice(
                    1
                )
        )
        .join(
            " "
        );
}


function createDisplayNameFromEmail(
    email
) {

    const localPart =
        stringValue(
            email
        )
            .split(
                "@"
            )[0]
            .trim();


    const cleaned =
        localPart
            .replace(
                /[._-]+/g,
                " "
            )
            .trim();


    return cleaned ||
        "Account";
}


function getInitials(
    value
) {

    const normalized =
        stringValue(
            value
        );


    if (!normalized) {
        return "DV";
    }


    const parts =
        normalized.split(
            /\s+/
        );


    if (parts.length === 1) {

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
        parts[parts.length - 1][0]
    ).toUpperCase();
}