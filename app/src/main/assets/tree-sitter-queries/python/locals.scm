; Locals query for tree-sitter-python (sora-editor)
; Capture names must match LocalsCaptureSpec: local.scope, local.scope.members, local.definition, local.reference

; --- Scopes ---
(module) @local.scope

(function_definition
  body: (block) @local.scope)

(class_definition
  body: (block) @local.scope.members)

(for_statement
  body: (block) @local.scope)

(while_statement
  body: (block) @local.scope)

(if_statement
  consequence: (block) @local.scope)

; --- Definitions ---
; Function name
(function_definition
  name: (identifier) @local.definition)

; Class name
(class_definition
  name: (identifier) @local.definition)

; Parameters
(parameters
  (identifier) @local.definition)

(default_parameter
  name: (identifier) @local.definition)

(typed_parameter
  name: (identifier) @local.definition)

(list_splat_pattern
  (identifier) @local.definition)

(dictionary_splat_pattern
  (identifier) @local.definition)

; Assignment targets
(assignment
  left: (identifier) @local.definition)

(assignment
  left: (pattern_list
          (identifier) @local.definition))

(for_statement
  left: (identifier) @local.definition)

(for_statement
  left: (pattern_list
          (identifier) @local.definition))

; With statement aliases: `with expr as name:`
(as_pattern
  alias: (as_pattern_target (identifier) @local.definition))

; --- References ---
(identifier) @local.reference
