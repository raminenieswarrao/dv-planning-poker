"use strict";


const signupForm =
    document.getElementById(
        "signupForm"
    );

const nameInput =
    document.getElementById(
        "name"
    );

const emailInput =
    document.getElementById(
        "email"
    );

const genderInput =
    document.getElementById(
        "gender"
    );

const passwordInput =
    document.getElementById(
        "password"
    );

const confirmPasswordInput =
    document.getElementById(
        "confirmPassword"
    );

const signupButton =
    document.getElementById(
        "signupButton"
    );

const signupMessage =
    document.getElementById(
        "signupMessage"
    );

const togglePassword =
    document.getElementById(
        "togglePassword"
    );

const toggleConfirmPassword =
    document.getElementById(
        "toggleConfirmPassword"
    );


let registrationSucceeded =
    false;


/* =====================================================
   CHECK EXISTING SESSION
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
         * Signup must remain usable even when
         * session checking temporarily fails.
         */
    }
}


/* =====================================================
   PASSWORD VISIBILITY
   ===================================================== */

configurePasswordToggle(
    togglePassword,
    passwordInput
);


configurePasswordToggle(
    toggleConfirmPassword,
    confirmPasswordInput
);


function configurePasswordToggle(
    button,
    input
) {

    if (
        !button ||
        !input
    ) {

        return;
    }


    button.addEventListener(
        "click",
        function () {

            const currentlyHidden =
                input.type ===
                "password";


            input.type =
                currentlyHidden
                    ? "text"
                    : "password";


            button.textContent =
                currentlyHidden
                    ? "Hide"
                    : "Show";


            button.setAttribute(
                "aria-label",
                currentlyHidden
                    ? "Hide password"
                    : "Show password"
            );
        }
    );
}


/* =====================================================
   SIGNUP
   ===================================================== */

signupForm.addEventListener(
    "submit",
    async function (event) {

        /*
         * CRITICAL:
         *
         * Prevent normal browser form submission.
         *
         * Without this, form values including passwords
         * could be submitted through normal navigation.
         */
        event.preventDefault();


        if (registrationSucceeded) {
            return;
        }


        clearMessage();


        const name =
            nameInput
                .value
                .trim()
                .replace(
                    /\s+/g,
                    " "
                );


        const email =
            emailInput
                .value
                .trim()
                .toLowerCase();


        const gender =
            genderInput.value;


        const password =
            passwordInput.value;


        const confirmPassword =
            confirmPasswordInput.value;


        /* =================================================
           CLIENT VALIDATION
           ================================================= */

        if (
            name.length < 3
        ) {

            showError(
                "Name must be at least 3 characters."
            );

            nameInput.focus();

            return;
        }


        if (
            name.length > 100
        ) {

            showError(
                "Name must be 100 characters or fewer."
            );

            nameInput.focus();

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


        if (!gender) {

            showError(
                "Please select a gender."
            );

            genderInput.focus();

            return;
        }


        if (
            password.length < 8
        ) {

            showError(
                "Password must be at least 8 characters."
            );

            passwordInput.focus();

            return;
        }


        if (
            password.length > 128
        ) {

            showError(
                "Password is too long."
            );

            passwordInput.focus();

            return;
        }


        if (
            password !==
            confirmPassword
        ) {

            showError(
                "Passwords do not match."
            );

            confirmPasswordInput.focus();

            return;
        }


        setLoading(
            true
        );


        try {

            const response =
                await fetch(
                    "/api/auth/register",
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

                                email:
                                    email,

                                gender:
                                    gender,

                                password:
                                    password,

                                confirmPassword:
                                    confirmPassword
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
                        "Unable to create account."
                    )
                );

                return;
            }


            /*
             * Registration succeeded.
             *
             * Prevent additional submissions while redirecting.
             */
            registrationSucceeded =
                true;


            /*
             * Remove passwords from the page immediately.
             */
            passwordInput.value =
                "";

            confirmPasswordInput.value =
                "";


            /*
             * CASE 1:
             *
             * Supabase email confirmation is enabled.
             */
            if (
                data &&
                data.emailConfirmationRequired ===
                true
            ) {

                showSuccess(
                    "Account created. Please confirm your email before signing in."
                );


                signupButton.textContent =
                    "Account Created";


                setTimeout(
                    function () {

                        window.location.replace(
                            "/login.html"
                        );
                    },
                    3000
                );


                return;
            }


            /*
             * CASE 2:
             *
             * Supabase returned an authenticated session.
             *
             * AuthController already stored the tokens
             * in HttpOnly cookies.
             */
            showSuccess(
                "Account created successfully. Redirecting..."
            );


            signupButton.textContent =
                "Account Created";


            setTimeout(
                function () {

                    window.location.replace(
                        "/dashboard.html"
                    );
                },
                800
            );

        } catch (error) {

            showError(
                "Unable to connect to the authentication service. Please try again."
            );

        } finally {

            /*
             * Only re-enable the form when registration failed.
             *
             * On success we are waiting for redirect.
             */
            if (!registrationSucceeded) {

                setLoading(
                    false
                );
            }
        }
    }
);


/* =====================================================
   EMAIL VALIDATION
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


    if (
        atIndex <= 0 ||
        atIndex >=
        email.length - 1
    ) {

        return false;
    }


    /*
     * Reject multiple @ characters.
     */
    if (
        email.indexOf(
            "@",
            atIndex + 1
        ) !== -1
    ) {

        return false;
    }


    const domain =
        email.substring(
            atIndex + 1
        );


    if (
        !domain.includes(
            "."
        )
    ) {

        return false;
    }


    if (
        domain.startsWith(
            "."
        ) ||
        domain.endsWith(
            "."
        )
    ) {

        return false;
    }


    return true;
}


/* =====================================================
   LOADING STATE
   ===================================================== */

function setLoading(
    loading
) {

    signupButton.disabled =
        loading;


    nameInput.disabled =
        loading;


    emailInput.disabled =
        loading;


    genderInput.disabled =
        loading;


    passwordInput.disabled =
        loading;


    confirmPasswordInput.disabled =
        loading;


    togglePassword.disabled =
        loading;


    toggleConfirmPassword.disabled =
        loading;


    signupButton.textContent =
        loading
            ? "Creating Account..."
            : "Create Account";
}


/* =====================================================
   MESSAGES
   ===================================================== */

function clearMessage() {

    signupMessage.textContent =
        "";


    signupMessage.className =
        "form-message";
}


function showError(
    message
) {

    signupMessage.textContent =
        message;


    signupMessage.className =
        "form-message error";
}


function showSuccess(
    message
) {

    signupMessage.textContent =
        message;


    signupMessage.className =
        "form-message success";
}


/* =====================================================
   RESPONSE HELPERS
   ===================================================== */

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
