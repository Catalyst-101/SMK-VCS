# SMK VCS - Quick Start Guide

## 📁 Project Structure

```
vcs2/
├── src/
│   ├── core/              # Core VCS classes (package: core)
│   │   ├── BranchManager.java
│   │   ├── CommitManager.java
│   │   ├── ConfigManager.java
│   │   ├── DiffManager.java
│   │   ├── IndexManager.java
│   │   ├── IndexMap.java
│   │   ├── MergeManager.java
│   │   ├── ObjectManager.java
│   │   └── Utils.java
│   ├── GUI.java           # JavaFX GUI application
│   └── Main.java          # Command-line interface
```

## 🚀 Quick Commands

### Compile Everything (CLI + Core)

```bash
javac -cp src src/core/*.java src/Main.java
```

### Compile GUI (Requires JavaFX)

```bash
# If JAVAFX_HOME is set:
javac --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -cp src src/GUI.java

# Or compile everything at once:
javac --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -cp src src/core/*.java src/Main.java src/GUI.java
```

### Run CLI

```bash
java -cp src Main init
java -cp src Main add .
java -cp src Main commit -m "Initial commit"
java -cp src Main status
```

### Run GUI

```bash
# If JAVAFX_HOME is set:
java --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -cp src GUI
```

## 📝 Step-by-Step

### 1. Compile Core Classes

```bash
cd C:\Users\MuhammadUllah\IdeaProjects\vcs2
javac -cp src src/core/*.java src/Main.java
```

### 2. Test CLI

```bash
java -cp src Main init
java -cp src Main add .
java -cp src Main commit -m "First commit"
java -cp src Main status
```

### 3. Compile GUI (Optional - Requires JavaFX)

**First, install JavaFX:**
1. Download from: https://openjfx.io/
2. Extract to `C:\javafx-sdk-17` (or any folder)
3. Set environment variable:
   ```cmd
   setx JAVAFX_HOME "C:\javafx-sdk-17"
   ```
4. Restart terminal

**Then compile:**
```bash
javac --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -cp src src/GUI.java
```

**Run GUI:**
```bash
java --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -cp src GUI
```

## 🔧 Troubleshooting

### "package core does not exist"
- Make sure you're in the project root directory
- Check that `src/core/` exists with all `.java` files
- Use: `javac -cp src src/core/*.java src/Main.java`

### "package javafx does not exist" (GUI only)
- JavaFX is not installed or JAVAFX_HOME is not set
- Install JavaFX from https://openjfx.io/
- Or skip GUI and use CLI only (no JavaFX needed)

### "Error: module not found: javafx.controls"
- JAVAFX_HOME is not set correctly
- Restart terminal after setting JAVAFX_HOME
- Or use classpath method if JavaFX is in your classpath

### GUI can't find Main class
- Make sure you compiled Main.java first
- Check that classpath includes `src` directory
- The GUI automatically finds the project root

## ✅ Verification

After compilation, you should see `.class` files in:
- `src/core/*.class`
- `src/Main.class`
- `src/GUI.class` (if GUI compiled)

## 📚 All Available Commands

1. `smk init` - Initialize repository
2. `smk add <file>` - Add file to index
3. `smk add .` - Add all files
4. `smk commit -m "message"` - Create commit
5. `smk commit --amend -m "message"` - Amend commit
6. `smk status` - Show status
7. `smk branch` - List branches
8. `smk branch <name>` - Create branch
9. `smk checkout <branch>` - Checkout branch
10. `smk checkout <commit>` - Checkout commit
11. `smk merge <branch>` - Merge branch
12. `smk log` - Show commit log
13. `smk log --oneline` - One-line log
14. `smk diff` - Show diff
15. `smk config` - List config
16. `smk config <key> <value>` - Set config
17. `smk revert <commit>` - Revert commit
18. `smk reset <commit>` - Reset to commit
19. `smk clone <path>` - Clone repo
20. `smk show <commit>` - Show commit
21. `smk clean` - Remove untracked files

