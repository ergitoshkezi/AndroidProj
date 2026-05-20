---
trigger: always_on
---

## MCP Protocol: Serena & Claude-Mem

This protocol defines how and when to use Serena (for structured Code Intelligence) and Claude-Mem (for persistent memory and AST-based search) MCP tools to ensure maximum token efficiency and code modification precision.

### 1. SERENA Protocol (Code Intelligence & AST-Editing)
When Serena MCP tools are available, they must be preferred over standard filesystem tools (such as `view_file`, `replace_file_content`, `grep_search`).

*   **Code Reading (FORBIDDEN for direct discovery):**
    *   **DO NOT** use `view_file` to explore or search code files.
    *   **Use** `get_symbols_overview` to obtain the overview of symbols within a file.
    *   **Use** `find_symbol` with `include_body=false` to search for symbols, and set `include_body=true` only when you are certain you need to read the body of a specific method/class.
    *   Using `view_file` is permitted only if you already have a symbol overview of the file and need to inspect a few isolated lines of code that do not constitute a full symbol.
*   **Reference Searching:**
    *   **Use** `find_referencing_symbols` to verify the impact of a change on other parts of the codebase before applying it.
    *   Standard commands like `grep_search` are allowed only for initial, generic string discoveries.
*   **Code Modification (FORBIDDEN via standard tools):**
    *   **DO NOT** use `replace_file_content` or `write_to_file` to modify existing code files.
    *   **Use** `replace_symbol_body` to replace the entire definition of a function, class, or method.
    *   **Use** `insert_after_symbol` or `insert_before_symbol` to inject new definitions.
    *   **Use** Serena's `replace_content` tool for targeted, regex/string-based modifications within method bodies.
    *   *Note:* All line numbers returned or requested by Serena's tools are **0-based** (starting from 0).

---

### 2. CLAUDE-MEM Protocol (Persistent Memory & AST-Search)
Use Claude-Mem both to retrieve historical context from previous coding sessions and to perform fast, token-efficient codebase analyses.

*   **Querying Historical Memory (Mandatory 3-layer workflow):**
    *   To query the project's memory without wasting tokens, always follow this sequence:
        1.  `search(query)`: Retrieve the summary index with observation IDs (~50-100 tokens per result).
        2.  `timeline(anchor=ID)`: Retrieve the chronological context around a specific ID.
        3.  `get_observations([IDs])`: Fetch full details only for the filtered, strictly necessary IDs.
    *   **DO NOT** request full details of observations without filtering by ID first.
*   **Static Project Analysis:**
    *   **Use** `smart_search` to search for class, function, or file definitions in the workspace using tree-sitter AST parsing.
    *   **Use** `smart_outline` to get the structural outline of a file (keeping method/function bodies folded).
    *   **Use** `smart_unfold` to expand and view the full source code of a single, specific symbol identified in the previous steps.