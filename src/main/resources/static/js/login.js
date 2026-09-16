"use strict";


const loginForm =
    document.getElementById(
        "loginForm"
    );

const emailInput =
    document.getElementById(
        "email"
    );

const passwordInput =
    document.getElementById(
        "password"
    );

const loginButton =
    document.getElementById(
        "loginButton"
    );

const loginMessage =
    document.getElementById(
        "loginMessage"
    );

const togglePassword =
    document.getElementById(
        "togglePassword"
    );


/* =====================================================
   INITIAL SESSION CHECK
   ===================================================== */

document.addEventListener(
    "DOMContentLoaded",
    checkExistingSession
);


async function checkExistingSession() {

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
            return;
        }


        const data =
            await response.json();


        if (
            data &&
            data.authenticated === true
        ) {

            window.location.replace(
                "/dashboard.html"
            );
        }

    } catch (error) {

        /*
         * Login page should still work even if
         * session-check temporarily fails.
         */
    }
}


/* =====================================================
   PASSWORD VISIBILITY
   ===================================================== */

togglePassword.addEventListener(
    "click",
    function () {

        const currentlyHidden =
            passwordInput.type ===
            "password";


        passwordInput.type =
            currentlyHidden
                ? "text"
                : "password";


        togglePassword.textContent =
            currentlyHidden
                ? "Hide"
                : "Show";


        togglePassword.setAttribute(
            "aria-label",
            currentlyHidden
                ? "Hide password"
                : "Show password"
        );
    }
);


/* =====================================================
   LOGIN
   ===================================================== */

loginForm.addEventListener(
    "submit",
    async function (event) {

        event.preventDefault();


        clearMessage();


        const email =
            emailInput
                .value
                .trim();


        const password =
            passwordInput.value;


        if (!email) {

            showError(
                "Please enter your email address."
            );

            emailInput.focus();

            return;
        }


        if (!isReasonableEmail(
            email
        )) {

            showError(
                "Please enter a valid email address."
            );

            emailInput.focus();

            return;
        }


        if (!password) {

            showError(
                "Please enter your password."
            );

            passwordInput.focus();

            return;
        }


        setLoading(
            true
        );


        try {

            const response =
                await fetch(
                    "/api/auth/login",
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
                                email:
                                    email,

                                password:
                                    password
                            })
                    }
                );


            const data =
                await readJsonSafely(
                    response
                );


            if (!response.ok) {

                showError(
                    getSafeMessage(
                        data,
                        "Invalid email or password."
                    )
                );

                return;
            }


            showSuccess(
                "Signed in successfully. Redirecting..."
            );


            /*
             * Password no longer needs to remain in the DOM.
             */
            passwordInput.value =
                "";


            setTimeout(
                function () {

                    window.location.replace(
                        "/dashboard.html"
                    );
                },
                550
            );

        } catch (error) {

            showError(
                "Unable to connect to the authentication service. Please try again."
            );

        } finally {

            setLoading(
                false
            );
        }
    }
);


/* =====================================================
   HELPERS
   ===================================================== */

function isReasonableEmail(
    email
) {

    if (
        typeof email !==
        "string"
    ) {

        return false;
    }


    if (
        email.length < 3 ||
        email.length > 254
    ) {

        return false;
    }


    const atIndex =
        email.indexOf(
            "@"
        );


    return atIndex > 0
        &&
        atIndex <
        email.length - 1;
}


function setLoading(
    loading
) {

    loginButton.disabled =
        loading;


    emailInput.disabled =
        loading;


    passwordInput.disabled =
        loading;


    togglePassword.disabled =
        loading;


    loginButton.textContent =
        loading
            ? "Signing In..."
            : "Sign In";
}


function clearMessage() {

    loginMessage.textContent =
        "";


    loginMessage.className =
        "form-message";
}


function showError(
    message
) {

    loginMessage.textContent =
        message;


    loginMessage.className =
        "form-message error";
}


function showSuccess(
    message
) {

    loginMessage.textContent =
        message;


    loginMessage.className =
        "form-message success";
}


function getSafeMessage(
    data,
    fallback
) {

    if (
        !data ||
        typeof data !==
        "object"
    ) {

        return fallback;
    }


    if (
        typeof data.message !==
        "string"
    ) {

        return fallback;
    }


    const message =
        data.message.trim();


    if (
        !message ||
        message.length > 200
    ) {

        return fallback;
    }


    return message;
}


async function readJsonSafely(
    response
) {

    try {

        return await response.json();

    } catch (error) {

        return null;
    }
}