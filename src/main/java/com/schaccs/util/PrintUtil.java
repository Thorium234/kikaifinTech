package com.schaccs.util;

import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import javafx.stage.Window;
import javafx.print.PrinterJob;

public final class PrintUtil {

    private PrintUtil() {
    }

    public static boolean printText(String title, String content, Window ownerWindow) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            return false;
        }
        TextArea area = new TextArea(content);
        area.setWrapText(true);
        area.setEditable(false);
        area.setFont(Font.font("Monospaced", 10));
        boolean proceed = ownerWindow != null ? job.showPrintDialog(ownerWindow) : job.showPrintDialog(null);
        if (!proceed) {
            return false;
        }
        try {
            return job.printPage(area) && job.endJob();
        } finally {
            if (job.getJobStatus() == javafx.print.PrinterJob.JobStatus.NOT_STARTED
                    || job.getJobStatus() == javafx.print.PrinterJob.JobStatus.PRINTING) {
                job.cancelJob();
            }
        }
    }
}
