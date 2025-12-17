# SMK VCS - Technical Implementation Guide

This document provides a detailed, technical explanation of all 20 SMK commands, their implementation, function call chains, and the Data Structures & Algorithms (DSA) concepts used throughout.

---

## 📁 .smk Directory Creation and Structure

### `smk init` - Repository Initialization

**Entry Point:** `Main.initRepo()`

**Implementation Steps:**

1. **Check if repository exists:**
   ```java
   if (Files.exists(Paths.get(SMK_DIR))) {
       System.out.println("Repository already initialized.");
       return;
   }
   ```
   - Uses `Files.exists()` to check if `.smk` directory exists
   - **DSA Concept:** Simple file system check (O(1) operation)

2. **Create directory structure:**
   ```java
   Utils.ensureDir(SMK_DIR + "/objects");        // Creates .smk/objects/
   Utils.ensureDir(REFS_HEADS_DIR);              // Creates .smk/refs/heads/
   ```
   - Calls `Utils.ensureDir()` which uses `Files.createDirectories()`
   - Creates parent directories recursively if needed
   - **DSA Concept:** Tree structure creation (directory tree)

3. **Initialize HEAD file:**
   ```java
   Utils.writeFile(Paths.get(HEAD_FILE), "ref: refs/heads/master\n");
   ```
   - Creates `.smk/HEAD` file with symbolic reference
   - Points to `refs/heads/master` (indirect reference)

4. **Create master branch reference:**
   ```java
   Utils.writeFile(Paths.get(REFS_HEADS_DIR + "/master"), "\n");
   ```
   - Creates `.smk/refs/heads/master` file (empty initially)

5. **Create empty index:**
   ```java
   Utils.writeFile(Paths.get(INDEX_FILE), "");
   ```
   - Creates `.smk/index` file (staging area, empty initially)

**Final Directory Structure:**
```
.smk/
├── HEAD                    # "ref: refs/heads/master\n"
├── index                   # "" (empty)
├── objects/                # Directory for object storage
└── refs/
    └── heads/
        └── master          # "" (empty until first commit)
```

**DSA Concepts Used:**
- **Tree Structure:** Directory hierarchy
- **File I/O:** Sequential file operations
- **String Operations:** Path concatenation, file content

**Helper Functions Called:**
- `Utils.ensureDir(String)` - Creates directory recursively
- `Utils.writeFile(Path, String)` - Writes content to file

---

## 1. `smk add <file>` - Stage Single File

**Entry Point:** `Main.cmdAdd(String file)`

**Function Call Chain:**
```
Main.cmdAdd(file)
  ├── ObjectManager.hashBlobFromFile(file)
  │     ├── Utils.readFileStr(file)
  │     └── ObjectManager.hashAndStoreObject("blob", content)
  │           ├── ObjectManager.calculateContentHash(full)
  │           └── Utils.writeFile(path, full)
  ├── IndexManager.readIndex()
  │     └── Utils.readFileStr(".smk/index")
  ├── CommitManager.readHeadTree()
  │     ├── CommitManager.readRefHead()
  │     │     └── Utils.readFileStr(".smk/HEAD")
  │     └── CommitManager.readTreeFromCommit(headCommit)
  │           ├── ObjectManager.readObjectContent(commitHash)
  │           └── CommitManager.readTree(treeHash)
  │                 └── ObjectManager.readObjectContent(treeHash)
  └── IndexManager.writeIndex(idx)
        └── Utils.writeFile(Paths.get(".smk/index"), content)
```

**Step-by-Step Implementation:**

1. **Validate file path:**
   ```java
   if (file.startsWith(SMK_DIR + "/") || file.contains("/" + SMK_DIR + "/")) {
       System.out.println("fatal: cannot add .smk directory");
       return;
   }
   ```
   - Prevents adding internal VCS files
   - **DSA Concept:** String pattern matching

2. **Check file existence:**
   ```java
   if (!Files.exists(filePath)) {
       System.out.println("fatal: pathspec '" + file + "' did not match any files");
       return;
   }
   ```

3. **Hash file content:**
   ```java
   String h = ObjectManager.hashBlobFromFile(file);
   ```
   - Reads file content using `Utils.readFileStr(file)`
   - Creates blob object: `ObjectManager.hashAndStoreObject("blob", content)`
   - **Hash Calculation:**
     ```java
     String header = type + "\n" + content.length() + "\n";
     String full = header + content;
     long h = Objects.hashCode(data) & 0xFFFFFFFFL;
     String id = String.format("%016x", h);
     ```
   - **DSA Concepts:**
     - **Hash Function:** Uses Java's `Objects.hashCode()` for content hashing
     - **Content-Addressable Storage:** Hash based on content, not location

4. **Load staging area and HEAD tree:**
   ```java
   IndexMap idx = IndexManager.readIndex();           // HashMap: path → hash
   IndexMap headTree = CommitManager.readHeadTree();  // HashMap: path → hash
   ```
   - **DSA Concept:** **HashMap/IndexMap** - Key-value mapping for O(1) lookups
   - `IndexMap` extends `HashMap<String, String>`

5. **Compare with HEAD:**
   ```java
   String headHash = headTree.get(file);  // O(1) HashMap lookup
   if (headHash == null || !headHash.equals(h)) {
       idx.put(file, h);  // O(1) HashMap insertion
   }
   ```
   - Only stages if file is new or changed
   - **DSA Concept:** HashMap get/put operations (average O(1))

