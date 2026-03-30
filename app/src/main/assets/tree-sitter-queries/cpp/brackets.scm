; Brackets query for tree-sitter-cpp (sora-editor)

(compound_statement
  "{" @editor.brackets.open
  "}" @editor.brackets.close)

(parenthesized_expression
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

(parameter_list
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

(argument_list
  "(" @editor.brackets.open
  ")" @editor.brackets.close)

(subscript_expression
  "[" @editor.brackets.open
  "]" @editor.brackets.close)

(template_argument_list
  "<" @editor.brackets.open
  ">" @editor.brackets.close)

(template_parameter_list
  "<" @editor.brackets.open
  ">" @editor.brackets.close)
