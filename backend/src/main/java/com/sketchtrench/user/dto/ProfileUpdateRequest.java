package com.sketchtrench.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Profile customization payload (display name + avatar). Display name is optional so
 * the caller can update just the avatar; colors are hex; expressions/wigs match the
 * frontend's known set so the UI can always render them.
 */
public record ProfileUpdateRequest(
        @Size(min = 3, max = 50, message = "display name must be 3-50 characters")
        String displayName,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "avatar color must be a hex color like #3b82f6")
        String color,

        @Size(max = 16)
        String expression,

        Boolean sunglasses,

        @Size(max = 16)
        String wig
) {
}
