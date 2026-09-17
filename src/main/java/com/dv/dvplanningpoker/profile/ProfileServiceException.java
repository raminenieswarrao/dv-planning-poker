package com.dv.dvplanningpoker.profile;

/**
 * Represents a failure while communicating with the Supabase
 * Profiles REST API.
 */
public class ProfileServiceException
        extends RuntimeException {

    private final int statusCode;

    public ProfileServiceException(
            String message) {

        this(
                message,
                0
        );
    }

    public ProfileServiceException(
            String message,
            int statusCode) {

        super(
                message
        );

        this.statusCode =
                statusCode;
    }

    public int getStatusCode() {

        return statusCode;
    }
}