package com.schaccs.model.payroll;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Runtime-configurable Kenyan statutory parameters.
 *
 * <p>The payroll calculation engine reads these values from the database at
 * run time instead of hard-coding them, so the bursar can update a rate or
 * tax band in the Settings module when the government adjusts it mid-year
 * without changing backend source code (see payroll.md §1).
 */
public final class StatutoryConfig {

    /** A single incremental PAYE band: charge {@code rate} on income up to {@code ceiling}. */
    public static final class PayeBand {
        private BigDecimal ceiling = CurrencyConfig.zero();
        private BigDecimal rate = BigDecimal.ZERO;

        public PayeBand() {}

        public PayeBand(BigDecimal ceiling, BigDecimal rate) {
            this.ceiling = CurrencyConfig.money(ceiling);
            this.rate = rate;
        }

        public BigDecimal getCeiling() { return ceiling; }
        public void setCeiling(BigDecimal v) { this.ceiling = CurrencyConfig.money(v); }

        public BigDecimal getRate() { return rate; }
        public void setRate(BigDecimal rate) { this.rate = rate; }
    }

    private final String id;
    private String label = "Default Kenya Statutory Rates";
    private boolean active = true;

    // PAYE (graduated bands + top rate beyond the last ceiling)
    private List<PayeBand> payeBands = new ArrayList<>();
    private BigDecimal payeTopRate = new BigDecimal("0.35");
    private BigDecimal personalRelief = CurrencyConfig.money(2400);

    // SHIF — absolute 2.75% of gross (no minimum-income floor)
    private BigDecimal shifRate = new BigDecimal("0.0275");

    // Affordable Housing Levy — employee 1.5%, employer 1.5%
    private BigDecimal ahlEmployeeRate = new BigDecimal("0.015");
    private BigDecimal ahlEmployerRate = new BigDecimal("0.015");

    // NSSF — Tier I & Tier II split by pensionable salary ceilings, 6% each
    private BigDecimal nssfTierILower = CurrencyConfig.money(0);
    private BigDecimal nssfTierICeiling = CurrencyConfig.money(7000);
    private BigDecimal nssfTierIICeiling = CurrencyConfig.money(36000);
    private BigDecimal nssfRate = new BigDecimal("0.06");
    private BigDecimal nssfEmployerRate = new BigDecimal("0.06");

    public StatutoryConfig() {
        this.id = UUID.randomUUID().toString();
    }

    private StatutoryConfig(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static StatutoryConfig withId(String id) {
        return new StatutoryConfig(id);
    }

    public String getId() { return id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public List<PayeBand> getPayeBands() { return payeBands; }
    public void setPayeBands(List<PayeBand> payeBands) {
        this.payeBands = payeBands == null ? new ArrayList<>() : payeBands;
    }

    public BigDecimal getPayeTopRate() { return payeTopRate; }
    public void setPayeTopRate(BigDecimal payeTopRate) { this.payeTopRate = payeTopRate; }

    public BigDecimal getPersonalRelief() { return personalRelief; }
    public void setPersonalRelief(BigDecimal personalRelief) { this.personalRelief = CurrencyConfig.money(personalRelief); }

    public BigDecimal getShifRate() { return shifRate; }
    public void setShifRate(BigDecimal shifRate) { this.shifRate = shifRate; }

    public BigDecimal getAhlEmployeeRate() { return ahlEmployeeRate; }
    public void setAhlEmployeeRate(BigDecimal r) { this.ahlEmployeeRate = r; }

    public BigDecimal getAhlEmployerRate() { return ahlEmployerRate; }
    public void setAhlEmployerRate(BigDecimal r) { this.ahlEmployerRate = r; }

    public BigDecimal getNssfTierILower() { return nssfTierILower; }
    public void setNssfTierILower(BigDecimal v) { this.nssfTierILower = CurrencyConfig.money(v); }

    public BigDecimal getNssfTierICeiling() { return nssfTierICeiling; }
    public void setNssfTierICeiling(BigDecimal v) { this.nssfTierICeiling = CurrencyConfig.money(v); }

    public BigDecimal getNssfTierIICeiling() { return nssfTierIICeiling; }
    public void setNssfTierIICeiling(BigDecimal v) { this.nssfTierIICeiling = CurrencyConfig.money(v); }

    public BigDecimal getNssfRate() { return nssfRate; }
    public void setNssfRate(BigDecimal nssfRate) { this.nssfRate = nssfRate; }

    public BigDecimal getNssfEmployerRate() { return nssfEmployerRate; }
    public void setNssfEmployerRate(BigDecimal nssfEmployerRate) { this.nssfEmployerRate = nssfEmployerRate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatutoryConfig that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
