# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NPS Watch (pension-tracker) - 국민연금 대량보유 주식 변동 추적 시스템. DART Open API에서 공시 데이터를 수집하고, 보유비율 변동을 감지하여 이메일 알림을 발송한다.

## Tech Stack

- Java 25 / Spring Boot 3.4.5 / Gradle 9.5
- Spring Data JPA + PostgreSQL
- Spring Data Redis
- Spring Mail (Gmail SMTP)
- OpenCSV (CSV 파싱)
- Lombok

## Build & Run

```bash
./gradlew compileJava          # 컴파일
./gradlew test                 # 테스트 실행
./gradlew bootRun              # 애플리케이션 실행
./gradlew build                # 전체 빌드
```

## Environment Variables

필수 환경변수 (application.yml에서 `${VAR}` 형태로 참조):

| 변수 | 설명 |
|------|------|
| DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD | PostgreSQL 연결 |
| REDIS_HOST, REDIS_PORT | Redis 연결 |
| DART_API_KEY | DART Open API 인증키 |
| MAIL_USERNAME, MAIL_PASSWORD | Gmail SMTP 계정/앱 비밀번호 |
| ALERT_EMAIL_TO | 변동 알림 수신 이메일 |

프로파일: `local` (기본, ddl-auto=update), `prod` (ddl-auto=validate)

## Architecture

```
com.juyeon.pension_tracker
├── domain/          # 엔티티 + Repository (JPA)
│   ├── stock/       # Stock (PK: corp_code) - 종목
│   ├── disclosure/  # Disclosure (PK: rcept_no) - DART 공시
│   ├── change/      # HoldingChange (PK: auto id) - 보유비율 변동이력
│   └── common/      # BaseTimeEntity (createdAt 자동관리)
├── batch/           # 배치 잡
│   ├── CsvDataLoader        # 앱 시작 시 CSV → Stock 초기 적재 (1회성)
│   └── DisclosureBatchJob   # 매일 9시 DART API 수집 + 변동 감지
├── api/             # REST API 계층
│   ├── controller/  # StockController, StockService
│   ├── dto/         # Response DTOs
│   └── common/      # ApiResponse<T>, GlobalExceptionHandler, NotFoundException
└── infra/           # 외부 시스템 연동
    ├── DartApiClient        # DART API 호출 (retry 3회)
    ├── EmailAlertService    # Gmail 알림 발송
    └── dto/                 # DART API 응답 DTOs
```

## Key Data Flow

1. **초기 적재**: CSV 파일(`resources/data/nps_stock.csv`) → DART 기업개황 API로 corp_code 조회 → Stock 저장
2. **일일 배치** (`@Scheduled cron 0 0 9 * * *`): Stock 순회 → DART majorstock API 호출 → 신규 Disclosure 저장 → stkrt_irds ≠ 0이면 HoldingChange 저장 + 이메일 알림
3. **REST API**: `/api/stocks`, `/api/stocks/{corpCode}/disclosures`, `/api/stocks/{corpCode}/changes`, `/api/sectors/summary`

## ERD Relationships

- Stock (1) → (N) Disclosure: corp_code FK
- Stock (1) → (N) HoldingChange: corp_code FK

## Conventions

- 모든 API 응답은 `ApiResponse<T>` 래퍼 사용 (`{ success, data, message }`)
- 엔티티는 `@Builder` + `@NoArgsConstructor(PROTECTED)` 패턴
- DART API 호출 실패 시 `DartApiException` throw, 배치에서는 개별 종목 skip
- 이메일 발송 실패 시 배치 중단 없이 로그만 남김
