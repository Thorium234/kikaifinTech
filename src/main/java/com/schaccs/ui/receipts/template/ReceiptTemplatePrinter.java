package com.schaccs.ui.receipts.template;

import javafx.geometry.Bounds;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.stage.Window;

/**
 * Helper to print a JavaFX Node scaled to A4 portrait with margins.
 * Keeps printing concerns separate from the template.
 */
public final class ReceiptTemplatePrinter {

    private ReceiptTemplatePrinter() {}

    public static boolean printNode(Node node, Window ownerWindow) {
        Printer printer = Printer.getDefaultPrinter();
        if (printer == null) return false;
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job == null) return false;
        boolean proceed = job.showPrintDialog(ownerWindow);
        if (!proceed) {
            job.cancelJob();
            return false;
        }

        PageLayout layout = printer.createPageLayout(Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);

        // Scale node to fit printable area
        Bounds bounds = node.getBoundsInParent();
        double scaleX = layout.getPrintableWidth() / Math.max(bounds.getWidth(), 1);
        double scaleY = layout.getPrintableHeight() / Math.max(bounds.getHeight(), 1);
        double scale = Math.min(scaleX, scaleY);

        double origScaleX = node.getScaleX();
        double origScaleY = node.getScaleY();
        try {
            node.setScaleX(origScaleX * scale);
            node.setScaleY(origScaleY * scale);
            boolean printed = job.printPage(layout, node);
            if (printed) {
                job.endJob();
                return true;
            } else {
                job.cancelJob();
                return false;
            }
        } finally {
            node.setScaleX(origScaleX);
            node.setScaleY(origScaleY);
        }
    }
}
