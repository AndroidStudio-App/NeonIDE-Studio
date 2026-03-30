SEARCH & NAVIGATION

1. Always use `rg` (ripgrep) for searching since it's much faster. If `rg` is not found, attempt to install it (`apt install ripgrep` / `brew install ripgrep`) before falling back to `grep -r`.
2. Always read a file before editing it. Never assume file contents. This ensures accurate tool usage and prevents corrupting existing code.
3. Use `find` or `fd` for locating files by name. Use `rg` for searching file contents. Don't confuse the two.

EDITING DISCIPLINE

4. When editing any files, always follow the existing coding style exactly. Match indentation (tabs vs spaces), naming conventions, bracket placement, and patterns already present in the codebase. Never inject unnecessary comments, TODO markers, or placeholder code.
5. Never remove, modify, or reformat any code lines that are unrelated to the user's request. Stay laser-focused on the prompt. Zero collateral changes.
6. Always make sure the code is syntactically correct before finalizing any edit. Ensure all brackets {}, parentheses (), semicolons, and closing tags are properly matched. A missing brace means failed compilation.
7. Apply minimal, surgical edits. Prefer the smallest diff that accomplishes the task. Don't rewrite entire files when changing a few lines suffices.

IMPLEMENTATION PLANNING

8. Before creating new files or functions, always check if existing ones can be reused to avoid duplication of code. Search the codebase first.
9. If the user asks for complex implementation, always validate that it is possible and correct within our project's language, framework, and environment limitations before writing code.
10. When editing or implementing code, always make sure it matches the user's request precisely. Re-read the prompt before and after making changes. Don't add unrequested features or skip requested ones.

VERIFICATION & SAFETY

11. After editing, re-read the modified file to confirm correctness. Catch truncation, duplication, or merge errors immediately.
12. Run the project's build, compile, or lint command after changes when available. Don't assume it works, verify it.
13. Never truncate or summarize file contents when writing. Always write complete, exact content. Partial writes corrupt files.
14. Back up or confirm before overwriting files with destructive operations. Especially for config files, migrations, or anything irreversible.

COMMUNICATION

15. If a request is ambiguous, ask for clarification rather than guessing. A wrong implementation wastes more time than a quick question.
16. When multiple approaches exist, briefly state the chosen approach and why before implementing, unless the user clearly specified one.
