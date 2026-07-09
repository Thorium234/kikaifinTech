package com.schaccs.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PaymentValidator {

    public List<String> validateAmount(String rawAmount) {
        List<String> errors = new ArrayList<>();
        if (rawAmount == null || rawAmount.isBlank()) {
            errors.add("Enter an amount.");
            return errors;
        }
        try {
            String cleaned = rawAmount.replace(",", "").replace(" ", "").trim();
            BigDecimal amount = new BigDecimal(cleaned);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Amount must be greater than zero.");
            }
        } catch (NumberFormatException ex) {
            errors.add("Invalid amount format.");
        }
        return errors;
    }
}
