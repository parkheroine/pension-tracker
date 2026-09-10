package com.juyeon.pension_tracker.api.controller;

import com.juyeon.pension_tracker.api.common.ApiResponse;
import com.juyeon.pension_tracker.api.dto.*;
import com.juyeon.pension_tracker.batch.DisclosureBatchJob;
import com.juyeon.pension_tracker.batch.NpsStockDiscoveryJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final DisclosureBatchJob disclosureBatchJob;
    private final NpsStockDiscoveryJob npsStockDiscoveryJob;

    // 전체 종목 리스트 (페이지네이션)
    @GetMapping("/stocks")
    public ApiResponse<Page<StockResponse>> getStocks(Pageable pageable) {
        log.info("GET /api/stocks - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return ApiResponse.ok(stockService.getStocks(pageable));
    }

    // 종목 추가
    @PostMapping("/stocks")
    public ApiResponse<StockResponse> addStock(@RequestBody StockAddRequest request) {
        log.info("POST /api/stocks - 종목 추가: {}", request.getCorpName());
        return ApiResponse.ok(stockService.addStock(request.getCorpName()));
    }

    // 특정 종목의 공시 이력 (페이지네이션)
    @GetMapping("/stocks/{corpCode}/disclosures")
    public ApiResponse<Page<DisclosureResponse>> getDisclosures(
            @PathVariable String corpCode, Pageable pageable) {
        log.info("GET /api/stocks/{}/disclosures", corpCode);
        return ApiResponse.ok(stockService.getDisclosures(corpCode, pageable));
    }

    // 특정 종목의 변동 이력
    @GetMapping("/stocks/{corpCode}/changes")
    public ApiResponse<List<HoldingChangeResponse>> getChanges(@PathVariable String corpCode) {
        log.info("GET /api/stocks/{}/changes", corpCode);
        return ApiResponse.ok(stockService.getChanges(corpCode));
    }

    // 최근 변동 이력 (전체 종목)
    @GetMapping("/changes/recent")
    public ApiResponse<List<HoldingChangeResponse>> getRecentChanges() {
        log.info("GET /api/changes/recent");
        return ApiResponse.ok(stockService.getRecentChanges());
    }

    // 공시 수집 배치 수동 실행
    @PostMapping("/batch/collect")
    public ApiResponse<String> triggerBatch() {
        log.info("POST /api/batch/collect - 공시 수집 배치 수동 실행");
        disclosureBatchJob.collectDisclosures();
        return ApiResponse.ok("공시 수집 배치 실행 완료");
    }

    // 국민연금 종목 발견 배치 수동 실행
    @PostMapping("/batch/discover")
    public ApiResponse<String> triggerDiscovery() {
        log.info("POST /api/batch/discover - 종목 발견 배치 수동 실행");
        int discovered = npsStockDiscoveryJob.runDiscovery();
        return ApiResponse.ok(discovered + "개 신규 종목 발견");
    }

    // 업종별 평균 보유비율 집계
    @GetMapping("/sectors/summary")
    public ApiResponse<List<SectorSummaryResponse>> getSectorSummary() {
        log.info("GET /api/sectors/summary");
        return ApiResponse.ok(stockService.getSectorSummary());
    }
}
