# SMK VCS - Complete Project Explanation

## 📋 Project Overview

**SMK VCS** is a Version Control System (VCS) written in Java, implementing Git-like functionality for managing file versions, branches, commits, and merges. It was developed as a Data Structures and Algorithms (DSA) project by a team of four developers.

**Group Members:**
- Saad Muhammad Khan
- Muhammad Kaif bin Abubakr
- Hammad Ajmal
- Syed Wamiq ul Islam

### Architecture

The project consists of:
- **CLI (Command-Line Interface)**: `Main.java` - Text-based interface for all VCS operations
- **GUI (Graphical User Interface)**: `GUI.java` - JavaFX-based visual interface with commit history visualization
- **Core Package**: Modular components handling specific VCS operations

---

## 🏗️ System Architecture

### Repository Structure

When you run `smk init`, it creates a `.smk` directory with:
```
.smk/
├── HEAD                    # Points to current branch or commit
├── index                   # Staging area (files ready to commit)
├── config                  # Configuration file (if implemented)
├── objects/                # Content-addressable object storage
│   └── [hash files]       # Blobs, trees, and commits
└── refs/
    └── heads/             # Branch references
        ├── master         # Default branch
        └── [branch names] # Other branches
```

### Core Components

#### 1. **ObjectManager.java**
- **Purpose**: Content-addressable storage for all VCS objects
- **Object Types**:
  - **Blob**: File content (actual file data)
  - **Tree**: Directory snapshot (maps file paths to blob hashes)
  - **Commit**: Snapshot metadata (tree hash, parent commit, author, date, message)
- **Key Functions**:
  - `hashAndStoreObject(type, content)`: Creates and stores objects using content hashing
  - `readObjectContent(id)`: Retrieves object by hash ID
  - `hashBlobFromFile(filePath)`: Creates blob from working directory file
- **Hash Algorithm**: Uses Java's `String.hashCode()` (simplified; real Git uses SHA-1/SHA-256)

#### 2. **IndexManager.java**
- **Purpose**: Manages the staging area (index)
- **Key Functions**:
  - `readIndex()`: Loads staging area from `.smk/index` file
  - `writeIndex(IndexMap)`: Saves staging area to disk
- **IndexMap**: Extends `HashMap<String, String>` (file path → blob hash mapping)

#### 3. **CommitManager.java**
- **Purpose**: Handles commit creation and tree management
- **Key Functions**:
  - `createCommit(message, amend)`: Creates new commit or amends previous one
  - `readTreeFromCommit(commitHash)`: Reads file tree from a commit
  - `readHeadTree()`: Gets file tree of current HEAD commit
  - `writeTreeFromIndex(index)`: Creates tree object from staging area
  - `readRefHead()`: Reads current HEAD reference
  - `writeRefHead(commitHash)`: Updates HEAD to point to a commit

#### 4. **BranchManager.java**
- **Purpose**: Branch creation, deletion, and checkout operations
- **Key Functions**:
  - `listBranches()`: Lists all branches (marks current with *)
  - `createBranch(name)`: Creates new branch pointing to current HEAD
  - `deleteBranch(name)`: Deletes branch (prevents deleting master/current)
  - `checkoutBranch(name)`: Switches working directory to branch's state
- **Features**:
  - Fast-forward checkout (updates files efficiently)
  - Warning for uncommitted changes

#### 5. **MergeManager.java**
- **Purpose**: Implements branch merging with conflict detection
- **Key Functions**:
  - `mergeBranch(branchName)`: Merges target branch into current branch
  - `findCommonAncestor(commit1, commit2)`: Finds lowest common ancestor using BFS
- **Merge Types**:
  - **Fast-forward**: When current branch is ancestor of target (simple update)
  - **3-way merge**: Uses common ancestor, current branch, and target branch
  - **Conflict detection**: Identifies conflicts when both branches modify same file
- **Note**: Branches are NEVER deleted during merge (preserves history)

