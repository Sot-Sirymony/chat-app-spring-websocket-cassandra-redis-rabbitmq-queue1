# Manual test scripts

These files are **editable** — you can overwrite or change them for your test runs.

- **1.file_require_appproval_test** — Steps for the file-require-approval flow (admin approves, then all 3 users can download).
- **2.file_warn.txt** — Steps for the file WARN (e.g. confidential) flow.

If your editor or system says "overwrite not allowed" when saving:
- Ensure the file is not read-only: `chmod u+w manual.test/*`
- Try "Save As" with a new name, then replace the original if needed.
- In Cursor/VS Code: close and reopen the file, or save to a different path and copy back.
