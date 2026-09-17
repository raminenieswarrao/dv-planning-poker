package com.dv.dvplanningpoker.profile;

/**
 * Represents one application-provided profile avatar.
 *
 * The frontend sends only the avatar ID. The trusted image path
 * is resolved by the backend and cannot be chosen by the client.
 */
public record AvatarOption(
        String id,
        String imageUrl,
        String category) {
}