6. **Write updated index:**
   ```java
   IndexManager.writeIndex(idx);
   ```
   - Serializes HashMap to file: `path\thash\n` format
   - **DSA Concept:** Serialization/Deserialization

**DSA Concepts Summary:**
- **HashMap:** Fast lookups and insertions (O(1) average)
- **Hash Function:** Content-based hashing for deduplication
- **String Operations:** Pattern matching, parsing
- **File I/O:** Reading/writing files

---

## 2. `smk add .` - Stage All Files

**Entry Point:** `Main.cmdAddAll()`

**Function Call Chain:**
```
Main.cmdAddAll()
  ├── Utils.listFilesRecursive(".")
  │     └── Files.walk(start)  // Java Stream API
  ├── IndexManager.readIndex()
  ├── CommitManager.readHeadTree()
  └── For each file:
        ├── ObjectManager.hashBlobFromFile(f)
        └── IndexManager.writeIndex(idx)
```

**Step-by-Step Implementation:**

1. **Recursively list all files:**
   ```java
   List<String> files = Utils.listFilesRecursive(".");
   ```
   - Uses `Files.walk(Paths.get(dir))` with Java Stream API
   - Filters for regular files only: `.filter(Files::isRegularFile)`
   - **DSA Concept:** **Tree Traversal** - DFS (Depth-First Search) via `Files.walk()`

2. **Filter out .smk and hidden files:**
   ```java
   for (String f : files) {
       if (f.startsWith(SMK_DIR + "/")) continue;
       if (filePath.getFileName().toString().startsWith(".")) continue;
   }
   ```
   - **DSA Concept:** Linear iteration with filtering (O(n))

3. **Process each file:**
   - Same logic as `cmdAdd()` but in a loop
   - Only stages changed files (efficient)

**DSA Concepts Summary:**
- **Tree Traversal:** DFS via `Files.walk()`
- **HashMap:** Index operations
- **Iteration:** Linear scan through files (O(n) where n = number of files)
- **Filtering:** Conditional processing

**Time Complexity:** O(F · S) where F = files, S = average file size

---

## 3. `smk commit -m "message"` - Create Commit

**Entry Point:** `CommitManager.createCommit(String message, boolean amend)`

**Function Call Chain (Regular Commit):**
```
CommitManager.createCommit(message, false)
  ├── CommitManager.readRefHead()
  ├── CommitManager.readHeadTree()
  │     ├── CommitManager.readRefHead()
  │     └── CommitManager.readTreeFromCommit(headCommit)
  ├── IndexManager.readIndex()
  ├── Build new tree:
  │     ├── newTree.putAll(headTree)  // Copy all entries
  │     ├── Check for deleted files (Files.exists check)
  │     └── newTree.putAll(idx)       // Apply staged changes
  ├── CommitManager.writeTreeFromIndex(newTree)
  │     └── ObjectManager.hashAndStoreObject("tree", content)
  ├── ObjectManager.hashAndStoreObject("commit", meta)
  ├── CommitManager.writeRefHead(commitHash)
  └── IndexManager.writeIndex(new IndexMap())  // Clear index
```

**Step-by-Step Implementation:**

1. **Read current state:**
   ```java
   String parent = readRefHead();              // Current HEAD commit
   IndexMap headTree = readHeadTree();         // Files in HEAD
   IndexMap idx = IndexManager.readIndex();    // Staged files
   ```
   - **DSA Concept:** HashMap retrieval

2. **Build new tree snapshot:**
   ```java
   IndexMap newTree = new IndexMap();
   newTree.putAll(headTree);  // Start with HEAD snapshot
   ```
   - **DSA Concept:** HashMap copy operation (O(n) where n = entries)

3. **Remove deleted files:**
   ```java
   for (String path : new ArrayList<>(newTree.keySet())) {
       if (!Files.exists(Paths.get(path))) {
           newTree.remove(path);  // O(1) HashMap removal
       }
   }
   ```
   - Creates copy of keySet to avoid concurrent modification
   - **DSA Concept:** HashMap keySet iteration, deletion

4. **Apply staged changes:**
   ```java
   for (Map.Entry<String, String> kv : idx.entrySet()) {
       newTree.put(kv.getKey(), kv.getValue());  // Overwrites if exists
   }
   ```
   - Staged files override HEAD versions
   - **DSA Concept:** HashMap put operation (O(1) average)

5. **Create tree object:**
   ```java
   String treeHash = writeTreeFromIndex(newTree);
   ```
   - Serializes tree: `path\thash\n` format
   - Stores as "tree" object
   - Returns hash ID

6. **Build commit metadata:**
   ```java
   StringBuilder meta = new StringBuilder();
   meta.append("tree ").append(treeHash).append("\n");
   if (parent != null && !parent.isEmpty()) 
       meta.append("parent ").append(parent).append("\n");
   meta.append("author Local User <local@smk>\n");
   meta.append("date ").append(new Date().getTime() / 1000).append("\n\n");
   meta.append(message).append("\n");
   ```
   - **DSA Concept:** String building (StringBuilder for efficiency)

7. **Create and store commit object:**
   ```java
   String commitHash = ObjectManager.hashAndStoreObject("commit", meta.toString());
   ```

8. **Update HEAD reference:**
   ```java
   writeRefHead(commitHash);
   ```

9. **Clear staging area:**
   ```java
   IndexManager.writeIndex(new IndexMap());  // Empty HashMap
   ```

**Commit Object Structure:**
```
tree [tree_hash]
parent [parent_commit_hash]
author Local User <local@smk>
date [timestamp]

[commit message]
```

