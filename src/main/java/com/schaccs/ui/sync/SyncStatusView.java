@@
-        ScrollPane scroll = new ScrollPane(allContent);
-        scroll.setFitToWidth(true);
-        scroll.getStyleClass().add("inline-scroll-pane");
-        VBox.setVgrow(scroll, Priority.ALWAYS);
-        getChildren().add(scroll);
+        ScrollPane scroll = new ScrollPane(allContent);
+        scroll.setFitToWidth(true);
+        // allow the sync status view to resize vertically so errors/breakdown can scroll
+        scroll.setFitToHeight(true);
+        scroll.getStyleClass().add("inline-scroll-pane");
+        VBox.setVgrow(scroll, Priority.ALWAYS);
+        getChildren().add(scroll);
