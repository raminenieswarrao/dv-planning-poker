"use strict";


(() => {

    const PROFILE_ENDPOINT =
        "/api/profile";

    const AVATARS_ENDPOINT =
        "/api/profile/avatars";

    const AVATAR_ENDPOINT =
        "/api/profile/avatar";


    let availableAvatars =
        [];

    let selectedAvatarId =
        "";

    let currentAvatarUrl =
        "";

    let saving =
        false;


    document.addEventListener(
        "DOMContentLoaded",
        initializeAvatarFeature
    );


    /* =====================================================
       INITIALIZE
       ===================================================== */

    function initializeAvatarFeature() {

        bindAvatarEvents();

        loadCurrentAvatarState();
    }


    function bindAvatarEvents() {

        getElement(
            "changeAvatarButton"
        )?.addEventListener(
            "click",
            openAvatarModal
        );


        getElement(
            "closeAvatarModalButton"
        )?.addEventListener(
            "click",
            closeAvatarModal
        );


        getElement(
            "cancelAvatarButton"
        )?.addEventListener(
            "click",
            closeAvatarModal
        );


        getElement(
            "avatarModalBackdrop"
        )?.addEventListener(
            "click",
            closeAvatarModal
        );


        getElement(
            "saveAvatarButton"
        )?.addEventListener(
            "click",
            saveSelectedAvatar
        );


        getElement(
            "removeAvatarButton"
        )?.addEventListener(
            "click",
            removeCurrentAvatar
        );


        document.addEventListener(
            "keydown",
            handleKeyboard
        );
    }


    /* =====================================================
       CURRENT AVATAR
       ===================================================== */

    async function loadCurrentAvatarState() {

        try {

            const response =
                await fetch(
                    PROFILE_ENDPOINT,
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


            const result =
                await readJsonResponse(
                    response
                );


            if (!response.ok ||
                    !result ||
                    result.success !== true ||
                    !result.profile) {

                return;
            }


            currentAvatarUrl =
                stringValue(
                    result.profile.avatarUrl
                );


            updateRemoveButton();

        } catch (error) {

            /*
             * The main profile loader handles profile errors.
             * Avatar initialization should not replace its message.
             */
        }
    }


    function updateRemoveButton() {

        const removeButton =
            getElement(
                "removeAvatarButton"
            );


        if (!removeButton) {
            return;
        }


        removeButton.classList.toggle(
            "profile-hidden",
            !currentAvatarUrl
        );
    }


    /* =====================================================
       OPEN AND CLOSE MODAL
       ===================================================== */

    async function openAvatarModal() {

        const modal =
            getElement(
                "avatarModal"
            );


        if (!modal) {
            return;
        }


        selectedAvatarId =
            "";


        hideAvatarMessage();


        modal.classList.remove(
            "profile-hidden"
        );


        modal.setAttribute(
            "aria-hidden",
            "false"
        );


        document.body.classList.add(
            "avatar-modal-open"
        );


        setAvatarLoading(
            true
        );


        getElement(
            "closeAvatarModalButton"
        )?.focus();


        await loadAvailableAvatars();
    }


    function closeAvatarModal(
        force = false
    ) {

        /*
         * Prevent the user from closing the modal during a request,
         * but allow the successful save operation to close it.
         */
        if (saving &&
                force !== true) {

            return;
        }


        const modal =
            getElement(
                "avatarModal"
            );


        if (!modal) {
            return;
        }


        modal.classList.add(
            "profile-hidden"
        );


        modal.setAttribute(
            "aria-hidden",
            "true"
        );


        document.body.classList.remove(
            "avatar-modal-open"
        );


        selectedAvatarId =
            "";


        setSaveAvatarEnabled(
            false
        );


        getElement(
            "changeAvatarButton"
        )?.focus();
    }


    function handleKeyboard(
        event
    ) {

        if (event.key !== "Escape") {
            return;
        }


        const modal =
            getElement(
                "avatarModal"
            );


        if (!modal ||
                modal.classList.contains(
                    "profile-hidden"
                )) {

            return;
        }


        closeAvatarModal();
    }


    /* =====================================================
       LOAD AVATARS
       ===================================================== */

    async function loadAvailableAvatars() {

        try {

            const response =
                await fetch(
                    AVATARS_ENDPOINT,
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


            if (response.status === 401) {

                window.location.replace(
                    "/login.html"
                );

                return;
            }


            const result =
                await readJsonResponse(
                    response
                );


            if (!response.ok ||
                    !result ||
                    result.success !== true ||
                    !Array.isArray(
                        result.avatars
                    )) {

                throw new Error(
                    responseMessage(
                        result,
                        "Unable to load profile avatars."
                    )
                );
            }


            availableAvatars =
                result.avatars
                    .map(
                        normalizeAvatar
                    )
                    .filter(
                        avatar =>
                                avatar.id &&
                                avatar.imageUrl
                    );


            renderAvatarOptions();

        } catch (error) {

            availableAvatars =
                [];


            renderAvatarOptions();


            showAvatarMessage(
                errorMessage(
                    error,
                    "Unable to load profile avatars."
                ),
                "error"
            );

        } finally {

            setAvatarLoading(
                false
            );
        }
    }


    function normalizeAvatar(
        avatar
    ) {

        return {
            id:
                stringValue(
                    avatar?.id
                ),

            imageUrl:
                stringValue(
                    avatar?.imageUrl
                ),

            category:
                stringValue(
                    avatar?.category
                )
        };
    }


    /* =====================================================
       RENDER AVATARS
       ===================================================== */

    function renderAvatarOptions() {

        const grid =
            getElement(
                "avatarGrid"
            );


        const emptyState =
            getElement(
                "avatarEmptyState"
            );


        if (!grid ||
                !emptyState) {

            return;
        }


        grid.innerHTML =
            "";


        if (availableAvatars.length === 0) {

            grid.classList.add(
                "profile-hidden"
            );


            emptyState.classList.remove(
                "profile-hidden"
            );


            setSaveAvatarEnabled(
                false
            );


            return;
        }


        emptyState.classList.add(
            "profile-hidden"
        );


        grid.classList.remove(
            "profile-hidden"
        );


        availableAvatars.forEach(
            avatar => {

                grid.appendChild(
                    createAvatarOption(
                        avatar
                    )
                );
            }
        );
    }


    function createAvatarOption(
        avatar
    ) {

        const button =
            document.createElement(
                "button"
            );


        button.type =
            "button";


        button.className =
            "avatar-option";


        button.dataset.avatarId =
            avatar.id;


        button.setAttribute(
            "role",
            "radio"
        );


        button.setAttribute(
            "aria-checked",
            "false"
        );


        button.setAttribute(
            "aria-label",
            `Select avatar ${avatar.id}`
        );


        const image =
            document.createElement(
                "img"
            );


        image.src =
            avatar.imageUrl;


        image.alt =
            "";


        image.loading =
            "lazy";


        image.addEventListener(
            "error",
            () => {

                button.disabled =
                    true;

                button.classList.add(
                    "profile-hidden"
                );
            }
        );


        const check =
            document.createElement(
                "span"
            );


        check.className =
            "avatar-option-check";


        check.textContent =
            "✓";


        check.setAttribute(
            "aria-hidden",
            "true"
        );


        button.appendChild(
            image
        );


        button.appendChild(
            check
        );


        button.addEventListener(
            "click",
            () => {

                selectAvatar(
                    avatar.id
                );
            }
        );


        return button;
    }


    function selectAvatar(
        avatarId
    ) {

        selectedAvatarId =
            avatarId;


        document
            .querySelectorAll(
                ".avatar-option"
            )
            .forEach(
                option => {

                    const selected =
                        option.dataset.avatarId ===
                        selectedAvatarId;


                    option.classList.toggle(
                        "selected",
                        selected
                    );


                    option.setAttribute(
                        "aria-checked",
                        selected
                            ? "true"
                            : "false"
                    );
                }
            );


        hideAvatarMessage();


        setSaveAvatarEnabled(
            true
        );
    }


    /* =====================================================
       SAVE AVATAR
       ===================================================== */

    async function saveSelectedAvatar() {

        if (saving ||
                !selectedAvatarId) {

            return;
        }


        setAvatarSaving(
            true
        );


        hideAvatarMessage();


        try {

            const response =
                await fetch(
                    AVATAR_ENDPOINT,
                    {
                        method:
                            "PUT",

                        credentials:
                            "same-origin",

                        headers: {
                            "Accept":
                                "application/json",

                            "Content-Type":
                                "application/json"
                        },

                        body:
                            JSON.stringify(
                                {
                                    avatarId:
                                        selectedAvatarId
                                }
                            )
                    }
                );


            if (response.status === 401) {

                window.location.replace(
                    "/login.html"
                );

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
                    responseMessage(
                        result,
                        "Unable to update profile picture."
                    )
                );
            }


            currentAvatarUrl =
                stringValue(
                    result.profile.avatarUrl
                );


            applyAvatarToPage(
                result.profile
            );


            updateRemoveButton();


            closeAvatarModal(
                true
            );


            showProfileMessage(
                responseMessage(
                    result,
                    "Profile picture updated successfully."
                ),
                "success"
            );


            notifyNavigation(
                result.profile
            );

        } catch (error) {

            showAvatarMessage(
                errorMessage(
                    error,
                    "Unable to update profile picture."
                ),
                "error"
            );

        } finally {

            setAvatarSaving(
                false
            );
        }
    }


    /* =====================================================
       REMOVE AVATAR
       ===================================================== */

    async function removeCurrentAvatar() {

        if (saving ||
                !currentAvatarUrl) {

            return;
        }


        const confirmed =
            window.confirm(
                "Remove your profile picture and use your initials instead?"
            );


        if (!confirmed) {
            return;
        }


        setAvatarSaving(
            true
        );


        try {

            const response =
                await fetch(
                    AVATAR_ENDPOINT,
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


            const result =
                await readJsonResponse(
                    response
                );


            if (!response.ok ||
                    !result ||
                    result.success !== true ||
                    !result.profile) {

                throw new Error(
                    responseMessage(
                        result,
                        "Unable to remove profile picture."
                    )
                );
            }


            currentAvatarUrl =
                "";


            applyAvatarToPage(
                result.profile
            );


            updateRemoveButton();


            showProfileMessage(
                responseMessage(
                    result,
                    "Profile picture removed successfully."
                ),
                "success"
            );


            notifyNavigation(
                result.profile
            );

        } catch (error) {

            showProfileMessage(
                errorMessage(
                    error,
                    "Unable to remove profile picture."
                ),
                "error"
            );

        } finally {

            setAvatarSaving(
                false
            );
        }
    }


    /* =====================================================
       UPDATE PAGE
       ===================================================== */

    function applyAvatarToPage(
        profile
    ) {

        const image =
            getElement(
                "profileAvatarImage"
            );


        const initials =
            getElement(
                "profileAvatarInitials"
            );


        if (!image ||
                !initials) {

            return;
        }


        const avatarUrl =
            stringValue(
                profile.avatarUrl
            );


        if (!avatarUrl) {

            image.classList.add(
                "profile-hidden"
            );


            image.removeAttribute(
                "src"
            );


            initials.classList.remove(
                "profile-hidden"
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

                image.classList.add(
                    "profile-hidden"
                );

                initials.classList.remove(
                    "profile-hidden"
                );
            };


        image.src =
            avatarUrl;
    }


    function notifyNavigation(
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
       STATE
       ===================================================== */

    function setAvatarLoading(
        loading
    ) {

        getElement(
            "avatarLoading"
        )?.classList.toggle(
            "profile-hidden",
            !loading
        );


        if (loading) {

            getElement(
                "avatarGrid"
            )?.classList.add(
                "profile-hidden"
            );


            getElement(
                "avatarEmptyState"
            )?.classList.add(
                "profile-hidden"
            );
        }
    }


    function setSaveAvatarEnabled(
        enabled
    ) {

        const button =
            getElement(
                "saveAvatarButton"
            );


        if (button) {

            button.disabled =
                !enabled ||
                saving;
        }
    }


    function setAvatarSaving(
        value
    ) {

        saving =
            value;


        const saveButton =
            getElement(
                "saveAvatarButton"
            );


        const removeButton =
            getElement(
                "removeAvatarButton"
            );


        const buttonText =
            getElement(
                "saveAvatarButtonText"
            );


        const spinner =
            getElement(
                "saveAvatarSpinner"
            );


        if (saveButton) {

            saveButton.disabled =
                saving ||
                !selectedAvatarId;
        }


        if (removeButton) {

            removeButton.disabled =
                saving;
        }


        if (buttonText) {

            buttonText.textContent =
                saving
                    ? "Saving..."
                    : "Save Avatar";
        }


        spinner?.classList.toggle(
            "profile-hidden",
            !saving
        );
    }


    /* =====================================================
       MESSAGES
       ===================================================== */

    function showAvatarMessage(
        message,
        type
    ) {

        const element =
            getElement(
                "avatarModalMessage"
            );


        if (!element) {
            return;
        }


        element.textContent =
            message;


        element.classList.remove(
            "profile-hidden",
            "avatar-modal-message-success",
            "avatar-modal-message-error"
        );


        element.classList.add(
            type === "success"
                ? "avatar-modal-message-success"
                : "avatar-modal-message-error"
        );
    }


    function hideAvatarMessage() {

        getElement(
            "avatarModalMessage"
        )?.classList.add(
            "profile-hidden"
        );
    }


    function showProfileMessage(
        message,
        type
    ) {

        const element =
            getElement(
                "profileMessage"
            );


        if (!element) {
            return;
        }


        element.textContent =
            message;


        element.classList.remove(
            "profile-hidden",
            "profile-message-success",
            "profile-message-error"
        );


        element.classList.add(
            type === "success"
                ? "profile-message-success"
                : "profile-message-error"
        );
    }


    /* =====================================================
       HELPERS
       ===================================================== */

    async function readJsonResponse(
        response
    ) {

        try {

            return await response.json();

        } catch (error) {

            return null;
        }
    }


    function responseMessage(
        result,
        fallback
    ) {

        return stringValue(
            result?.message
        ) || fallback;
    }


    function errorMessage(
        error,
        fallback
    ) {

        return stringValue(
            error?.message
        ) || fallback;
    }


    function stringValue(
        value
    ) {

        return typeof value === "string"
            ? value.trim()
            : "";
    }


    function getElement(
        id
    ) {

        return document.getElementById(
            id
        );
    }

})();