**DSA Concepts Summary:**
- **HashMap:** Tree snapshot management (putAll, put, remove)
- **Graph Structure:** Commit chain (parent pointers form linked list/DAG)
- **String Operations:** StringBuilder for efficient concatenation
- **Hash Function:** Content-based object storage

---

## 4. `smk commit --amend -m "message"` - Amend Last Commit

**Implementation:** Same as regular commit, but:

1. **Get grandparent instead of parent:**
   ```java
   ObjectManager.ObjectContent parentObj = ObjectManager.readObjectContent(parent);
   // Parse parent commit to get grandparent
   String grandParent = "";  // Extract from parent commit content
   parent = grandParent;     // Use grandparent as new parent
   ```
   - **DSA Concept:** **Linked List Traversal** - Following parent pointer

2. **Build tree from parent + index:**
   - Starts from parent's tree instead of HEAD
   - Applies current index changes

**DSA Concept:** **Linked List Manipulation** - Skipping one node (parent)

---

## 5. `smk status` - Show Repository Status

**Entry Point:** `Main.cmdStatus()`

**Function Call Chain:**
```
Main.cmdStatus()
  ├── CommitManager.readHeadTree()  // HashMap
  ├── IndexManager.readIndex()      // HashMap
  ├── For each file in index:
  │     └── ObjectManager.hashBlobFromFile(path)  // Compare hashes
  ├── Utils.listFilesRecursive(".")
  └── Build categorized lists:
        ├── ArrayList<String> stagedNew
        ├── ArrayList<String> stagedModified
        ├── ArrayList<String> stagedDeleted
        ├── ArrayList<String> notStagedModified
        ├── ArrayList<String> notStagedDeleted
        └── ArrayList<String> untracked
```

**Step-by-Step Implementation:**

1. **Load trees:**
   ```java
   IndexMap headTree = CommitManager.readHeadTree();  // HEAD files
   IndexMap idx = IndexManager.readIndex();            // Staged files
   ```
   - **DSA Concept:** HashMap loading

2. **Find staged changes (Index vs HEAD):**
   ```java
   for (Map.Entry<String, String> kv : idx.entrySet()) {
       String headBlob = headTree.get(kv.getKey());  // O(1) lookup
       if (headBlob == null) {
           stagedNew.add(path);  // New file
       } else if (!headBlob.equals(kv.getValue())) {
           stagedModified.add(path);  // Modified
       }
   }
   ```
   - **DSA Concept:** HashMap iteration and lookup (O(n) iteration, O(1) lookup)

3. **Find staged deletions:**
   ```java
   for (Map.Entry<String, String> kv : headTree.entrySet()) {
       if (!idx.containsKey(kv.getKey())) {
           stagedDeleted.add(path);  // In HEAD but not in index
       }
   }
   ```
   - **DSA Concept:** HashMap key containment check (O(1))

4. **Find unstaged changes (Working dir vs Index):**
   ```java
   for (Map.Entry<String, String> kv : idx.entrySet()) {
       String workBlob = ObjectManager.hashBlobFromFile(path);
       if (!workBlob.equals(kv.getValue())) {
           notStagedModified.add(path);
       }
   }
   ```
   - Hashes working directory files and compares

5. **Find untracked files:**
   ```java
   List<String> files = Utils.listFilesRecursive(".");
   Set<String> trackedInIndexAndHead = new HashSet<>();
   trackedInIndexAndHead.addAll(idx.keySet());
   trackedInIndexAndHead.addAll(headTree.keySet());
   
   for (String f : files) {
       if (!trackedInIndexAndHead.contains(f)) {
           untracked.add(f);
       }
   }
   ```
   - **DSA Concepts:**
     - **HashSet:** Fast membership testing (O(1) contains)
     - **Set Union:** Combining index and head keys
     - **Set Difference:** Files not in tracked set

**DSA Concepts Summary:**
- **HashMap:** Index and tree lookups (O(1))
- **HashSet:** Fast untracked file detection (O(1) contains)
- **ArrayList:** Categorized lists for output
- **Set Operations:** Union (addAll), difference (contains check)

**Time Complexity:** O(F · S) where F = files, S = average file size (due to hashing)

---

## 6. `smk log` - Show Commit History

**Entry Point:** `Main.cmdLog(boolean oneline)`

**Function Call Chain:**
```
Main.cmdLog(oneline)
  ├── CommitManager.readRefHead()
  └── While loop (linked list traversal):
        ├── ObjectManager.readObjectContent(cur)
        ├── Parse commit content:
        │     ├── Extract header (before "\n\n")
        │     └── Extract message (after "\n\n")
        └── Extract parent commit hash
```

**Step-by-Step Implementation:**

1. **Get HEAD commit:**
   ```java
   String cur = CommitManager.readRefHead();
   ```

2. **Traverse commit chain:**
   ```java
   while (true) {
       ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(cur);
       if (!"commit".equals(obj.type())) break;
       
       // Parse and display commit
       String meta = obj.content();
       int blank = meta.indexOf("\n\n");
       String header = meta.substring(0, blank);
       String msg = meta.substring(blank + 2).trim();
       
       // Extract parent
       String parent = "";
       try (BufferedReader reader = new BufferedReader(new StringReader(header))) {
           String line;
           while ((line = reader.readLine()) != null) {
               if (line.startsWith("parent ")) {
                   parent = line.substring("parent ".length());
                   break;
               }
           }
       }
       
       if (parent.isEmpty()) break;  // Reached root commit
       cur = parent;  // Move to parent
   }
   ```
   - **DSA Concept:** **Linked List Traversal** - Following parent pointers
   - Each commit has pointer to parent → forms linked list
   - **Time Complexity:** O(n) where n = number of commits

