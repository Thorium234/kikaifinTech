package com.schaccs.config;

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
}
