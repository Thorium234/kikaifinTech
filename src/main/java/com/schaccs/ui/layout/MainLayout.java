package com.schaccs.ui.layout;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MainLayout extends BorderPane {

    private final Sidebar sidebar = new Sidebar();
    private final TopBar topBar = new TopBar();
    private final StatusBar statusBar = new StatusBar();
    private final StackPane content = new StackPane();
    private final Map<String, Supplier<Node>> factories = new HashMap<>();
    private final Map<String, String> titles = new HashMap<>();
    private final Map<String, Node> cache = new HashMap<>();

    public MainLayout() {
        content.getStyleClass().add("content-area");
        setLeft(sidebar);
        setTop(topBar);
        setCenter(content);
        setBottom(statusBar);

        sidebar.setOnNavigate(this::show);
    }

    public void register(String key, String title, Supplier<Node> factory) {
        factories.put(key, factory);
        titles.put(key, title);
    }

    public void show(String key) {
        show(key, null);
    }

    public void show(String key, Consumer<Node> afterShow) {
        Node view = cache.computeIfAbsent(key, k -> {
            Supplier<Node> factory = factories.get(k);
            return factory != null ? factory.get() : new Label("Missing view: " + k);
        });
        // refresh the underlying view (not the display wrapper)
        if (view instanceof Refreshable r) {
            r.refresh();
        }
        content.getChildren().setAll(wrapForResponsiveLayout(view));
        sidebar.setActive(key);
        topBar.setTitle(titles.getOrDefault(key, key));
        statusBar.setMessage("Viewing " + titles.getOrDefault(key, key));
        if (afterShow != null) {
            afterShow.accept(view);
        }
    }

    /**
     * Makes a view responsive: it fills the content area and scrolls instead of
     * clipping when the window is too small. Views that already provide their own
     * scrolling (a ScrollPane or TabPane at the top level) are passed through.
     */
    private Node wrapForResponsiveLayout(Node view) {
        if (view instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        if (managesOwnScroll(view)) {
            return view;
        }
        ScrollPane pane = new ScrollPane(view);
        pane.setFitToWidth(true);
        pane.setFitToHeight(true);
        pane.setPannable(true);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.getStyleClass().add("content-scroll");
        return pane;
    }

    private boolean managesOwnScroll(Node view) {
        if (view instanceof ScrollPane || view instanceof TabPane) {
            return true;
        }
        if (view instanceof VBox vbox) {
            for (Node child : vbox.getChildren()) {
                if (child instanceof ScrollPane || child instanceof TabPane) {
                    return true;
                }
            }
        }
        return false;
    }

    public Sidebar getSidebar() {
        return sidebar;
    }

    public TopBar getTopBar() {
        return topBar;
    }

    public StatusBar getStatusBar() {
        return statusBar;
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    public interface Refreshable {
        void refresh();
    }
}
