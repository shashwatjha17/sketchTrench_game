package com.sketchtrench.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "token is required")
        String token,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String newPassword
) {
}
