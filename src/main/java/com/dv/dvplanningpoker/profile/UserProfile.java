package com.dv.dvplanningpoker.profile;

/**
 * Internal representation of a user profile stored in
 * the public.profiles Supabase table.
 *
 * API response objects are kept separate so the database
 * structure does not directly control the frontend contract.
 */
public record UserProfile(
        String id,
        String name,
        String gender,
        String avatarUrl,
        String role,
        String createdAt,
        String updatedAt) {
}