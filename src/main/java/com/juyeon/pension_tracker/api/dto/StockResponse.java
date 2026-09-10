package com.juyeon.pension_tracker.api.dto;

import com.juyeon.pension_tracker.domain.stock.Stock;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class StockResponse {

    private final String corpCode;
    private final String corpName;
    private final String sector;
    private final LocalDateTime createdAt;

    public StockResponse(Stock stock) {
        this.corpCode = stock.getCorpCode();
        this.corpName = stock.getCorpName();
        this.sector = stock.getSector();
        this.createdAt = stock.getCreatedAt();
    }
}
