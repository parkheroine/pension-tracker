package com.juyeon.pension_tracker.api.dto;

import com.juyeon.pension_tracker.domain.disclosure.Disclosure;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class DisclosureResponse {

    private final String rceptNo;
    private final String corpCode;
    private final String rceptDt;
    private final Long stkqy;
    private final Long stkqyIrds;
    private final BigDecimal stkrt;
    private final BigDecimal stkrtIrds;
    private final String reportTp;
    private final String reportResn;
    private final LocalDateTime createdAt;

    public DisclosureResponse(Disclosure disclosure) {
        this.rceptNo = disclosure.getRceptNo();
        this.corpCode = disclosure.getStock().getCorpCode();
        this.rceptDt = disclosure.getRceptDt();
        this.stkqy = disclosure.getStkqy();
        this.stkqyIrds = disclosure.getStkqyIrds();
        this.stkrt = disclosure.getStkrt();
        this.stkrtIrds = disclosure.getStkrtIrds();
        this.reportTp = disclosure.getReportTp();
        this.reportResn = disclosure.getReportResn();
        this.createdAt = disclosure.getCreatedAt();
    }
}
