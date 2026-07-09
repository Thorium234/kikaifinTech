package com.schaccs.util;

import com.schaccs.config.AppConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;

/**
 * Formats an official school receipt as plain text (print/preview ready).
 */
public final class ReceiptPrinter {

    private ReceiptPrinter() {
    }

    public static String format(Receipt receipt) {
        SchoolProfile school = AppConfig.getInstance().getSchoolProfile();
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(52)).append('\n');
        sb.append(center(school.getMinistry(), 52)).append('\n');
        sb.append(center(school.getSchoolName(), 52)).append('\n');
        sb.append(center(school.getLocation(), 52)).append('\n');
        sb.append(center("OFFICIAL FEE RECEIPT", 52)).append('\n');
        sb.append("=".repeat(52)).append('\n');
        sb.append(String.format("Receipt No: %-12s  Date: %s%n",
                receipt.getReceiptNumberDisplay(), DateUtil.format(receipt.getDate())));
        sb.append(String.format("Student:    %s%n", receipt.getStudentName()));
        sb.append(String.format("Adm No:     %-12s  Class: %s%n",
                receipt.getAdmissionNumber(), receipt.getClassLabel()));
        sb.append(String.format("Mode:       %s%n", receipt.getPaymentMode().getDisplayName()));
        if (receipt.getBankReference() != null && !receipt.getBankReference().isBlank()) {
            sb.append(String.format("Reference:  %s%n", receipt.getBankReference()));
        }
        sb.append("-".repeat(52)).append('\n');
        sb.append(String.format("%-28s %20s%n", "Vote Head", "Amount (KSh)"));
        sb.append("-".repeat(52)).append('\n');
        for (ReceiptLine line : receipt.getLines()) {
            sb.append(String.format("%-28s %20s%n",
                    line.getVoteheadName(),
                    CurrencyUtil.formatPlain(line.getAmount())));
        }
        sb.append("-".repeat(52)).append('\n');
        sb.append(String.format("%-28s %20s%n", "TOTAL PAID",
                CurrencyUtil.formatPlain(receipt.getAmount())));
        sb.append(String.format("In words: %s%n", CurrencyUtil.toWords(receipt.getAmount())));
        sb.append("=".repeat(52)).append('\n');
        sb.append(String.format("Received by: %s%n", nullToEmpty(receipt.getReceivedBy())));
        sb.append(String.format("Principal:   %s%n", school.getPrincipal()));
        sb.append('\n');
        sb.append("Bank: ").append(school.getBankName()).append('\n');
        sb.append("A/C:  ").append(school.getBankAccount()).append('\n');
        sb.append("PayBill: ").append(school.getPayBill())
                .append("  Acc: ").append(school.getPayBillAccount()).append('\n');
        sb.append('\n');
        sb.append(school.getCashPolicy()).append('\n');
        sb.append("=".repeat(52)).append('\n');
        return sb.toString();
    }

    private static String center(String text, int width) {
        if (text == null) {
            text = "";
        }
        if (text.length() >= width) {
            return text;
        }
        int pad = (width - text.length()) / 2;
        return " ".repeat(pad) + text;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
