package com.juyeon.pension_tracker.api.controller;

import com.juyeon.pension_tracker.api.common.NotFoundException;
import com.juyeon.pension_tracker.api.dto.HoldingChangeResponse;
import com.juyeon.pension_tracker.api.dto.StockResponse;
import com.juyeon.pension_tracker.domain.change.HoldingChange;
import com.juyeon.pension_tracker.domain.change.HoldingChangeRepository;
import com.juyeon.pension_tracker.domain.disclosure.Disclosure;
import com.juyeon.pension_tracker.domain.disclosure.DisclosureRepository;
import com.juyeon.pension_tracker.domain.stock.Stock;
import com.juyeon.pension_tracker.domain.stock.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @InjectMocks
    private StockService stockService;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private DisclosureRepository disclosureRepository;

    @Mock
    private HoldingChangeRepository holdingChangeRepository;

    @Test
    @DisplayName("전체 종목 리스트를 페이지네이션으로 조회한다")
    void getStocks() {
        // given
        Stock stock = Stock.builder()
                .corpCode("00126380")
                .corpName("삼성전자")
                .sector("264")
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        given(stockRepository.findAll(pageable))
                .willReturn(new PageImpl<>(List.of(stock), pageable, 1));

        // when
        Page<StockResponse> result = stockService.getStocks(pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCorpCode()).isEqualTo("00126380");
        assertThat(result.getContent().get(0).getCorpName()).isEqualTo("삼성전자");
    }

    @Test
    @DisplayName("존재하지 않는 종목 조회 시 NotFoundException을 던진다")
    void getDisclosures_notFound() {
        // given
        given(stockRepository.existsById("99999999")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> stockService.getDisclosures("99999999", PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99999999");
    }

    @Test
    @DisplayName("특정 종목의 변동 이력을 조회한다")
    void getChanges() {
        // given
        String corpCode = "00126380";
        Stock stock = Stock.builder().corpCode(corpCode).corpName("삼성전자").build();
        HoldingChange change = HoldingChange.builder()
                .stock(stock)
                .beforeStkrt(new BigDecimal("8.33"))
                .afterStkrt(new BigDecimal("8.50"))
                .changeAmount(new BigDecimal("0.17"))
                .detectedAt(LocalDateTime.now())
                .build();

        given(stockRepository.existsById(corpCode)).willReturn(true);
        given(holdingChangeRepository.findByStockCorpCodeOrderByDetectedAtDesc(corpCode))
                .willReturn(List.of(change));

        // when
        List<HoldingChangeResponse> result = stockService.getChanges(corpCode);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBeforeStkrt()).isEqualByComparingTo("8.33");
        assertThat(result.get(0).getAfterStkrt()).isEqualByComparingTo("8.50");
        assertThat(result.get(0).getChangeAmount()).isEqualByComparingTo("0.17");
    }

    @Test
    @DisplayName("업종별 평균 보유비율을 집계한다")
    void getSectorSummary() {
        // given
        Stock stock1 = Stock.builder().corpCode("00126380").corpName("삼성전자").sector("264").build();
        Stock stock2 = Stock.builder().corpCode("00164779").corpName("SK하이닉스").sector("264").build();

        Disclosure d1 = Disclosure.builder()
                .rceptNo("20240101000001").stock(stock1).stkrt(new BigDecimal("8.50")).build();
        Disclosure d2 = Disclosure.builder()
                .rceptNo("20240101000002").stock(stock2).stkrt(new BigDecimal("9.50")).build();

        given(stockRepository.findAll()).willReturn(List.of(stock1, stock2));
        given(disclosureRepository.findTopByStockCorpCodeOrderByRceptDtDesc("00126380"))
                .willReturn(Optional.of(d1));
        given(disclosureRepository.findTopByStockCorpCodeOrderByRceptDtDesc("00164779"))
                .willReturn(Optional.of(d2));

        // when
        var result = stockService.getSectorSummary();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSector()).isEqualTo("264");
        assertThat(result.get(0).getAvgStkrt()).isEqualByComparingTo("9.00"); // (8.50 + 9.50) / 2
        assertThat(result.get(0).getStockCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 종목의 변동 이력 조회 시 NotFoundException을 던진다")
    void getChanges_notFound() {
        // given
        given(stockRepository.existsById("99999999")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> stockService.getChanges("99999999"))
                .isInstanceOf(NotFoundException.class);
    }
}
