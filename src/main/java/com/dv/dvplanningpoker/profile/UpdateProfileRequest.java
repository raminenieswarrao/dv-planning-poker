package com.dv.dvplanningpoker.profile;

/**
 * Fields a signed-in user is permitted to update.
 *
 * Email, role, user ID and account timestamps are deliberately
 * excluded so they cannot be modified through this endpoint.
 *
 * Avatar upload will use a separate endpoint later.
 */
public record UpdateProfileRequest(
        String name,
        String gender) {
}