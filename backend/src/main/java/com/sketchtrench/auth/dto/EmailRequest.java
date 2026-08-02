package com.sketchtrench.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailRequest(
        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        String email
) {
}
