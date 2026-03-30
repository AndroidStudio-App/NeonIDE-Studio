; Brackets query for tree-sitter-json (sora-editor)

(object
  "{" @editor.brackets.open
  "}" @editor.brackets.close)

(array
  "[" @editor.brackets.open
  "]" @editor.brackets.close)
