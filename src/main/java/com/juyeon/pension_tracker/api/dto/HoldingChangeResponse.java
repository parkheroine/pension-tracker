package com.juyeon.pension_tracker.api.dto;

import com.juyeon.pension_tracker.domain.change.HoldingChange;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class HoldingChangeResponse {

    private final Long id;
    private final String corpCode;
    private final String corpName;
    private final String sector;
    private final BigDecimal beforeStkrt;
    private final BigDecimal afterStkrt;
    private final BigDecimal changeAmount;
    private final LocalDateTime detectedAt;
    private final Boolean alerted;

    public HoldingChangeResponse(HoldingChange change) {
        this.id = change.getId();
        this.corpCode = change.getStock().getCorpCode();
        this.corpName = change.getStock().getCorpName();
        this.sector = change.getStock().getSector();
        this.beforeStkrt = change.getBeforeStkrt();
        this.afterStkrt = change.getAfterStkrt();
        this.changeAmount = change.getChangeAmount();
        this.detectedAt = change.getDetectedAt();
        this.alerted = change.getAlerted();
    }
}
