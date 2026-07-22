package com.schaccs.model.finance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class FiscalYear {

    private final String id;
    private int year;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean isOpen = true;
    private boolean isClosed = false;
    private LocalDateTime closedAt;
    private String closedBy;

    public FiscalYear() {
        this.id = UUID.randomUUID().toString();
    }

    private FiscalYear(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static FiscalYear withId(String id) {
        return new FiscalYear(id);
    }

    public String getId() { return id; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean open) { isOpen = open; }
    public boolean isClosed() { return isClosed; }
    public void setClosed(boolean closed) { isClosed = closed; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }

    @Override
    public String toString() { return "FY " + year; }
}
