@@
-        ScrollPane scrollPane = new ScrollPane(content);
-        scrollPane.setFitToWidth(true);
-        scrollPane.setPannable(true);
-        scrollPane.getStyleClass().add("content-scroll");
-        VBox.setVgrow(scrollPane, Priority.ALWAYS);
-        getChildren().add(scrollPane);
+        ScrollPane scrollPane = new ScrollPane(content);
+        scrollPane.setFitToWidth(true);
+        // allow the receipts main ScrollPane to resize vertically with the window
+        scrollPane.setFitToHeight(true);
+        scrollPane.setPannable(true);
+        scrollPane.getStyleClass().add("content-scroll");
+        VBox.setVgrow(scrollPane, Priority.ALWAYS);
+        getChildren().add(scrollPane);
