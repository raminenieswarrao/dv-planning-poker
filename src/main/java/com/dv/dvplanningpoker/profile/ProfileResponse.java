package com.dv.dvplanningpoker.profile;

/**
 * Profile information returned to the frontend.
 *
 * Email comes from Supabase Auth, while the remaining
 * information comes from the public.profiles table.
 *
 * Sensitive authentication information and access tokens
 * must never be included in this response.
 */
public record ProfileResponse(
        String id,
        String name,
        String email,
        String gender,
        String avatarUrl,
        String role,
        String createdAt,
        String updatedAt) {
}