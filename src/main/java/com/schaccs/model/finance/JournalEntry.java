package com.schaccs.model.finance;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

/**
 * Double-entry journal: one or more debit lines must equal credit lines.
 */
public class JournalEntry {

    private final String id;
    private LocalDate date;
    private String reference;
    private String narration;
    private final List<JournalLine> lines = new ArrayList<>();

    public JournalEntry() {
        this.id = UUID.randomUUID().toString();
        this.date = LocalDate.now();
    }

    public String getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public List<JournalLine> getLines() {
        return lines;
    }

    public void addLine(AccountType account, String voteheadCode, BigDecimal debit, BigDecimal credit, String description) {
        lines.add(new JournalLine(account, voteheadCode, debit, credit, description));
    }

    public BigDecimal totalDebits() {
        return lines.stream().map(JournalLine::getDebit).reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    public BigDecimal totalCredits() {
        return lines.stream().map(JournalLine::getCredit).reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    public boolean isBalanced() {
        return totalDebits().compareTo(totalCredits()) == 0;
    }

    public static final class JournalLine {
        private final AccountType accountType;
        private final String voteheadCode;
        private final BigDecimal debit;
        private final BigDecimal credit;
        private final String description;

        public JournalLine(AccountType accountType, String voteheadCode,
                           BigDecimal debit, BigDecimal credit, String description) {
            this.accountType = accountType;
            this.voteheadCode = voteheadCode;
            this.debit = CurrencyConfig.money(debit);
            this.credit = CurrencyConfig.money(credit);
            this.description = description;
        }

        public AccountType getAccountType() {
            return accountType;
        }

        public String getVoteheadCode() {
            return voteheadCode;
        }

        public BigDecimal getDebit() {
            return debit;
        }

        public BigDecimal getCredit() {
            return credit;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JournalLine that)) return false;
            return accountType == that.accountType
                    && Objects.equals(voteheadCode, that.voteheadCode)
                    && Objects.equals(debit, that.debit)
                    && Objects.equals(credit, that.credit)
                    && Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountType, voteheadCode, debit, credit, description);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JournalEntry that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
