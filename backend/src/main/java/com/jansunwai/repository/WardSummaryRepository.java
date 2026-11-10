package com.jansunwai.repository;

import com.jansunwai.entity.WardSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WardSummaryRepository extends JpaRepository<WardSummary, Long> {
    Optional<WardSummary> findByWardIdAndWeekStart(Long wardId, LocalDate weekStart);

    List<WardSummary> findByWeekStartOrderByWardId(LocalDate weekStart);

    List<WardSummary> findTop10ByWardIdOrderByWeekStartDesc(Long wardId);
}
