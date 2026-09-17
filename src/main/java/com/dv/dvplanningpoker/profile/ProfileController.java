package com.dv.dvplanningpoker.profile;

import com.dv.dvplanningpoker.auth.AuthSessionResolver;
import com.dv.dvplanningpoker.auth.AuthSessionResolver.ResolvedSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides authenticated access to the current user's profile.
 *
 * User identity is always taken from the authenticated Supabase
 * session. The client cannot select another user's profile ID.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final AuthSessionResolver sessionResolver;

    private final SupabaseProfileService profileService;

    private final ProfileAvatarCatalog avatarCatalog;


    public ProfileController(
            AuthSessionResolver sessionResolver,
            SupabaseProfileService profileService,
            ProfileAvatarCatalog avatarCatalog) {

        this.sessionResolver =
                sessionResolver;

        this.profileService =
                profileService;

        this.avatarCatalog =
                avatarCatalog;
    }


    /* =====================================================
       GET CURRENT PROFILE
       ===================================================== */

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                sessionResolver.resolve(
                        request,
                        response
                );


        if (session == null ||
                session.user() == null) {

            return errorResponse(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in to view your profile."
            );
        }


        try {

            UserProfile profile =
                    profileService.getProfile(
                            session.accessToken(),
                            session.user().id()
                    );


            if (profile == null) {

                return errorResponse(
                        HttpStatus.NOT_FOUND,
                        "Profile was not found."
                );
            }


            return successResponse(
                    "Profile loaded successfully.",
                    toResponse(
                            profile,
                            session.user().email()
                    )
            );

        } catch (IllegalArgumentException ex) {

            return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    safeMessage(
                            ex.getMessage(),
                            "Unable to load profile."
                    )
            );

        } catch (IllegalStateException ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile service is temporarily unavailable."
            );

        } catch (ProfileServiceException ex) {

            return profileServiceError(
                    ex,
                    "Unable to load profile."
            );

        } catch (Exception ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile service is temporarily unavailable."
            );
        }
    }


    /* =====================================================
       UPDATE CURRENT PROFILE
       ===================================================== */

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestBody(required = false)
            UpdateProfileRequest updateRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                sessionResolver.resolve(
                        request,
                        response
                );


        if (session == null ||
                session.user() == null) {

            return errorResponse(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in to update your profile."
            );
        }


        if (updateRequest == null) {

            return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    "Invalid profile update request."
            );
        }


        try {

            UserProfile profile =
                    profileService.updateProfile(
                            session.accessToken(),
                            session.user().id(),
                            updateRequest.name(),
                            updateRequest.gender()
                    );


            return successResponse(
                    "Profile updated successfully.",
                    toResponse(
                            profile,
                            session.user().email()
                    )
            );

        } catch (IllegalArgumentException ex) {

            return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    safeMessage(
                            ex.getMessage(),
                            "Profile information is invalid."
                    )
            );

        } catch (IllegalStateException ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile service is temporarily unavailable."
            );

        } catch (ProfileServiceException ex) {

            return profileServiceError(
                    ex,
                    "Unable to update profile."
            );

        } catch (Exception ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile service is temporarily unavailable."
            );
        }
    }


    /* =====================================================
       GET AVAILABLE AVATARS
       ===================================================== */

    @GetMapping("/avatars")
    public ResponseEntity<Map<String, Object>> getAvailableAvatars(
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                sessionResolver.resolve(
                        request,
                        response
                );


        if (session == null ||
                session.user() == null) {

            return errorResponse(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in to view profile avatars."
            );
        }


        try {

            UserProfile profile =
                    profileService.getProfile(
                            session.accessToken(),
                            session.user().id()
                    );


            if (profile == null) {

                return errorResponse(
                        HttpStatus.NOT_FOUND,
                        "Profile was not found."
                );
            }


            Map<String, Object> result =
                    new LinkedHashMap<>();


            result.put(
                    "success",
                    true
            );


            result.put(
                    "avatars",
                    avatarCatalog.getAvailableAvatars(
                            profile.gender()
                    )
            );


            return ResponseEntity.ok(
                    result
            );

        } catch (IllegalArgumentException ex) {

            return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    safeMessage(
                            ex.getMessage(),
                            "Unable to load profile avatars."
                    )
            );

        } catch (IllegalStateException ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile service is temporarily unavailable."
            );

        } catch (ProfileServiceException ex) {

            return profileServiceError(
                    ex,
                    "Unable to load profile avatars."
            );

        } catch (Exception ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to load profile avatars."
            );
        }
    }


    /* =====================================================
       SELECT BUILT-IN AVATAR
       ===================================================== */

    @PutMapping("/avatar")
    public ResponseEntity<Map<String, Object>> updateAvatar(
            @RequestBody(required = false)
            UpdateAvatarRequest avatarRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                sessionResolver.resolve(
                        request,
                        response
                );


        if (session == null ||
                session.user() == null) {

            return errorResponse(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in to update your profile picture."
            );
        }


        if (avatarRequest == null) {

            return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    "Avatar selection is required."
            );
        }


        try {

            UserProfile currentProfile =
                    profileService.getProfile(
                            session.accessToken(),
                            session.user().id()
                    );


            if (currentProfile == null) {

                return errorResponse(
                        HttpStatus.NOT_FOUND,
                        "Profile was not found."
                );
            }


            AvatarOption avatar =
                    avatarCatalog.getRequiredAvatar(
                            avatarRequest.avatarId(),
                            currentProfile.gender()
                    );


            UserProfile updatedProfile =
                    profileService.updateAvatar(
                            session.accessToken(),
                            session.user().id(),
                            avatar.imageUrl()
                    );


            return successResponse(
                    "Profile picture updated successfully.",
                    toResponse(
                            updatedProfile,
                            session.user().email()
                    )
            );

        } catch (IllegalArgumentException ex) {

            return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    safeMessage(
                            ex.getMessage(),
                            "Avatar selection is invalid."
                    )
            );

        } catch (IllegalStateException ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile service is temporarily unavailable."
            );

        } catch (ProfileServiceException ex) {

            return profileServiceError(
                    ex,
                    "Unable to update profile picture."
            );

        } catch (Exception ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to update profile picture."
            );
        }
    }


    /* =====================================================
       REMOVE PROFILE AVATAR
       ===================================================== */

    @DeleteMapping("/avatar")
    public ResponseEntity<Map<String, Object>> removeAvatar(
            HttpServletRequest request,
            HttpServletResponse response) {

        ResolvedSession session =
                sessionResolver.resolve(
                        request,
                        response
                );


        if (session == null ||
                session.user() == null) {

            return errorResponse(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in to remove your profile picture."
            );
        }


        try {

            UserProfile updatedProfile =
                    profileService.removeAvatar(
                            session.accessToken(),
                            session.user().id()
                    );


            return successResponse(
                    "Profile picture removed successfully.",
                    toResponse(
                            updatedProfile,
                            session.user().email()
                    )
            );

        } catch (IllegalArgumentException ex) {

            return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    safeMessage(
                            ex.getMessage(),
                            "Unable to remove profile picture."
                    )
            );

        } catch (IllegalStateException ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile service is temporarily unavailable."
            );

        } catch (ProfileServiceException ex) {

            return profileServiceError(
                    ex,
                    "Unable to remove profile picture."
            );

        } catch (Exception ex) {

            return errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to remove profile picture."
            );
        }
    }


    /* =====================================================
       RESPONSE MAPPING
       ===================================================== */

    private ProfileResponse toResponse(
            UserProfile profile,
            String email) {

        return new ProfileResponse(
                profile.id(),
                profile.name(),
                email,
                profile.gender(),
                profile.avatarUrl(),
                profile.role(),
                profile.createdAt(),
                profile.updatedAt()
        );
    }


    private ResponseEntity<Map<String, Object>>
    successResponse(
            String message,
            ProfileResponse profile) {

        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "success",
                true
        );


        result.put(
                "message",
                message
        );


        result.put(
                "profile",
                profile
        );


        return ResponseEntity.ok(
                result
        );
    }


    private ResponseEntity<Map<String, Object>>
    profileServiceError(
            ProfileServiceException ex,
            String fallbackMessage) {

        HttpStatus status =
                switch (ex.getStatusCode()) {

                    case 400, 422 ->
                            HttpStatus.BAD_REQUEST;

                    case 401 ->
                            HttpStatus.UNAUTHORIZED;

                    case 403 ->
                            HttpStatus.FORBIDDEN;

                    case 404 ->
                            HttpStatus.NOT_FOUND;

                    case 409 ->
                            HttpStatus.CONFLICT;

                    case 429 ->
                            HttpStatus.TOO_MANY_REQUESTS;

                    default ->
                            HttpStatus.SERVICE_UNAVAILABLE;
                };


        return errorResponse(
                status,
                safeMessage(
                        ex.getMessage(),
                        fallbackMessage
                )
        );
    }


    private ResponseEntity<Map<String, Object>>
    errorResponse(
            HttpStatus status,
            String message) {

        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "success",
                false
        );


        result.put(
                "message",
                message
        );


        return ResponseEntity
                .status(
                        status
                )
                .body(
                        result
                );
    }


    private String safeMessage(
            String message,
            String fallbackMessage) {

        if (message == null ||
                message.isBlank() ||
                message.length() > 200) {

            return fallbackMessage;
        }


        return message;
    }
}