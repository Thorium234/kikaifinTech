@@
-        ScrollPane mainScroll = new ScrollPane(allContent);
-        mainScroll.setFitToWidth(true);
-        mainScroll.setFitToHeight(false);
-        mainScroll.getStyleClass().add("inline-scroll-pane");
-        VBox.setVgrow(mainScroll, Priority.ALWAYS);
+        ScrollPane mainScroll = new ScrollPane(allContent);
+        mainScroll.setFitToWidth(true);
+        // allow the settings content to expand/shrink vertically with window
+        mainScroll.setFitToHeight(true);
+        mainScroll.getStyleClass().add("inline-scroll-pane");
+        VBox.setVgrow(mainScroll, Priority.ALWAYS);
*** End Patch
