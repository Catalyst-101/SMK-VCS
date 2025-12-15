package core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DiffManager {

    /**
     * Shows diff between Working Directory and Staging Area (unstaged changes).
     * This is the default behavior for "smk diff".
     */
    public static void showDiff() {
        IndexMap index = IndexManager.readIndex();
        showDiffBetweenIndexAndWorkingDir(index);
    }

    /**
     * Shows diff between Working Directory and HEAD commit (all changes).
     * This is called for "smk diff HEAD".
     */
    public static void showDiffHead() {
        IndexMap headTree = CommitManager.readHeadTree();
        showDiffBetweenIndexAndWorkingDir(headTree);
    }

    /**
     * Shows diff between two commits.
     * This is called for "smk diff <commitA> <commitB>".
     */
    public static void showDiffCommits(String commitA, String commitB) {
        IndexMap treeA = CommitManager.readTreeFromCommit(commitA);
        IndexMap treeB = CommitManager.readTreeFromCommit(commitB);
        
        if (treeA.isEmpty() && !commitA.isEmpty()) {
            System.out.println("fatal: unknown commit " + commitA);
            return;
        }
        if (treeB.isEmpty() && !commitB.isEmpty()) {
            System.out.println("fatal: unknown commit " + commitB);
            return;
        }
        
        showDiffBetweenTrees(treeA, treeB, commitA, commitB);
    }

    /**
     * Shows diff between an IndexMap (staging area or commit tree) and working directory.
     */
    private static void showDiffBetweenIndexAndWorkingDir(IndexMap sourceTree) {
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(sourceTree.keySet());
        
        // Also check for files in working directory that might not be in source
        try {
            Path rootPath = Paths.get(".").toAbsolutePath().normalize();
            List<String> workingFiles = Utils.listFilesRecursive(".");
            for (String file : workingFiles) {
                // Convert to relative path and normalize separators
                Path filePath = Paths.get(file);
                if (filePath.isAbsolute()) {
                    filePath = rootPath.relativize(filePath);
                }
                String relPath = filePath.toString().replace('\\', '/');
                
                // Skip .smk files and hidden files
                if (relPath.startsWith(".smk") || relPath.contains("/.smk/") || relPath.contains("\\.smk\\")) {
                    continue;
                }
                Path relPathObj = Paths.get(relPath);
                if (relPathObj.getFileName() != null && relPathObj.getFileName().toString().startsWith(".")) {
                    continue;
                }
                allFiles.add(relPath);
            }
        } catch (Exception e) {
            // Ignore errors listing files
        }

        boolean hasChanges = false;
        for (String path : allFiles) {
            String sourceHash = sourceTree.get(path);
            
            // Get working directory content
            String workingContent = null;
            boolean workingExists = Files.exists(Paths.get(path));
            if (workingExists) {
                try {
                    workingContent = Utils.readFileStr(path);
                } catch (IOException e) {
                    // File exists but can't be read - treat as different
                    workingContent = "";
                }
            }
            
            // Get source content
            String sourceContent = null;
            if (sourceHash != null && !sourceHash.isEmpty()) {
                ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(sourceHash);
                if ("blob".equals(obj.type())) {
                    sourceContent = obj.content();
                }
            }
            
            // Compare content directly
            boolean contentDifferent = false;
            if (sourceContent == null && workingContent != null) {
                contentDifferent = true;
            } else if (sourceContent != null && workingContent == null) {
                contentDifferent = true;
            } else if (sourceContent != null && workingContent != null && !sourceContent.equals(workingContent)) {
                contentDifferent = true;
            }
            
            // Show diff if different
            if (sourceHash == null && workingContent != null) {
                // New file in working directory
                hasChanges = true;
                showFileDiff(path, null, workingContent, "new file");
            } else if (sourceHash != null && !workingExists) {
                // File deleted in working directory
                hasChanges = true;
                showFileDiff(path, sourceContent, null, "deleted file");
            } else if (contentDifferent) {
                // File modified
                hasChanges = true;
                showFileDiff(path, sourceContent, workingContent, "modified");
            }
        }
        
        if (!hasChanges) {
            System.out.println("No unstaged changes.");
        }
    }

    /**
     * Shows diff between two commit trees.
     */
    private static void showDiffBetweenTrees(IndexMap treeA, IndexMap treeB, String commitA, String commitB) {
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(treeA.keySet());
        allFiles.addAll(treeB.keySet());
        
        boolean hasChanges = false;
        for (String path : allFiles) {
            String hashA = treeA.get(path);
            String hashB = treeB.get(path);
            
            if (hashA == null && hashB != null) {
                // File added in B
                hasChanges = true;
                ObjectManager.ObjectContent objB = ObjectManager.readObjectContent(hashB);
                if ("blob".equals(objB.type())) {
                    showFileDiff(path, null, objB.content(), "new file");
                }
            } else if (hashA != null && hashB == null) {
                // File deleted in B
                hasChanges = true;
                ObjectManager.ObjectContent objA = ObjectManager.readObjectContent(hashA);
                if ("blob".equals(objA.type())) {
                    showFileDiff(path, objA.content(), null, "deleted file");
                }
            } else if (hashA != null && hashB != null && !hashA.equals(hashB)) {
                // File modified
                hasChanges = true;
                ObjectManager.ObjectContent objA = ObjectManager.readObjectContent(hashA);
                ObjectManager.ObjectContent objB = ObjectManager.readObjectContent(hashB);
                if ("blob".equals(objA.type()) && "blob".equals(objB.type())) {
                    showFileDiff(path, objA.content(), objB.content(), "modified");
                }
            }
        }
        
        if (!hasChanges) {
            System.out.println("No differences between " + commitA + " and " + commitB + ".");
        }
    }

    /**
     * Shows the diff for a single file.
     */
    private static void showFileDiff(String path, String oldContent, String newContent, String changeType) {
        System.out.println("diff -- " + path + " (" + changeType + ")");
        
        if (oldContent == null) oldContent = "";
        if (newContent == null) newContent = "";
        
        // If both are empty, skip
        if (oldContent.isEmpty() && newContent.isEmpty()) {
            return;
        }
        
        // Line-by-line comparison
        List<String> oldLines = new ArrayList<>();
        List<String> newLines = new ArrayList<>();
        
        try (BufferedReader oldReader = new BufferedReader(new StringReader(oldContent));
             BufferedReader newReader = new BufferedReader(new StringReader(newContent))) {
            
            String line;
            while ((line = oldReader.readLine()) != null) {
                oldLines.add(line);
            }
            while ((line = newReader.readLine()) != null) {
                newLines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading content for diff: " + e.getMessage());
            return;
        }
        
        int maxLines = Math.max(oldLines.size(), newLines.size());
        boolean hasOutput = false;
        
        for (int i = 0; i < maxLines; i++) {
            String oldLine = i < oldLines.size() ? oldLines.get(i) : null;
            String newLine = i < newLines.size() ? newLines.get(i) : null;
            
            if (oldLine == null && newLine != null) {
                // Line added
                System.out.println("+" + newLine);
                hasOutput = true;
            } else if (oldLine != null && newLine == null) {
                // Line removed
                System.out.println("-" + oldLine);
                hasOutput = true;
            } else if (oldLine != null && newLine != null && !oldLine.equals(newLine)) {
                // Line modified
                System.out.println("-" + oldLine);
                System.out.println("+" + newLine);
                hasOutput = true;
            }
        }
        
        if (!hasOutput && !oldContent.equals(newContent)) {
            // Content changed but line-by-line comparison didn't catch it
            // This can happen with different line endings or whitespace
            System.out.println("(binary or content differences)");
        }
        
        System.out.println(); // Blank line between files
    }
}
