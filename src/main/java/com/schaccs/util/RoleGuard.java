package com.schaccs.util;

import com.schaccs.config.AppConfig;

/**
 * Centralized role-based access control guard. Call the appropriate check
 * method at the entry point of any privileged operation. Throws
 * {@link SecurityException} if the current user's role lacks the required
 * permission.
 *
 * <p>Role hierarchy (most privileged first):
 * <ul>
 *   <li>PRINCIPAL / ADMIN — full access to everything</li>
 *   <li>BURSAR — finances: receipts, payments, vouchers, bank reconciliation,
 *       fiscal year, budget, payroll accounting</li>
 *   <li>CLERK — data entry: students, fee structures, fee allocations,
 *       mid-term enrollments, receipt creation (but NOT payment approvals
 *       or deletions)</li>
 *   <li>VIEWER — read-only, no write operations allowed</li>
 * </ul>
 */
public final class RoleGuard {

    private RoleGuard() {}

    /**
     * Development bypass for destructive-action gates. While the product is
     * under active development, purge/reset/delete flows are open to every
     * role so developers and testers are not blocked by role setup. Flip to
     * {@code false} before the first production release.
     */
    static final boolean DEVELOPMENT_BYPASS = true;

    private static String currentRole() {
        String role = AppConfig.getInstance().getCurrentUserRole();
        return role != null ? role.trim().toUpperCase() : "";
    }

    private static boolean isOneOf(String role, String... targets) {
        for (String t : targets) {
            if (role.equals(t)) return true;
        }
        return false;
    }

    /**
     * Throws SecurityException if the role is VIEWER. All write operations
     * should call this first.
     */
    public static void requireNotReadOnly() {
        String role = currentRole();
        if (isOneOf(role, "VIEWER")) {
            throw new SecurityException("Your role (" + role
                    + ") does not have permission to modify data.");
        }
    }

    /**
     * Requires PRINCIPAL or ADMIN. Use for destructive operations: system
     * purge, user management, fiscal year close, academic year promotion.
     *
     * <p>Relaxed for every role while {@link #DEVELOPMENT_BYPASS} is on.
     */
    public static void requireFullAccess() {
        if (DEVELOPMENT_BYPASS) {
            return;
        }
        String role = currentRole();
        if (!isOneOf(role, "PRINCIPAL", "ADMIN")) {
            throw new SecurityException("Only a Principal or Administrator can perform this action. Your role: " + role);
        }
    }

    /**
     * Requires BURSAR, PRINCIPAL, or ADMIN. Use for financial operations:
     * payment voucher approval, bank reconciliation, fiscal year open/close,
     * budget management, payroll posting.
     */
    public static void requireFinanceAdmin() {
        String role = currentRole();
        if (!isOneOf(role, "BURSAR", "PRINCIPAL", "ADMIN")) {
            throw new SecurityException("A Bursar, Principal, or Administrator role is required for this action. Your role: " + role);
        }
    }

    /**
     * Requires CLERK, BURSAR, PRINCIPAL, or ADMIN. Use for data-entry
     * operations: creating/editing students, fee structures, receipts,
     * mid-term enrollments, supplier records.
     */
    public static void requireDataEntry() {
        String role = currentRole();
        if (!isOneOf(role, "CLERK", "BURSAR", "PRINCIPAL", "ADMIN")) {
            throw new SecurityException("A Clerk or higher role is required for data entry. Your role: " + role);
        }
    }

    /**
     * Requires BURSAR, PRINCIPAL, or ADMIN. Use for payment receipt
     * creation (collecting money from students).
     */
    public static void requireReceiptCreation() {
        requireFinanceAdmin();
    }
}
