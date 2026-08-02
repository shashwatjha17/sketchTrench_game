package com.sketchtrench.admin.service;

import com.sketchtrench.admin.dto.AdminDto;
import com.sketchtrench.exception.ConflictException;
import com.sketchtrench.exception.NotFoundException;
import com.sketchtrench.game.entity.Word;
import com.sketchtrench.game.entity.WordCategory;
import com.sketchtrench.game.repository.WordCategoryRepository;
import com.sketchtrench.game.repository.WordRepository;
import com.sketchtrench.room.entity.Room;
import com.sketchtrench.room.repository.RoomRepository;
import com.sketchtrench.social.dto.ReportDto;
import com.sketchtrench.social.entity.Report;
import com.sketchtrench.social.service.ReportService;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Admin-only operations. Every method here is behind {@code hasRole('ADMIN')}. */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final WordRepository wordRepository;
    private final WordCategoryRepository categoryRepository;
    private final ReportService reportService;

    @Transactional
    public void banUser(Long userId, Instant until) {
        User user = requireUser(userId);
        user.setBanned(true);
        user.setBannedUntil(until);
    }

    @Transactional
    public void unbanUser(Long userId) {
        User user = requireUser(userId);
        user.setBanned(false);
        user.setBannedUntil(null);
    }

    @Transactional
    public void muteUser(Long userId, Instant until) {
        User user = requireUser(userId);
        user.setMutedUntil(until);
    }

    @Transactional
    public Word addWord(AdminDto.AddWordRequest request) {
        if (wordRepository.findByTextIgnoreCase(request.text()).isPresent()) {
            throw new ConflictException("WORD_EXISTS", "Word already in the bank");
        }
        WordCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Category", request.categoryId()));
        Word word = new Word();
        word.setText(request.text());
        word.setDifficulty(request.difficulty() == null ? Word.Difficulty.MEDIUM : request.difficulty());
        word.setCategory(category);
        word.setCreatedAt(Instant.now());
        return wordRepository.save(word);
    }

    @Transactional
    public void deactivateWord(Long wordId) {
        Word word = wordRepository.findById(wordId)
                .orElseThrow(() -> new NotFoundException("Word", wordId));
        word.setActive(false);
    }

    @Transactional
    public WordCategory addCategory(String name, String emoji) {
        if (categoryRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ConflictException("CATEGORY_EXISTS", "Category already exists");
        }
        WordCategory category = new WordCategory(name, emoji);
        category.setCreatedAt(Instant.now());
        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public List<ReportDto.ReportResponse> openReports() {
        return reportService.listByStatus(Report.Status.OPEN);
    }

    @Transactional
    public ReportDto.ReportResponse resolveReport(Long reportId, Long adminId, Report.Status status) {
        return reportService.resolve(reportId, adminId, status);
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room", roomId));
        roomRepository.deleteById(roomId);
    }

    @Transactional(readOnly = true)
    public AdminDto.Analytics analytics() {
        return new AdminDto.Analytics(
                userRepository.count(),
                userRepository.countByCreatedAtAfter(Instant.now().minusSeconds(86_400)),
                roomRepository.findAll().stream()
                        .filter(r -> r.getStatus() == Room.Status.PLAYING).count(),
                reportService.listByStatus(Report.Status.OPEN).size());
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }
}
