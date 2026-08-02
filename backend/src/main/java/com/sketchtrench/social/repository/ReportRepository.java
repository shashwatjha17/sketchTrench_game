package com.sketchtrench.social.repository;

import com.sketchtrench.social.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByCreatedAtDesc(Report.Status status);
}
