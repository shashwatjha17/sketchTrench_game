package com.sketchtrench.admin.controller;

import com.sketchtrench.admin.dto.AdminDto;
import com.sketchtrench.admin.service.AdminService;
import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.game.entity.Word;
import com.sketchtrench.game.entity.WordCategory;
import com.sketchtrench.social.dto.ReportDto;
import com.sketchtrench.social.entity.Report;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Admin panel API. {@code @PreAuthorize} on the class = every endpoint requires the
 * ADMIN role; the security layer enforces it before the method even runs.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/users/{id}/ban")
    public void ban(@PathVariable Long id, @RequestParam(required = false) Long until) {
        adminService.banUser(id, until == null ? null : Instant.now().plusSeconds(until));
    }

    @PostMapping("/users/{id}/unban")
    public void unban(@PathVariable Long id) {
        adminService.unbanUser(id);
    }

    @PostMapping("/users/{id}/mute")
    public void mute(@PathVariable Long id, @RequestParam(required = false) Long until) {
        adminService.muteUser(id, until == null ? null : Instant.now().plusSeconds(until));
    }

    @PostMapping("/words")
    public Word addWord(@Valid @RequestBody AdminDto.AddWordRequest request) {
        return adminService.addWord(request);
    }

    @DeleteMapping("/words/{id}")
    public void deactivateWord(@PathVariable Long id) {
        adminService.deactivateWord(id);
    }

    @PostMapping("/categories")
    public WordCategory addCategory(@RequestParam String name, @RequestParam(defaultValue = "box") String emoji) {
        return adminService.addCategory(name, emoji);
    }

    @GetMapping("/reports")
    public List<ReportDto.ReportResponse> openReports() {
        return adminService.openReports();
    }

    @PostMapping("/reports/{id}/resolve")
    public ReportDto.ReportResponse resolveReport(@PathVariable Long id, @RequestParam String status) {
        return adminService.resolveReport(id, SecurityUtils.currentUserId(),
                Report.Status.valueOf(status.toUpperCase()));
    }

    @DeleteMapping("/rooms/{id}")
    public void deleteRoom(@PathVariable Long id) {
        adminService.deleteRoom(id);
    }

    @GetMapping("/analytics")
    public AdminDto.Analytics analytics() {
        return adminService.analytics();
    }
}