#### 6. **DiffManager.java**
- **Purpose**: Shows differences between versions
- **Key Functions**:
  - `showDiff()`: Working directory vs staging area (unstaged changes)
  - `showDiffHead()`: Working directory vs HEAD commit (all changes)
  - `showDiffCommits(commitA, commitB)`: Compares two commits
- **Output Format**: Unified diff format with `+` (added) and `-` (removed) lines

#### 7. **Utils.java**
- **Purpose**: Common file I/O utilities
- **Key Functions**:
  - `readFileStr(path)`: Reads entire file as string
  - `writeFile(path, data)`: Writes data to file (creates directories)
  - `listFilesRecursive(dir)`: Recursively lists all files
  - `ensureDir(path)`: Creates directory structure

---

## 📚 All SMK Commands & Functions

### Repository Management

#### 1. `smk init`
- **Function**: `initRepo()`
- **Purpose**: Initializes a new SMK repository
- **What it does**:
  - Creates `.smk` directory structure
  - Sets up `objects/`, `refs/heads/` directories
  - Creates initial `HEAD` pointing to `refs/heads/master`
  - Creates empty `master` branch reference
  - Creates empty `index` file

#### 2. `smk clone <path>`
- **Function**: `cmdClone(source)`
- **Purpose**: Clones a local repository
- **What it does**:
  - Copies entire directory structure including `.smk` folder
  - Creates new directory with repository name
  - Preserves all commits, branches, and objects

---

### Staging & Committing

#### 3. `smk add <file>`
- **Function**: `cmdAdd(file)`
- **Purpose**: Stages a single file
- **What it does**:
  - Calculates file hash using `ObjectManager.hashBlobFromFile()`
  - Compares with HEAD version
  - Only stages if file is new or modified
  - Updates index file
  - Prevents adding `.smk` directory

#### 4. `smk add .`
- **Function**: `cmdAddAll()`
- **Purpose**: Stages all files in working directory
- **What it does**:
  - Recursively lists all files
  - Skips `.smk` directory and hidden files
  - Stages only new/modified files (efficient)
  - Updates index

#### 5. `smk commit -m "message"`
- **Function**: `CommitManager.createCommit(message, false)`
- **Purpose**: Creates a new commit
- **What it does**:
  - Reads staging area (index)
  - Merges index with HEAD tree (preserves unmodified files)
  - Creates tree object from combined snapshot
  - Creates commit object with:
    - Tree hash
    - Parent commit hash (from HEAD)
    - Author info
    - Timestamp
    - Commit message
  - Updates HEAD reference
  - Clears staging area

#### 6. `smk commit --amend -m "message"`
- **Function**: `CommitManager.createCommit(message, true)`
- **Purpose**: Modifies the last commit
- **What it does**:
  - Finds grandparent of current HEAD
  - Uses staged changes (or previous tree if no changes)
  - Creates new commit replacing the previous one
  - Updates HEAD to new commit hash
  - Preserves commit history structure

---

### Status & Information

#### 7. `smk status`
- **Function**: `cmdStatus()`
- **Purpose**: Shows repository status
- **What it shows**:
  - Current branch name (or detached HEAD)
  - **Staged changes**: New, modified, deleted files ready to commit
  - **Unstaged changes**: Modified/deleted files not in index
  - **Untracked files**: Files not in index or HEAD
  - "Working tree clean" if no changes

#### 8. `smk log`
- **Function**: `cmdLog(false)`
- **Purpose**: Shows commit history
- **Format**:
  ```
  commit [full_hash]

      [commit message]

  commit [parent_hash]
  ...
  ```
- **Traversal**: Follows parent pointers from HEAD to root

#### 9. `smk log --oneline`
- **Function**: `cmdLog(true)`
- **Purpose**: Compact one-line commit log
- **Format**: `[short_hash] [first line of message]`

#### 10. `smk show <commit>`
- **Function**: `cmdShow(commitHash)`
- **Purpose**: Shows detailed commit information
- **What it shows**:
  - Full commit hash
  - Tree hash
  - Parent hash(es)
  - Author
  - Date
  - Full commit message

