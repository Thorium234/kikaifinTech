package com.schaccs.model.finance;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Objects;

public class Asset {

    public enum AssetStatus {
        IN_USE("In Use"),
        DISPOSED("Disposed"),
        UNDER_MAINTENANCE("Under Maintenance");

        private final String displayName;
        AssetStatus(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private final String id;
    private String categoryId;
    private String assetCode;
    private String name;
    private String description;
    private LocalDate purchaseDate;
    private BigDecimal purchaseCost = CurrencyConfig.zero();
    private BigDecimal currentValue = CurrencyConfig.zero();
    private BigDecimal salvageValue = CurrencyConfig.zero();
    private String location;
    private String condition;
    private AssetStatus status = AssetStatus.IN_USE;

    public Asset() {
        this.id = UUID.randomUUID().toString();
    }

    private Asset(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Asset withId(String id) { return new Asset(id); }

    public String getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public BigDecimal getPurchaseCost() { return purchaseCost; }
    public void setPurchaseCost(BigDecimal purchaseCost) { this.purchaseCost = CurrencyConfig.money(purchaseCost); }
    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = CurrencyConfig.money(currentValue); }
    public BigDecimal getSalvageValue() { return salvageValue; }
    public void setSalvageValue(BigDecimal salvageValue) { this.salvageValue = CurrencyConfig.money(salvageValue); }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }

    @Override
    public String toString() { return assetCode + " - " + name; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Asset that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