**DSA Concepts Summary:**
- **Linked List:** Commit chain (parent pointers)
- **String Parsing:** Extracting header and message
- **Iteration:** Linear traversal

---

## 7. `smk branch` - List Branches

**Entry Point:** `BranchManager.listBranches()`

**Function Call Chain:**
```
BranchManager.listBranches()
  ├── BranchManager.currentBranchName()
  │     └── BranchManager.headSymbolicRef()
  │           └── Utils.readFileStr(".smk/HEAD")
  └── Files.list(REFS_HEADS_DIR)
```

**Step-by-Step Implementation:**

1. **Get current branch name:**
   ```java
   String currentBranch = currentBranchName();
   ```
   - Reads HEAD file
   - Extracts branch name from `ref: refs/heads/master`

2. **List all branch files:**
   ```java
   List<String> out = new ArrayList<>();
   try (var stream = Files.list(REFS_HEADS_DIR)) {
       stream.forEach(entry -> {
           if (Files.isRegularFile(entry)) {
               String name = entry.getFileName().toString();
               if (name.equals(currentBranch)) {
                   out.add("* " + name);  // Mark current
               } else {
                   out.add("  " + name);
               }
           }
       });
   }
   ```
   - **DSA Concept:** **Iteration** over directory entries
   - **ArrayList:** Building result list

**DSA Concepts Summary:**
- **File System Iteration:** Listing directory contents
- **ArrayList:** Result collection

---

## 8. `smk branch <name>` - Create Branch

**Entry Point:** `BranchManager.createBranch(String name)`

**Function Call Chain:**
```
BranchManager.createBranch(name)
  ├── CommitManager.readRefHead()  // Get current HEAD commit
  └── Utils.writeFile(refPath, headCommit + "\n")
```

**Step-by-Step Implementation:**

1. **Validate branch name:**
   ```java
   if (name == null || name.isEmpty()) {
       System.out.println("Branch name required.");
       return;
   }
   ```

2. **Get current HEAD commit:**
   ```java
   String headCommit = CommitManager.readRefHead();
   ```

3. **Check if branch exists:**
   ```java
   Path refPath = Paths.get(REFS_HEADS_DIR, name);
   if (Files.exists(refPath)) {
       System.out.println("Branch already exists: " + name);
       return;
   }
   ```

4. **Create branch reference:**
   ```java
   Utils.writeFile(refPath, headCommit + "\n");
   ```
   - Creates file `.smk/refs/heads/<name>` with commit hash
   - **DSA Concept:** Simple file creation (O(1))

**Branch Reference Structure:**
- File: `.smk/refs/heads/<branch_name>`
- Content: `[commit_hash]\n`

**DSA Concepts Summary:**
- **File I/O:** Creating reference file
- **String Operations:** Path building

---

## 9. `smk branch -d <name>` - Delete Branch

**Entry Point:** `BranchManager.deleteBranch(String name)`

**Implementation:**

1. **Safety checks:**
   ```java
   if (name.equals("master")) {
       System.out.println("fatal: cannot delete master branch");
       return;
   }
   if (name.equals(current)) {
       System.out.println("fatal: cannot delete the current checked-out branch");
       return;
   }
   ```
   - **DSA Concept:** String comparison

2. **Delete branch file:**
   ```java
   Files.delete(refPath);
   ```
   - Only deletes reference file, commits remain in object store
   - **DSA Concept:** File deletion (O(1))

---

## 10. `smk checkout <branch>` - Switch Branch

**Entry Point:** `BranchManager.checkoutBranch(String name)`

**Function Call Chain:**
```
BranchManager.checkoutBranch(name)
  ├── Utils.readFileStr(refPath)  // Read branch commit hash
  ├── Check for uncommitted changes:
  │     ├── CommitManager.readHeadTree()
  │     ├── IndexManager.readIndex()
  │     └── ObjectManager.hashBlobFromFile(path)  // Compare hashes
  ├── CommitManager.readHeadTree()  // Current tree
  ├── CommitManager.readTreeFromCommit(targetCommit)  // Target tree
  ├── BranchManager.writeTreeToWorkdirAndIndex(oldTree, newTree)
  │     ├── Delete files not in new tree
  │     ├── For each file in new tree:
  │     │     ├── ObjectManager.readObjectContent(blobHash)
  │     │     └── Utils.writeFile(path, content)
  │     └── IndexManager.writeIndex(newTree)
  └── Utils.writeFile(HEAD_FILE, "ref: refs/heads/" + name + "\n")
```

**Step-by-Step Implementation:**

1. **Read target branch commit:**
   ```java
   String targetCommit = Utils.readFileStr(refPath.toString()).trim();
   ```

2. **Check for uncommitted changes:**
   ```java
   IndexMap headTree = CommitManager.readHeadTree();
   IndexMap idx = IndexManager.readIndex();
   
   // Check if index differs from HEAD
   if (!idx.equals(headTree)) {
       hasUncommitted = true;
   }
   
   // Check if working dir differs from index
   for (Map.Entry<String, String> kv : idx.entrySet()) {
       String workBlob = ObjectManager.hashBlobFromFile(path);
       if (!workBlob.equals(kv.getValue())) {
           hasUncommitted = true;
           break;
       }
   }
   ```
   - **DSA Concept:** HashMap comparison (`equals()` method)
   - Hash-based comparison for efficiency

