; Brackets query for tree-sitter-aidl (sora-editor)

; Blocks
(interface_body
  "{" @editor.brackets.open
  "}" @editor.brackets.close)

; Parentheses
(parenthesized_expression
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

(formal_parameters
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

; Arrays / indexing
(dimensions
  "[" @editor.brackets.open
  "]" @editor.brackets.close)

(array_access
  "[" @editor.brackets.open
  "]" @editor.brackets.close)

; Generic type arguments
(type_arguments
  "<" @editor.brackets.open
  ">" @editor.brackets.close)

(type_parameters
  "<" @editor.brackets.open
  ">" @editor.brackets.close)
