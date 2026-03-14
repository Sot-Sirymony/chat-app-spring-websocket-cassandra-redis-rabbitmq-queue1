# Resolving Merge Conflicts

## Quick steps

1. **See which files conflict:**  
   `git status` → look for "Unmerged paths" or "both modified".  
   Or: `git diff --name-only --diff-filter=U`

2. **Open each listed file** and find blocks like:
   ```
   <<<<<<< HEAD
   your version
   =======
   their version
   >>>>>>> branch-name
   ```
   Edit to keep one version (or combine), and **delete the three marker lines**.

3. **Stage and finish:**  
   `git add <file>` for each resolved file, then  
   `git commit -m "Resolve merge conflicts"`  
   (No commit message needed if you just ran `git merge` — Git will use the default merge message.)

4. **To cancel the merge instead:**  
   `git merge --abort`

---

## Check for conflict markers

From the project root (or this worktree):

```bash
rg -l '<<<<<<<|=======|>>>>>>>' --glob '!*.pack.gz' --glob '!.next/**' . 2>/dev/null || true
```

If any files are listed, open them and resolve the conflicts (see below).

## Resolving conflicts

1. **Find the markers** in the file:
   - `<<<<<<< HEAD` – start of your current branch version
   - `=======` – separator
   - `>>>>>>> branch-name` – end of the incoming version

2. **Edit the file**: delete the three marker lines and choose one version (or combine both). Keep only the code you want in the final file.

3. **Save and stage**:
   ```bash
   git add <path-to-file>
   ```

4. **Finish the merge** (if you were in the middle of a merge):
   ```bash
   git status   # ensure no "Unmerged paths"
   git add .    # stage all resolved files (avoid staging .next/ and build artifacts)
   git commit -m "Merge: resolve conflicts"
   ```

## If Cursor/IDE says "Merge Conflicts Detected"

- **Source Control panel**: Open the file(s) listed as conflicted and fix the markers.
- **Or run a merge again** in the repo where you see the message:
  ```bash
  git status
  git diff --name-only --diff-filter=U   # list unmerged files
  ```
  Then open each path and remove/merge the conflict blocks.

## Abort a merge (discard incoming changes)

```bash
git merge --abort
```

## This worktree (ivw)

As of the last check, **no conflict markers** were present in source files under this worktree. If you see "Merge Conflicts Detected" in the UI, it may refer to:

- The **main repo** (different path)
- A **merge/pull in progress** in another window
- **Sync with remote** (e.g. after `git pull`)

Run the `rg` command above in the repo where the message appears to see which files still have markers.
