package core;

import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Date;
import java.util.ArrayList;

public class CommitManager {

    private static final String SMK_DIR = ".smk";
    private static final String HEAD_FILE = ".smk/HEAD";

    public static String writeTreeFromIndex(final IndexMap idx) {
        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, String> kv : idx.entrySet()) {
            content.append(kv.getKey()).append("\t").append(kv.getValue()).append("\n");
        }
        return ObjectManager.hashAndStoreObject("tree", content.toString());
    }

    public static IndexMap readTree(final String treeHash) {
        IndexMap tree = new IndexMap();
        if (treeHash == null || treeHash.isEmpty()) return tree;

        ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(treeHash);
        if (!"tree".equals(obj.type())) return tree;

        try (BufferedReader reader = new BufferedReader(new StringReader(obj.content()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                int tab = line.indexOf('\t');
                if (tab == -1) continue;

                String path = line.substring(0, tab);
                String blob = line.substring(tab + 1);

                if (!path.isEmpty() && !blob.isEmpty()) {
                    tree.put(path, blob);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading tree object: " + e.getMessage());
        }
        return tree;
    }

    public static IndexMap readTreeFromCommit(final String commitHash) {
        IndexMap empty = new IndexMap();
        if (commitHash == null || commitHash.isEmpty()) return empty;

        ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(commitHash);
        if (!"commit".equals(obj.type())) return empty;

        String treeHash = "";
        try (BufferedReader reader = new BufferedReader(new StringReader(obj.content()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("tree ")) {
                    treeHash = line.substring("tree ".length());
                    break;
                }
                if (line.isEmpty()) break;
            }
        } catch (IOException e) {
            System.err.println("Error reading commit object: " + e.getMessage());
        }

        return readTree(treeHash);
    }

    public static IndexMap readHeadTree() {
        String headCommit = readRefHead();
        return readTreeFromCommit(headCommit);
    }

    public static String readRefHead() {
        String head;
        try {
            head = Utils.readFileStr(HEAD_FILE).trim();
        } catch (IOException e) {
            return "";
        }

        if (head.startsWith("ref: ")) {
            String refpath = SMK_DIR + "/" + head.substring("ref: ".length());
            try {
                return Utils.readFileStr(refpath).trim();
            } catch (IOException e) {
                return "";
            }
        } else {
            return head;
        }
    }

    public static void writeRefHead(final String commitHash) {
        String head;
        try {
            head = Utils.readFileStr(HEAD_FILE).trim();
        } catch (IOException e) {
            head = "";
        }

        Path refPath;
        if (head.startsWith("ref: ")) {
            String refRelativePath = head.substring("ref: ".length());
            refPath = Paths.get(SMK_DIR, refRelativePath);
        } else {
            refPath = Paths.get(HEAD_FILE);
        }

        try {
            Utils.writeFile(refPath, commitHash + "\n");
        } catch (IOException e) {
            System.err.println("Error writing reference file: " + e.getMessage());
        }
    }

    public static void createCommit(final String message, boolean amend) {
        String parent = readRefHead();
        IndexMap headTree = readHeadTree();

        if (amend) {
            if (parent.isEmpty()) {
                System.out.println("Nothing to amend.");
                return;
            }

            // For amend: use current index if it has changes, otherwise use previous commit's tree
            IndexMap idx = IndexManager.readIndex();
            IndexMap parentTree = readTreeFromCommit(parent);

            // Build a full snapshot starting from parentTree, then apply staged changes
            IndexMap newTree = new IndexMap();
            newTree.putAll(parentTree);

            // Drop files that no longer exist in the working directory (treat as deletions)
            for (String path : new ArrayList<>(newTree.keySet())) {
                if (!java.nio.file.Files.exists(java.nio.file.Paths.get(path))) {
                    newTree.remove(path);
                }
            }

            // Apply staged updates (add / modify)
            for (Map.Entry<String, String> kv : idx.entrySet()) {
                newTree.put(kv.getKey(), kv.getValue());
            }

            // If index was empty (amend message only), keep previous snapshot
            if (idx.isEmpty()) {
                newTree = parentTree;
            }
            
            // Get grandparent for amend
            ObjectManager.ObjectContent parentObj = ObjectManager.readObjectContent(parent);
            if ("commit".equals(parentObj.type())) {
                String grandParent = "";
                try (BufferedReader reader = new BufferedReader(new StringReader(parentObj.content()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("parent ")) {
                            grandParent = line.substring("parent ".length());
                            break;
                        }
                        if (line.isEmpty()) break;
                    }
                } catch (IOException e) {
                    System.err.println("Error reading parent commit for amend: " + e.getMessage());
                }

                parent = grandParent;
            } else {
                parent = "";
            }
            
            String treeHash = writeTreeFromIndex(newTree);
            StringBuilder meta = new StringBuilder();
            meta.append("tree ").append(treeHash).append("\n");
            if (parent != null && !parent.isEmpty()) meta.append("parent ").append(parent).append("\n");

            meta.append("author Local User <local@smk>\n");
            long t = new Date().getTime() / 1000;
            meta.append("date ").append(t).append("\n\n");

            meta.append(message).append("\n");

            String commitHash = ObjectManager.hashAndStoreObject("commit", meta.toString());
            writeRefHead(commitHash);
            IndexManager.writeIndex(new IndexMap());

            System.out.println("Committed: " + commitHash);
            return;
        }

        // Regular commit
        IndexMap idx = IndexManager.readIndex();

        // Start from HEAD snapshot so previously committed files remain present
        IndexMap newTree = new IndexMap();
        newTree.putAll(headTree);

        // Remove files that were deleted in the working directory
        for (String path : new ArrayList<>(newTree.keySet())) {
            if (!java.nio.file.Files.exists(java.nio.file.Paths.get(path))) {
                newTree.remove(path);
            }
        }

        // Apply staged changes (add/modify)
        for (Map.Entry<String, String> kv : idx.entrySet()) {
            newTree.put(kv.getKey(), kv.getValue());
        }

        if (newTree.isEmpty()) {
            System.out.println("No changes to commit.");
            return;
        }

        String treeHash = writeTreeFromIndex(newTree);
        StringBuilder meta = new StringBuilder();
        meta.append("tree ").append(treeHash).append("\n");
        if (parent != null && !parent.isEmpty()) meta.append("parent ").append(parent).append("\n");

        meta.append("author Local User <local@smk>\n");
        long t = new Date().getTime() / 1000;
        meta.append("date ").append(t).append("\n\n");

        meta.append(message).append("\n");

        String commitHash = ObjectManager.hashAndStoreObject("commit", meta.toString());

        writeRefHead(commitHash);

        IndexManager.writeIndex(new IndexMap());

        System.out.println("Committed: " + commitHash);
    }
}