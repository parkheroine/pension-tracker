package com.juyeon.pension_tracker.domain.disclosure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DisclosureRepository extends JpaRepository<Disclosure, String> {

    Page<Disclosure> findByStockCorpCodeOrderByRceptDtDesc(String corpCode, Pageable pageable);

    // 해당 종목의 가장 최근 공시 조회 (변동 감지 시 이전 보유비율 비교용)
    Optional<Disclosure> findTopByStockCorpCodeOrderByRceptDtDesc(String corpCode);

    /**
     * 업종별 집계를 DB에서 수행하는 네이티브 쿼리 (5단계에서 사용).
     * 종목별 최신 공시의 보유비율을 기준으로 업종별 평균을 계산한다.
     * N+1 문제 원천 차단 — 쿼리 1개로 집계 완료.
     */
    @Query(value = """
        SELECT s.sector, AVG(d.stkrt) as avg_stkrt, COUNT(*) as stock_count
        FROM stock s
        JOIN (
            SELECT DISTINCT ON (corp_code) corp_code, stkrt
            FROM disclosure
            WHERE stkrt IS NOT NULL
            ORDER BY corp_code, rcept_dt DESC
        ) d ON s.corp_code = d.corp_code
        WHERE s.sector IS NOT NULL
        GROUP BY s.sector
        ORDER BY avg_stkrt DESC
        """, nativeQuery = true)
    List<Object[]> findSectorSummary();
}
