package com.juyeon.pension_tracker.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class SectorSummaryResponse {

    private final String sector;
    private final BigDecimal avgStkrt;
    private final Long stockCount;
}
