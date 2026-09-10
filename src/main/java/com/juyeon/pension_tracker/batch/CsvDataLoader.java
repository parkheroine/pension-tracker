package com.juyeon.pension_tracker.batch;

import com.juyeon.pension_tracker.domain.stock.Stock;
import com.juyeon.pension_tracker.domain.stock.StockRepository;
import com.juyeon.pension_tracker.infra.DartApiClient;
import com.juyeon.pension_tracker.infra.DartApiException;
import com.juyeon.pension_tracker.infra.dto.DartCompanyResponse;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 애플리케이션 시작 시 CSV 파일에서 국민연금 보유 종목을 Stock 테이블에 적재.
 * DB에 데이터가 이미 존재하면 실행하지 않는다(1회성 초기 적재).
 *
 * 흐름:
 * 1) 로컬 CORPCODE.xml에서 기업명 → corp_code 매핑 구성
 * 2) CSV에서 기업명을 읽어 corp_code를 매핑
 * 3) corp_code로 기업개황 API를 호출하여 업종명(sector) 조회
 * 4) Stock 엔티티로 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsvDataLoader implements ApplicationRunner {

    private final StockRepository stockRepository;
    private final DartApiClient dartApiClient;

    @Override
    public void run(ApplicationArguments args) {
        if (stockRepository.count() > 0) {
            log.info("Stock 테이블에 이미 데이터 존재 → CSV 적재 skip");
            return;
        }

        ClassPathResource csvResource = new ClassPathResource("data/nps_stock.csv");
        if (!csvResource.exists()) {
            log.warn("CSV 파일이 존재하지 않음 (data/nps_stock.csv) → 적재 skip");
            return;
        }

        ClassPathResource xmlResource = new ClassPathResource("data/CORPCODE.xml");
        if (!xmlResource.exists()) {
            log.warn("기업코드 XML 파일이 존재하지 않음 (data/CORPCODE.xml) → 적재 skip");
            return;
        }

        // 1) 로컬 CORPCODE.xml에서 기업명 → corp_code 매핑 구성
        Map<String, String> corpCodeMap = loadCorpCodeMap(xmlResource);
        if (corpCodeMap.isEmpty()) {
            log.error("기업코드 매핑이 비어있음 → CSV 적재 중단");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(csvResource.getInputStream(), StandardCharsets.UTF_8))) {

            // 헤더 skip
            reader.readNext();

            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length < 2) continue;

                String corpName = line[1].trim();
                try {
                    // 2) 기업명으로 corp_code 찾기
                    String corpCode = corpCodeMap.get(corpName);
                    if (corpCode == null) {
                        failCount++;
                        log.warn("기업코드 매핑 실패 (DART 목록에 없음): {}", corpName);
                        continue;
                    }

                    // 중복 저장 방지
                    if (stockRepository.existsById(corpCode)) {
                        log.debug("이미 존재하는 종목 skip: {} ({})", corpName, corpCode);
                        continue;
                    }

                    // 3) corp_code로 기업개황 API 호출 → 업종명 조회
                    String sector = null;
                    try {
                        DartCompanyResponse companyInfo = dartApiClient.getCompanyInfo(corpCode);
                        sector = companyInfo.getIndutyCode();
                    } catch (DartApiException e) {
                        log.warn("기업개황 API 호출 실패 (업종명 없이 저장): {} - {}", corpName, e.getMessage());
                    }

                    // 4) Stock 저장
                    Stock stock = Stock.builder()
                            .corpCode(corpCode)
                            .corpName(corpName)
                            .sector(sector)
                            .build();

                    stockRepository.save(stock);
                    successCount++;
                    log.info("종목 적재 성공: {} ({})", corpName, corpCode);

                } catch (Exception e) {
                    failCount++;
                    log.warn("종목 적재 실패: {} - {}", corpName, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("CSV 파일 읽기 실패", e);
        }

        log.info("CSV 초기 데이터 적재 완료 - 성공: {}건, 실패: {}건", successCount, failCount);
    }

    /**
     * CORPCODE.xml을 StAX로 파싱하여 기업명 → corp_code 매핑을 반환한다.
     */
    private Map<String, String> loadCorpCodeMap(ClassPathResource xmlResource) {
        Map<String, String> map = new HashMap<>();

        try (InputStream is = xmlResource.getInputStream()) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

            String currentElement = null;
            String corpCode = null;
            String corpName = null;
            StringBuilder textBuffer = new StringBuilder();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    currentElement = reader.getLocalName();
                    textBuffer.setLength(0);
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    if (currentElement != null) {
                        textBuffer.append(reader.getText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String endName = reader.getLocalName();
                    if ("corp_code".equals(endName)) {
                        corpCode = textBuffer.toString().trim();
                    } else if ("corp_name".equals(endName)) {
                        corpName = textBuffer.toString().trim();
                    } else if ("list".equals(endName) && corpCode != null && corpName != null) {
                        map.putIfAbsent(corpName, corpCode);
                        corpCode = null;
                        corpName = null;
                    }
                    currentElement = null;
                    textBuffer.setLength(0);
                }
            }
            reader.close();
        } catch (Exception e) {
            log.error("CORPCODE.xml 파싱 실패", e);
        }

        log.info("기업코드 매핑 로드 완료: {}개 기업", map.size());
        return map;
    }
}
