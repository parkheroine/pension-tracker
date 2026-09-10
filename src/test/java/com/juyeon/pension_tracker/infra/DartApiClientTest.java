package com.juyeon.pension_tracker.infra;

import com.juyeon.pension_tracker.infra.dto.DartCompanyResponse;
import com.juyeon.pension_tracker.infra.dto.DartDisclosureResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DartApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private DartApiClient dartApiClient;

    private void initClient() {
        dartApiClient = new DartApiClient(restTemplate, "test-key", "https://opendart.fss.or.kr/api");
    }

    @Test
    @DisplayName("정상 응답(status=000) 시 DartDisclosureResponse를 반환한다")
    void getMajorStock_success() throws Exception {
        // given
        initClient();
        DartDisclosureResponse mockResponse = new DartDisclosureResponse();
        setField(mockResponse, "status", "000");

        given(restTemplate.getForObject(anyString(), eq(DartDisclosureResponse.class)))
                .willReturn(mockResponse);

        // when
        DartDisclosureResponse result = dartApiClient.getMajorStock("00126380");

        // then
        assertThat(result.getStatus()).isEqualTo("000");
    }

    @Test
    @DisplayName("status가 013(데이터 없음)이면 예외 없이 응답을 반환한다")
    void getMajorStock_noData() throws Exception {
        // given
        initClient();
        DartDisclosureResponse mockResponse = new DartDisclosureResponse();
        setField(mockResponse, "status", "013");

        given(restTemplate.getForObject(anyString(), eq(DartDisclosureResponse.class)))
                .willReturn(mockResponse);

        // when
        DartDisclosureResponse result = dartApiClient.getMajorStock("00126380");

        // then
        assertThat(result.getStatus()).isEqualTo("013");
    }

    @Test
    @DisplayName("status가 에러 코드이면 DartApiException을 던진다")
    void getMajorStock_errorStatus() throws Exception {
        // given
        initClient();
        DartDisclosureResponse mockResponse = new DartDisclosureResponse();
        setField(mockResponse, "status", "020");
        setField(mockResponse, "message", "미등록 API KEY");

        given(restTemplate.getForObject(anyString(), eq(DartDisclosureResponse.class)))
                .willReturn(mockResponse);

        // when & then
        assertThatThrownBy(() -> dartApiClient.getMajorStock("00126380"))
                .isInstanceOf(DartApiException.class)
                .hasMessageContaining("020");
    }

    @Test
    @DisplayName("API 호출 실패 시 3회 재시도 후 DartApiException을 던진다")
    void getMajorStock_retryAndFail() {
        // given
        initClient();
        given(restTemplate.getForObject(anyString(), eq(DartDisclosureResponse.class)))
                .willThrow(new RestClientException("Connection refused"));

        // when & then
        assertThatThrownBy(() -> dartApiClient.getMajorStock("00126380"))
                .isInstanceOf(DartApiException.class)
                .hasMessageContaining("3회 재시도 실패");

        // 정확히 3회 호출되었는지 확인
        verify(restTemplate, times(3)).getForObject(anyString(), eq(DartDisclosureResponse.class));
    }

    @Test
    @DisplayName("첫 번째 호출 실패 후 두 번째 호출에서 성공하면 정상 반환한다")
    void getMajorStock_retryAndSucceed() throws Exception {
        // given
        initClient();
        DartDisclosureResponse mockResponse = new DartDisclosureResponse();
        setField(mockResponse, "status", "000");

        given(restTemplate.getForObject(anyString(), eq(DartDisclosureResponse.class)))
                .willThrow(new RestClientException("Timeout"))
                .willReturn(mockResponse);

        // when
        DartDisclosureResponse result = dartApiClient.getMajorStock("00126380");

        // then
        assertThat(result.getStatus()).isEqualTo("000");
        verify(restTemplate, times(2)).getForObject(anyString(), eq(DartDisclosureResponse.class));
    }

    @Test
    @DisplayName("기업개황 API 정상 호출 시 DartCompanyResponse를 반환한다")
    void getCompanyInfo_success() throws Exception {
        // given
        initClient();
        DartCompanyResponse mockResponse = new DartCompanyResponse();
        setField(mockResponse, "status", "000");
        setField(mockResponse, "corpName", "삼성전자");
        setField(mockResponse, "indutyCode", "264");

        given(restTemplate.getForObject(anyString(), eq(DartCompanyResponse.class)))
                .willReturn(mockResponse);

        // when
        DartCompanyResponse result = dartApiClient.getCompanyInfo("00126380");

        // then
        assertThat(result.getCorpName()).isEqualTo("삼성전자");
        assertThat(result.getIndutyCode()).isEqualTo("264");
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
