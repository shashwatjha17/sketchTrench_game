package com.sketchtrench.guest;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP surface for the guest flow ({@code /app/guest/...}). The principal is the guest
 * playerId installed by {@link GuestChannelInterceptor} on CONNECT.
 */
@Controller
@RequiredArgsConstructor
public class GuestMessageHandler {

    private final GuestService service;

    private GuestPlayer player(Principal principal) {
        return principal == null ? null : service.requirePlayer(principal.getName());
    }

    @MessageMapping("/guest/drawing/{roomId}")
    public void drawing(@DestinationVariable String roomId,
                        @Payload(required = false) Map<String, Object> stroke,
                        Principal principal) {
        GuestPlayer player = player(principal);
        if (player != null && stroke != null) {
            service.forwardStroke(player, roomId, stroke);
        }
    }

    @MessageMapping("/guest/chat/{roomId}")
    public void chat(@DestinationVariable String roomId,
                     @Payload(required = false) GuestDto.WordRequest request,
                     Principal principal) {
        GuestPlayer player = player(principal);
        if (player == null || request == null || request.words() == null || request.words().isEmpty()) {
            return;
        }
        String text = request.words().get(0);
        GuestDto.CorrectGuess guess = service.submitGuess(player.playerId, roomId, text);
        if (guess == null) {
            service.chat(player, roomId, text);
        }
    }

    @MessageMapping("/guest/typing/{roomId}")
    public void typing(@DestinationVariable String roomId, Principal principal) {
        GuestPlayer player = player(principal);
        if (player != null) {
            service.typing(player, roomId);
        }
    }

    @MessageMapping("/guest/game/{roomId}/word-select")
    public void wordSelect(@DestinationVariable String roomId,
                           @Payload(required = false) Map<String, String> body,
                           Principal principal) {
        GuestPlayer player = player(principal);
        if (player != null && body != null && body.get("wordId") != null) {
            service.selectWord(player.playerId, roomId, body.get("wordId"));
        }
    }
}
