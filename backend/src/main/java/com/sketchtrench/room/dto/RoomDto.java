package com.sketchtrench.room.dto;

import com.sketchtrench.room.entity.Room;
import com.sketchtrench.room.entity.RoomMember;
import com.sketchtrench.room.entity.RoomSettings;
import com.sketchtrench.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request/response shapes for the rooms API. {@code RoomResponse.of(room)} maps the
 * aggregate (host + members + settings) in one place — the API never sees entities.
 */
public record RoomDto() {

    public record CreateRoomRequest(
            @NotBlank(message = "room name is required")
            @Size(min = 3, max = 64, message = "room name must be 3-64 characters")
            String name,

            Room.Mode mode,
            Room.Visibility visibility,
            @Size(max = 64) String password,
            @Min(2) @Max(16) Integer maxPlayers,
            @Valid SettingsRequest settings
    ) {
    }

    public record SettingsRequest(
            @Min(15) @Max(300) Integer drawingTimeSec,
            @Min(1) @Max(10) Integer rounds,
            Boolean hintsEnabled,
            Boolean allowSpectators,
            Boolean customWords,
            @Min(1) @Max(6) Integer wordCount
    ) {
    }

    public record JoinRoomRequest(
            String inviteCode,
            String password
    ) {
    }

    public record AddCustomWordsRequest(
            @NotEmpty(message = "provide at least one word")
            List<@NotBlank(message = "words cannot be blank") @Size(max = 64, message = "words are limited to 64 chars") String> words
    ) {
    }

    public record CustomWordResponse(Long id, String word) {
        public static CustomWordResponse from(com.sketchtrench.game.entity.RoomCustomWord w) {
            return new CustomWordResponse(w.getId(), w.getWord());
        }
    }

    public record RoomMemberResponse(
            Long userId,
            String username,
            RoomMember.Role role,
            boolean ready,
            int score,
            boolean connected,
            String avatarColor,
            String avatarExpression,
            boolean avatarSunglasses,
            String avatarWig
    ) {
        static RoomMemberResponse from(RoomMember member) {
            User user = member.getUser();
            return new RoomMemberResponse(user.getId(), user.getUsername(), member.getRole(),
                    member.isReady(), member.getScore(), member.isConnected(),
                    user.getAvatarColor(), user.getAvatarExpression(),
                    user.isAvatarSunglasses(), user.getAvatarWig());
        }
    }

    public record RoomSettingsResponse(
            int drawingTimeSec,
            int rounds,
            boolean hintsEnabled,
            boolean allowSpectators,
            boolean customWords,
            int wordCount
    ) {
        static RoomSettingsResponse from(RoomSettings settings) {
            return new RoomSettingsResponse(settings.getDrawingTimeSec(), settings.getRounds(),
                    settings.isHintsEnabled(), settings.isAllowSpectators(),
                    settings.isCustomWords(), settings.getWordCount());
        }
    }

    public record RoomResponse(
            Long id,
            String name,
            Room.Mode mode,
            Room.Visibility visibility,
            Room.Status status,
            Long hostId,
            String hostName,
            int maxPlayers,
            int currentRound,
            String inviteCode,
            RoomSettingsResponse settings,
            List<RoomMemberResponse> members,
            Instant createdAt
    ) {
        public static RoomResponse from(Room room) {
            return new RoomResponse(
                    room.getId(), room.getName(), room.getMode(), room.getVisibility(), room.getStatus(),
                    room.getHost().getId(), room.getHost().getUsername(),
                    room.getMaxPlayers(), room.getCurrentRound(), room.getInviteCode(),
                    room.getSettings() == null ? null : RoomSettingsResponse.from(room.getSettings()),
                    room.getMembers().stream().map(RoomMemberResponse::from).toList(),
                    room.getCreatedAt());
        }
    }
}
