package com.schaccs.validation;

import com.schaccs.enums.PaymentMode;
import com.schaccs.model.student.Student;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReceiptValidator {

    public List<String> validate(Student student, BigDecimal amount, PaymentMode mode, String bankRef) {
        List<String> errors = new ArrayList<>();
        if (student == null) {
            errors.add("Select a student before receiving payment.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Payment amount must be greater than zero.");
        }
        if (mode == null) {
            errors.add("Payment mode is required.");
        } else if (!mode.isAllowed()) {
            errors.add(mode.getDisplayName() + " is not accepted. "
                    + "Please use bank pay-in slip only.");
        }
        if (mode == PaymentMode.BANK_SLIP || mode == PaymentMode.MPESA || mode == PaymentMode.CHEQUE) {
            if (bankRef == null || bankRef.isBlank()) {
                errors.add("Bank / payment reference is required for " + mode.getDisplayName() + ".");
            }
        }
        return errors;
    }
}
