; Brackets pattern for sora-editor (Kotlin)
; Capture named 'editor.brackets.open' is regarded as open symbol node,
; capture named 'editor.brackets.close' is regarded as close symbol node.

; Blocks
(class_body
  "{" @editor.brackets.open
  "}" @editor.brackets.close)

(function_body
  "{" @editor.brackets.open
  "}" @editor.brackets.close)

(statements
  "{" @editor.brackets.open
  "}" @editor.brackets.close)

; Parentheses
(parenthesized_expression
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

(call_suffix
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

(value_arguments
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

; Indexing
(indexing_suffix
  "[" @editor.brackets.open
  "]" @editor.brackets.close)

(collection_literal
  "[" @editor.brackets.open
  "]" @editor.brackets.close)
