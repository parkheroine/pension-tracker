package com.juyeon.pension_tracker.infra;

import com.juyeon.pension_tracker.infra.dto.DartCompanyResponse;
import com.juyeon.pension_tracker.infra.dto.DartDisclosureResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 금융감독원 DART Open API 호출 클라이언트.
 * 대량보유 상황보고(majorstock)와 기업개황(company) API를 제공한다.
 * 호출 실패 시 최대 3회 재시도한다.
 */
@Slf4j
@Component
public class DartApiClient {

    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final String STATUS_OK = "000";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public DartApiClient(
            RestTemplate restTemplate,
            @Value("${dart.api.key}") String apiKey,
            @Value("${dart.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    /**
     * 대량보유 상황보고 API 호출.
     * 해당 기업의 국민연금 등 대량보유자 공시 목록을 반환한다.
     */
    public DartDisclosureResponse getMajorStock(String corpCode) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/majorstock.json")
                .queryParam("crtfc_key", apiKey)
                .queryParam("corp_code", corpCode)
                .toUriString();

        DartDisclosureResponse response = executeWithRetry(url, DartDisclosureResponse.class, corpCode);

        // status "013"은 조회 결과 없음 → 정상 케이스로 처리
        if ("013".equals(response.getStatus())) {
            log.debug("종목 {} 대량보유 공시 데이터 없음", corpCode);
            return response;
        }

        if (!STATUS_OK.equals(response.getStatus())) {
            throw new DartApiException(
                    String.format("DART majorstock API 오류 [%s]: %s (corp_code: %s)",
                            response.getStatus(), response.getMessage(), corpCode));
        }

        return response;
    }

    /**
     * 기업개황 API 호출.
     * corp_code로 기업명과 업종명을 조회한다.
     */
    public DartCompanyResponse getCompanyInfo(String corpCode) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/company.json")
                .queryParam("crtfc_key", apiKey)
                .queryParam("corp_code", corpCode)
                .toUriString();

        DartCompanyResponse response = executeWithRetry(url, DartCompanyResponse.class, corpCode);

        if (!STATUS_OK.equals(response.getStatus())) {
            throw new DartApiException(
                    String.format("DART company API 오류 [%s]: %s (corp_code: %s)",
                            response.getStatus(), response.getMessage(), corpCode));
        }

        return response;
    }

    /**
     * DART 기업코드 목록(corpCode.xml)을 다운로드하여 기업명 → corp_code 매핑을 반환한다.
     * ZIP 파일 안의 XML을 파싱하여 corp_name → corp_code Map을 구성한다.
     */
    public Map<String, String> getCorpCodeMap() {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/corpCode.xml")
                .queryParam("crtfc_key", apiKey)
                .toUriString();

        log.info("DART 기업코드 목록 다운로드 시작");

        byte[] zipBytes = restTemplate.getForObject(url, byte[].class);
        if (zipBytes == null) {
            throw new DartApiException("DART 기업코드 목록 다운로드 실패: 응답이 null");
        }
        log.info("DART ZIP 다운로드 완료: {} bytes", zipBytes.length);

        Map<String, String> corpCodeMap = new HashMap<>();

        // 30MB XML을 효율적으로 파싱하기 위해 StAX(스트리밍) 파서 사용
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                log.info("ZIP 엔트리 발견: {}", entry.getName());
                if (entry.getName().toUpperCase().endsWith(".xml")) {
                    // ZipInputStream을 직접 StAX에 넘기면 스트림이 닫히므로 바이트로 먼저 읽기
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    log.info("XML 바이트 크기: {}", baos.size());

                    XMLInputFactory factory = XMLInputFactory.newInstance();
                    XMLStreamReader reader = factory.createXMLStreamReader(
                            new ByteArrayInputStream(baos.toByteArray()), "UTF-8");

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
                                corpCodeMap.putIfAbsent(corpName, corpCode);
                                corpCode = null;
                                corpName = null;
                            }
                            currentElement = null;
                            textBuffer.setLength(0);
                        }
                    }
                    reader.close();
                }
            }
        } catch (Exception e) {
            throw new DartApiException("DART 기업코드 XML 파싱 실패", e);
        }

        log.info("DART 기업코드 목록 로드 완료: {}개 기업", corpCodeMap.size());
        return corpCodeMap;
    }

    /**
     * API 호출 실패 시 최대 3회 재시도.
     * 각 재시도 사이에 1초 간격을 둔다.
     */
    private <T> T executeWithRetry(String url, Class<T> responseType, String corpCode) {
        RestClientException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                log.debug("DART API 호출 시도 {}/{} (corp_code: {})", attempt, MAX_RETRY, corpCode);
                return restTemplate.getForObject(url, responseType);
            } catch (RestClientException e) {
                lastException = e;
                log.warn("DART API 호출 실패 {}/{} (corp_code: {}): {}", attempt, MAX_RETRY, corpCode, e.getMessage());

                if (attempt < MAX_RETRY) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new DartApiException("DART API 호출 중 인터럽트 발생", ie);
                    }
                }
            }
        }

        throw new DartApiException(
                String.format("DART API 호출 %d회 재시도 실패 (corp_code: %s)", MAX_RETRY, corpCode),
                lastException);
    }
}
