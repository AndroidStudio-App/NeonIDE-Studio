; Code blocks query for tree-sitter-python (sora-editor)

(function_definition
  body: (_) @scope.marked)

(class_definition
  body: (_) @scope.marked)

(if_statement
  consequence: (_) @scope.marked)

(for_statement
  body: (_) @scope.marked)

(while_statement
  body: (_) @scope.marked)

(block) @scope.marked
