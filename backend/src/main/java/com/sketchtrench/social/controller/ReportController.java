package com.sketchtrench.social.controller;

import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.social.dto.ReportDto;
import com.sketchtrench.social.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportDto.ReportResponse> file(@Valid @RequestBody ReportDto.FileReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.file(SecurityUtils.currentUserId(), request));
    }
}
