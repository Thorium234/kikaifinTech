package com.schaccs.service.importer;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.student.StudentService;
import com.schaccs.store.StudentStore;
import com.schaccs.validation.StudentValidator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudentImportService {

    private final StudentService studentService;
    private final FeeCalculationService feeCalculationService;
    private final StudentStore studentStore;
    private final StudentValidator studentValidator;

    public StudentImportService() {
        this(new StudentService(), new FeeCalculationService(), StudentStore.getInstance());
    }

    public StudentImportService(StudentService studentService, FeeCalculationService feeCalculationService) {
        this(studentService, feeCalculationService, StudentStore.getInstance());
    }

    public StudentImportService(StudentService studentService, FeeCalculationService feeCalculationService, StudentStore studentStore) {
        this.studentService = studentService;
        this.feeCalculationService = feeCalculationService;
        this.studentStore = studentStore;
        this.studentValidator = new StudentValidator(studentStore);
    }

    public ImportResult importFile(Path path) {
        return importFile(path, true);
    }

    public ImportResult previewFile(Path path) {
        return importFile(path, false);
    }

    private ImportResult importFile(Path path, boolean commit) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) {
                return importCsv(path, commit);
            }
            if (name.endsWith(".xlsx")) {
                return importXlsx(path, commit);
            }
            return ImportResult.failure(List.of("Unsupported file type. Use .csv or .xlsx."));
        } catch (Exception e) {
            return ImportResult.failure(List.of("Import failed: " + e.getMessage()));
        }
    }

    private ImportResult importCsv(Path path, boolean commit) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return ImportResult.failure(List.of("The selected CSV file is empty."));
            }
            List<String> headers = splitCsv(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = splitCsv(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String key = normalize(headers.get(i));
                    String value = i < values.size() ? values.get(i).trim() : "";
                    row.put(key, value);
                }
                rows.add(row);
            }
        }
        return importRows(rows, commit);
    }

    private ImportResult importXlsx(Path path, boolean commit) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(path))) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return ImportResult.failure(List.of("The selected workbook has no sheets."));
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return ImportResult.failure(List.of("The selected workbook is empty."));
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell));
            }
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row rowObj = sheet.getRow(i);
                if (rowObj == null) {
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                boolean any = false;
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = rowObj.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : formatter.formatCellValue(cell);
                    if (!value.isBlank()) {
                        any = true;
                    }
                    row.put(normalize(headers.get(j)), value.trim());
                }
                if (any) {
                    rows.add(row);
                }
            }
        }
        return importRows(rows, commit);
    }

    public ImportResult importRows(List<Map<String, String>> rows) {
        return importRows(rows, true);
    }

    public ImportResult previewRows(List<Map<String, String>> rows) {
        return importRows(rows, false);
    }

    public ImportResult importRows(List<Map<String, String>> rows, boolean commit) {
        List<String> warnings = new ArrayList<>();
        List<RowFailure> failures = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        List<Student> stagedStudents = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int displayRow = i + 2;
            Student student = new Student();
            student.setAdmissionNumber(value(row, "admissionnumber", "admissionno", "admno", "adm", "admission"));
            student.setName(value(row, "fullname", "name", "studentname"));
            student.setGender(blankToDefault(value(row, "gender", "sex"), "Male"));
            student.setFormClass(value(row, "formclass", "form", "class"));
            student.setStream(value(row, "stream", "section"));
            student.setParentName(value(row, "parentname", "parent", "guardian", "guardianname"));
            student.setPhone(value(row, "phone", "phonenumber", "contact"));
            student.setBoardingStatus(parseBoardingStatus(value(row, "boardingstatus", "boarding", "status")));
            student.setStatus(parseStudentStatus(value(row, "studentstatus", "active", "recordstatus")));
            Integer academicYear = parseInteger(value(row, "academicyear", "year"));
            Integer yearOfAdmission = parseInteger(value(row, "yearofadmission", "admissionyear"));
            if (academicYear != null) {
                student.setAcademicYear(academicYear);
            }
            if (yearOfAdmission != null) {
                student.setYearOfAdmission(yearOfAdmission);
            }

            List<String> errors = validateCandidate(student, stagedStudents);
            if (!errors.isEmpty()) {
                String message = String.join("; ", errors);
                warnings.add("Row " + displayRow + " skipped: " + message);
                failures.add(new RowFailure(displayRow, message));
                skipped++;
                continue;
            }
            stagedStudents.add(student);
            imported++;
        }
        if (commit && imported > 0) {
            for (Student student : stagedStudents) {
                try {
                    studentStore.add(student);
                    feeCalculationService.chargeTermFees(student, com.schaccs.enums.AcademicTerm.TERM_1);
                } catch (IllegalArgumentException e) {
                    warnings.add("Skipped " + student.getAdmissionNumber() + ": " + e.getMessage());
                    imported--;
                    skipped++;
                }
            }
            PersistenceService.getInstance().saveAll();
        }
        return new ImportResult(imported, skipped, warnings, failures);
    }

    private List<String> splitCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private BoardingStatus parseBoardingStatus(String value) {
        String normalized = normalize(value);
        if (normalized.contains("day")) {
            return BoardingStatus.DAY;
        }
        return BoardingStatus.BOARDING;
    }

    private StudentStatus parseStudentStatus(String value) {
        String normalized = normalize(value);
        if (normalized.equals("inactive") || normalized.equals("false")) {
            return StudentStatus.INACTIVE;
        }
        return StudentStatus.ACTIVE;
    }

    private List<String> validateCandidate(Student student, List<Student> stagedStudents) {
        List<String> errors = new ArrayList<>(studentValidator.validate(student, true));
        String admission = student.getAdmissionNumber();
        if (admission != null && !admission.isBlank()) {
            studentService.findByAdmission(admission).ifPresent(existing ->
                    errors.add("Admission number already exists: " + admission));
            boolean duplicateInFile = stagedStudents.stream()
                    .anyMatch(s -> admission.equalsIgnoreCase(s.getAdmissionNumber()));
            if (duplicateInFile) {
                errors.add("Admission number is duplicated in the import file: " + admission);
            }
        }
        return errors;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).intValueExact();
        } catch (Exception e) {
            return null;
        }
    }

    public static final class ImportResult {
        private final int imported;
        private final int skipped;
        private final List<String> warnings;
        private final List<RowFailure> failures;

        public ImportResult(int imported, int skipped, List<String> warnings, List<RowFailure> failures) {
            this.imported = imported;
            this.skipped = skipped;
            this.warnings = List.copyOf(warnings);
            this.failures = List.copyOf(failures);
        }

        public static ImportResult failure(List<String> warnings) {
            return new ImportResult(0, 0, warnings, List.of());
        }

        public int getImported() {
            return imported;
        }

        public int getSkipped() {
            return skipped;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public int getRejected() {
            return skipped;
        }

        public List<RowFailure> getFailures() {
            return failures;
        }
    }

    public static final class RowFailure {
        private final int rowNumber;
        private final String reason;

        public RowFailure(int rowNumber, String reason) {
            this.rowNumber = rowNumber;
            this.reason = reason;
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public String getReason() {
            return reason;
        }
    }
}
