@@
-        ScrollPane formScroll = new ScrollPane(card);
-        formScroll.setFitToWidth(true);
-        formScroll.setFitToHeight(false);
-        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
-        formScroll.getStyleClass().add("content-scroll");
-
-        formTab.setContent(formScroll);
+        ScrollPane formScroll = new ScrollPane(card);
+        formScroll.setFitToWidth(true);
+        // allow vertical resizing so the form scrolls when space is constrained
+        formScroll.setFitToHeight(true);
+        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
+        formScroll.getStyleClass().add("content-scroll");
+
+        formTab.setContent(formScroll);
*** End Patch
