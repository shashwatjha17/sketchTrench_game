package com.sketchtrench.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Validation lives HERE (declarative, on the boundary) so the
 * controller is one annotation — {@code @Valid} — and the service never parses garbage.
 *
 * <p>Why a record? Immutable request payloads can't be mutated between validation and
 * use, and the record's compact constructor is a natural place for cross-field checks.
 */
public record RegisterRequest(
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 32, message = "username must be 3-32 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "username may only contain letters, digits and underscore")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 255, message = "email is too long")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password
) {
}
