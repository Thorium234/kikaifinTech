package com.schaccs.ui.layout;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MainLayout extends BorderPane {

    private final Sidebar sidebar = new Sidebar();
    private final TopBar topBar = new TopBar();
    private final StatusBar statusBar = new StatusBar();
    private final StackPane content = new StackPane();
    private final Map<String, Supplier<Node>> factories = new HashMap<>();
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
        // title stored via navigate
    }

    public void show(String key) {
        Node node = cache.computeIfAbsent(key, k -> {
            Supplier<Node> factory = factories.get(k);
            Node view = factory != null ? factory.get() : new javafx.scene.control.Label("Missing view: " + k);
            return wrapForResponsiveLayout(view);
        });
        // refresh if view supports it
        if (node instanceof Refreshable r) {
            r.refresh();
        }
        content.getChildren().setAll(node);
        sidebar.setActive(key);
        topBar.setTitle(key);
        statusBar.setMessage("Viewing " + key);
    }

    private Node wrapForResponsiveLayout(Node view) {
        if (view instanceof ScrollPane) {
            VBox container = new VBox(view);
            container.setFillWidth(true);
            VBox.setVgrow(view, javafx.scene.layout.Priority.ALWAYS);
            return container;
        }
        ScrollPane scrollPane = new ScrollPane(view);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("content-scroll");
        if (view instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        VBox container = new VBox(scrollPane);
        container.setFillWidth(true);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);
        return container;
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
