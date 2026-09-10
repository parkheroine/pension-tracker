package com.juyeon.pension_tracker.api.controller;

import com.juyeon.pension_tracker.api.common.NotFoundException;
import com.juyeon.pension_tracker.api.dto.*;
import com.juyeon.pension_tracker.domain.change.HoldingChangeRepository;
import com.juyeon.pension_tracker.domain.disclosure.DisclosureRepository;
import com.juyeon.pension_tracker.domain.stock.Stock;
import com.juyeon.pension_tracker.domain.stock.StockRepository;
import com.juyeon.pension_tracker.infra.DartApiClient;
import com.juyeon.pension_tracker.infra.dto.DartCompanyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;
    private final DisclosureRepository disclosureRepository;
    private final HoldingChangeRepository holdingChangeRepository;
    private final DartApiClient dartApiClient;

    public Page<StockResponse> getStocks(Pageable pageable) {
        log.info("전체 종목 리스트 조회 - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return stockRepository.findAll(pageable).map(StockResponse::new);
    }

    public Page<DisclosureResponse> getDisclosures(String corpCode, Pageable pageable) {
        log.info("종목 공시 이력 조회: {}", corpCode);
        validateStockExists(corpCode);
        return disclosureRepository.findByStockCorpCodeOrderByRceptDtDesc(corpCode, pageable)
                .map(DisclosureResponse::new);
    }

    public List<HoldingChangeResponse> getChanges(String corpCode) {
        log.info("종목 변동 이력 조회: {}", corpCode);
        validateStockExists(corpCode);
        return holdingChangeRepository.findByStockCorpCodeOrderByDetectedAtDesc(corpCode)
                .stream()
                .map(HoldingChangeResponse::new)
                .toList();
    }

    public List<HoldingChangeResponse> getRecentChanges() {
        log.info("최근 변동 이력 조회");
        return holdingChangeRepository.findTop20ByOrderByDetectedAtDesc()
                .stream()
                .map(HoldingChangeResponse::new)
                .toList();
    }

    /**
     * 업종별 평균 보유비율 집계.
     *
     * [최적화 전] 전체 Stock 조회 → 종목별 최신 Disclosure 개별 조회 → 앱에서 집계
     *   → N+1 문제: 371개 종목이면 1 + 371 = 372개 쿼리 실행
     *
     * [최적화 후] DB에서 GROUP BY로 단일 쿼리 집계 + Redis 1시간 캐싱
     *   → 쿼리 1개, 캐시 히트 시 0개
     */
    @Cacheable(value = "sectorSummary", key = "'all'")
    public List<SectorSummaryResponse> getSectorSummary() {
        StopWatch sw = new StopWatch();
        sw.start();

        List<SectorSummaryResponse> result = disclosureRepository.findSectorSummary()
                .stream()
                .map(row -> new SectorSummaryResponse(
                        (String) row[0],
                        ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP),
                        ((Number) row[2]).longValue()))
                .toList();

        sw.stop();
        log.info("업종별 집계 조회 완료 - {}ms ({}개 업종)", sw.getTotalTimeMillis(), result.size());
        return result;
    }

    /**
     * 배치 완료 후 캐시 무효화.
     * 새로운 공시 데이터가 들어오면 집계 결과가 달라지므로 캐시를 갱신한다.
     */
    @CacheEvict(value = {"sectorSummary", "stocks"}, allEntries = true)
    public void evictCaches() {
        log.info("캐시 무효화 완료 (sectorSummary, stocks)");
    }

    /**
     * 기업명으로 종목을 추가한다.
     * CORPCODE.xml에서 corp_code를 찾고, DART 기업개황 API로 업종코드를 조회한다.
     */
    @Transactional
    public StockResponse addStock(String corpName) {
        log.info("종목 추가 요청: {}", corpName);

        // CORPCODE.xml에서 corp_code 찾기
        String corpCode = findCorpCodeByName(corpName);
        if (corpCode == null) {
            throw new NotFoundException("DART에 등록되지 않은 기업입니다: " + corpName);
        }

        if (stockRepository.existsById(corpCode)) {
            throw new IllegalArgumentException("이미 추적 중인 종목입니다: " + corpName);
        }

        // 기업개황 API로 업종코드 조회
        String sector = null;
        try {
            DartCompanyResponse companyInfo = dartApiClient.getCompanyInfo(corpCode);
            sector = companyInfo.getIndutyCode();
        } catch (Exception e) {
            log.warn("기업개황 API 호출 실패 (업종코드 없이 저장): {} - {}", corpName, e.getMessage());
        }

        Stock stock = Stock.builder()
                .corpCode(corpCode)
                .corpName(corpName)
                .sector(sector)
                .build();
        stockRepository.save(stock);

        log.info("종목 추가 완료: {} ({})", corpName, corpCode);
        return new StockResponse(stock);
    }

    private String findCorpCodeByName(String corpName) {
        ClassPathResource xmlResource = new ClassPathResource("data/CORPCODE.xml");
        try (InputStream is = xmlResource.getInputStream()) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

            String currentElement = null;
            String code = null;
            String name = null;
            StringBuilder textBuffer = new StringBuilder();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    currentElement = reader.getLocalName();
                    textBuffer.setLength(0);
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    if (currentElement != null) textBuffer.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String endName = reader.getLocalName();
                    if ("corp_code".equals(endName)) code = textBuffer.toString().trim();
                    else if ("corp_name".equals(endName)) name = textBuffer.toString().trim();
                    else if ("list".equals(endName)) {
                        if (corpName.equals(name)) {
                            reader.close();
                            return code;
                        }
                        code = null;
                        name = null;
                    }
                    currentElement = null;
                    textBuffer.setLength(0);
                }
            }
            reader.close();
        } catch (Exception e) {
            log.error("CORPCODE.xml 파싱 실패", e);
        }
        return null;
    }

    private void validateStockExists(String corpCode) {
        if (!stockRepository.existsById(corpCode)) {
            throw new NotFoundException("종목을 찾을 수 없습니다: " + corpCode);
        }
    }
}
