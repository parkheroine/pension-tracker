package com.juyeon.pension_tracker.batch;

import com.juyeon.pension_tracker.domain.change.HoldingChange;
import com.juyeon.pension_tracker.domain.change.HoldingChangeRepository;
import com.juyeon.pension_tracker.domain.disclosure.Disclosure;
import com.juyeon.pension_tracker.domain.disclosure.DisclosureRepository;
import com.juyeon.pension_tracker.domain.stock.Stock;
import com.juyeon.pension_tracker.domain.stock.StockRepository;
import com.juyeon.pension_tracker.api.controller.StockService;
import com.juyeon.pension_tracker.infra.DartApiClient;
import com.juyeon.pension_tracker.infra.DartApiException;
import com.juyeon.pension_tracker.infra.EmailAlertService;
import com.juyeon.pension_tracker.infra.dto.DartDisclosureResponse;
import com.juyeon.pension_tracker.infra.dto.DartDisclosureResponse.DisclosureItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 매일 오전 9시 실행되는 공시 수집 배치.
 * 1) 전체 종목을 순회하며 DART API를 호출
 * 2) 신규 공시를 Disclosure에 저장
 * 3) 보유비율 변동이 있으면 HoldingChange를 저장하고 이메일 알림을 발송
 *
 * 개별 종목 처리 실패 시 해당 종목만 skip하고 배치 전체는 계속 진행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisclosureBatchJob {

    private final StockRepository stockRepository;
    private final DisclosureRepository disclosureRepository;
    private final HoldingChangeRepository holdingChangeRepository;
    private final DartApiClient dartApiClient;
    private final EmailAlertService emailAlertService;
    private final StockService stockService;

    @Scheduled(cron = "0 0 9 * * *")
    public void collectDisclosures() {
        StopWatch sw = new StopWatch();
        sw.start();
        log.info("===== 공시 수집 배치 시작 =====");

        List<Stock> stocks = stockRepository.findAll();
        int totalCount = stocks.size();
        int newDisclosureCount = 0;
        int changeDetectedCount = 0;
        int failCount = 0;

        for (Stock stock : stocks) {
            try {
                int[] result = processStock(stock);
                newDisclosureCount += result[0];
                changeDetectedCount += result[1];

                // DART API 요청 제한 방지: 종목 간 200ms 딜레이
                Thread.sleep(200);
            } catch (DartApiException e) {
                failCount++;
                log.warn("종목 처리 실패 (DART API): {} ({}) - {}",
                        stock.getCorpName(), stock.getCorpCode(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("배치 인터럽트 발생 — 중단");
                break;
            } catch (Exception e) {
                failCount++;
                log.error("종목 처리 실패: {} ({}) - {}",
                        stock.getCorpName(), stock.getCorpCode(), e.getMessage());
            }
        }

        // 배치 완료 후 캐시 무효화 — 새 공시 데이터 반영
        stockService.evictCaches();

        sw.stop();
        log.info("===== 공시 수집 배치 종료 (소요시간: {}초) =====", sw.getTotalTimeSeconds());
        log.info("전체 종목: {}건, 신규 공시: {}건, 변동 감지: {}건, 실패: {}건",
                totalCount, newDisclosureCount, changeDetectedCount, failCount);
    }

    /**
     * 개별 종목의 공시를 처리한다.
     * @return [신규 공시 수, 변동 감지 수]
     */
    private int[] processStock(Stock stock) {
        DartDisclosureResponse response = dartApiClient.getMajorStock(stock.getCorpCode());

        // 조회 결과 없음
        if (response.getList() == null || response.getList().isEmpty()) {
            return new int[]{0, 0};
        }

        int newCount = 0;
        int changeCount = 0;

        for (DisclosureItem item : response.getList()) {
            // 중복 공시 방지: rcept_no가 이미 존재하면 skip
            if (disclosureRepository.existsById(item.getRceptNo())) {
                continue;
            }

            BigDecimal stkrt = parseBigDecimal(item.getStkrt());
            BigDecimal stkrtIrds = parseBigDecimal(item.getStkrtIrds());

            Disclosure disclosure = Disclosure.builder()
                    .rceptNo(item.getRceptNo())
                    .stock(stock)
                    .rceptDt(item.getRceptDt())
                    .stkqy(parseLong(item.getStkqy()))
                    .stkqyIrds(parseLong(item.getStkqyIrds()))
                    .stkrt(stkrt)
                    .stkrtIrds(stkrtIrds)
                    .reportTp(item.getReportTp())
                    .reportResn(item.getReportResn())
                    .build();

            disclosureRepository.save(disclosure);
            newCount++;
            log.info("신규 공시 저장: {} ({}) - rcept_no: {}",
                    stock.getCorpName(), stock.getCorpCode(), item.getRceptNo());

            // 변동 감지: stkrt_irds가 0이 아닐 때
            if (stkrtIrds != null && stkrtIrds.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal beforeStkrt = getBeforeStkrt(stock.getCorpCode(), stkrt, stkrtIrds);

                HoldingChange holdingChange = HoldingChange.builder()
                        .stock(stock)
                        .beforeStkrt(beforeStkrt)
                        .afterStkrt(stkrt)
                        .changeAmount(stkrtIrds)
                        .detectedAt(LocalDateTime.now())
                        .build();

                holdingChangeRepository.save(holdingChange);
                changeCount++;
                log.info("변동 감지: {} ({}) - 변동폭: {}%",
                        stock.getCorpName(), stock.getCorpCode(), stkrtIrds);

                // 이메일 알림 발송
                emailAlertService.sendChangeAlert(stock, holdingChange);
            }
        }

        return new int[]{newCount, changeCount};
    }

    /**
     * 변동 전 보유비율을 계산한다.
     * 현재 보유비율에서 증감분을 빼서 역산한다.
     */
    private BigDecimal getBeforeStkrt(String corpCode, BigDecimal currentStkrt, BigDecimal stkrtIrds) {
        // 직전 공시의 보유비율 조회 시도
        Optional<Disclosure> latestDisclosure =
                disclosureRepository.findTopByStockCorpCodeOrderByRceptDtDesc(corpCode);

        if (latestDisclosure.isPresent() && latestDisclosure.get().getStkrt() != null) {
            return latestDisclosure.get().getStkrt();
        }

        // 직전 공시 없으면 현재 비율에서 증감분을 빼서 역산
        return currentStkrt.subtract(stkrtIrds);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return Long.parseLong(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
