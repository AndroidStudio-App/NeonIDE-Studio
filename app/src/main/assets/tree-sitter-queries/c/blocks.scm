; Code blocks query for tree-sitter-c (sora-editor)

(function_definition
  body: (compound_statement) @scope.marked)

(compound_statement) @scope.marked

(if_statement
  consequence: (compound_statement) @scope.marked)

(for_statement
  body: (compound_statement) @scope.marked)

(while_statement
  body: (compound_statement) @scope.marked)

(do_statement
  body: (compound_statement) @scope.marked)
