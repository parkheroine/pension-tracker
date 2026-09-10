package com.juyeon.pension_tracker.batch;

import com.juyeon.pension_tracker.domain.change.HoldingChange;
import com.juyeon.pension_tracker.domain.change.HoldingChangeRepository;
import com.juyeon.pension_tracker.domain.disclosure.Disclosure;
import com.juyeon.pension_tracker.domain.disclosure.DisclosureRepository;
import com.juyeon.pension_tracker.domain.stock.Stock;
import com.juyeon.pension_tracker.domain.stock.StockRepository;
import com.juyeon.pension_tracker.infra.DartApiClient;
import com.juyeon.pension_tracker.infra.DartApiException;
import com.juyeon.pension_tracker.infra.EmailAlertService;
import com.juyeon.pension_tracker.infra.dto.DartDisclosureResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisclosureBatchJobTest {

    @InjectMocks
    private DisclosureBatchJob disclosureBatchJob;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private DisclosureRepository disclosureRepository;

    @Mock
    private HoldingChangeRepository holdingChangeRepository;

    @Mock
    private DartApiClient dartApiClient;

    @Mock
    private EmailAlertService emailAlertService;

    @Test
    @DisplayName("신규 공시를 저장하고 변동이 있으면 HoldingChange를 생성한다")
    void collectDisclosures_savesNewDisclosureAndDetectsChange() {
        // given
        Stock stock = Stock.builder().corpCode("00126380").corpName("삼성전자").build();
        given(stockRepository.findAll()).willReturn(List.of(stock));

        // DART API 응답 생성 — 변동폭 0.17%인 공시 1건
        DartDisclosureResponse response = createResponse("20240301000001", "8.50", "0.17");
        given(dartApiClient.getMajorStock("00126380")).willReturn(response);
        given(disclosureRepository.existsById("20240301000001")).willReturn(false);

        // when
        disclosureBatchJob.collectDisclosures();

        // then
        verify(disclosureRepository).save(any(Disclosure.class));
        verify(holdingChangeRepository).save(any(HoldingChange.class));
        verify(emailAlertService).sendChangeAlert(eq(stock), any(HoldingChange.class));
    }

    @Test
    @DisplayName("이미 존재하는 공시(rcept_no 중복)는 저장하지 않는다 — 멱등성 보장")
    void collectDisclosures_skipsExistingDisclosure() {
        // given
        Stock stock = Stock.builder().corpCode("00126380").corpName("삼성전자").build();
        given(stockRepository.findAll()).willReturn(List.of(stock));

        DartDisclosureResponse response = createResponse("20240301000001", "8.50", "0.17");
        given(dartApiClient.getMajorStock("00126380")).willReturn(response);
        // 이미 존재하는 공시
        given(disclosureRepository.existsById("20240301000001")).willReturn(true);

        // when
        disclosureBatchJob.collectDisclosures();

        // then — save가 한 번도 호출되지 않아야 함
        verify(disclosureRepository, never()).save(any(Disclosure.class));
        verify(holdingChangeRepository, never()).save(any(HoldingChange.class));
    }

    @Test
    @DisplayName("변동폭이 0이면 HoldingChange를 생성하지 않는다")
    void collectDisclosures_noChangeWhenStkrtIrdsIsZero() {
        // given
        Stock stock = Stock.builder().corpCode("00126380").corpName("삼성전자").build();
        given(stockRepository.findAll()).willReturn(List.of(stock));

        // stkrt_irds가 "0.00"인 공시
        DartDisclosureResponse response = createResponse("20240301000001", "8.50", "0.00");
        given(dartApiClient.getMajorStock("00126380")).willReturn(response);
        given(disclosureRepository.existsById("20240301000001")).willReturn(false);

        // when
        disclosureBatchJob.collectDisclosures();

        // then — Disclosure는 저장되지만 HoldingChange는 생성되지 않음
        verify(disclosureRepository).save(any(Disclosure.class));
        verify(holdingChangeRepository, never()).save(any(HoldingChange.class));
    }

    @Test
    @DisplayName("한 종목에서 API 호출 실패해도 다른 종목은 정상 처리된다 — 장애 격리")
    void collectDisclosures_isolatesFailure() {
        // given
        Stock failStock = Stock.builder().corpCode("00126380").corpName("삼성전자").build();
        Stock okStock = Stock.builder().corpCode("00164779").corpName("SK하이닉스").build();
        given(stockRepository.findAll()).willReturn(List.of(failStock, okStock));

        // 삼성전자는 API 실패
        given(dartApiClient.getMajorStock("00126380"))
                .willThrow(new DartApiException("API 호출 실패"));

        // SK하이닉스는 정상 응답
        DartDisclosureResponse response = createResponse("20240301000002", "6.20", "-0.15");
        given(dartApiClient.getMajorStock("00164779")).willReturn(response);
        given(disclosureRepository.existsById("20240301000002")).willReturn(false);

        // when
        disclosureBatchJob.collectDisclosures();

        // then — 삼성전자 실패에도 SK하이닉스 공시는 저장됨
        verify(disclosureRepository).save(any(Disclosure.class));
        verify(holdingChangeRepository).save(any(HoldingChange.class));
    }

    @Test
    @DisplayName("DART API 응답에 공시 데이터가 없으면 아무것도 저장하지 않는다")
    void collectDisclosures_emptyResponse() {
        // given
        Stock stock = Stock.builder().corpCode("00126380").corpName("삼성전자").build();
        given(stockRepository.findAll()).willReturn(List.of(stock));

        DartDisclosureResponse emptyResponse = new DartDisclosureResponse();
        given(dartApiClient.getMajorStock("00126380")).willReturn(emptyResponse);

        // when
        disclosureBatchJob.collectDisclosures();

        // then
        verify(disclosureRepository, never()).save(any(Disclosure.class));
    }

    /**
     * 테스트용 DART API 응답 생성 헬퍼
     */
    private DartDisclosureResponse createResponse(String rceptNo, String stkrt, String stkrtIrds) {
        // Reflection으로 내부 필드 세팅 (DTO에 setter가 없으므로)
        try {
            DartDisclosureResponse response = new DartDisclosureResponse();
            var statusField = DartDisclosureResponse.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(response, "000");

            DartDisclosureResponse.DisclosureItem item = new DartDisclosureResponse.DisclosureItem();
            setField(item, "rceptNo", rceptNo);
            setField(item, "rceptDt", "2024-03-01");
            setField(item, "corpCode", "00126380");
            setField(item, "corpName", "삼성전자");
            setField(item, "reportTp", "변동");
            setField(item, "stkqy", "500,000,000");
            setField(item, "stkqyIrds", "10,000,000");
            setField(item, "stkrt", stkrt);
            setField(item, "stkrtIrds", stkrtIrds);
            setField(item, "reportResn", "주식등의 대량보유 변동보고");

            var listField = DartDisclosureResponse.class.getDeclaredField("list");
            listField.setAccessible(true);
            listField.set(response, List.of(item));

            return response;
        } catch (Exception e) {
            throw new RuntimeException("테스트 데이터 생성 실패", e);
        }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
