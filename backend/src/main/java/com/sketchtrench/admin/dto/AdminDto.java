package com.sketchtrench.admin.dto;

import com.sketchtrench.game.entity.Word;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminDto() {

    public record AddWordRequest(
            @NotBlank @Size(max = 64) String text,
            @NotNull Long categoryId,
            Word.Difficulty difficulty
    ) {
    }

    public record Analytics(
            long totalUsers,
            long usersToday,
            long activeRooms,
            long openReports
    ) {
    }
}
