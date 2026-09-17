package com.dv.dvplanningpoker.profile;

/**
 * Request used to select one application-provided avatar.
 *
 * The frontend submits only the trusted catalog ID, such as
 * "men-01" or "women-01". It cannot submit an image URL or path.
 */
public record UpdateAvatarRequest(
        String avatarId) {
}