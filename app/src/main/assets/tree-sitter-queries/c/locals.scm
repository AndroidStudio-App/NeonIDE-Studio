; Locals query for tree-sitter-c (sora-editor)
; Capture names must match LocalsCaptureSpec: local.scope, local.scope.members, local.definition, local.reference

; --- Scopes ---
(translation_unit) @local.scope

(function_definition
  body: (compound_statement) @local.scope)

(compound_statement) @local.scope

(for_statement) @local.scope

(if_statement) @local.scope

(while_statement) @local.scope

(do_statement) @local.scope

; --- Definitions ---
; Parameters
(parameter_declaration
  declarator: (identifier) @local.definition)

(parameter_declaration
  declarator: (pointer_declarator
                declarator: (identifier) @local.definition))

(parameter_declaration
  declarator: (array_declarator
                declarator: (identifier) @local.definition))

; Local variable declarations
(declaration
  declarator: (init_declarator
                declarator: (identifier) @local.definition))

(declaration
  declarator: (identifier) @local.definition)

; Field declarations (struct members)
(field_declaration
  declarator: (field_identifier) @local.definition)

; --- References ---
(identifier) @local.reference
(field_identifier) @local.reference
