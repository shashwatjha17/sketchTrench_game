package com.sketchtrench.game.websocket;

import com.sketchtrench.auth.security.UserPrincipal;
import com.sketchtrench.game.dto.GameDto;
import com.sketchtrench.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP endpoint surface ({@code /app/...}). Thin as the REST controllers: parse the
 * destination + principal, delegate to {@link GameService}. All fan-out happens in the
 * service via the publisher — these methods return void.
 */
@Controller
@RequiredArgsConstructor
public class GameMessageHandler {

    private final GameService gameService;

    @MessageMapping("/game/{roomId}/guess")
    public void guess(@DestinationVariable Long roomId,
                      @Payload(required = false) GameDto.GuessRequest request,
                      Principal principal) {
        UserPrincipal user = (UserPrincipal) principal;
        gameService.submitGuess(roomId, user.id(), request == null ? "" : request.text());
    }

    @MessageMapping("/game/{roomId}/word-select")
    public void wordSelect(@DestinationVariable Long roomId,
                           @Payload(required = false) GameDto.WordChoiceRequest request,
                           Principal principal) {
        if (request == null || request.wordId() == null) {
            return;
        }
        gameService.selectWord(roomId, ((UserPrincipal) principal).id(), request.wordId());
    }

    @MessageMapping("/drawing/{roomId}")
    public void drawing(@DestinationVariable Long roomId,
                        @Payload Map<String, Object> stroke,
                        Principal principal) {
        gameService.forwardStroke(roomId, ((UserPrincipal) principal).id(), stroke);
    }

    @MessageMapping("/chat/{roomId}")
    public void chat(@DestinationVariable Long roomId,
                     @Payload(required = false) GameDto.ChatRequest request,
                     Principal principal) {
        gameService.chat(roomId, ((UserPrincipal) principal).id(),
                request == null ? "" : request.text());
    }

    @MessageMapping("/typing/{roomId}")
    public void typing(@DestinationVariable Long roomId, Principal principal) {
        gameService.typing(roomId, ((UserPrincipal) principal).id());
    }
}
