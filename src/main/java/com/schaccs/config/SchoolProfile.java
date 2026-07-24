package com.schaccs.config;

import java.math.BigDecimal;

/**
 * School identity and banking details for Friends School Kikai Boys.
 */
public final class SchoolProfile {

    private String schoolName = "Friends School Kikai Boys Secondary School";
    private String location = "P.O. Box 345-50202, Chwele";
    private String ministry = "Republic of Kenya, Ministry of Education";
    private String principal = "Mr. Kituyi S.A.";
    private String bankName = "National Bank Bungoma Branch";
    private String bankAccount = "0121054619700";
    private String payBill = "7230546";
    private String payBillAccount = "1260057495";
    private String cashPolicy = "NO CASH WILL BE ACCEPTED EXCEPT BANK PAY IN SLIP BY THE SCHOOL PRINCIPAL - MR. KITUYI S.A.";
    private int academicYear = 2026;
    private long nextReceiptNumber = 21571;
    private long nextVoucherNumber = 1001;
    private boolean siblingDiscountEnabled = false;
    private BigDecimal siblingDiscountRate = CurrencyConfig.money("0.10");
    private String logoPath;
    private String stampPath;
    private String signaturePath;
    private boolean pdfStampEnabled = true;
    private long nextProcurementRequestNumber = 1001;
    private long nextTenderNumber = 1001;
    private long nextContractNumber = 1001;
    private long nextSupplierNumber = 1001;

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getMinistry() {
        return ministry;
    }

    public void setMinistry(String ministry) {
        this.ministry = ministry;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getPayBill() {
        return payBill;
    }

    public void setPayBill(String payBill) {
        this.payBill = payBill;
    }

    public String getPayBillAccount() {
        return payBillAccount;
    }

    public void setPayBillAccount(String payBillAccount) {
        this.payBillAccount = payBillAccount;
    }

    public String getCashPolicy() {
        return cashPolicy;
    }

    public void setCashPolicy(String cashPolicy) {
        this.cashPolicy = cashPolicy;
    }

    public int getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(int academicYear) {
        this.academicYear = academicYear;
    }

    public long getNextReceiptNumber() {
        return nextReceiptNumber;
    }

    public void setNextReceiptNumber(long nextReceiptNumber) {
        this.nextReceiptNumber = nextReceiptNumber;
    }

    public synchronized long allocateReceiptNumber() {
        return nextReceiptNumber++;
    }

    public long getNextVoucherNumber() {
        return nextVoucherNumber;
    }

    public void setNextVoucherNumber(long nextVoucherNumber) {
        this.nextVoucherNumber = nextVoucherNumber;
    }

    public synchronized long allocateVoucherNumber() {
        return nextVoucherNumber++;
    }

    public boolean isSiblingDiscountEnabled() {
        return siblingDiscountEnabled;
    }

    public void setSiblingDiscountEnabled(boolean siblingDiscountEnabled) {
        this.siblingDiscountEnabled = siblingDiscountEnabled;
    }

    public BigDecimal getSiblingDiscountRate() {
        return siblingDiscountRate;
    }

    public void setSiblingDiscountRate(BigDecimal siblingDiscountRate) {
        this.siblingDiscountRate = CurrencyConfig.money(siblingDiscountRate);
    }

    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }

    public String getStampPath() {
        return stampPath;
    }

    public void setStampPath(String stampPath) {
        this.stampPath = stampPath;
    }

    public String getSignaturePath() {
        return signaturePath;
    }

    public void setSignaturePath(String signaturePath) {
        this.signaturePath = signaturePath;
    }

    public boolean isPdfStampEnabled() {
        return pdfStampEnabled;
    }

    public void setPdfStampEnabled(boolean pdfStampEnabled) {
        this.pdfStampEnabled = pdfStampEnabled;
    }

    public long getNextProcurementRequestNumber() {
        return nextProcurementRequestNumber;
    }

    public void setNextProcurementRequestNumber(long nextProcurementRequestNumber) {
        this.nextProcurementRequestNumber = nextProcurementRequestNumber;
    }

    public synchronized long allocateProcurementRequestNumber() {
        return nextProcurementRequestNumber++;
    }

    public long getNextTenderNumber() {
        return nextTenderNumber;
    }

    public void setNextTenderNumber(long nextTenderNumber) {
        this.nextTenderNumber = nextTenderNumber;
    }

    public synchronized long allocateTenderNumber() {
        return nextTenderNumber++;
    }

    public long getNextContractNumber() {
        return nextContractNumber;
    }

    public void setNextContractNumber(long nextContractNumber) {
        this.nextContractNumber = nextContractNumber;
    }

    public synchronized long allocateContractNumber() {
        return nextContractNumber++;
    }

    public long getNextSupplierNumber() {
        return nextSupplierNumber;
    }

    public void setNextSupplierNumber(long nextSupplierNumber) {
        this.nextSupplierNumber = nextSupplierNumber;
    }

    public synchronized long allocateSupplierNumber() {
        return nextSupplierNumber++;
    }
}
