package com.jansunwai.repository;

import com.jansunwai.entity.ComplaintAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ComplaintAiAnalysisRepository extends JpaRepository<ComplaintAiAnalysis, Long> {
    Optional<ComplaintAiAnalysis> findFirstByComplaintIdAndStatusOrderByCreatedAtDesc(Long complaintId,
                                                                                       com.jansunwai.entity.AiStatus status);

    List<ComplaintAiAnalysis> findByComplaintIdOrderByCreatedAtDesc(Long complaintId);

    /** AI usage & cost per provider/model: calls, failures, tokens, cost, average latency. */
    @Query("""
           select a.provider, a.model, count(a),
                  sum(case when a.status = com.jansunwai.entity.AiStatus.FAILED then 1 else 0 end),
                  coalesce(sum(a.inputTokens), 0), coalesce(sum(a.outputTokens), 0), coalesce(sum(a.costUsd), 0),
                  coalesce(avg(a.latencyMs), 0)
           from ComplaintAiAnalysis a group by a.provider, a.model
           """)
    List<Object[]> usageByProvider();
}