---

### Branching

#### 11. `smk branch`
- **Function**: `BranchManager.listBranches()`
- **Purpose**: Lists all branches
- **Format**: `* branch_name` (asterisk marks current branch)

#### 12. `smk branch <name>`
- **Function**: `BranchManager.createBranch(name)`
- **Purpose**: Creates a new branch
- **What it does**:
  - Creates reference file in `.smk/refs/heads/<name>`
  - Points branch to current HEAD commit
  - Does NOT switch to new branch (use `checkout` for that)

#### 13. `smk branch -d <name>`
- **Function**: `BranchManager.deleteBranch(name)`
- **Purpose**: Deletes a branch
- **Safety checks**:
  - Cannot delete `master` branch
  - Cannot delete current branch
  - Only deletes reference file (commits remain in object store)

#### 14. `smk checkout <branch>`
- **Function**: `BranchManager.checkoutBranch(name)`
- **Purpose**: Switches to a branch
- **What it does**:
  - Reads target branch's commit hash
  - Reads tree from that commit
  - Updates working directory files:
    - Deletes files not in target tree
    - Creates/updates files from target tree
  - Updates index to match target tree
  - Updates HEAD to point to branch reference
  - Warns if uncommitted changes exist

---

### Merging

#### 15. `smk merge <branch>`
- **Function**: `MergeManager.mergeBranch(branchName)`
- **Purpose**: Merges a branch into current branch
- **Merge Strategies**:

  **a) Fast-forward Merge**:
  - When current branch is ancestor of target
  - Simply moves HEAD forward
  - No merge commit created
  
  **b) 3-way Merge**:
  - Finds common ancestor using BFS
  - Compares three trees: base, current, target
  - For each file:
    - **Same in both**: Keep current
    - **Changed in one**: Take changed version
    - **Changed in both differently**: **CONFLICT**
    - **Deleted in one**: Conflict if modified in other
  - Creates merge commit with two parents
  - Updates working directory

- **Conflict Handling**:
  - Detects conflicts
  - Stops merge process
  - Files in conflicted state
  - User must resolve and commit
  - **Important**: Source branch is NEVER deleted

---

### Diff & Comparison

#### 16. `smk diff`
- **Function**: `DiffManager.showDiff()`
- **Purpose**: Shows unstaged changes
- **Compares**: Working directory vs Staging area (index)
- **Format**: Unified diff with `+` and `-` lines

#### 17. `smk diff HEAD`
- **Function**: `DiffManager.showDiffHead()`
- **Purpose**: Shows all changes (staged + unstaged)
- **Compares**: Working directory vs HEAD commit
- **Use case**: See complete picture of changes

#### 18. `smk diff <commitA> <commitB>`
- **Function**: `DiffManager.showDiffCommits(commitA, commitB)`
- **Purpose**: Compares two commits
- **What it shows**:
  - Files added/deleted/modified between commits
  - Line-by-line differences
  - Supports `HEAD` as special reference

---

### History Management

#### 19. `smk revert <commit>`
- **Function**: `cmdRevert(commitHash)`
- **Purpose**: Reverts working directory to a previous commit
- **What it does**:
  - Reads tree from target commit
  - Deletes files not in target tree
  - Restores files from target tree
  - Updates index
  - Creates new commit (preserves history)
- **Note**: Does NOT delete commits, only creates new state

---

### Cleanup

#### 20. `smk clean`
- **Function**: `cmdClean()`
- **Purpose**: Removes untracked files
- **What it does**:
  - Finds files not in index or HEAD
  - Deletes untracked files
  - Preserves all tracked files
  - Skips `.smk` directory

---

## 🎨 GUI Features

The JavaFX GUI (`GUI.java`) provides:

1. **Visual Commit History**:
   - Commit graph visualization
   - DAG (Directed Acyclic Graph) representation
   - LinkedList visualization of commits
   - Stack representation of commit history

