package com.sketchtrench.game.controller;

import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.game.dto.GameDto;
import com.sketchtrench.game.entity.Drawing;
import com.sketchtrench.game.repository.DrawingRepository;
import com.sketchtrench.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST triggers for the game. Starts happen over HTTP (guaranteed delivery of the
 * command beats a WebSocket frame that could be lost mid-handshake); the resulting
 * state changes broadcast over WebSocket to everyone in the room.
 */
@RestController
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final DrawingRepository drawingRepository;

    @PostMapping("/api/rooms/{roomId}/start")
    public void start(@PathVariable Long roomId) {
        gameService.startGame(roomId, SecurityUtils.currentUserId());
    }

    @PostMapping("/api/game/{roomId}/guess")
    public GameDto.CorrectGuess guess(@PathVariable Long roomId,
                                      @RequestBody GameDto.GuessRequest request) {
        return gameService.submitGuess(roomId, SecurityUtils.currentUserId(), request.text());
    }

    /** Current live-game snapshot — powers rejoin after a page refresh. */
    @GetMapping("/api/game/{roomId}/state")
    public GameDto.GameState state(@PathVariable Long roomId) {
        return gameService.getState(roomId, SecurityUtils.currentUserId());
    }

    /** Latest drawing snapshot for a room — powers the replay feature. */
    @GetMapping("/api/rooms/{roomId}/drawing")
    public List<Object> latestDrawing(@PathVariable Long roomId) {
        Drawing drawing = drawingRepository.findByRoundRoomIdOrderByIdDesc(roomId, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        return drawing == null ? List.of() : parse(drawing.getData());
    }

    @SuppressWarnings("unchecked")
    private List<Object> parse(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
