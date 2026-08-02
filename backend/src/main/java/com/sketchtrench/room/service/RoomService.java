package com.sketchtrench.room.service;

import com.sketchtrench.common.websocket.RoomPublisher;
import com.sketchtrench.exception.ConflictException;
import com.sketchtrench.exception.ForbiddenException;
import com.sketchtrench.exception.NotFoundException;
import com.sketchtrench.game.entity.RoomCustomWord;
import com.sketchtrench.game.repository.RoomCustomWordRepository;
import com.sketchtrench.room.dto.RoomDto;
import com.sketchtrench.room.entity.Room;
import com.sketchtrench.room.entity.RoomMember;
import com.sketchtrench.room.entity.RoomSettings;
import com.sketchtrench.room.repository.RoomMemberRepository;
import com.sketchtrench.room.repository.RoomRepository;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lobby + room lifecycle. Every mutating method persists the change AND pushes a fresh
 * {@link RoomDto.RoomResponse} over WebSocket so every member's lobby view stays live.
 *
 * <p>Authorization rule of thumb used here: mutations are gated on "is the caller the
 * host?"; joins are gated on room state (open? full? password?) — then enforced again by
 * the DB unique constraint as the last line of defense.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoomPublisher publisher;
    private final RoomCustomWordRepository customWordRepository;

    // ponytail: in-memory room bans; add a room_bans table if multiple instances are needed
    private final Map<Long, Set<Long>> roomBans = new ConcurrentHashMap<>();

    @Transactional
    public RoomDto.RoomResponse create(Long hostId, RoomDto.CreateRoomRequest request) {
        User host = userRepository.getReferenceById(hostId);
        Room room = new Room();
        room.setHost(host);
        room.setName(request.name());
        room.setMode(request.mode() == null ? Room.Mode.CLASSIC : request.mode());
        room.setVisibility(request.visibility() == null ? Room.Visibility.PUBLIC : request.visibility());
        room.setMaxPlayers(request.maxPlayers() == null ? 8 : request.maxPlayers());

        if (room.getVisibility() == Room.Visibility.PRIVATE) {
            room.setInviteCode(generateInviteCode());
        }
        if (request.password() != null && !request.password().isBlank()) {
            room.setPasswordProtected(true);
            room.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        RoomSettings settings = RoomSettings.forRoom(room);
        applySettings(settings, request.settings());
        room.setSettings(settings);

        RoomMember hostMember = member(room, host, RoomMember.Role.HOST);
        room.getMembers().add(hostMember);

        Room saved = roomRepository.save(room);
        log.info("Room '{}' (id={}) created by user {}", saved.getName(), saved.getId(), hostId);
        return RoomDto.RoomResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RoomDto.RoomResponse> listPublic() {
        return roomRepository
                .findByStatusAndVisibilityOrderByCreatedAtDesc(Room.Status.WAITING, Room.Visibility.PUBLIC)
                .stream().map(RoomDto.RoomResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RoomDto.RoomResponse get(Long roomId) {
        return RoomDto.RoomResponse.from(load(roomId));
    }

    @Transactional
    public RoomDto.RoomResponse join(Long roomId, Long userId, RoomDto.JoinRoomRequest request) {
        Room room = load(roomId);

        if (roomBans.getOrDefault(roomId, Set.of()).contains(userId)) {
            throw new ForbiddenException("You are banned from this room");
        }
        if (room.getStatus() != Room.Status.WAITING) {
            throw new ConflictException("ROOM_NOT_OPEN", "Game already in progress");
        }
        if (memberRepository.countByRoomId(roomId) >= room.getMaxPlayers()) {
            throw new ConflictException("ROOM_FULL", "Room is full");
        }
        if (room.getVisibility() == Room.Visibility.PRIVATE
                && (request.inviteCode() == null
                    || !request.inviteCode().equals(room.getInviteCode()))) {
            throw new ForbiddenException("Invalid invite code");
        }
        if (room.isPasswordProtected()
                && (request.password() == null
                    || !passwordEncoder.matches(request.password(), room.getPasswordHash()))) {
            throw new ForbiddenException("Wrong room password");
        }
        if (memberRepository.findByRoomIdAndUserId(roomId, userId).isPresent()) {
            return RoomDto.RoomResponse.from(room); // idempotent
        }

        User user = userRepository.getReferenceById(userId);
        room.getMembers().add(member(room, user, RoomMember.Role.PLAYER));
        Room saved = roomRepository.save(room);
        broadcast(saved);
        log.info("User {} joined room {}", userId, roomId);
        return RoomDto.RoomResponse.from(saved);
    }

    @Transactional
    public RoomDto.RoomResponse leave(Long roomId, Long userId) {
        Room room = load(roomId);
        memberRepository.findByRoomIdAndUserId(roomId, userId)
                .ifPresent(memberRepository::delete);

        // Host left: hand the room to the next player, or close it if empty.
        List<RoomMember> remaining = memberRepository.findByRoomId(roomId);
        if (remaining.isEmpty()) {
            roomRepository.delete(room);
            return null;
        }
        if (userId.equals(room.getHost().getId())) {
            RoomMember nextHost = remaining.get(0);
            nextHost.setRole(RoomMember.Role.HOST);
            room.setHost(nextHost.getUser());
            remaining.forEach(m -> m.setReady(m.getRole() == RoomMember.Role.HOST));
        }
        Room saved = roomRepository.save(room);
        broadcast(saved);
        return RoomDto.RoomResponse.from(saved);
    }

    @Transactional
    public RoomDto.RoomResponse kick(Long roomId, Long hostId, Long targetId) {
        Room room = requireHost(roomId, hostId);
        if (targetId.equals(hostId)) {
            throw new ConflictException("CANNOT_KICK_SELF", "Host cannot kick themselves");
        }
        memberRepository.findByRoomIdAndUserId(roomId, targetId).ifPresent(memberRepository::delete);
        Room saved = roomRepository.save(room);
        broadcast(saved);
        return RoomDto.RoomResponse.from(saved);
    }

    @Transactional
    public RoomDto.RoomResponse ban(Long roomId, Long hostId, Long targetId) {
        Room room = requireHost(roomId, hostId);
        roomBans.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(targetId);
        memberRepository.findByRoomIdAndUserId(roomId, targetId).ifPresent(memberRepository::delete);
        Room saved = roomRepository.save(room);
        broadcast(saved);
        return RoomDto.RoomResponse.from(saved);
    }

    @Transactional
    public RoomDto.RoomResponse transferHost(Long roomId, Long hostId, Long targetId) {
        Room room = requireHost(roomId, hostId);
        RoomMember oldHost = memberRepository.findByRoomIdAndUserId(roomId, hostId)
                .orElseThrow(() -> new NotFoundException("Host member", hostId));
        RoomMember newHost = memberRepository.findByRoomIdAndUserId(roomId, targetId)
                .orElseThrow(() -> new NotFoundException("Member", targetId));

        oldHost.setRole(RoomMember.Role.PLAYER);
        newHost.setRole(RoomMember.Role.HOST);
        room.setHost(newHost.getUser());
        Room saved = roomRepository.save(room);
        broadcast(saved);
        return RoomDto.RoomResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RoomDto.CustomWordResponse> listCustomWords(Long roomId) {
        return customWordRepository.findByRoomIdOrderByIdAsc(roomId).stream()
                .map(RoomDto.CustomWordResponse::from).toList();
    }

    @Transactional
    public List<RoomDto.CustomWordResponse> addCustomWords(Long roomId, Long hostId,
                                                          RoomDto.AddCustomWordsRequest request) {
        Room room = requireHost(roomId, hostId);
        if (room.getSettings() == null || !room.getSettings().isCustomWords()) {
            throw new ConflictException("CUSTOM_WORDS_DISABLED", "Enable custom words in room settings first");
        }
        List<String> words = request.words().stream()
                .map(w -> w.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(w -> !w.isEmpty())
                .distinct()
                .limit(200)
                .toList();
        User host = userRepository.getReferenceById(hostId);
        for (String w : words) {
            if (customWordRepository.findByRoomIdAndWord(roomId, w).isEmpty()) {
                RoomCustomWord cw = new RoomCustomWord();
                cw.setRoom(room);
                cw.setWord(w);
                cw.setAddedBy(host);
                cw.setCreatedAt(Instant.now());
                customWordRepository.save(cw);
            }
        }
        return listCustomWords(roomId);
    }

    @Transactional
    public void removeCustomWord(Long roomId, Long hostId, Long wordId) {
        requireHost(roomId, hostId);
        customWordRepository.deleteByIdAndRoomId(wordId, roomId);
    }

    @Transactional
    public RoomDto.RoomResponse setReady(Long roomId, Long userId, boolean ready) {
        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new NotFoundException("Room member", userId));
        member.setReady(ready);
        Room room = load(roomId);
        room.getMembers().stream()
                .filter(m -> m.getRole() == RoomMember.Role.HOST)
                .forEach(m -> m.setReady(true));
        Room saved = roomRepository.save(room);
        broadcast(saved);
        return RoomDto.RoomResponse.from(saved);
    }

    @Transactional
    public RoomDto.RoomResponse update(Long roomId, Long hostId, RoomDto.CreateRoomRequest request) {
        Room room = requireHost(roomId, hostId);
        room.setName(request.name());
        if (request.maxPlayers() != null) {
            room.setMaxPlayers(request.maxPlayers());
        }
        if (request.visibility() != null) {
            room.setVisibility(request.visibility());
            if (room.getVisibility() == Room.Visibility.PRIVATE && room.getInviteCode() == null) {
                room.setInviteCode(generateInviteCode());
            }
        }
        if (request.password() != null && !request.password().isBlank()) {
            room.setPasswordProtected(true);
            room.setPasswordHash(passwordEncoder.encode(request.password()));
        } else if (request.password() != null) {
            room.setPasswordProtected(false);
            room.setPasswordHash(null);
        }
        applySettings(room.getSettings(), request.settings());
        Room saved = roomRepository.save(room);
        broadcast(saved);
        return RoomDto.RoomResponse.from(saved);
    }

    @Transactional
    public void delete(Long roomId, Long hostId) {
        requireHost(roomId, hostId);
        roomBans.remove(roomId);
        roomRepository.deleteById(roomId);
        publisher.roomUpdate(roomId, Map.of("type", "ROOM_CLOSED"));
    }

    // ---- internals ----

    public Room load(Long roomId) {
        return roomRepository.findWithDetailsById(roomId)
                .orElseThrow(() -> new NotFoundException("Room", roomId));
    }

    private Room requireHost(Long roomId, Long userId) {
        Room room = load(roomId);
        if (!room.getHost().getId().equals(userId)) {
            throw new ForbiddenException("Only the host can do this");
        }
        return room;
    }

    private RoomMember member(Room room, User user, RoomMember.Role role) {
        RoomMember member = new RoomMember();
        member.setRoom(room);
        member.setUser(user);
        member.setRole(role);
        member.setReady(role == RoomMember.Role.HOST);
        member.setJoinedAt(Instant.now());
        return member;
    }

    private void applySettings(RoomSettings settings, RoomDto.SettingsRequest request) {
        if (request == null) {
            return;
        }
        if (request.drawingTimeSec() != null) settings.setDrawingTimeSec(request.drawingTimeSec());
        if (request.rounds() != null) settings.setRounds(request.rounds());
        if (request.hintsEnabled() != null) settings.setHintsEnabled(request.hintsEnabled());
        if (request.allowSpectators() != null) settings.setAllowSpectators(request.allowSpectators());
        if (request.customWords() != null) settings.setCustomWords(request.customWords());
        if (request.wordCount() != null) settings.setWordCount(request.wordCount());
        settings.setUpdatedAt(Instant.now());
    }

    private String generateInviteCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(INVITE_ALPHABET.charAt(RANDOM.nextInt(INVITE_ALPHABET.length())));
        }
        return code.toString();
    }

    private void broadcast(Room room) {
        publisher.roomUpdate(room.getId(), RoomDto.RoomResponse.from(room));
    }
}
