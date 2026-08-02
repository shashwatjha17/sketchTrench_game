package com.sketchtrench.common.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * The ONLY place that knows topic destination strings. Services call
 * {@code publisher.roomUpdate(id, payload)} and never leak destination coupling.
 *
 * <p>Topics follow the spec: /topic/room/{id}, /topic/game/{id}, /topic/drawing/{id},
 * /topic/chat/{id}, /topic/leaderboard; private messages go to /user/{name}/queue/*.
 */
@Component
@RequiredArgsConstructor
public class RoomPublisher {

    private final SimpMessagingTemplate messaging;

    public void roomUpdate(Long roomId, Object payload) {
        messaging.convertAndSend("/topic/room/" + roomId, payload);
    }

    public void gameUpdate(Long roomId, Object payload) {
        messaging.convertAndSend("/topic/game/" + roomId, payload);
    }

    public void drawing(Long roomId, Object payload) {
        messaging.convertAndSend("/topic/drawing/" + roomId, payload);
    }

    public void chat(Long roomId, Object payload) {
        messaging.convertAndSend("/topic/chat/" + roomId, payload);
    }

    public void leaderboard(Object payload) {
        messaging.convertAndSend("/topic/leaderboard", payload);
    }

    public void notifyUser(String username, Object payload) {
        messaging.convertAndSendToUser(username, "/queue/notifications", payload);
    }

    /** Private word-selection options — only the drawer's session receives this. */
    public void wordOptions(String username, Object payload) {
        messaging.convertAndSendToUser(username, "/queue/word-options", payload);
    }

    /** The secret word — only the drawer's session receives this. */
    public void secretWord(String username, Object payload) {
        messaging.convertAndSendToUser(username, "/queue/word", payload);
    }
}
