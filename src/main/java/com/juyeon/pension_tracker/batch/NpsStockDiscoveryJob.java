package com.juyeon.pension_tracker.batch;

import com.juyeon.pension_tracker.domain.stock.Stock;
import com.juyeon.pension_tracker.domain.stock.StockRepository;
import com.juyeon.pension_tracker.infra.DartApiClient;
import com.juyeon.pension_tracker.infra.DartApiException;
import com.juyeon.pension_tracker.infra.dto.DartCompanyResponse;
import com.juyeon.pension_tracker.infra.dto.DartDisclosureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 국민연금 대량보유 종목 자동 발견 배치.
 *
 * CORPCODE.xml에서 상장기업 목록을 가져와서, 매일 500개씩
 * DART majorstock API를 호출하여 국민연금공단이 보고자(repror)인
 * 종목을 자동으로 Stock 테이블에 등록한다.
 *
 * 전체 상장사 ~4000개를 약 8일에 걸쳐 전수 스캔한다.
 * 스캔 위치는 DB의 Stock 수를 기반으로 offset을 계산한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NpsStockDiscoveryJob {

    private static final int BATCH_SIZE = 500;
    private static final String NPS_REPORTER = "국민연금공단";

    private final StockRepository stockRepository;
    private final DartApiClient dartApiClient;

    // 현재 스캔 위치 (앱 재시작 시 0부터 다시 시작)
    private final AtomicInteger scanOffset = new AtomicInteger(0);

    /**
     * 매일 오전 7시 실행 — 공시 수집 배치(9시) 전에 새 종목을 발견하도록
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void discoverNpsStocks() {
        runDiscovery();
    }

    /**
     * 수동 실행용 메서드
     */
    public int runDiscovery() {
        log.info("===== 국민연금 종목 발견 배치 시작 (offset: {}) =====", scanOffset.get());

        List<String[]> listedCompanies = loadListedCompanies();
        int totalListed = listedCompanies.size();

        if (totalListed == 0) {
            log.warn("상장기업 목록이 비어있음 → 발견 배치 중단");
            return 0;
        }

        int offset = scanOffset.get() % totalListed;
        int end = Math.min(offset + BATCH_SIZE, totalListed);
        List<String[]> batch = listedCompanies.subList(offset, end);

        log.info("스캔 범위: {}/{} ~ {}/{}", offset, totalListed, end, totalListed);

        int discovered = 0;
        int scanned = 0;
        int skipped = 0;

        for (String[] company : batch) {
            String corpCode = company[0];
            String corpName = company[1];

            // 이미 추적 중인 종목은 skip
            if (stockRepository.existsById(corpCode)) {
                skipped++;
                continue;
            }

            scanned++;
            try {
                DartDisclosureResponse response = dartApiClient.getMajorStock(corpCode);

                if (response.getList() == null || response.getList().isEmpty()) {
                    continue;
                }

                // 보고자(repror)에 국민연금공단이 있는지 확인
                boolean hasNps = response.getList().stream()
                        .anyMatch(item -> NPS_REPORTER.equals(item.getRepror()));

                if (hasNps) {
                    // 기업개황 API로 업종코드 조회
                    String sector = null;
                    try {
                        DartCompanyResponse companyInfo = dartApiClient.getCompanyInfo(corpCode);
                        sector = companyInfo.getIndutyCode();
                    } catch (DartApiException e) {
                        log.warn("기업개황 조회 실패 (업종 없이 등록): {} - {}", corpName, e.getMessage());
                    }

                    Stock stock = Stock.builder()
                            .corpCode(corpCode)
                            .corpName(corpName)
                            .sector(sector)
                            .build();
                    stockRepository.save(stock);
                    discovered++;
                    log.info("국민연금 보유 종목 발견: {} ({})", corpName, corpCode);
                }

            } catch (DartApiException e) {
                log.debug("API 호출 실패 (skip): {} ({}) - {}", corpName, corpCode, e.getMessage());
            } catch (Exception e) {
                log.debug("처리 실패 (skip): {} ({}) - {}", corpName, corpCode, e.getMessage());
            }
        }

        // 다음 스캔 위치 저장
        scanOffset.set(end >= totalListed ? 0 : end);

        log.info("===== 국민연금 종목 발견 배치 종료 =====");
        log.info("스캔: {}건, skip(이미 추적중): {}건, 신규 발견: {}건, 다음 offset: {}",
                scanned, skipped, discovered, scanOffset.get());

        return discovered;
    }

    /**
     * CORPCODE.xml에서 상장기업(stock_code가 있는) 목록을 반환한다.
     * @return [corp_code, corp_name] 리스트
     */
    private List<String[]> loadListedCompanies() {
        List<String[]> result = new ArrayList<>();
        ClassPathResource xmlResource = new ClassPathResource("data/CORPCODE.xml");

        try (InputStream is = xmlResource.getInputStream()) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

            String currentElement = null;
            String corpCode = null;
            String corpName = null;
            String stockCode = null;
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
                    if ("corp_code".equals(endName)) corpCode = textBuffer.toString().trim();
                    else if ("corp_name".equals(endName)) corpName = textBuffer.toString().trim();
                    else if ("stock_code".equals(endName)) stockCode = textBuffer.toString().trim();
                    else if ("list".equals(endName)) {
                        // stock_code가 있으면 상장기업
                        if (stockCode != null && !stockCode.isEmpty()) {
                            result.add(new String[]{corpCode, corpName});
                        }
                        corpCode = null;
                        corpName = null;
                        stockCode = null;
                    }
                    currentElement = null;
                    textBuffer.setLength(0);
                }
            }
            reader.close();
        } catch (Exception e) {
            log.error("CORPCODE.xml 파싱 실패", e);
        }

        log.info("상장기업 목록 로드: {}개", result.size());
        return result;
    }
}
