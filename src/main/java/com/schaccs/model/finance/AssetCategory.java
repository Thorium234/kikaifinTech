package com.schaccs.model.finance;

import java.util.UUID;

public class AssetCategory {

    public enum DepreciationMethod {
        STRAIGHT_LINE("Straight Line"),
        REDUCING_BALANCE("Reducing Balance");

        private final String displayName;
        DepreciationMethod(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private final String id;
    private String name;
    private DepreciationMethod depreciationMethod = DepreciationMethod.STRAIGHT_LINE;
    private int usefulLifeYears;
    private double salvageValuePercent;

    public AssetCategory() {
        this.id = UUID.randomUUID().toString();
    }

    private AssetCategory(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static AssetCategory withId(String id) { return new AssetCategory(id); }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DepreciationMethod getDepreciationMethod() { return depreciationMethod; }
    public void setDepreciationMethod(DepreciationMethod depreciationMethod) { this.depreciationMethod = depreciationMethod; }
    public int getUsefulLifeYears() { return usefulLifeYears; }
    public void setUsefulLifeYears(int usefulLifeYears) { this.usefulLifeYears = usefulLifeYears; }
    public double getSalvageValuePercent() { return salvageValuePercent; }
    public void setSalvageValuePercent(double salvageValuePercent) { this.salvageValuePercent = salvageValuePercent; }
}