2. **Interactive Terminal**:
   - Command input field
   - Output display area
   - Command history (arrow keys)
   - Color-coded output

3. **File Explorer**:
   - Lists files in repository
   - Shows SMK tree structure

4. **Repository Status Display**:
   - Current branch indicator
   - Help text with all commands
   - Visual status badges

5. **Zoom Controls**:
   - Adjustable zoom for commit graphs
   - Scrollable canvas

---

## 🔑 Key Features

### 1. **Content-Addressable Storage**
- All objects (blobs, trees, commits) stored by content hash
- Deduplication: same content = same hash = stored once
- Efficient storage for version control

### 2. **Three-State Model**
- **Working Directory**: Actual files you edit
- **Staging Area (Index)**: Files prepared for commit
- **Repository (HEAD)**: Committed snapshots

### 3. **Branch-Based Workflow**
- Lightweight branches (just reference files)
- Fast branch creation/checkout
- Branch history preserved independently

### 4. **Merging Algorithm**
- Fast-forward merge for linear history
- 3-way merge for divergent branches
- Conflict detection and reporting
- Merge commits preserve both parent histories

### 5. **Efficient Diff Algorithm**
- Line-by-line comparison
- Detects additions, deletions, modifications
- Supports multiple comparison modes

### 6. **History Preservation**
- Commits are immutable (never deleted)
- Branch deletion doesn't affect commits
- Complete history always recoverable

### 7. **Safety Features**
- Prevents adding `.smk` directory
- Cannot delete master branch
- Cannot delete current branch
- Warnings for uncommitted changes

---

## 🧮 Data Structures Used

1. **HashMap/IndexMap**: File path → hash mappings
2. **HashSet**: Track visited commits, file sets
3. **Deque/Queue**: BFS traversal for common ancestor
4. **ArrayList/List**: File lists, commit parents
5. **String**: Hash identifiers, file paths, content

---

## 📊 Time Complexity

Based on benchmark analysis (`Benchmarks.md`):

| Operation | Complexity | Explanation |
|-----------|------------|-------------|
| `add` | O(F · S) | Must hash all files and their content |
| `commit` | O(F · S) | Serializes all staged files |
| `diff` | O(F + L) | Traverses files + compares lines |
| `checkout` | O(F · S) | Writes all files to working directory |
| `merge` | O(F + L) | Compares file trees + line differences |

Where:
- **F** = Number of files
- **S** = Average file size (lines)
- **L** = Total lines compared

---

## 🚀 Usage Examples

### Basic Workflow
```bash
smk init                          # Initialize repository
smk add .                         # Stage all files
smk commit -m "Initial commit"    # Create first commit
smk status                        # Check repository state
```

### Branching Workflow
```bash
smk branch feature                # Create feature branch
smk checkout feature              # Switch to feature branch
# ... make changes ...
smk add .
smk commit -m "Add feature"
smk checkout master               # Switch back
smk merge feature                 # Merge feature into master
```

### History Inspection
```bash
smk log                          # View commit history
smk log --oneline                # Compact view
smk show HEAD                    # View last commit
smk diff HEAD                    # See all changes
```

---

## 🔧 Technical Notes

1. **Hash Algorithm**: Uses `String.hashCode()` (simplified for educational purposes)
2. **File Storage**: Plain text files (not binary-optimized)
3. **Conflict Resolution**: Manual (user must resolve and commit)
4. **Branch References**: Simple file-based (no symbolic refs for tags)
5. **Windows/Unix Compatibility**: Handles both path separators (`/` and `\`)

---

## 📝 Notes on Missing Features

1. **ConfigManager**: Mentioned in documentation but not implemented in codebase
2. **Remote Operations**: No network/remote repository support
3. **Tags**: No tagging system implemented
4. **Stashing**: No temporary change storage
5. **Rebase**: No rebase functionality

---

This VCS demonstrates core concepts of version control systems using fundamental data structures and algorithms, making it an excellent educational project for understanding how Git and similar systems work under the hood!

