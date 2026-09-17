package com.dv.dvplanningpoker.profile;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Trusted catalog of application-provided profile avatars.
 *
 * Avatar paths are defined only by the backend. The client submits
 * an avatar ID and cannot store an arbitrary file or external URL.
 */
@Component
public class ProfileAvatarCatalog {

    private static final String MEN =
            "MEN";

    private static final String WOMEN =
            "WOMEN";


    private static final List<AvatarOption> AVATARS =
            List.of(
                    new AvatarOption(
                            "men-01",
                            "/images/avatars/men/avatar-01.png",
                            MEN
                    ),
                    new AvatarOption(
                            "men-02",
                            "/images/avatars/men/avatar-02.png",
                            MEN
                    ),
                    new AvatarOption(
                            "men-03",
                            "/images/avatars/men/avatar-03.png",
                            MEN
                    ),
                    new AvatarOption(
                            "men-04",
                            "/images/avatars/men/avatar-04.png",
                            MEN
                    ),
                    new AvatarOption(
                            "men-05",
                            "/images/avatars/men/avatar-05.png",
                            MEN
                    ),
                    new AvatarOption(
                            "women-01",
                            "/images/avatars/women/avatar-01.png",
                            WOMEN
                    ),
                    new AvatarOption(
                            "women-02",
                            "/images/avatars/women/avatar-02.png",
                            WOMEN
                    ),
                    new AvatarOption(
                            "women-03",
                            "/images/avatars/women/avatar-03.png",
                            WOMEN
                    ),
                    new AvatarOption(
                            "women-04",
                            "/images/avatars/women/avatar-04.png",
                            WOMEN
                    ),
                    new AvatarOption(
                            "women-05",
                            "/images/avatars/women/avatar-05.png",
                            WOMEN
                    )
            );


    /**
     * Returns the avatar choices permitted for the supplied
     * profile gender.
     *
     * MALE users see the men's collection.
     * FEMALE users see the women's collection.
     * Other or unspecified values see both collections.
     */
    public List<AvatarOption> getAvailableAvatars(
            String gender) {

        String category =
                categoryForGender(
                        gender
                );


        if (category == null) {

            return AVATARS;
        }


        return AVATARS
                .stream()
                .filter(
                        avatar ->
                                category.equals(
                                        avatar.category()
                                )
                )
                .toList();
    }


    /**
     * Resolves an avatar ID and verifies that it is permitted for
     * the supplied profile gender.
     */
    public AvatarOption getRequiredAvatar(
            String avatarId,
            String gender) {

        String normalizedId =
                normalizeAvatarId(
                        avatarId
                );


        return getAvailableAvatars(
                gender
        )
                .stream()
                .filter(
                        avatar ->
                                avatar.id().equals(
                                        normalizedId
                                )
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Please select a valid avatar."
                                )
                );
    }


    private String categoryForGender(
            String gender) {

        if (gender == null ||
                gender.isBlank()) {

            return null;
        }


        String normalized =
                gender
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        return switch (normalized) {

            case "MALE" ->
                    MEN;

            case "FEMALE" ->
                    WOMEN;

            default ->
                    null;
        };
    }


    private String normalizeAvatarId(
            String avatarId) {

        if (avatarId == null ||
                avatarId.isBlank()) {

            throw new IllegalArgumentException(
                    "Avatar selection is required."
            );
        }


        String normalized =
                avatarId
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );


        if (normalized.length() > 40) {

            throw new IllegalArgumentException(
                    "Avatar selection is invalid."
            );
        }


        return normalized;
    }
}