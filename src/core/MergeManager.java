package core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Date;

/**
 * Implements branch merge logic (fast-forward, 3-way, conflict detection) without deleting branches.
 */
public class MergeManager {

    private static final String REFS_HEADS_DIR = ".smk/refs/heads/";

    /**
     * Finds the common ancestor commit between two commit hashes.
     * Handles merge commits (commits with multiple parents) by following all parents.
     */
    private static String findCommonAncestor(String commit1, String commit2) {
        // Collect all ancestors of commit1 (including merge commits)
        Set<String> ancestors1 = new HashSet<>();
        Deque<String> queue1 = new ArrayDeque<>();
        queue1.add(commit1);
        Set<String> visited1 = new HashSet<>();
        
        while (!queue1.isEmpty()) {
            String cur = queue1.poll();
            if (visited1.contains(cur)) continue;
            visited1.add(cur);
            ancestors1.add(cur);
            
            ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(cur);
            if (!"commit".equals(obj.type())) continue;
            
            List<String> parents = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new StringReader(obj.content()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("parent ")) {
                        parents.add(line.substring("parent ".length()));
                    }
                    if (line.isEmpty()) break;
                }
            } catch (IOException e) {
                break;
            }
            
            // Add all parents to queue (for merge commits)
            for (String parent : parents) {
                if (!parent.isEmpty() && !visited1.contains(parent)) {
                    queue1.add(parent);
                }
            }
        }

        // Find first common ancestor when traversing commit2's ancestors
        Deque<String> queue2 = new ArrayDeque<>();
        queue2.add(commit2);
        Set<String> visited2 = new HashSet<>();
        
        while (!queue2.isEmpty()) {
            String cur = queue2.poll();
            if (visited2.contains(cur)) continue;
            visited2.add(cur);
            
            if (ancestors1.contains(cur)) {
                return cur;
            }
            
            ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(cur);
            if (!"commit".equals(obj.type())) continue;
            
            List<String> parents = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new StringReader(obj.content()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("parent ")) {
                        parents.add(line.substring("parent ".length()));
                    }
                    if (line.isEmpty()) break;
                }
            } catch (IOException e) {
                break;
            }
            
            // Add all parents to queue (for merge commits)
            for (String parent : parents) {
                if (!parent.isEmpty() && !visited2.contains(parent)) {
                    queue2.add(parent);
                }
            }
        }

        return ""; // No common ancestor found
    }

    /**
     * Performs a merge from the target branch to the current HEAD.
     * Handles fast-forward, three-way merges, and conflicts.
     * 
     * IMPORTANT: Branches are NEVER deleted during merge operations.
     * The source branch remains intact after merging.
     */
    public static void mergeBranch(final String branch) {
        String refPathStr = REFS_HEADS_DIR + branch;
        Path refPath = Paths.get(refPathStr);

        String target;
        try {
            target = Utils.readFileStr(refPathStr).trim();
        } catch (IOException e) {
            System.out.println("Branch not found: " + branch);
            return;
        }

        if (target.isEmpty()) {
            System.out.println("Branch not found: " + branch);
            return;
        }

        String cur = CommitManager.readRefHead();
        if (cur.isEmpty()) {
            System.out.println("Cannot merge: no commits in current branch.");
            return;
        }

        if (cur.equals(target)) {
            System.out.println("Already up to date.");
            return;
        }

        // Check if fast-forward merge is possible
        String ancestor = findCommonAncestor(cur, target);
        if (ancestor.equals(cur)) {
            // Fast-forward merge: current is ancestor of target
            // No new commit is created, just move HEAD forward
            // The source branch is NOT deleted - it still exists
            IndexMap oldTree = CommitManager.readHeadTree();
            IndexMap newTree = CommitManager.readTreeFromCommit(target);

            if (newTree.isEmpty()) {
                System.out.println("Nothing to merge from " + branch + ".");
                return;
            }

            // Update working directory
            UpdatDir(oldTree, newTree);
            CommitManager.writeRefHead(target);
            System.out.println("Fast-forward merged '" + branch + "' into current branch.");
            System.out.println("Note: Branch '" + branch + "' still exists and was not deleted.");
            return;
        }
        
        // Check for reverse fast-forward (target is ancestor of current)
        if (ancestor.equals(target)) {
            // Current branch already contains all commits from target
            System.out.println("Already up to date. Current branch contains all commits from '" + branch + "'.");
            return;
        }

        // 3-way merge
        IndexMap baseTree = CommitManager.readTreeFromCommit(ancestor);
        IndexMap curTree = CommitManager.readHeadTree();
        IndexMap targetTree = CommitManager.readTreeFromCommit(target);

        IndexMap mergedTree = new IndexMap();
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(baseTree.keySet());
        allFiles.addAll(curTree.keySet());
        allFiles.addAll(targetTree.keySet());

        boolean hasConflicts = false;

        for (String path : allFiles) {
            String baseHash = baseTree.get(path);
            String curHash = curTree.get(path);
            String targetHash = targetTree.get(path);

            // Determine merge result
            if (baseHash == null) {
                // File is new in both branches
                if (curHash != null && targetHash != null) {
                    if (!curHash.equals(targetHash)) {
                        // Conflict: both branches added different content
                        System.out.println("CONFLICT: " + path + " added in both branches with different content");
                        hasConflicts = true;
                        // Take current version for now
                        mergedTree.put(path, curHash);
                    } else {
                        mergedTree.put(path, curHash);
                    }
                } else if (curHash != null) {
                    mergedTree.put(path, curHash);
                } else if (targetHash != null) {
                    mergedTree.put(path, targetHash);
                }
            } else {
                // File existed in base
                if (curHash != null && targetHash != null) {
                    if (curHash.equals(targetHash)) {
                        // Both changed the same way
                        mergedTree.put(path, curHash);
                    } else if (curHash.equals(baseHash)) {
                        // Only target changed
                        mergedTree.put(path, targetHash);
                    } else if (targetHash.equals(baseHash)) {
                        // Only current changed
                        mergedTree.put(path, curHash);
                    } else {
                        // Conflict: both changed differently
                        System.out.println("CONFLICT: " + path + " modified in both branches");
                        hasConflicts = true;
                        // Take current version for now
                        mergedTree.put(path, curHash);
                    }
                } else if (curHash != null) {
                    if (curHash.equals(baseHash)) {
                        // Deleted in target
                        // Don't add to merged tree (file is deleted)
                    } else {
                        // Modified in current, deleted in target
                        System.out.println("CONFLICT: " + path + " modified in current, deleted in " + branch);
                        hasConflicts = true;
                        mergedTree.put(path, curHash);
                    }
                } else if (targetHash != null) {
                    if (targetHash.equals(baseHash)) {
                        // Deleted in current
                        // Don't add to merged tree
                    } else {
                        // Modified in target, deleted in current
                        System.out.println("CONFLICT: " + path + " deleted in current, modified in " + branch);
                        hasConflicts = true;
                        mergedTree.put(path, targetHash);
                    }
                } else {
                    // Deleted in both - don't add to merged tree
                }
            }
        }

        if (hasConflicts) {
            // Merge with conflicts: merge stops, files enter conflicted state
            // The source branch is NOT deleted - it still exists
            // User must manually resolve conflicts and commit
            System.out.println("Merge conflicts detected. Resolve conflicts and commit.");
            System.out.println("Note: Branch '" + branch + "' still exists and was not deleted.");
            System.out.println("After resolving conflicts, commit to complete the merge.");
            IndexManager.writeIndex(mergedTree);
            // Note: Merge is aborted if conflicts are not resolved and committed
            return;
        }

        // Update working directory with merged tree
        IndexMap oldTree = CommitManager.readHeadTree();
        UpdatDir(oldTree, mergedTree);

        // Three-way merge: Create merge commit with two parents
        // The source branch is NOT deleted - it still exists
        // Commits from the source branch do not move - master just points to the merge commit
        String treeHash = CommitManager.writeTreeFromIndex(mergedTree);
        StringBuilder meta = new StringBuilder();
        meta.append("tree ").append(treeHash).append("\n");
        meta.append("parent ").append(cur).append("\n");
        meta.append("parent ").append(target).append("\n");
        meta.append("author Local User <local@smk>\n");
        long t = new Date().getTime() / 1000;
        meta.append("date ").append(t).append("\n\n");
        meta.append("Merge branch '").append(branch).append("'\n");

        String mergeCommitHash = ObjectManager.hashAndStoreObject("commit", meta.toString());
        CommitManager.writeRefHead(mergeCommitHash);

        System.out.println("Merged '" + branch + "' into current branch. Commit: " + mergeCommitHash);
        System.out.println("Note: Branch '" + branch + "' still exists and was not deleted.");
    }

    private static void UpdatDir(IndexMap oldTree, IndexMap newTree) {
        for (final String path : oldTree.keySet()) {
            if (!newTree.containsKey(path)) {
                try {
                    Files.deleteIfExists(Paths.get(path));
                } catch (IOException ignored) {}
            }
        }

        for (Map.Entry<String, String> kv : newTree.entrySet()) {
            ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(kv.getValue());
            if (!"blob".equals(obj.type())) continue;

            Path p = Paths.get(kv.getKey());
            try {
                if (p.getParent() != null) {
                    Files.createDirectories(p.getParent());
                }
                Utils.writeFile(p, obj.content());
            } catch (IOException e) {
                System.err.println("Error writing file during merge: " + e.getMessage());
            }
        }

        IndexManager.writeIndex(newTree);
    }
}