3. **Get trees:**
   ```java
   IndexMap oldTree = CommitManager.readHeadTree();      // Current state
   IndexMap newTree = CommitManager.readTreeFromCommit(targetCommit);  // Target state
   ```

4. **Update working directory:**
   ```java
   writeTreeToWorkdirAndIndex(oldTree, newTree);
   ```
   - **Delete files not in new tree:**
     ```java
     for (String path : oldTree.keySet()) {
         if (!newTree.containsKey(path)) {
             Files.deleteIfExists(root.resolve(path));  // O(1) file deletion
         }
     }
     ```
   - **Create/update files from new tree:**
     ```java
     for (Map.Entry<String, String> entry : newTree.entrySet()) {
         ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(blobHash);
         Utils.writeFile(filePath, obj.content());
     }
     ```
   - **Update index:**
     ```java
     IndexManager.writeIndex(newTree);
     ```

5. **Update HEAD:**
   ```java
   Utils.writeFile(Paths.get(HEAD_FILE), "ref: refs/heads/" + name + "\n");
   ```

**DSA Concepts Summary:**
- **HashMap Comparison:** Checking for changes (O(n) where n = entries)
- **Set Difference:** Finding files to delete (containsKey check)
- **Tree Traversal:** Processing all files in tree
- **File I/O:** Reading/writing files

**Time Complexity:** O(F · S) where F = files, S = average file size

---

## 11. `smk merge <branch>` - Merge Branches

**Entry Point:** `MergeManager.mergeBranch(String branch)`

**Function Call Chain:**
```
MergeManager.mergeBranch(branch)
  ├── CommitManager.readRefHead()  // Current HEAD
  ├── Utils.readFileStr(refPath)   // Target branch commit
  ├── MergeManager.findCommonAncestor(cur, target)
  │     ├── BFS traversal of commit1 ancestors:
  │     │     ├── Deque<String> queue1
  │     │     ├── HashSet<String> visited1
  │     │     └── HashSet<String> ancestors1
  │     └── BFS traversal of commit2 ancestors:
  │           ├── Deque<String> queue2
  │           └── HashSet<String> visited2
  ├── Check for fast-forward merge
  ├── If 3-way merge:
  │     ├── CommitManager.readTreeFromCommit(ancestor)  // Base tree
  │     ├── CommitManager.readHeadTree()                // Current tree
  │     ├── CommitManager.readTreeFromCommit(target)    // Target tree
  │     ├── Build merged tree:
  │     │     ├── HashSet<String> allFiles  // Union of all file paths
  │     │     └── For each file: conflict detection logic
  │     ├── MergeManager.UpdatDir(oldTree, mergedTree)
  │     ├── CommitManager.writeTreeFromIndex(mergedTree)
  │     └── ObjectManager.hashAndStoreObject("commit", meta)
```

**Step-by-Step Implementation:**

### A. Find Common Ancestor

**Algorithm: Breadth-First Search (BFS)**

```java
private static String findCommonAncestor(String commit1, String commit2) {
    // Step 1: Collect all ancestors of commit1 using BFS
    Set<String> ancestors1 = new HashSet<>();
    Deque<String> queue1 = new ArrayDeque<>();  // Queue for BFS
    queue1.add(commit1);
    Set<String> visited1 = new HashSet<>();
    
    while (!queue1.isEmpty()) {
        String cur = queue1.poll();  // Dequeue
        if (visited1.contains(cur)) continue;
        visited1.add(cur);
        ancestors1.add(cur);
        
        // Get all parents (handles merge commits with multiple parents)
        List<String> parents = extractParents(cur);
        for (String parent : parents) {
            if (!parent.isEmpty() && !visited1.contains(parent)) {
                queue1.add(parent);  // Enqueue
            }
        }
    }
    
    // Step 2: Traverse commit2's ancestors until we find common one
    Deque<String> queue2 = new ArrayDeque<>();
    queue2.add(commit2);
    Set<String> visited2 = new HashSet<>();
    
    while (!queue2.isEmpty()) {
        String cur = queue2.poll();
        if (visited2.contains(cur)) continue;
        visited2.add(cur);
        
        if (ancestors1.contains(cur)) {  // Found common ancestor!
            return cur;
        }
        
        List<String> parents = extractParents(cur);
        for (String parent : parents) {
            if (!parent.isEmpty() && !visited2.contains(parent)) {
                queue2.add(parent);
            }
        }
    }
    
    return "";  // No common ancestor
}
```

**DSA Concepts:**
- **BFS (Breadth-First Search):** Using `Deque` as queue
- **Graph Traversal:** Commit graph (DAG - Directed Acyclic Graph)
- **HashSet:** Fast ancestor lookup (O(1) contains)
- **Deque/Queue:** FIFO data structure for BFS

**Time Complexity:** O(C) where C = number of commits in history

### B. Fast-Forward Merge

```java
if (ancestor.equals(cur)) {
    // Current is ancestor of target → fast-forward
    IndexMap newTree = CommitManager.readTreeFromCommit(target);
    UpdatDir(oldTree, newTree);
    CommitManager.writeRefHead(target);  // Just move HEAD forward
    return;
}
```

- No merge commit needed
- **DSA Concept:** Simple pointer update

### C. 3-Way Merge

**Merge Algorithm:**

