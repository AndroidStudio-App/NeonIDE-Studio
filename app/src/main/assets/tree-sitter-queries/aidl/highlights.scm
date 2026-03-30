; Basic highlights for tree-sitter-aidl (sora-editor)
;
; This grammar is adapted from tree-sitter-java, so we can reuse many captures.

; --- Declarations ---
(interface_declaration
  name: (identifier) @type)

(method_declaration
  name: (identifier) @function.method)

; Types
(type_identifier) @type
(scoped_type_identifier) @type
(generic_type) @type

[
  (boolean_type)
  (integral_type)
  (floating_point_type)
  (void_type)
] @type.builtin

; Modifiers / directions
[
  "oneway"
  "in"
  "out"
  "inout"
] @keyword

; Common AIDL keywords
[
  "package"
  "import"
  "interface"
  "parcelable"
  "throws"
  "return"
  "static"
] @keyword

; Variables
(identifier) @variable

; Constants
((identifier) @constant
 (#match? @constant "^_*[A-Z][A-Z\\d_]+$"))

; Literals
[
  (hex_integer_literal)
  (decimal_integer_literal)
  (octal_integer_literal)
  (binary_integer_literal)
  (decimal_floating_point_literal)
  (hex_floating_point_literal)
] @number

[
  (character_literal)
  (string_literal)
] @string
(escape_sequence) @string.escape

[
  (true)
  (false)
  (null_literal)
] @constant.builtin

; Comments
[
  (line_comment)
  (block_comment)
] @comment

; Annotations
(annotation
  name: (identifier) @attribute)
(marker_annotation
  name: (identifier) @attribute)

"@" @operator
