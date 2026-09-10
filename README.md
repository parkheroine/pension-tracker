# NPS Watch - 국민연금 대량보유 주식 변동 추적 시스템

국민연금이 대량보유하고 있는 주식의 변동을 자동으로 추적하고, 보유비율 변동 발생 시 이메일로 알림을 발송하는 시스템입니다.

[DART Open API](https://opendart.fss.or.kr)에서 공시 데이터를 수집하여 변동을 감지합니다.

## 주요 기능

- **공시 자동 수집** — 매일 9시 DART API에서 대량보유 공시 데이터를 수집
- **변동 감지 및 알림** — 보유비율 변동(증가/감소) 발생 시 이메일 자동 발송
- **종목 자동 발견** — 매일 7시 전체 상장사를 스캔하여 국민연금 보유 종목을 자동 추가
- **업종별 분석** — 업종별 평균 보유비율 집계
- **Redis 캐싱** — 업종별 요약(1h), 종목 목록(24h) TTL 기반 캐시

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 3.4.5 |
| ORM | Spring Data JPA |
| Database | PostgreSQL |
| Cache | Redis |
| Mail | Spring Mail (Gmail SMTP) |
| Build | Gradle 9.5 |
| Test | JUnit 5, Mockito |

## 아키텍처

```
com.juyeon.pension_tracker
├── domain/          # 엔티티 + Repository
│   ├── stock/       # Stock — 종목 (PK: corp_code)
│   ├── disclosure/  # Disclosure — DART 공시 (PK: rcept_no)
│   ├── change/      # HoldingChange — 보유비율 변동이력
│   └── common/      # BaseTimeEntity (createdAt 자동관리)
├── batch/           # 배치 잡
│   ├── CsvDataLoader          # 앱 시작 시 CSV → Stock 초기 적재
│   ├── DisclosureBatchJob     # 매일 9시 공시 수집 + 변동 감지
│   └── NpsStockDiscoveryJob   # 매일 7시 신규 종목 자동 발견
├── api/             # REST API
│   ├── controller/  # StockController, StockService
│   ├── dto/         # 요청/응답 DTO
│   └── common/      # ApiResponse<T>, GlobalExceptionHandler
└── infra/           # 외부 시스템 연동
    ├── DartApiClient          # DART API 호출 (재시도 3회)
    ├── EmailAlertService      # Gmail 알림 발송
    ├── RedisConfig            # 캐시 설정
    └── WebConfig              # CORS 설정
```

## ERD

```
┌──────────────┐       ┌──────────────────┐       ┌──────────────────┐
│    Stock     │       │   Disclosure     │       │  HoldingChange   │
├──────────────┤       ├──────────────────┤       ├──────────────────┤
│ corp_code PK │──1:N─▶│ rcept_no PK      │       │ id PK (auto)     │
│ corp_name    │       │ corp_code FK     │       │ corp_code FK     │
│ sector       │       │ rcept_dt         │  ◀────│ before_stkrt     │
│ created_at   │──1:N─▶│ stkqy / stkrt    │       │ after_stkrt      │
│              │       │ stkqy_irds       │       │ change_amount    │
│              │       │ stkrt_irds       │       │ detected_at      │
│              │       │ report_tp        │       │ alerted          │
│              │       │ created_at       │       │ created_at       │
└──────────────┘       └──────────────────┘       └──────────────────┘
```

## API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/stocks` | 전체 종목 조회 (페이지네이션) |
| POST | `/api/stocks` | 종목 추가 |
| GET | `/api/stocks/{corpCode}/disclosures` | 특정 종목 공시 이력 |
| GET | `/api/stocks/{corpCode}/changes` | 특정 종목 변동 이력 |
| GET | `/api/changes/recent` | 최근 변동 이력 (전체) |
| GET | `/api/sectors/summary` | 업종별 평균 보유비율 |
| POST | `/api/batch/collect` | 공시 수집 배치 수동 실행 |
| POST | `/api/batch/discover` | 종목 발견 배치 수동 실행 |

## 데이터 플로우

```
1. 초기 적재 (앱 시작)
   nps_stock.csv → DART 기업개황 API → Stock 테이블 저장

2. 종목 발견 (매일 07:00)
   CORPCODE.xml → 상장사 500건/일 스캔 → DART majorstock API → 신규 종목 추가

3. 공시 수집 (매일 09:00)
   Stock 순회 → DART majorstock API → Disclosure 저장
   → 보유비율 변동 감지 → HoldingChange 저장 + 이메일 알림
```
