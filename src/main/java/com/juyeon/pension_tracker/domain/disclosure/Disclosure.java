package com.juyeon.pension_tracker.domain.disclosure;

import com.juyeon.pension_tracker.domain.common.BaseTimeEntity;
import com.juyeon.pension_tracker.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DART 대량보유 공시 엔티티.
 * rcept_no(DART 접수번호)를 PK로 사용하여 중복 공시 저장을 방지한다.
 */
@Entity
@Table(name = "disclosure", indexes = {
        // corp_code 단독 조회가 빈번 (종목별 공시 이력 조회)
        @Index(name = "idx_disclosure_corp_code", columnList = "corp_code"),
        // corp_code + rcept_dt 복합 인덱스: 종목별 최신 공시 조회(ORDER BY rcept_dt DESC)에 사용
        @Index(name = "idx_disclosure_corp_code_rcept_dt", columnList = "corp_code, rcept_dt DESC"),
        // rcept_dt 범위 조회 (최근 N일 공시 필터링)
        @Index(name = "idx_disclosure_rcept_dt", columnList = "rcept_dt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Disclosure extends BaseTimeEntity {

    @Id
    @Column(name = "rcept_no", length = 14)
    private String rceptNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corp_code", nullable = false)
    private Stock stock;

    // 접수일자 (YYYY-MM-DD 형식)
    @Column(name = "rcept_dt", length = 10)
    private String rceptDt;

    // 보유주식 수
    @Column(name = "stkqy")
    private Long stkqy;

    // 보유주식 증감
    @Column(name = "stkqy_irds")
    private Long stkqyIrds;

    // 보유비율 (예: 5.23%)
    @Column(name = "stkrt", precision = 5, scale = 2)
    private BigDecimal stkrt;

    // 보유비율 증감
    @Column(name = "stkrt_irds", precision = 5, scale = 2)
    private BigDecimal stkrtIrds;

    // 보고구분
    @Column(name = "report_tp", length = 50)
    private String reportTp;

    // 보고사유
    @Column(name = "report_resn", length = 200)
    private String reportResn;

    @Builder
    public Disclosure(String rceptNo, Stock stock, String rceptDt,
                      Long stkqy, Long stkqyIrds, BigDecimal stkrt, BigDecimal stkrtIrds,
                      String reportTp, String reportResn) {
        this.rceptNo = rceptNo;
        this.stock = stock;
        this.rceptDt = rceptDt;
        this.stkqy = stkqy;
        this.stkqyIrds = stkqyIrds;
        this.stkrt = stkrt;
        this.stkrtIrds = stkrtIrds;
        this.reportTp = reportTp;
        this.reportResn = reportResn;
    }
}
