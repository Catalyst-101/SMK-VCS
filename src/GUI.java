import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GUI extends Application {
    private Stage primaryStage;
    private Scene splashScene;
    private Scene mainScene;
    private TextArea terminalArea;
    private TextField commandInput;
    private Label repoStateLabel;
    private Label helpTextLabel;
    private Label branchBadge;
    private String currentRepoPath = System.getProperty("user.dir");
    private final StringBuilder terminalBuffer = new StringBuilder();
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    private final List<Command> commands = List.of(
            new Command("smk init", "Initialize a new repository (.smk)"),
            new Command("smk add <file>", "Add a file to the index"),
            new Command("smk add .", "Add all files"),
            new Command("smk commit -m \"message\"", "Commit staged changes with message"),
            new Command("smk commit --amend -m \"message\"", "Amend the last commit"),
            new Command("smk status", "Show staged/unstaged/untracked files"),
            new Command("smk branch", "List branches"),
            new Command("smk branch <name>", "Create a new branch"),
            new Command("smk branch -d <name>", "Delete a branch (not master/current)"),
            new Command("smk checkout <branch>", "Checkout a branch"),
            new Command("smk merge <branch>", "Merge a branch into current"),
            new Command("smk log", "Show full commit log"),
            new Command("smk log --oneline", "Show one-line log"),
            new Command("smk diff", "Show diff of working tree vs index"),
            new Command("smk revert <commit>", "Revert working tree to a commit"),
//            new Command("smk reset <commit>", "Reset branch to a commit"),
            new Command("smk clone <path>", "Clone another local repo"),
            new Command("smk show <commit>", "Show metadata and message of a commit"),
            new Command("smk clean", "Remove untracked files")
    );

    private ListView<String> fileExplorer;
    private ListView<String> smkTreeView;
    private Canvas commitCanvas;
    private Canvas dagCanvas;
    private Canvas linkedListCanvas;
    private Canvas stackCanvas;
    private ComboBox<Double> zoomChoice;
    private ScrollPane commitScrollPane;

    public static void main(String[] args) {
        launch(args);
    }

    private void createSplashScene() {
        VBox splashLayout = new VBox(20);
        splashLayout.setAlignment(Pos.CENTER);
        splashLayout.setPadding(new Insets(50));
        splashLayout.setStyle("-fx-background-color: linear-gradient(to bottom, #ff6a00, #ff9b3d);");

        Label title = new Label("SMK VCS");
        title.setFont(Font.font("Arial", 48));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Version Control System");
        subtitle.setFont(Font.font("Arial", 18));
        subtitle.setTextFill(Color.rgb(255, 230, 204));

        Button openDirBtn = new Button("Open Repository Directory");
        openDirBtn.setStyle("-fx-background-color: white; -fx-text-fill: #ff6a00; -fx-font-weight: bold; -fx-font-size: 16px; -fx-background-radius: 12px; -fx-padding: 10 20;");
        openDirBtn.setOnAction(e -> {
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("Select Repository Directory");
            File dir = dirChooser.showDialog(primaryStage);
            if (dir != null) {
                currentRepoPath = dir.getAbsolutePath();
                appendLine("Opened repository: " + currentRepoPath, Color.web("#cfd8dc"));
                primaryStage.setScene(mainScene);
                updateRepoState();
            }
        });

        Button useCurrentBtn = new Button("Use Current Directory");
        useCurrentBtn.setStyle("-fx-background-color: rgba(255,255,255,0.3); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 12px; -fx-padding: 8 16;");
        useCurrentBtn.setOnAction(e -> {
            appendLine("Using current directory: " + currentRepoPath, Color.web("#cfd8dc"));
            primaryStage.setScene(mainScene);
            updateRepoState();
        });

        VBox buttonBox = new VBox(10, openDirBtn, useCurrentBtn);
        buttonBox.setAlignment(Pos.CENTER);
        splashLayout.getChildren().addAll(title, subtitle, buttonBox);
        splashScene = new Scene(splashLayout, 900, 600);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        createSplashScene();
        createMainScene();
        primaryStage.setScene(splashScene);
        primaryStage.setTitle("SMK VCS");
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    private void createMainScene() {
        // Left panel: tabs for Commands, File Explorer, SMK Tree
        TabPane sideTabs = new TabPane();
        sideTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sideTabs.setPrefWidth(280);

        // Commands Tab
        Tab commandsTab = new Tab("Commands");
        VBox commandPanel = new VBox(15);
        commandPanel.setPadding(new Insets(15));
        commandPanel.setStyle("-fx-background-color: linear-gradient(to bottom, #ff9b3d, #ff6a00); -fx-background-radius: 15px;");

        Label titleLabel = new Label("SMK VCS");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: white;");
        commandPanel.getChildren().add(titleLabel);

        helpTextLabel = new Label("Select a command");
        helpTextLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #ffe6cc; -fx-wrap-text: true;");
        helpTextLabel.setMaxWidth(Double.MAX_VALUE);
        commandPanel.getChildren().add(helpTextLabel);

        VBox commandListVBox = new VBox(8);
        for (Command cmd : commands) {
            Button btn = new Button(cmd.cmd);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 8;");
            btn.setOnAction(e -> {
                commandInput.setText(cmd.cmd);
                helpTextLabel.setText(cmd.help);
            });
            commandListVBox.getChildren().add(btn);
        }

        ScrollPane scrollPane = new ScrollPane(commandListVBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        commandPanel.getChildren().add(scrollPane);
        commandsTab.setContent(commandPanel);
        sideTabs.getTabs().add(commandsTab);

        // File Explorer Tab
        Tab fileExplorerTab = new Tab("Files");
        VBox filePanel = new VBox(10);
        filePanel.setPadding(new Insets(15));
        filePanel.setStyle("-fx-background-color: linear-gradient(to bottom, #ff9b3d, #ff6a00); -fx-background-radius: 15px;");

        HBox fileHeader = new HBox(10);
        Label fileLabel = new Label("File Explorer");
        fileLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: white;");
        Button refreshFilesBtn = new Button("Refresh");
        refreshFilesBtn.setStyle("-fx-background-color: rgba(255,255,255,0.3); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px;");
        refreshFilesBtn.setOnAction(e -> updateFileExplorer());
        fileHeader.getChildren().addAll(fileLabel, refreshFilesBtn);
        filePanel.getChildren().add(fileHeader);

        fileExplorer = new ListView<>();
        fileExplorer.setStyle("-fx-background-color: linear-gradient(to bottom, #ff9b3d, #ff6a00); -fx-background-radius: 15px;");
        fileExplorer.setPrefHeight(450);
        filePanel.getChildren().add(fileExplorer);
        fileExplorerTab.setContent(filePanel);
        sideTabs.getTabs().add(fileExplorerTab);

        // SMK Tree View Tab
        Tab smkTreeTab = new Tab("Tree");
        VBox smkPanel = new VBox(10);
        smkPanel.setPadding(new Insets(15));
        smkPanel.setStyle("-fx-background-color: linear-gradient(to bottom, #ff9b3d, #ff6a00); -fx-background-radius: 15px;");

        Label smkLabel = new Label("Structure");
        smkLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: white;");
        smkPanel.getChildren().add(smkLabel);

        smkTreeView = new ListView<>();
        smkTreeView.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: white; -fx-font-family: 'Monospaced';");
        smkTreeView.setPrefHeight(450);
        smkPanel.getChildren().add(smkTreeView);
        smkTreeTab.setContent(smkPanel);
        sideTabs.getTabs().add(smkTreeTab);

        // Visual Tab
        Tab visualsTab = new Tab("Visuals");
        VBox visualsBox = new VBox(10);
        visualsBox.setPadding(new Insets(10));
        visualsBox.setStyle("-fx-background-color: linear-gradient(to bottom, #2f3542, #1e242d);");

        Label zoomLabel = new Label("Zoom");
        zoomLabel.setTextFill(Color.web("#cfd8dc"));

        zoomChoice = new ComboBox<>();
        zoomChoice.getItems().addAll(0.75, 1.0, 1.25, 1.5, 2.0, 3.0);
        zoomChoice.setValue(1.5);
        zoomChoice.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : (int)(item * 100) + "%");
            }
        });
        zoomChoice.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : (int)(item * 100) + "%");
            }
        });
        zoomChoice.setOnAction(e -> updateVisuals());

        commitCanvas = new Canvas(1200, 900);
        VBox visContent = new VBox(8, zoomLabel, zoomChoice, commitCanvas);
        ScrollPane visScroll = new ScrollPane(visContent);
        visScroll.setFitToWidth(true);
        visScroll.setFitToHeight(true);
        visualsBox.getChildren().add(visScroll);
        visualsTab.setContent(visualsBox);
        sideTabs.getTabs().add(visualsTab);


        // -------------------------------
        // TERMINAL PANEL FIXED TO BLACK
        // -------------------------------
        VBox terminalPanel = new VBox(10);
        terminalPanel.setPadding(new Insets(15));
        terminalPanel.setStyle("-fx-background-color: linear-gradient(to bottom, #ff9b3d, #ff6a00);" + "-fx-background-radius: 15px;");

        HBox termHeader = new HBox(10);
        termHeader.setAlignment(Pos.CENTER_LEFT);

        branchBadge = new Label("master");
        branchBadge.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #ff6a00; -fx-padding: 3 8; -fx-background-radius: 12px;");

        Label dots = new Label("● ● ●");
        dots.setStyle("-fx-text-fill: white;");

        Label termLabel = new Label("smk terminal");
        termLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");

        termHeader.getChildren().addAll(dots, termLabel, branchBadge);
        terminalPanel.getChildren().add(termHeader);

        terminalArea = new TextArea();
        terminalArea.setEditable(false);
        terminalArea.setWrapText(true);
        terminalArea.setFont(Font.font("Consolas", 12));
        terminalArea.setStyle(
                "-fx-control-inner-background: #232121;" +
                        "-fx-text-fill: #e8e8e8;" +
                        "-fx-highlight-fill: #555555;" +
                        "-fx-highlight-text-fill: white;" +
                        "-fx-border-color: #232121;");
        terminalArea.setPrefHeight(400);
        terminalArea.setFocusTraversable(true);

        HBox inputBox = new HBox(10);
        commandInput = new TextField();
        commandInput.setPromptText("Type a command (e.g. smk status)");
        commandInput.setStyle("-fx-font-family: 'Consolas', 'Monospaced';");

        Button runBtn = new Button("Run");
        runBtn.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #ff6a00; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 8 20;");
        runBtn.setOnAction(e -> runCommand(commandInput.getText()));

        Button clearBtn = new Button("Clear");
        clearBtn.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 8 14;");
        clearBtn.setOnAction(e -> clearTerminal());

        Button copyBtn = new Button("Copy");
        copyBtn.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 8 14;");
        copyBtn.setOnAction(e -> copyTerminal());

        inputBox.getChildren().addAll(commandInput, runBtn, clearBtn, copyBtn);
        HBox.setHgrow(commandInput, Priority.ALWAYS);

        terminalPanel.getChildren().addAll(terminalArea, inputBox);

        commandInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                runCommand(commandInput.getText());
            } else if (e.getCode() == KeyCode.UP) {
                navigateHistory(-1);
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                navigateHistory(1);
                e.consume();
            }
        });

        // -------------------------------
        // META PANEL FIX (gradient + white)
        // -------------------------------
        VBox metaPanel = new VBox(15);
        metaPanel.setPadding(new Insets(15));
        metaPanel.setPrefWidth(250);
        metaPanel.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #ff9b3d, #ff6a00); " +
                        "-fx-background-radius: 15px;"
        );

        Label metaLabel = new Label("Repository State");
        metaLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: white;");

        repoStateLabel = new Label();
        repoStateLabel.setStyle("-fx-font-family: 'Monospaced'; -fx-text-fill: white; -fx-font-size: 12px; -fx-wrap-text: true;");
        repoStateLabel.setMaxWidth(Double.MAX_VALUE);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: rgba(255,255,255,0.25); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px;");
        refreshBtn.setOnAction(e -> {
            updateRepoState();
            updateFileExplorer();
            updateSMKTreeView();
        });

        metaPanel.getChildren().addAll(metaLabel, repoStateLabel, refreshBtn);

        // Main layout
        Button backBtn = new Button("Back");
        backBtn.setOnAction(e -> primaryStage.setScene(splashScene));
        HBox topBar = new HBox(backBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(5, 5, 5, 5));

        HBox mainLayout = new HBox(15, sideTabs, terminalPanel, metaPanel);
        HBox.setHgrow(terminalPanel, Priority.ALWAYS);
        mainLayout.setPadding(new Insets(15));

        VBox root = new VBox(topBar, mainLayout);

        mainScene = new Scene(root, 1400, 750);
        updateRepoState();
        updateFileExplorer();
        updateSMKTreeView();
        updateVisuals();
    }

    private void updateFileExplorer() {
        try {
            List<String> files = new ArrayList<>();
            Path repoPath = Paths.get(currentRepoPath);

            if (Files.exists(repoPath)) {
                try (var stream = Files.walk(repoPath, 3)) {
                    stream.filter(Files::isRegularFile)
                        .map(p -> repoPath.relativize(p).toString())
                        .filter(f -> !f.startsWith(".smk") && !f.contains(".smk/") && !f.contains(".smk\\"))
                        .sorted()
                        .forEach(files::add);
                }
            }

            Platform.runLater(() -> {
                fileExplorer.getItems().setAll(files);
            });
        } catch (IOException e) {
            Platform.runLater(() -> {
                fileExplorer.getItems().setAll("Error reading files: " + e.getMessage());
            });
        }
    }

    private void updateSMKTreeView() {
        try {
            List<String> treeItems = new ArrayList<>();
            Path repoPath = Paths.get(currentRepoPath);
            Path smkDir = repoPath.resolve(".smk");

            if (!Files.exists(smkDir)) {
                treeItems.add("Repository not initialized");
                Platform.runLater(() -> {
                    smkTreeView.getItems().setAll(treeItems);
                });
                return;
            }

            // Add HEAD
            try {
                String head = Files.readString(smkDir.resolve("HEAD")).trim();
                treeItems.add("HEAD: " + head);
            } catch (IOException e) {
                treeItems.add("HEAD: (empty)");
            }

            // Add branches with commits (master first)
            Path refsHeads = smkDir.resolve("refs/heads");
            if (Files.exists(refsHeads)) {
                Map<String, String> branchTips = new LinkedHashMap<>();
                try (var stream = Files.list(refsHeads)) {
                    Map<String, String> finalBranchTips = branchTips;
                    stream.forEach(branch -> {
                        try {
                            finalBranchTips.put(branch.getFileName().toString(), Files.readString(branch).trim());
                        } catch (IOException ignored) {}
                    });
                }
                // reorder master first
                branchTips = getStringStringMap(branchTips);
                treeItems.add("Branches:");
                for (Map.Entry<String, String> br : branchTips.entrySet()) {
                    String bName = br.getKey();
                    String tip = br.getValue();
                    treeItems.add(" " + bName + " -> " + tip);
                    // list commits along this branch (follow parents)
                    String cur = tip;
                    int count = 0;
                    while (cur != null && !cur.isEmpty() && count < 12) {
                        treeItems.add("   • " + cur);
                        cur = readParent(cur, smkDir);
                        count++;
                    }
                    if (cur != null && !cur.isEmpty()) treeItems.add("   ...");
                }
            }

            Platform.runLater(() -> {
                smkTreeView.getItems().setAll(treeItems);
            });
        } catch (IOException e) {
            Platform.runLater(() -> {
                smkTreeView.getItems().setAll("Error reading SMK structure: " + e.getMessage());
            });
        }
    }

    private String readParent(String hash, Path smkDir) {
        if (hash == null || hash.isEmpty()) return "";
        try {
            Path obj = smkDir.resolve("objects").resolve(hash);
            if (!Files.exists(obj)) return "";
            String content = Files.readString(obj);
            int blank = content.indexOf("\n\n");
            String header = blank == -1 ? content : content.substring(0, blank);
            try (BufferedReader r = new BufferedReader(new StringReader(header))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("parent ")) return line.substring("parent ".length());
                }
            }
        } catch (IOException ignored) {}
        return "";
    }

    private void updateVisuals() {
        if (commitCanvas == null) return;
        try {
            Path repoPath = Paths.get(currentRepoPath);
            Path smkDir = repoPath.resolve(".smk");
            GraphicsContext gc = commitCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, commitCanvas.getWidth(), commitCanvas.getHeight());

            if (!Files.exists(smkDir)) {
                gc.setFill(Color.GRAY);
                gc.fillText("Repository not initialized", 20, 40);
                return;
            }

            // Build commit graph (parents, children)
            Path objectsDir = smkDir.resolve("objects");
            Map<String, List<String>> parents = new HashMap<>();
            if (Files.exists(objectsDir)) {
                try (var stream = Files.list(objectsDir)) {
                    stream.forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            if (!content.startsWith("commit\n")) return;
                            String hash = p.getFileName().toString();
                            List<String> ps = new ArrayList<>();
                            try (BufferedReader r = new BufferedReader(new StringReader(content))) {
                                String line;
                                boolean inMsg = false;
                                while ((line = r.readLine()) != null) {
                                    if (line.isEmpty()) { inMsg = true; continue; }
                                    if (!inMsg && line.startsWith("parent ")) {
                                        ps.add(line.substring("parent ".length()));
                                    }
                                }
                            }
                            parents.put(hash, ps);
                        } catch (IOException ignored) {}
                    });
                }
            }

            // Determine HEAD and branches (master first)
            String headHash = "";
            Map<String, String> branches = new LinkedHashMap<>();
            try {
                String head = Files.readString(smkDir.resolve("HEAD")).trim();
                if (head.startsWith("ref: ")) {
                    String refRel = head.substring(5);
                    Path refPath = smkDir.resolve(refRel);
                    if (Files.exists(refPath)) headHash = Files.readString(refPath).trim();
                } else {
                    headHash = head;
                }
            } catch (IOException ignored) {}
            Path refsHeads = smkDir.resolve("refs/heads");
            if (Files.exists(refsHeads)) {
                try (var stream = Files.list(refsHeads)) {
                    Map<String, String> finalBranches = branches;
                    stream.forEach(b -> {
                        try {
                            finalBranches.put(b.getFileName().toString(), Files.readString(b).trim());
                        } catch (IOException ignored) {}
                    });
                }
            }
            branches = getStringStringMap(branches);

            // Build children map for layout
            Map<String, List<String>> children = new HashMap<>();
            for (var e : parents.entrySet()) {
                String child = e.getKey();
                for (String p : e.getValue()) {
                    children.computeIfAbsent(p, k -> new ArrayList<>()).add(child);
                }
            }

            // Build initial node set from all commits
            Set<String> allNodes = new HashSet<>(parents.keySet());
            parents.values().forEach(allNodes::addAll);
            
            // Filter nodes: only show commits reachable from at least one branch tip
            Set<String> reachableNodes = new HashSet<>();
            for (String tip : branches.values()) {
                if (tip == null || tip.isBlank()) continue;
                Deque<String> walk = new ArrayDeque<>();
                walk.add(tip);
                Set<String> visited = new HashSet<>();
                while (!walk.isEmpty()) {
                    String h = walk.poll();
                    if (visited.contains(h)) continue;
                    visited.add(h);
                    reachableNodes.add(h);
                    for (String p : parents.getOrDefault(h, List.of())) {
                        if (!visited.contains(p)) walk.add(p);
                    }
                }
            }
            
            // Only work with reachable nodes
            Set<String> nodes = new HashSet<>(reachableNodes);
            
            // Depth (older higher): distance from roots (commits with no parents) - only for reachable nodes
            List<String> roots = nodes.stream()
                .filter(h -> {
                    List<String> ps = parents.getOrDefault(h, List.of());
                    return ps.isEmpty() || ps.stream().noneMatch(nodes::contains);
                })
                .collect(Collectors.toList());
            Map<String, Integer> depth = new HashMap<>();
            Deque<String> dq = new ArrayDeque<>(roots);
            roots.forEach(r -> depth.put(r, 0));
            while (!dq.isEmpty()) {
                String n = dq.poll();
                int d = depth.getOrDefault(n, 0);
                for (String c : children.getOrDefault(n, List.of())) {
                    if (!nodes.contains(c)) continue; // Only consider reachable children
                    int nd = d + 1;
                    if (nd > depth.getOrDefault(c, -1)) {
                        depth.put(c, nd);
                        dq.add(c);
                    }
                }
            }
            int maxDepth = depth.values().stream().max(Integer::compareTo).orElse(0);
            if (nodes.isEmpty()) {
                gc.setFill(Color.GRAY);
                gc.fillText("No commits to visualize", 20, 40);
                return;
            }

            // Column assignment: each branch tip gets its own column; propagate to ancestors
            // Use a map to track which branches claim which commits
            Map<String, Set<String>> branchCommits = new HashMap<>();
            Map<String, Integer> col = new HashMap<>();
            int colIdx = 0;
            
            // First pass: assign each branch tip to its own column
            for (String b : branches.keySet()) {
                String tip = branches.get(b);
                if (tip == null || tip.isBlank()) continue;
                branchCommits.put(b, new HashSet<>());
                Deque<String> walk = new ArrayDeque<>();
                walk.add(tip);
                Set<String> visited = new HashSet<>();
                while (!walk.isEmpty()) {
                    String h = walk.poll();
                    if (visited.contains(h)) continue;
                    visited.add(h);
                    branchCommits.get(b).add(h);
                    // Assign column if not already assigned (prefer earlier branches)
                    col.putIfAbsent(h, colIdx);
                    for (String p : parents.getOrDefault(h, List.of())) {
                        if (!visited.contains(p)) walk.add(p);
                    }
                }
                colIdx++;
            }
            
            // For commits that are in multiple branches, use the column of the first branch that claims it
            // This ensures all branches are visible even if they point to the same commit
            for (String h : nodes) {
                if (!col.containsKey(h)) {
                    // Find which branch(es) contain this commit
                    for (String b : branches.keySet()) {
                        if (branchCommits.get(b).contains(h)) {
                            col.put(h, col.getOrDefault(branches.get(b), 0));
                            break;
                        }
                    }
                    // If still not assigned, assign to next available column
                    if (!col.containsKey(h)) {
                        col.put(h, colIdx++);
                    }
                }
            }

            double scale = 1.0;
            if (zoomChoice != null && zoomChoice.getValue() != null) {
                scale = zoomChoice.getValue();
            }
            scale = Math.max(0.5, Math.min(scale, 3.0)); // clamp to safe range
            double yStep = 140 * scale;
            double xStep = 180 * scale;
            double radius = 24 * scale;
            int maxCol = col.values().stream().max(Integer::compareTo).orElse(0);
            double width = Math.max(900, (maxCol + 2) * xStep + 200);
            double height = Math.max(500, (maxDepth + 2) * yStep + 150);
            double maxDim = 5000; // avoid GPU RTTexture issues
            width = Math.min(width, maxDim);
            height = Math.min(height, maxDim);
            commitCanvas.setWidth(width);
            commitCanvas.setHeight(height);
            gc.setFill(Color.web("#1f2530"));
            gc.fillRect(0, 0, width, height);

            // Draw edges (only for reachable nodes)
            gc.setStroke(Color.web("#9aa5b5"));
            gc.setLineWidth(2 * scale);
            for (String h : nodes) {
                double x1 = 100 * scale + col.getOrDefault(h, 0) * xStep;
                double y1 = 100 * scale + depth.getOrDefault(h, 0) * yStep;
                for (String p : parents.getOrDefault(h, List.of())) {
                    // Only draw edge if parent is also reachable
                    if (!nodes.contains(p)) continue;
                    double x2 = 100 * scale + col.getOrDefault(p, col.getOrDefault(h,0)) * xStep;
                    double y2 = 100 * scale + depth.getOrDefault(p, 0) * yStep;
                    gc.strokeLine(x1, y1, x2, y2);
                    double ang = Math.atan2(y2 - y1, x2 - x1);
                    double ax = x2 + Math.cos(ang + Math.PI*0.9)*10*scale;
                    double ay = y2 + Math.sin(ang + Math.PI*0.9)*10*scale;
                    double bx = x2 + Math.cos(ang - Math.PI*0.9)*10*scale;
                    double by = y2 + Math.sin(ang - Math.PI*0.9)*10*scale;
                    gc.strokeLine(x2, y2, ax, ay);
                    gc.strokeLine(x2, y2, bx, by);
                }
            }

            // Draw branch labels at branch tips
            gc.setFont(Font.font("Consolas", Math.max(10, 12 * scale)));
            for (Map.Entry<String, String> br : branches.entrySet()) {
                String branchName = br.getKey();
                String tip = br.getValue();
                if (tip == null || tip.isBlank() || !nodes.contains(tip)) continue;
                double x = 100 * scale + col.getOrDefault(tip, 0) * xStep;
                double y = 100 * scale + depth.getOrDefault(tip, 0) * yStep;
                gc.setFill(Color.web("#ff9b3d"));
                gc.fillText(branchName, x + radius + 8, y - radius);
            }

            // Draw nodes
            for (String h : nodes) {
                double x = 100 * scale + col.getOrDefault(h, 0) * xStep;
                double y = 100 * scale + depth.getOrDefault(h, 0) * yStep;
                boolean isHead = h.equals(headHash);
                gc.setFill(isHead ? Color.web("#8da3b8") : Color.web("#4b5563"));
                gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
                gc.setStroke(Color.web("#cfd8dc"));
                gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);
                gc.setFill(Color.web("#e0e0e0"));
                gc.setFont(Font.font("Consolas", Math.max(8, 10 * scale)));
                gc.fillText(h, x - radius + 4, y + 4, radius * 2 - 8);
            }

        } catch (Exception e) {
            GraphicsContext gc = commitCanvas.getGraphicsContext2D();
            gc.setFill(Color.RED);
            gc.fillText("Visual error: " + e.getMessage(), 20, 40);
        }
    }

    private Map<String, String> getStringStringMap(Map<String, String> branches) {
        if (branches.containsKey("master")) {
            String m = branches.remove("master");
            LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
            ordered.put("master", m);
            ordered.putAll(branches);
            branches = ordered;
        }
        return branches;
    }

    private void runCommand(String raw) {
        if (raw == null || raw.isBlank()) return;

        appendLine("$ " + raw, Color.web("#cfd8dc"));
        commandInput.clear();
        addToHistory(raw);

        // Built-in clear command (client-side)
        if (raw.trim().equalsIgnoreCase("clear")) {
            clearTerminal();
            return;
        }

        // Run command in a separate thread to avoid blocking UI
        new Thread(() -> {
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos, true);

            System.setOut(ps);
            System.setErr(ps);

            try {
                // Parse and execute command
                String[] args = parseCommand(raw);
                if (args.length > 0 && args[0].equals("smk")) {
                    String[] cmdArgs = Arrays.copyOfRange(args, 1, args.length);

                    // Find project root directory (where src/core/ is located)
                    // Start from current repo path and walk up to find project root
                    File projectRoot = new File(currentRepoPath);

                    // Try to find project root by looking for src/core directory
                    while (projectRoot != null) {
                        File srcCore = new File(projectRoot, "src" + File.separator + "core");
                        if (srcCore.exists() && srcCore.isDirectory()) {
                            break; // Found project root
                        }
                        File parent = projectRoot.getParentFile();
                        if (parent == null) break;
                        projectRoot = parent;
                    }

                    // If not found, try to find just src directory
                    if (projectRoot == null || !new File(projectRoot, "src").exists()) {
                        projectRoot = new File(System.getProperty("user.dir"));
                        // Try to find src from current working directory
                        File checkDir = projectRoot;
                        while (checkDir != null && !new File(checkDir, "src").exists()) {
                            checkDir = checkDir.getParentFile();
                        }
                        if (checkDir != null) {
                            projectRoot = checkDir;
                        }
                    }

                    // Build classpath - point to src directory in project root
                    String classpath = new File(projectRoot, "src").getAbsolutePath()
                        + File.pathSeparator + System.getProperty("java.class.path");

                    // ProcessBuilder - set working directory to repo, but classpath to project
                    ProcessBuilder pb = new ProcessBuilder();
                    pb.directory(new File(currentRepoPath)); // Main will look for .smk here
                    pb.command("java", "-cp", classpath, "Main");

                    // Add command arguments
                    List<String> command = new ArrayList<>(pb.command());
                    command.addAll(Arrays.asList(cmdArgs));
                    pb.command(command);

                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    // Read output
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    process.waitFor();

                    // Update UI on JavaFX thread
                        String finalOutput = output.toString();
                        Platform.runLater(() -> {
                            if (!finalOutput.isEmpty()) {
                                appendColoredOutput(finalOutput);
                            }
                            updateRepoState();
                            updateFileExplorer();
                            updateSMKTreeView();
                            updateVisuals();
                            scrollTerminalToBottom();
                        });
                } else {
                    String errorMsg = "Error: Command must start with 'smk'\n";
                    Platform.runLater(() -> {
                        appendColoredOutput(errorMsg);
                        scrollTerminalToBottom();
                    });
                }
            } catch (Exception e) {
                String errorMsg = "Error executing command: " + e.getMessage() + "\n";
                if (e.getCause() != null) {
                    errorMsg += "Cause: " + e.getCause().getMessage() + "\n";
                }
                String finalErrorMsg = errorMsg;
                Platform.runLater(() -> {
                    appendColoredOutput(finalErrorMsg);
                    scrollTerminalToBottom();
                });
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }).start();
    }

    private String[] parseCommand(String raw) {
        List<String> parts = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : raw.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }

    private void updateRepoState() {
        try {
            Path repoPath = Paths.get(currentRepoPath);
            Path smkDir = repoPath.resolve(".smk");

            if (!Files.exists(smkDir)) {
                repoStateLabel.setText("Repository: Not initialized\n\nClick 'smk init' to start");
                branchBadge.setText("none");
                return;
            }

            // Read HEAD
            String headContent = "";
            String currentBranch = "detached";
            try {
                Path headFile = smkDir.resolve("HEAD");
                if (Files.exists(headFile)) {
                    headContent = Files.readString(headFile).trim();
                    if (headContent.startsWith("ref: ")) {
                        String ref = headContent.substring(5).trim();
                        if (ref.startsWith("refs/heads/")) {
                            currentBranch = ref.substring(11);
                        }
                        Path refFile = smkDir.resolve(ref);
                        if (Files.exists(refFile)) {
                            headContent = Files.readString(refFile).trim();
                        }
                    }
                }
            } catch (IOException e) {
                // Ignore
            }

            branchBadge.setText(currentBranch);

            // Count branches
            Path refsHeads = smkDir.resolve("refs/heads");
            int branchCount = 0;
            if (Files.exists(refsHeads)) {
                try {
                    branchCount = (int) Files.list(refsHeads).count();
                } catch (IOException e) {
                    // Ignore
                }
            }

            // Count commits (simplified - count objects that are commits)
            Path objectsDir = smkDir.resolve("objects");
            int commitCount = 0;
            if (Files.exists(objectsDir)) {
                try {
                    commitCount = (int) Files.list(objectsDir)
                        .filter(p -> {
                            try {
                                String content = Files.readString(p);
                                return content.startsWith("commit\n");
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .count();
                } catch (IOException e) {
                    // Ignore
                }
            }

            // Read index
            Path indexFile = smkDir.resolve("index");
            int stagedCount = 0;
            if (Files.exists(indexFile)) {
                try {
                    String indexContent = Files.readString(indexFile);
                    if (!indexContent.trim().isEmpty()) {
                        stagedCount = indexContent.split("\n").length;
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }

            StringBuilder state = new StringBuilder();
            state.append("Repository: ").append(repoPath.getFileName()).append("\n\n");
            state.append("Current Branch: ").append(currentBranch).append("\n");
            state.append("Branches: ").append(branchCount).append("\n");
            state.append("Commits: ").append(commitCount).append("\n");
            state.append("Staged Files: ").append(stagedCount).append("\n");

            if (!headContent.isEmpty()) {
                state.append("HEAD: ").append(headContent).append("\n");
            }

            repoStateLabel.setText(state.toString());
            updateVisuals();
        } catch (Exception e) {
            repoStateLabel.setText("Error reading repository state:\n" + e.getMessage());
        }
    }

    private void appendColoredOutput(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.isBlank()) continue;
            Color c = Color.web("#4e342e"); // default
            String lower = line.toLowerCase();
            if (lower.startsWith("fatal") || lower.contains("error")) {
                c = Color.web("#ef5350");
            } else if (lower.startsWith("warning")) {
                c = Color.web("#ffb74d");
            } else {
                c = Color.web("#2e7d32");
            }
            appendLine(line, c);
        }
        scrollTerminalToBottom();
    }

    private void appendLine(String text, Color color) {
        terminalArea.appendText(text + "\n");
        terminalBuffer.append(text).append("\n");
    }

    private void clearTerminal() {
        terminalArea.clear();
        terminalBuffer.setLength(0);
        historyIndex = commandHistory.size();
    }

    private void copyTerminal() {
        ClipboardContent content = new ClipboardContent();
        content.putString(terminalBuffer.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void scrollTerminalToBottom() {
        // Preserve selection while keeping the viewport pinned to the latest output
        Platform.runLater(() -> terminalArea.setScrollTop(Double.MAX_VALUE));
    }

    private void addToHistory(String raw) {
        if (raw == null || raw.isBlank()) return;
        // Avoid duplicate consecutive entries
        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(raw)) {
            commandHistory.add(raw);
        }
        historyIndex = commandHistory.size();
    }

    private void navigateHistory(int delta) {
        if (commandHistory.isEmpty()) return;
        historyIndex += delta;
        if (historyIndex < 0) historyIndex = 0;
        if (historyIndex > commandHistory.size()) historyIndex = commandHistory.size();

        if (historyIndex >= 0 && historyIndex < commandHistory.size()) {
            commandInput.setText(commandHistory.get(historyIndex));
            commandInput.positionCaret(commandInput.getText().length());
        } else if (historyIndex == commandHistory.size()) {
            commandInput.clear();
        }
    }

    static class Command {
        String cmd;
        String help;
        Command(String c, String h) {
            cmd = c;
            help = h;
        }
    }
}

