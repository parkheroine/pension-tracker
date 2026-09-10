package com.juyeon.pension_tracker.domain.stock;

import com.juyeon.pension_tracker.domain.change.HoldingChange;
import com.juyeon.pension_tracker.domain.common.BaseTimeEntity;
import com.juyeon.pension_tracker.domain.disclosure.Disclosure;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 국민연금이 보유한 종목 엔티티.
 * corp_code(DART 기업 고유번호)를 PK로 사용한다.
 */
@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseTimeEntity {

    @Id
    @Column(name = "corp_code", length = 8)
    private String corpCode;

    @Column(name = "corp_name", length = 100, nullable = false)
    private String corpName;

    @Column(name = "sector", length = 100)
    private String sector;

    // Stock 1 : N Disclosure
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Disclosure> disclosures = new ArrayList<>();

    // Stock 1 : N HoldingChange
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HoldingChange> holdingChanges = new ArrayList<>();

    @Builder
    public Stock(String corpCode, String corpName, String sector) {
        this.corpCode = corpCode;
        this.corpName = corpName;
        this.sector = sector;
    }
}
