package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV16ProcurementModule implements SchemaMigration {

    @Override
    public int version() {
        return 16;
    }

    @Override
    public String description() {
        return "Procurement and Tender Management module — suppliers, requests, tenders, bids, evaluations, awards, contracts, approvals";
    }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS suppliers (
                    id TEXT PRIMARY KEY,
                    supplier_number TEXT NOT NULL UNIQUE,
                    business_name TEXT NOT NULL,
                    contact_person TEXT,
                    email TEXT,
                    phone TEXT,
                    kra_pin TEXT,
                    registration_number TEXT,
                    address TEXT,
                    category TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    blacklisted INTEGER NOT NULL DEFAULT 0,
                    blacklist_reason TEXT,
                    notes TEXT,
                    created_at TEXT
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS procurement_requests (
                    id TEXT PRIMARY KEY,
                    request_number TEXT NOT NULL UNIQUE,
                    request_date TEXT,
                    department TEXT,
                    requested_by TEXT,
                    item_description TEXT,
                    quantity INTEGER NOT NULL DEFAULT 0,
                    estimated_cost TEXT NOT NULL DEFAULT '0',
                    justification TEXT,
                    required_date TEXT,
                    budget_account TEXT,
                    status TEXT NOT NULL DEFAULT 'DRAFT',
                    tender_id TEXT,
                    created_at TEXT
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tenders (
                    id TEXT PRIMARY KEY,
                    tender_number TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    description TEXT,
                    opening_date TEXT,
                    closing_date TEXT,
                    tender_type TEXT,
                    category TEXT,
                    estimated_budget TEXT NOT NULL DEFAULT '0',
                    evaluation_criteria TEXT,
                    status TEXT NOT NULL DEFAULT 'DRAFT',
                    procurement_request_id TEXT,
                    awarded_supplier_id TEXT,
                    awarded_amount TEXT,
                    award_date TEXT,
                    award_reason TEXT,
                    created_at TEXT,
                    FOREIGN KEY (procurement_request_id) REFERENCES procurement_requests(id)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tender_bids (
                    id TEXT PRIMARY KEY,
                    tender_id TEXT NOT NULL,
                    supplier_id TEXT NOT NULL,
                    submission_date TEXT,
                    bid_amount TEXT NOT NULL DEFAULT '0',
                    technical_score TEXT NOT NULL DEFAULT '0',
                    financial_score TEXT NOT NULL DEFAULT '0',
                    weighted_score TEXT NOT NULL DEFAULT '0',
                    documents TEXT,
                    remarks TEXT,
                    status TEXT NOT NULL DEFAULT 'SUBMITTED',
                    rank INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT,
                    FOREIGN KEY (tender_id) REFERENCES tenders(id),
                    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tender_evaluations (
                    id TEXT PRIMARY KEY,
                    tender_id TEXT NOT NULL,
                    bid_id TEXT NOT NULL,
                    evaluator_name TEXT,
                    evaluation_type TEXT,
                    score TEXT NOT NULL DEFAULT '0',
                    max_score TEXT NOT NULL DEFAULT '100',
                    comments TEXT,
                    evaluated_date TEXT,
                    created_at TEXT,
                    FOREIGN KEY (tender_id) REFERENCES tenders(id),
                    FOREIGN KEY (bid_id) REFERENCES tender_bids(id)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tender_awards (
                    id TEXT PRIMARY KEY,
                    tender_id TEXT NOT NULL,
                    supplier_id TEXT NOT NULL,
                    award_date TEXT,
                    award_amount TEXT NOT NULL DEFAULT '0',
                    award_reason TEXT,
                    contract_duration_months INTEGER NOT NULL DEFAULT 0,
                    approval_reference TEXT,
                    approved_by TEXT,
                    created_at TEXT,
                    FOREIGN KEY (tender_id) REFERENCES tenders(id),
                    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS contracts (
                    id TEXT PRIMARY KEY,
                    contract_number TEXT NOT NULL UNIQUE,
                    tender_id TEXT,
                    supplier_id TEXT NOT NULL,
                    start_date TEXT,
                    end_date TEXT,
                    contract_value TEXT NOT NULL DEFAULT '0',
                    deliverables TEXT,
                    status TEXT NOT NULL DEFAULT 'DRAFT',
                    notes TEXT,
                    created_at TEXT,
                    FOREIGN KEY (tender_id) REFERENCES tenders(id),
                    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS contract_milestones (
                    id TEXT PRIMARY KEY,
                    contract_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    due_date TEXT,
                    completed_date TEXT,
                    completed INTEGER NOT NULL DEFAULT 0,
                    amount TEXT NOT NULL DEFAULT '0',
                    created_at TEXT,
                    FOREIGN KEY (contract_id) REFERENCES contracts(id)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS procurement_approvals (
                    id TEXT PRIMARY KEY,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    action TEXT NOT NULL,
                    performed_by TEXT,
                    role TEXT,
                    comments TEXT,
                    timestamp TEXT
                )
            """);

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tender_bids_tender ON tender_bids(tender_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tender_bids_supplier ON tender_bids(supplier_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tender_evaluations_tender ON tender_evaluations(tender_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tender_evaluations_bid ON tender_evaluations(bid_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tenders_procurement_request ON tenders(procurement_request_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_contracts_supplier ON contracts(supplier_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_contract_milestones_contract ON contract_milestones(contract_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_procurement_approvals_entity ON procurement_approvals(entity_type, entity_id)");
        }
    }
}
