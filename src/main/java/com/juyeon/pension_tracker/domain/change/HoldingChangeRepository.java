package com.juyeon.pension_tracker.domain.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HoldingChangeRepository extends JpaRepository<HoldingChange, Long> {

    // fetch join으로 Stock을 함께 로딩 → N+1 방지
    @Query("SELECT hc FROM HoldingChange hc JOIN FETCH hc.stock WHERE hc.stock.corpCode = :corpCode ORDER BY hc.detectedAt DESC")
    List<HoldingChange> findByStockCorpCodeOrderByDetectedAtDesc(String corpCode);

    // fetch join으로 Stock을 함께 로딩 → N+1 방지
    @Query("SELECT hc FROM HoldingChange hc JOIN FETCH hc.stock ORDER BY hc.detectedAt DESC LIMIT 20")
    List<HoldingChange> findTop20ByOrderByDetectedAtDesc();
}
