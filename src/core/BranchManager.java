package core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class BranchManager {

    private static final String SMK_DIR = ".smk";
    private static final String HEAD_FILE = SMK_DIR + "/HEAD";
    private static final String REFS_HEADS_DIR = SMK_DIR + "/refs/heads";


    private static String headSymbolicRef() {
        try {
            String head = Utils.readFileStr(HEAD_FILE).trim();
            if (head.startsWith("ref: ")) {
                return head.substring("ref: ".length()).trim();
            }
            return "";
        } catch (IOException e) {
            return "";
        }
    }

    private static String currentBranchName() {
        String ref = headSymbolicRef();
        final String prefix = "refs/heads/";
        if (ref.startsWith(prefix)) {
            return ref.substring(prefix.length());
        }
        return "";
    }

    private static void writeTreeToWorkdirAndIndex(final IndexMap oldTree, final IndexMap newTree) {
        Path root = Paths.get(".").toAbsolutePath().normalize();

        for (String path : oldTree.keySet()) {
            if (!newTree.containsKey(path)) {
                try {
                    Files.deleteIfExists(root.resolve(path));
                } catch (IOException ignored) {
                }
            }
        }

        for (Map.Entry<String, String> entry : newTree.entrySet()) {
            String path = entry.getKey();
            String blobHash = entry.getValue();

            ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(blobHash);
            if (!"blob".equals(obj.type())) continue;

            try {
                Path filePath = root.resolve(path);
                Files.createDirectories(filePath.getParent());
                Utils.writeFile(filePath, obj.content());
            } catch (IOException e) {
                System.err.println("Error writing file " + path + ": " + e.getMessage());
            }
        }

        IndexManager.writeIndex(newTree);
    }

    public static List<String> listBranches() {
        List<String> out = new ArrayList<>();
        String currentBranch = currentBranchName();
        Path headsDir = Paths.get(REFS_HEADS_DIR);

        if (!Files.exists(headsDir)) {
            return out;
        }

        try (var stream = Files.list(headsDir)) {
            stream.forEach(entry -> {
                if (Files.isRegularFile(entry)) {
                    String name = entry.getFileName().toString();
                    if (name.equals(currentBranch)) {
                        out.add("* " + name);
                    } else {
                        out.add("  " + name);
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("Error listing branches: " + e.getMessage());
        }

        return out;
    }

    public static void createBranch(final String name) {
        if (name == null || name.isEmpty()) {
            System.out.println("Branch name required.");
            return;
        }

        String headCommit = CommitManager.readRefHead();
        if (headCommit.isEmpty()) {
            System.out.println("Cannot create branch without any commits.");
            return;
        }

        Path refPath = Paths.get(REFS_HEADS_DIR, name);
        if (Files.exists(refPath)) {
            System.out.println("Branch already exists: " + name);
            return;
        }

        try {
            Files.createDirectories(refPath.getParent());
            Utils.writeFile(refPath, headCommit + "\n");
            System.out.println("Created branch " + name);
        } catch (IOException e) {
            System.err.println("Error creating branch: " + e.getMessage());
        }
    }

    public static void checkoutBranch(final String name) {
        if (name == null || name.isEmpty()) {
            System.out.println("Usage: smk checkout <branch>");
            return;
        }

        Path refPath = Paths.get(REFS_HEADS_DIR, name);
        if (!Files.exists(refPath)) {
            System.out.println("Branch not found: " + name);
            return;
        }

        String targetCommit;
        try {
            targetCommit = Utils.readFileStr(refPath.toString()).trim();
        } catch (IOException e) {
            System.err.println("Error reading branch reference: " + e.getMessage());
            return;
        }

        if (targetCommit.isEmpty()) {
            System.out.println("Branch has no commits yet: " + name);
            return;
        }

        // Check for uncommitted changes
        IndexMap headTree = CommitManager.readHeadTree();
        IndexMap idx = IndexManager.readIndex();
        boolean hasUncommitted = false;
        
        // Check if index has changes
        if (!idx.equals(headTree)) {
            hasUncommitted = true;
        }
        
        // Check if working directory has changes
        for (Map.Entry<String, String> kv : idx.entrySet()) {
            String path = kv.getKey();
            if (!Files.exists(Paths.get(path))) {
                hasUncommitted = true;
                break;
            }
            try {
                String workHash = ObjectManager.hashBlobFromFile(path);
                if (!workHash.equals(kv.getValue())) {
                    hasUncommitted = true;
                    break;
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        
        if (hasUncommitted) {
            System.out.println("Warning: You have uncommitted changes. They may be overwritten.");
        }

        IndexMap oldTree = CommitManager.readHeadTree();
        IndexMap newTree = CommitManager.readTreeFromCommit(targetCommit);
        writeTreeToWorkdirAndIndex(oldTree, newTree);

        try {
            Utils.writeFile(Paths.get(HEAD_FILE), "ref: refs/heads/" + name + "\n");
            System.out.println("Switched to branch '" + name + "'");
        } catch (IOException e) {
            System.err.println("Error updating HEAD: " + e.getMessage());
        }
    }

}