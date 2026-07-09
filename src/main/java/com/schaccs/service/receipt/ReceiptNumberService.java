package com.schaccs.service.receipt;

import com.schaccs.util.NumberGenerator;

public class ReceiptNumberService {

    public long next() {
        return NumberGenerator.nextReceiptNumber();
    }
}
