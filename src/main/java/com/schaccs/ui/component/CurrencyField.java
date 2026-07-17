package com.schaccs.ui.component;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.util.CurrencyUtil;
import javafx.scene.control.TextField;

import java.math.BigDecimal;

public class CurrencyField extends TextField {

    public CurrencyField() {
        setPromptText("0.00");
        textProperty().addListener((obs, old, val) -> {
            if (val != null && !val.matches("[0-9,]*(\\.[0-9]{0,2})?")) {
                setText(old);
            }
        });
    }

    public BigDecimal getAmount() {
        return CurrencyUtil.parse(getText());
    }

    public void setAmount(BigDecimal amount) {
        setText(CurrencyConfig.formatPlain(amount));
    }
}
