package com.schaccs.model.finance;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Objects;

public class DepreciationSchedule {

    private final String id;
    private String assetId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal depreciationAmount = CurrencyConfig.zero();
    private BigDecimal accumulatedDepreciation = CurrencyConfig.zero();
    private BigDecimal netBookValue = CurrencyConfig.zero();

    public DepreciationSchedule() {
        this.id = UUID.randomUUID().toString();
    }

    private DepreciationSchedule(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static DepreciationSchedule withId(String id) { return new DepreciationSchedule(id); }

    public String getId() { return id; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public BigDecimal getDepreciationAmount() { return depreciationAmount; }
    public void setDepreciationAmount(BigDecimal depreciationAmount) { this.depreciationAmount = CurrencyConfig.money(depreciationAmount); }
    public BigDecimal getAccumulatedDepreciation() { return accumulatedDepreciation; }
    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) { this.accumulatedDepreciation = CurrencyConfig.money(accumulatedDepreciation); }
    public BigDecimal getNetBookValue() { return netBookValue; }
    public void setNetBookValue(BigDecimal netBookValue) { this.netBookValue = CurrencyConfig.money(netBookValue); }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DepreciationSchedule that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
