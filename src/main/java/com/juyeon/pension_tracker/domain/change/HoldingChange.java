package com.juyeon.pension_tracker.domain.change;

import com.juyeon.pension_tracker.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 국민연금 보유비율 변동 이력 엔티티.
 * 배치에서 변동을 감지할 때마다 새 레코드가 생성된다.
 */
@Entity
@Table(name = "holding_change", indexes = {
        // corp_code + detected_at 복합 인덱스: 종목별 변동이력 조회(ORDER BY detected_at DESC)
        @Index(name = "idx_holding_change_corp_detected", columnList = "corp_code, detected_at DESC"),
        // 미발송 알림 조회용: alerted=false인 레코드만 빠르게 필터링
        @Index(name = "idx_holding_change_alerted", columnList = "alerted"),
        // 최근 변동 전체 조회용 (detected_at DESC)
        @Index(name = "idx_holding_change_detected_at", columnList = "detected_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldingChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corp_code", nullable = false)
    private Stock stock;

    // 변동 전 보유비율
    @Column(name = "before_stkrt", precision = 5, scale = 2)
    private BigDecimal beforeStkrt;

    // 변동 후 보유비율
    @Column(name = "after_stkrt", precision = 5, scale = 2)
    private BigDecimal afterStkrt;

    // 변동폭
    @Column(name = "change_amount", precision = 5, scale = 2)
    private BigDecimal changeAmount;

    // 변동 감지 시각
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    // 알림 발송 여부
    @Column(name = "alerted", nullable = false)
    private Boolean alerted = false;

    @Builder
    public HoldingChange(Stock stock, BigDecimal beforeStkrt, BigDecimal afterStkrt,
                         BigDecimal changeAmount, LocalDateTime detectedAt) {
        this.stock = stock;
        this.beforeStkrt = beforeStkrt;
        this.afterStkrt = afterStkrt;
        this.changeAmount = changeAmount;
        this.detectedAt = detectedAt;
        this.alerted = false;
    }

    // 알림 발송 완료 처리
    public void markAlerted() {
        this.alerted = true;
    }
}
