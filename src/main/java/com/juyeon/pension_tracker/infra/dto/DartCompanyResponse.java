package com.juyeon.pension_tracker.infra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DART 기업개황 API(company.json) 응답 DTO.
 * 기업명, 업종명 등 기본 정보를 담는다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DartCompanyResponse {

    private String status;
    private String message;

    @JsonProperty("corp_code")
    private String corpCode;

    @JsonProperty("corp_name")
    private String corpName;

    // 업종코드 (DART API는 업종명이 아닌 업종코드만 제공)
    @JsonProperty("induty_code")
    private String indutyCode;
}
