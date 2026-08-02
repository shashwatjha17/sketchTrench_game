package com.sketchtrench.social.service;

import com.sketchtrench.exception.ConflictException;
import com.sketchtrench.exception.NotFoundException;
import com.sketchtrench.room.repository.RoomRepository;
import com.sketchtrench.social.dto.ReportDto;
import com.sketchtrench.social.entity.Report;
import com.sketchtrench.social.repository.ReportRepository;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Player reports: filing (anyone) and triage (admin, via AdminService). */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    @Transactional
    public ReportDto.ReportResponse file(Long reporterId, ReportDto.FileReportRequest request) {
        if (reporterId.equals(request.reportedId())) {
            throw new ConflictException("CANNOT_REPORT_SELF", "You cannot report yourself");
        }
        Report report = new Report();
        report.setReporter(userRepository.getReferenceById(reporterId));
        report.setReported(userRepository.getReferenceById(request.reportedId()));
        if (request.roomId() != null) {
            report.setRoom(roomRepository.findById(request.roomId())
                    .orElseThrow(() -> new NotFoundException("Room", request.roomId())));
        }
        report.setReason(request.reason());
        report.setDetails(request.details());
        report.setCreatedAt(Instant.now());
        return ReportDto.ReportResponse.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<ReportDto.ReportResponse> listByStatus(Report.Status status) {
        return reportRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream().map(ReportDto.ReportResponse::from).toList();
    }

    @Transactional
    public ReportDto.ReportResponse resolve(Long reportId, Long adminId, Report.Status status) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report", reportId));
        report.setStatus(status);
        report.setAdmin(userRepository.getReferenceById(adminId));
        report.setResolvedAt(Instant.now());
        return ReportDto.ReportResponse.from(report);
    }
}
