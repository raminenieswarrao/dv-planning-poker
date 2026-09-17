"use strict";


(() => {

    const PROFILE_ENDPOINT =
        "/api/profile";


    const LOGIN_PAGE =
        "/login.html";


    const DASHBOARD_PAGE =
        "/dashboard.html";


    let currentProfile =
        null;


    let messageTimer =
        null;


    /* =====================================================
       PAGE STARTUP
       ===================================================== */

    document.addEventListener(
        "DOMContentLoaded",
        initializeProfilePage
    );


    async function initializeProfilePage() {

        bindPageEvents();

        startHeaderClock();

        await loadProfile();
    }


    function bindPageEvents() {

        const editButton =
            document.getElementById(
                "editProfileButton"
            );


        const cancelButton =
            document.getElementById(
                "cancelProfileButton"
            );


        const editForm =
            document.getElementById(
                "profileEditForm"
            );


        if (editButton) {

            editButton.addEventListener(
                "click",
                enterEditMode
            );
        }


        if (cancelButton) {

            cancelButton.addEventListener(
                "click",
                cancelEdit
            );
        }


        if (editForm) {

            editForm.addEventListener(
                "submit",
                saveProfile
            );
        }
    }


    /* =====================================================
       LOAD PROFILE
       ===================================================== */

    async function loadProfile() {

        setPageLoading(
            true
        );


        try {

            const response =
                await fetch(
                    PROFILE_ENDPOINT,
                    {
                        method: "GET",

                        headers: {
                            "Accept":
                                "application/json"
                        },

                        credentials:
                            "same-origin",

                        cache:
                            "no-store"
                    }
                );


            if (response.status === 401) {

                redirectToLogin();

                return;
            }


            const result =
                await readJsonResponse(
                    response
                );


            if (!response.ok ||
                    !result ||
                    result.success !== true ||
                    !result.profile) {

                throw new Error(
                    getResponseMessage(
                        result,
                        "Unable to load your profile."
                    )
                );
            }


            currentProfile =
                normalizeProfile(
                    result.profile
                );


            renderProfile(
                currentProfile
            );


            showProfilePage();

        } catch (error) {

            showProfilePage();

            showMessage(
                getErrorMessage(
                    error,
                    "Unable to load your profile."
                ),
                "error",
                false
            );


            disableProfileActions();

        } finally {

            setPageLoading(
                false
            );
        }
    }


    /* =====================================================
       RENDER PROFILE
       ===================================================== */

    function renderProfile(
        profile
    ) {

        setText(
            "profileDisplayName",
            displayValue(
                profile.name,
                "User"
            )
        );


        setText(
            "profileDisplayEmail",
            displayValue(
                profile.email
            )
        );


        setText(
            "profileDisplayRole",
            formatRole(
                profile.role
            )
        );


        setText(
            "viewName",
            displayValue(
                profile.name
            )
        );


        setText(
            "viewEmail",
            displayValue(
                profile.email
            )
        );


        setText(
            "viewGender",
            displayValue(
                profile.gender,
                "Prefer not to say"
            )
        );


        setText(
            "viewRole",
            formatRole(
                profile.role
            )
        );


        setText(
            "viewCreatedAt",
            formatDate(
                profile.createdAt
            )
        );


        setText(
            "viewUpdatedAt",
            formatDate(
                profile.updatedAt
            )
        );


        renderAvatar(
            profile
        );


        populateEditForm(
            profile
        );
    }


    function renderAvatar(
        profile
    ) {

        const image =
            document.getElementById(
                "profileAvatarImage"
            );


        const initials =
            document.getElementById(
                "profileAvatarInitials"
            );


        if (!image ||
                !initials) {

            return;
        }


        const avatarUrl =
            safeAvatarUrl(
                profile.avatarUrl
            );


        if (!avatarUrl) {

            showInitialsAvatar(
                image,
                initials,
                profile.name
            );

            return;
        }


        image.onload =
            () => {

                image.classList.remove(
                    "profile-hidden"
                );

                initials.classList.add(
                    "profile-hidden"
                );
            };


        image.onerror =
            () => {

                image.removeAttribute(
                    "src"
                );

                showInitialsAvatar(
                    image,
                    initials,
                    profile.name
                );
            };


        image.alt =
            profile.name
                ? `${profile.name}'s profile picture`
                : "Profile picture";


        image.src =
            avatarUrl;
    }


    function showInitialsAvatar(
        image,
        initials,
        name
    ) {

        image.classList.add(
            "profile-hidden"
        );


        initials.textContent =
            getInitials(
                name
            );


        initials.classList.remove(
            "profile-hidden"
        );
    }


    function populateEditForm(
        profile
    ) {

        setInputValue(
            "profileName",
            profile.name
        );


        setInputValue(
            "profileEmail",
            profile.email
        );


        setInputValue(
            "profileRole",
            formatRole(
                profile.role
            )
        );


        setGenderValue(
            profile.gender
        );
    }


    function setGenderValue(
        gender
    ) {

        const select =
            document.getElementById(
                "profileGender"
            );


        if (!select) {
            return;
        }


        const normalizedGender =
            stringValue(
                gender
            );


        removeTemporaryGenderOption(
            select
        );


        if (!normalizedGender) {

            select.value =
                "";

            return;
        }


        const matchingOption =
            Array
                .from(
                    select.options
                )
                .find(
                    option =>
                        option.value
                            .toLowerCase() ===
                        normalizedGender
                            .toLowerCase()
                );


        if (matchingOption) {

            select.value =
                matchingOption.value;

            return;
        }


        const customOption =
            document.createElement(
                "option"
            );


        customOption.value =
            normalizedGender;

        customOption.textContent =
            normalizedGender;

        customOption.dataset.temporary =
            "true";


        select.appendChild(
            customOption
        );


        select.value =
            normalizedGender;
    }


    function removeTemporaryGenderOption(
        select
    ) {

        const temporaryOption =
            select.querySelector(
                'option[data-temporary="true"]'
            );


        if (temporaryOption) {

            temporaryOption.remove();
        }
    }


    /* =====================================================
       EDIT MODE
       ===================================================== */

    function enterEditMode() {

        if (!currentProfile) {
            return;
        }


        populateEditForm(
            currentProfile
        );


        clearValidationStyles();


        hideMessage();


        setElementHidden(
            "profileView",
            true
        );


        setElementHidden(
            "profileEditForm",
            false
        );


        const nameInput =
            document.getElementById(
                "profileName"
            );


        if (nameInput) {

            nameInput.focus();

            nameInput.select();
        }
    }


    function cancelEdit() {

        if (currentProfile) {

            populateEditForm(
                currentProfile
            );
        }


        clearValidationStyles();


        hideMessage();


        setElementHidden(
            "profileEditForm",
            true
        );


        setElementHidden(
            "profileView",
            false
        );
    }


    /* =====================================================
       SAVE PROFILE
       ===================================================== */

    async function saveProfile(
        event
    ) {

        event.preventDefault();


        if (!currentProfile) {
            return;
        }


        clearValidationStyles();

        hideMessage();


        const nameInput =
            document.getElementById(
                "profileName"
            );


        const genderSelect =
            document.getElementById(
                "profileGender"
            );


        if (!nameInput ||
                !genderSelect) {

            showMessage(
                "The profile form is unavailable.",
                "error"
            );

            return;
        }


        const name =
            normalizeName(
                nameInput.value
            );


        const gender =
            stringValue(
                genderSelect.value
            );


        const validationMessage =
            validateProfileInput(
                name,
                gender,
                nameInput,
                genderSelect
            );


        if (validationMessage) {

            showMessage(
                validationMessage,
                "error"
            );

            return;
        }


        setSaving(
            true
        );


        try {

            const response =
                await fetch(
                    PROFILE_ENDPOINT,
                    {
                        method: "PUT",

                        headers: {
                            "Accept":
                                "application/json",

                            "Content-Type":
                                "application/json"
                        },

                        credentials:
                            "same-origin",

                        body:
                            JSON.stringify(
                                {
                                    name:
                                        name,

                                    gender:
                                        gender || null
                                }
                            )
                    }
                );


            if (response.status === 401) {

                redirectToLogin();

                return;
            }


            const result =
                await readJsonResponse(
                    response
                );


            if (!response.ok ||
                    !result ||
                    result.success !== true ||
                    !result.profile) {

                throw new Error(
                    getResponseMessage(
                        result,
                        "Unable to update your profile."
                    )
                );
            }


            currentProfile =
                normalizeProfile(
                    result.profile
                );


            renderProfile(
                currentProfile
            );


            setElementHidden(
                "profileEditForm",
                true
            );


            setElementHidden(
                "profileView",
                false
            );


            showMessage(
                getResponseMessage(
                    result,
                    "Profile updated successfully."
                ),
                "success"
            );


            notifyProfileUpdated(
                currentProfile
            );

        } catch (error) {

            showMessage(
                getErrorMessage(
                    error,
                    "Unable to update your profile."
                ),
                "error"
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    function validateProfileInput(
        name,
        gender,
        nameInput,
        genderSelect
    ) {

        if (!name) {

            markInvalid(
                nameInput
            );

            nameInput.focus();

            return "Name is required.";
        }


        if (name.length < 2) {

            markInvalid(
                nameInput
            );

            nameInput.focus();

            return "Name must contain at least 2 characters.";
        }


        if (name.length > 100) {

            markInvalid(
                nameInput
            );

            nameInput.focus();

            return "Name cannot exceed 100 characters.";
        }


        if (gender.length > 30) {

            markInvalid(
                genderSelect
            );

            genderSelect.focus();

            return "Gender cannot exceed 30 characters.";
        }


        return null;
    }


    /* =====================================================
       SAVE STATE
       ===================================================== */

    function setSaving(
        saving
    ) {

        const saveButton =
            document.getElementById(
                "saveProfileButton"
            );


        const cancelButton =
            document.getElementById(
                "cancelProfileButton"
            );


        const buttonText =
            document.getElementById(
                "saveProfileButtonText"
            );


        const spinner =
            document.getElementById(
                "saveProfileSpinner"
            );


        if (saveButton) {

            saveButton.disabled =
                saving;
        }


        if (cancelButton) {

            cancelButton.disabled =
                saving;
        }


        if (buttonText) {

            buttonText.textContent =
                saving
                    ? "Saving..."
                    : "Save Changes";
        }


        if (spinner) {

            spinner.classList.toggle(
                "profile-hidden",
                !saving
            );
        }
    }


    /* =====================================================
       HEADER PROFILE UPDATE
       ===================================================== */

    function notifyProfileUpdated(
        profile
    ) {

        window.dispatchEvent(
            new CustomEvent(
                "dv-profile-updated",
                {
                    detail: {
                        name:
                            profile.name,

                        email:
                            profile.email,

                        role:
                            profile.role,

                        avatarUrl:
                            profile.avatarUrl
                    }
                }
            )
        );
    }


    /* =====================================================
       PAGE DISPLAY
       ===================================================== */

    function setPageLoading(
        loading
    ) {

        setElementHidden(
            "profileLoading",
            !loading
        );


        if (loading) {

            setElementHidden(
                "profileContent",
                true
            );
        }
    }


    function showProfilePage() {

        setElementHidden(
            "profileContent",
            false
        );
    }


    function disableProfileActions() {

        const editButton =
            document.getElementById(
                "editProfileButton"
            );


        if (editButton) {

            editButton.disabled =
                true;
        }
    }


    /* =====================================================
       MESSAGE HANDLING
       ===================================================== */

    function showMessage(
        message,
        type,
        autoHide = true
    ) {

        const messageElement =
            document.getElementById(
                "profileMessage"
            );


        if (!messageElement) {
            return;
        }


        if (messageTimer) {

            window.clearTimeout(
                messageTimer
            );

            messageTimer =
                null;
        }


        messageElement.textContent =
            message;


        messageElement.classList.remove(
            "profile-message-success",
            "profile-message-error",
            "profile-hidden"
        );


        if (type === "success") {

            messageElement.classList.add(
                "profile-message-success"
            );

        } else if (type === "error") {

            messageElement.classList.add(
                "profile-message-error"
            );
        }


        messageElement.scrollIntoView(
            {
                behavior:
                    "smooth",

                block:
                    "nearest"
            }
        );


        if (autoHide) {

            messageTimer =
                window.setTimeout(
                    hideMessage,
                    5000
                );
        }
    }


    function hideMessage() {

        const messageElement =
            document.getElementById(
                "profileMessage"
            );


        if (messageElement) {

            messageElement.classList.add(
                "profile-hidden"
            );
        }


        if (messageTimer) {

            window.clearTimeout(
                messageTimer
            );

            messageTimer =
                null;
        }
    }


    /* =====================================================
       VALIDATION STYLES
       ===================================================== */

    function markInvalid(
        element
    ) {

        if (element) {

            element.classList.add(
                "profile-input-error"
            );
        }
    }


    function clearValidationStyles() {

        document
            .querySelectorAll(
                ".profile-input-error"
            )
            .forEach(
                element => {

                    element.classList.remove(
                        "profile-input-error"
                    );
                }
            );
    }


    /* =====================================================
       PROFILE NORMALIZATION
       ===================================================== */

    function normalizeProfile(
        profile
    ) {

        return {
            id:
                stringValue(
                    profile.id
                ),

            name:
                stringValue(
                    profile.name
                ),

            email:
                stringValue(
                    profile.email
                ),

            gender:
                stringValue(
                    profile.gender
                ),

            avatarUrl:
                stringValue(
                    profile.avatarUrl
                ),

            role:
                stringValue(
                    profile.role
                ) || "USER",

            createdAt:
                stringValue(
                    profile.createdAt
                ),

            updatedAt:
                stringValue(
                    profile.updatedAt
                )
        };
    }


    function normalizeName(
        value
    ) {

        return stringValue(
            value
        )
            .replace(
                /\s+/g,
                " "
            );
    }


    function stringValue(
        value
    ) {

        if (typeof value !== "string") {

            return "";
        }


        return value.trim();
    }


    /* =====================================================
       DISPLAY HELPERS
       ===================================================== */

    function displayValue(
        value,
        fallback = "—"
    ) {

        const normalized =
            stringValue(
                value
            );


        return normalized ||
            fallback;
    }


    function formatRole(
        role
    ) {

        return stringValue(
            role
        )
            .toUpperCase() ||
            "USER";
    }


    function getInitials(
        name
    ) {

        const normalized =
            stringValue(
                name
            );


        if (!normalized) {

            return "U";
        }


        const parts =
            normalized
                .split(
                    /\s+/
                )
                .filter(
                    Boolean
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
            parts[0].charAt(
                0
            ) +
            parts[parts.length - 1].charAt(
                0
            )
        ).toUpperCase();
    }


    function formatDate(
        value
    ) {

        const normalized =
            stringValue(
                value
            );


        if (!normalized) {

            return "—";
        }


        const date =
            new Date(
                normalized
            );


        if (Number.isNaN(
                date.getTime()
        )) {

            return "—";
        }


        return new Intl.DateTimeFormat(
            undefined,
            {
                year:
                    "numeric",

                month:
                    "long",

                day:
                    "numeric"
            }
        ).format(
            date
        );
    }


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


            if (url.protocol !== "https:" &&
                    url.protocol !== "http:") {

                return "";
            }


            return url.href;

        } catch (error) {

            return "";
        }
    }


    /* =====================================================
       FETCH HELPERS
       ===================================================== */

    async function readJsonResponse(
        response
    ) {

        const contentType =
            response.headers.get(
                "content-type"
            ) || "";


        if (!contentType
                .toLowerCase()
                .includes(
                    "application/json"
                )) {

            return null;
        }


        try {

            return await response.json();

        } catch (error) {

            return null;
        }
    }


    function getResponseMessage(
        result,
        fallback
    ) {

        if (result &&
                typeof result.message === "string" &&
                result.message.trim()) {

            return result.message.trim();
        }


        return fallback;
    }


    function getErrorMessage(
        error,
        fallback
    ) {

        if (error &&
                typeof error.message === "string" &&
                error.message.trim()) {

            return error.message.trim();
        }


        return fallback;
    }


    /* =====================================================
       HEADER CLOCK
       ===================================================== */

    function startHeaderClock() {

        updateHeaderClock();


        window.setInterval(
            updateHeaderClock,
            1000
        );
    }


    function updateHeaderClock() {

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


        const now =
            new Date();


        if (dateElement) {

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
                ).format(
                    now
                );
        }


        if (timeElement) {

            timeElement.textContent =
                new Intl.DateTimeFormat(
                    undefined,
                    {
                        hour:
                            "numeric",

                        minute:
                            "2-digit",

                        second:
                            "2-digit"
                    }
                ).format(
                    now
                );
        }


        if (timezoneElement) {

            timezoneElement.textContent =
                getTimezoneLabel();
        }
    }


    function getTimezoneLabel() {

        try {

            const timezone =
                Intl
                    .DateTimeFormat()
                    .resolvedOptions()
                    .timeZone;


            return timezone || "";

        } catch (error) {

            return "";
        }
    }


    /* =====================================================
       GENERAL DOM HELPERS
       ===================================================== */

    function setText(
        elementId,
        value
    ) {

        const element =
            document.getElementById(
                elementId
            );


        if (element) {

            element.textContent =
                value;
        }
    }


    function setInputValue(
        elementId,
        value
    ) {

        const element =
            document.getElementById(
                elementId
            );


        if (element) {

            element.value =
                stringValue(
                    value
                );
        }
    }


    function setElementHidden(
        elementId,
        hidden
    ) {

        const element =
            document.getElementById(
                elementId
            );


        if (element) {

            element.classList.toggle(
                "profile-hidden",
                hidden
            );
        }
    }


    function redirectToLogin() {

        const returnPath =
            window.location.pathname +
            window.location.search;


        window.location.replace(
            `${LOGIN_PAGE}?returnTo=${
                encodeURIComponent(
                    returnPath || DASHBOARD_PAGE
                )
            }`
        );
    }

})();