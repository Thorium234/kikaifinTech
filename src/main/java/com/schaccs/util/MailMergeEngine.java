package com.schaccs.util;

import com.schaccs.config.AppConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.store.StudentStore;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class MailMergeEngine {

    private MailMergeEngine() {}

    public static Map<String, String> resolveFields(StudentBalance balance) {
        Student student = StudentStore.getInstance()
                .findByAdmissionNumber(balance.getAdmissionNumber()).orElse(null);
        return resolveFields(student, balance);
    }

    public static Map<String, String> resolveFields(Student student, StudentBalance balance) {
        Map<String, String> fields = new HashMap<>();
        String guardianName = student != null && student.getParentName() != null ? student.getParentName() : "Parent/Guardian";
        String guardianPhone = student != null && student.getGuardianPhone() != null ? student.getGuardianPhone() : "";
        String phone = student != null && student.getPhone() != null ? student.getPhone() : "";
        String form = student != null && student.getFormClass() != null ? student.getFormClass() : "";
        String stream = student != null && student.getStream() != null ? student.getStream() : "";
        String admission = balance.getAdmissionNumber();
        String studentName = balance.getStudentName();
        String classLabel = balance.getClassLabel();

        AcademicTerm currentTerm = AcademicTerm.TERM_1;
        if (student != null) {
            StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
            if (ledger.getCurrentTerm() != null) {
                currentTerm = ledger.getCurrentTerm();
            }
        }

        BigDecimal billed = balance.getTotalCharged();
        BigDecimal paid = balance.getTotalPaid();
        BigDecimal termBalance = billed.subtract(paid).max(BigDecimal.ZERO);
        BigDecimal arrears = balance.getArrears();
        BigDecimal totalDue = balance.getBalance();

        fields.put("Guardian_Name", guardianName);
        fields.put("Guardian_Phone", guardianPhone);
        fields.put("Student_Phone", phone);
        fields.put("Student_Name", studentName);
        fields.put("Adm_No", admission);
        fields.put("Form", form);
        fields.put("Stream", stream);
        fields.put("Class", classLabel);
        fields.put("Current_Term", currentTerm.getDisplayName());
        fields.put("Billed_Fee", CurrencyUtil.formatPlain(billed));
        fields.put("Paid_Amount", CurrencyUtil.formatPlain(paid));
        fields.put("Term_Balance", CurrencyUtil.formatPlain(termBalance));
        fields.put("Arrears", CurrencyUtil.formatPlain(arrears));
        fields.put("Total_Due", CurrencyUtil.formatPlain(totalDue));
        fields.put("School_Name", AppConfig.getInstance().getSchoolProfile().getSchoolName());
        fields.put("Academic_Year", String.valueOf(AppConfig.getInstance().getAcademicYear()));

        return fields;
    }

    public static String merge(String template, Map<String, String> fields) {
        String result = template;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public static String merge(String template, StudentBalance balance) {
        return merge(template, resolveFields(balance));
    }

    public static String merge(String template, Student student, StudentBalance balance) {
        return merge(template, resolveFields(student, balance));
    }
}
