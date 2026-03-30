; Brackets query for tree-sitter-python (sora-editor)

(parenthesized_expression
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

(argument_list
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

(parameters
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

(list
  "[" @editor.brackets.open
  "]" @editor.brackets.close)

(subscript
  "[" @editor.brackets.open
  "]" @editor.brackets.close)

(dictionary
  "{" @editor.brackets.open
  "}" @editor.brackets.close)

(set
  "{" @editor.brackets.open
  "}" @editor.brackets.close)
