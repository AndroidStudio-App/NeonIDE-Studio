; Locals query for tree-sitter-aidl (sora-editor)
; Capture names must match LocalsCaptureSpec: local.scope, local.scope.members, local.definition, local.reference

; --- Scopes ---
(program) @local.scope

(interface_declaration
  body: (_) @local.scope.members)

(interface_body) @local.scope

(block) @local.scope

; --- Definitions ---
; Local variables
(local_variable_declaration
  declarator: (variable_declarator
    name: (identifier) @local.definition))

; Fields (rare in AIDL, but grammar supports field_declaration)
(field_declaration
  declarator: (variable_declarator
    name: (identifier) @local.definition))

; Method parameters
(formal_parameter
  (variable_declarator
    name: (identifier) @local.definition))

; --- References ---
(identifier) @local.reference
