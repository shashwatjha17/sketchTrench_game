package com.sketchtrench.social.dto;

import com.sketchtrench.social.entity.Report;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ReportDto() {

    public record FileReportRequest(
            @NotNull Long reportedId,
            Long roomId,
            @NotBlank @Size(max = 64) String reason,
            @Size(max = 1000) String details
    ) {
    }

    public record ReportResponse(
            Long id,
            Long reporterId,
            Long reportedId,
            Long roomId,
            String reason,
            String details,
            Report.Status status,
            Instant createdAt
    ) {
        public static ReportResponse from(Report report) {
            return new ReportResponse(report.getId(), report.getReporter().getId(),
                    report.getReported().getId(),
                    report.getRoom() == null ? null : report.getRoom().getId(),
                    report.getReason(), report.getDetails(), report.getStatus(), report.getCreatedAt());
        }
    }
}