```java
IndexMap baseTree = readTreeFromCommit(ancestor);   // Common ancestor
IndexMap curTree = readHeadTree();                  // Current branch
IndexMap targetTree = readTreeFromCommit(target);   // Target branch

IndexMap mergedTree = new IndexMap();
Set<String> allFiles = new HashSet<>();
allFiles.addAll(baseTree.keySet());    // Set union
allFiles.addAll(curTree.keySet());
allFiles.addAll(targetTree.keySet());

for (String path : allFiles) {
    String baseHash = baseTree.get(path);
    String curHash = curTree.get(path);
    String targetHash = targetTree.get(path);
    
    // Merge logic based on 3-way comparison
    if (baseHash == null) {
        // File is new in both branches
        if (curHash != null && targetHash != null) {
            if (!curHash.equals(targetHash)) {
                // CONFLICT: Both added different content
                hasConflicts = true;
                mergedTree.put(path, curHash);  // Take current (user must resolve)
            } else {
                mergedTree.put(path, curHash);  // Same content
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
                mergedTree.put(path, curHash);  // Both changed same way
            } else if (curHash.equals(baseHash)) {
                mergedTree.put(path, targetHash);  // Only target changed
            } else if (targetHash.equals(baseHash)) {
                mergedTree.put(path, curHash);  // Only current changed
            } else {
                // CONFLICT: Both changed differently
                hasConflicts = true;
                mergedTree.put(path, curHash);
            }
        }
        // ... handle deletions
    }
}
```

**3-Way Merge Logic Table:**

| Base | Current | Target | Result | Conflict? |
|------|---------|--------|--------|-----------|
| ✓ | ✓ (same as base) | ✓ (changed) | Take target | No |
| ✓ | ✓ (changed) | ✓ (same as base) | Take current | No |
| ✓ | ✓ (changed) | ✓ (changed, same) | Take current | No |
| ✓ | ✓ (changed) | ✓ (changed, different) | Take current | **Yes** |
| - | ✓ | ✓ (same) | Take current | No |
| - | ✓ | ✓ (different) | Take current | **Yes** |
| ✓ | ✓ (changed) | - | Keep current | **Yes** |
| ✓ | - | ✓ (changed) | Keep target | **Yes** |
| ✓ | - | - | Delete | No |

**DSA Concepts:**
- **Set Union:** Combining file sets from three trees
- **HashMap Lookup:** Getting file hashes (O(1) per file)
- **Conflict Detection:** Hash comparison logic

**Time Complexity:** O(F + L) where F = files, L = lines compared

### D. Create Merge Commit

```java
meta.append("parent ").append(cur).append("\n");      // First parent
meta.append("parent ").append(target).append("\n");   // Second parent
String mergeCommitHash = ObjectManager.hashAndStoreObject("commit", meta.toString());
```

- **DSA Concept:** **Graph Structure** - Merge commit has two parents (forms DAG)

**DSA Concepts Summary:**
- **BFS:** Finding common ancestor
- **Graph (DAG):** Commit history structure
- **HashSet:** Ancestor tracking, file set union
- **Deque:** BFS queue
- **HashMap:** Tree comparisons
- **3-Way Comparison:** Merge algorithm

---

## 12. `smk diff` - Show Unstaged Changes

**Entry Point:** `DiffManager.showDiff()`

**Function Call Chain:**
```
DiffManager.showDiff()
  ├── IndexManager.readIndex()
  └── DiffManager.showDiffBetweenIndexAndWorkingDir(index)
        ├── Utils.listFilesRecursive(".")
        ├── For each file:
        │     ├── Utils.readFileStr(path)  // Working dir content
        │     ├── ObjectManager.readObjectContent(sourceHash)  // Index content
        │     └── DiffManager.showFileDiff(path, oldContent, newContent, type)
        │           ├── Split content into lines (ArrayList)
        │           └── Line-by-line comparison
```

**Step-by-Step Implementation:**

1. **Get staging area:**
   ```java
   IndexMap index = IndexManager.readIndex();
   ```

2. **Compare with working directory:**
   ```java
   showDiffBetweenIndexAndWorkingDir(index);
   ```

3. **For each file, compare content:**
   ```java
   String workingContent = Utils.readFileStr(path);
   ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(sourceHash);
   String sourceContent = obj.content();
   
   if (!sourceContent.equals(workingContent)) {
       showFileDiff(path, sourceContent, workingContent, "modified");
   }
   ```

4. **Line-by-line diff:**
   ```java
   List<String> oldLines = new ArrayList<>();
   List<String> newLines = new ArrayList<>();
   
   // Read into lists
   BufferedReader oldReader = new BufferedReader(new StringReader(oldContent));
   BufferedReader newReader = new BufferedReader(new StringReader(newContent));
   
   for (int i = 0; i < maxLines; i++) {
       String oldLine = i < oldLines.size() ? oldLines.get(i) : null;
       String newLine = i < newLines.size() ? newLines.get(i) : null;
       
       if (oldLine == null && newLine != null) {
           System.out.println("+" + newLine);  // Added line
       } else if (oldLine != null && newLine == null) {
           System.out.println("-" + oldLine);  // Removed line
       } else if (oldLine != null && !oldLine.equals(newLine)) {
           System.out.println("-" + oldLine);  // Modified line
           System.out.println("+" + newLine);
       }
   }
   ```
   - **DSA Concept:** **ArrayList** - Line storage
   - **Linear Comparison:** O(L) where L = number of lines

