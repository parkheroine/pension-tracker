package com.juyeon.pension_tracker.infra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DART 대량보유 상황보고 API(majorstock) 응답 DTO.
 * 최상위에 status/message가 있고, list 안에 개별 공시 데이터가 들어온다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DartDisclosureResponse {

    private String status;
    private String message;
    private List<DisclosureItem> list;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DisclosureItem {
        @JsonProperty("rcept_no")
        private String rceptNo;

        @JsonProperty("rcept_dt")
        private String rceptDt;

        @JsonProperty("corp_code")
        private String corpCode;

        @JsonProperty("corp_name")
        private String corpName;

        @JsonProperty("report_tp")
        private String reportTp;

        private String repror;

        private String stkqy;

        @JsonProperty("stkqy_irds")
        private String stkqyIrds;

        private String stkrt;

        @JsonProperty("stkrt_irds")
        private String stkrtIrds;

        @JsonProperty("ctr_stkqy")
        private String ctrStkqy;

        @JsonProperty("ctr_stkrt")
        private String ctrStkrt;

        @JsonProperty("report_resn")
        private String reportResn;
    }
}