**DSA Concepts Summary:**
- **ArrayList:** Storing file lines
- **String Comparison:** Content comparison
- **Linear Scan:** Line-by-line iteration

**Time Complexity:** O(F + L) where F = files, L = total lines

---

## 13. `smk diff HEAD` - Show All Changes

**Entry Point:** `DiffManager.showDiffHead()`

**Implementation:**
```java
IndexMap headTree = CommitManager.readHeadTree();
showDiffBetweenIndexAndWorkingDir(headTree);
```

- Same as `smk diff` but compares with HEAD instead of index
- **DSA Concept:** Same as above

---

## 14. `smk diff <commitA> <commitB>` - Compare Commits

**Entry Point:** `DiffManager.showDiffCommits(String commitA, String commitB)`

**Function Call Chain:**
```
DiffManager.showDiffCommits(commitA, commitB)
  ├── CommitManager.readTreeFromCommit(commitA)
  ├── CommitManager.readTreeFromCommit(commitB)
  └── DiffManager.showDiffBetweenTrees(treeA, treeB, commitA, commitB)
        └── For each file: showFileDiff()  // Same line-by-line comparison
```

**Implementation:**

1. **Load both commit trees:**
   ```java
   IndexMap treeA = CommitManager.readTreeFromCommit(commitA);
   IndexMap treeB = CommitManager.readTreeFromCommit(commitB);
   ```

2. **Find all files in both:**
   ```java
   Set<String> allFiles = new HashSet<>();
   allFiles.addAll(treeA.keySet());  // Set union
   allFiles.addAll(treeB.keySet());
   ```

3. **Compare each file:**
   ```java
   for (String path : allFiles) {
       String hashA = treeA.get(path);
       String hashB = treeB.get(path);
       
       if (hashA == null && hashB != null) {
           // File added in B
           showFileDiff(path, null, objB.content(), "new file");
       } else if (hashA != null && hashB == null) {
           // File deleted in B
           showFileDiff(path, objA.content(), null, "deleted file");
       } else if (hashA != null && hashB != null && !hashA.equals(hashB)) {
           // File modified
           showFileDiff(path, objA.content(), objB.content(), "modified");
       }
   }
   ```

**DSA Concepts Summary:**
- **HashSet:** File set union
- **HashMap:** Tree lookups
- **ArrayList:** Line storage for diff
- **Set Operations:** Union (addAll)

---

## 15. `smk revert <commit>` - Revert to Commit

**Entry Point:** `Main.cmdRevert(String commitHash)`

**Function Call Chain:**
```
Main.cmdRevert(commitHash)
  ├── CommitManager.readTreeFromCommit(commitHash)  // Target tree
  ├── CommitManager.readHeadTree()                  // Current tree
  ├── Delete files not in target:
  │     └── Files.deleteIfExists(path)
  ├── Restore files from target:
  │     ├── ObjectManager.readObjectContent(blobHash)
  │     └── Utils.writeFile(path, content)
  ├── IndexManager.writeIndex(newTree)
  └── CommitManager.createCommit("Revert to " + commitHash, false)
```

**Implementation:**

1. **Get trees:**
   ```java
   IndexMap newTree = CommitManager.readTreeFromCommit(commitHash);
   IndexMap oldTree = CommitManager.readHeadTree();
   ```

2. **Delete files not in target:**
   ```java
   for (String path : oldTree.keySet()) {
       if (!newTree.containsKey(path)) {
           Files.deleteIfExists(Paths.get(path));
       }
   }
   ```
   - **DSA Concept:** Set difference (containsKey check)

3. **Restore files from target:**
   ```java
   for (Map.Entry<String, String> kv : newTree.entrySet()) {
       ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(kv.getValue());
       Utils.writeFile(Paths.get(kv.getKey()), obj.content());
   }
   ```

4. **Update index and create commit:**
   ```java
   IndexManager.writeIndex(newTree);
   CommitManager.createCommit("Revert to " + commitHash, false);
   ```

**DSA Concepts Summary:**
- **HashMap:** Tree operations
- **Set Difference:** Finding files to delete
- **File I/O:** Reading/writing files

---

## 16. `smk clone <path>` - Clone Repository

**Entry Point:** `Main.cmdClone(String source)`

**Function Call Chain:**
```
Main.cmdClone(source)
  ├── Paths.get(source)
  ├── Files.createDirectories(dest)
  └── Files.walk(src)
        └── For each path:
              ├── Files.isDirectory() → Files.createDirectories()
              └── Files.isRegularFile() → Files.copy()
```

**Implementation:**

1. **Validate source:**
   ```java
   Path src = Paths.get(source);
   if (!Files.exists(src) || !Files.isDirectory(src)) {
       System.out.println("fatal: clone source not found: " + source);
       return;
   }
   ```

2. **Create destination:**
   ```java
   Path dest = src.getFileName();  // Use source directory name
   Files.createDirectories(dest);
   ```

3. **Copy all files recursively:**
   ```java
   try (Stream<Path> walk = Files.walk(src)) {
       walk.forEach(from -> {
           Path rel = src.relativize(from);  // Relative path
           Path to = dest.resolve(rel);      // Destination path
           
           if (Files.isDirectory(from)) {
               Files.createDirectories(to);
           } else if (Files.isRegularFile(from)) {
               Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
           }
       });
   }
   ```
   - **DSA Concept:** **Tree Traversal** - DFS via `Files.walk()`
   - Copies entire directory tree including `.smk` folder

**DSA Concepts Summary:**
- **Tree Traversal:** DFS with `Files.walk()`
- **File I/O:** Directory and file copying

**Time Complexity:** O(N) where N = total files/directories

---

## 17. `smk show <commit>` - Show Commit Details

**Entry Point:** `Main.cmdShow(String commitArg)`

**Function Call Chain:**
```
Main.cmdShow(commitArg)
  ├── CommitManager.readRefHead()  // If "HEAD"
  └── ObjectManager.readObjectContent(hash)
        └── Parse commit content
```

**Implementation:**

1. **Resolve commit hash:**
   ```java
   String hash = commitArg;
   if (hash.isEmpty() || "HEAD".equals(hash)) {
       hash = CommitManager.readRefHead();
   }
   ```

2. **Read commit object:**
   ```java
   ObjectManager.ObjectContent obj = ObjectManager.readObjectContent(hash);
   ```

3. **Parse and display:**
   ```java
   String meta = obj.content();
   int blank = meta.indexOf("\n\n");
   String header = meta.substring(0, blank);      // Metadata
   String msg = meta.substring(blank + 2);        // Message
   
   System.out.println("commit " + hash);
   System.out.println(header);  // tree, parent, author, date
   System.out.println(msg);     // Commit message
   ```
   - **DSA Concept:** String parsing (substring, indexOf)

**DSA Concepts Summary:**
- **String Operations:** Parsing commit content

---

## 18. `smk clean` - Remove Untracked Files

**Entry Point:** `Main.cmdClean()`

**Function Call Chain:**
```
Main.cmdClean()
  ├── CommitManager.readHeadTree()
  ├── IndexManager.readIndex()
  ├── Utils.listFilesRecursive(".")
  └── For each file:
        ├── Check if untracked: !idx.containsKey(path) && !headTree.containsKey(path)
        └── Files.deleteIfExists(path)
```

**Implementation:**

1. **Load tracking information:**
   ```java
   IndexMap headTree = CommitManager.readHeadTree();
   IndexMap idx = IndexManager.readIndex();
   ```

2. **List all files:**
   ```java
   List<String> files = Utils.listFilesRecursive(".");
   ```

3. **Delete untracked files:**
   ```java
   for (String path : files) {
       // Skip .smk directory
       if (path.startsWith(SMK_DIR)) continue;
       
       // Only remove if not in index AND not in HEAD
       if (!idx.containsKey(path) && !headTree.containsKey(path)) {
           Files.deleteIfExists(Paths.get(path));
           System.out.println("removed " + path);
       }
   }
   ```
   - **DSA Concept:** **Set Membership Test** - `containsKey()` checks (O(1))
   - **Set Intersection:** Files must NOT be in either set

**DSA Concepts Summary:**
- **HashMap Lookup:** Checking if file is tracked (O(1))
- **Set Operations:** Intersection (file must be in neither set)
- **File Deletion:** Removing files

**Time Complexity:** O(F) where F = number of files

---

## Summary of DSA Concepts Used

### Data Structures:

1. **HashMap/IndexMap:**
   - File path → hash mapping
   - Used in: Index, trees, status, diff, merge
   - Operations: put, get, containsKey (O(1) average)

2. **HashSet:**
   - Fast membership testing
   - Used in: Merge (ancestor tracking), status (untracked detection), diff (file sets)
   - Operations: add, contains (O(1) average)

3. **ArrayList:**
   - Dynamic arrays for lists
   - Used in: Status categories, file lists, commit parents, diff lines
   - Operations: add, get (O(1))

4. **Deque/Queue:**
   - FIFO for BFS
   - Used in: Merge (finding common ancestor)
   - Operations: add (enqueue), poll (dequeue) (O(1))

5. **Linked List:**
   - Commit chain (via parent pointers)
   - Used in: Log traversal
   - Operations: Traversal (O(n))

6. **Tree (Directory Structure):**
   - File system hierarchy
   - Used in: File listing, clone
   - Operations: DFS traversal

7. **Graph (DAG):**
   - Commit history with merge commits
   - Used in: Merge (multiple parents)
   - Operations: BFS traversal

### Algorithms:

1. **BFS (Breadth-First Search):**
   - Finding common ancestor in merge
   - Uses: Deque, HashSet

2. **DFS (Depth-First Search):**
   - File system traversal
   - Uses: Files.walk()

3. **Hash Function:**
   - Content-based hashing
   - Used in: Object storage, file comparison

4. **3-Way Merge:**
   - Merging divergent branches
   - Uses: HashMap comparison, conflict detection

5. **Linear Search/Comparison:**
   - Diff algorithm (line-by-line)
   - Uses: ArrayList iteration

6. **Set Operations:**
   - Union, intersection, difference
   - Used in: Status, merge, diff

### Time Complexities:

| Operation | Time Complexity | Reason |
|-----------|----------------|--------|
| `add` | O(F · S) | Must hash all files |
| `commit` | O(F · S) | Serialize all files |
| `status` | O(F · S) | Hash working directory files |
| `log` | O(C) | Traverse commit chain |
| `merge` | O(C + F + L) | BFS for ancestor + file comparison |
| `diff` | O(F + L) | Compare files and lines |
| `checkout` | O(F · S) | Write all files |
| `clean` | O(F) | Check and delete untracked files |

Where:
- **F** = Number of files
- **S** = Average file size
- **C** = Number of commits
- **L** = Total lines compared

---

This technical guide provides a complete understanding of how each SMK command is implemented, which functions are called, and what DSA concepts are applied at each step.

