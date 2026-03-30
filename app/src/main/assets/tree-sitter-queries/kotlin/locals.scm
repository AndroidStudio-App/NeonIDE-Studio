; Locals query for Kotlin (sora-editor)
; Uses capture names expected by LocalsCaptureSpec:
;   local.scope, local.scope.members, local.definition, local.reference

; --- Scopes ---
(source_file) @local.scope

(class_declaration
  (class_body) @local.scope.members)

(object_declaration
  (class_body) @local.scope.members)

(function_declaration) @local.scope

(lambda_literal) @local.scope

(block) @local.scope

; --- Definitions ---
; Local variables
(property_declaration
  (variable_declaration
    (simple_identifier) @local.definition))

; Function parameters
(parameter
  (simple_identifier) @local.definition)

(parameter_with_optional_type
  (simple_identifier) @local.definition)

; Lambda parameters
(lambda_literal
  (lambda_parameters
    (variable_declaration
      (simple_identifier) @local.definition)))

; --- References ---
(simple_identifier) @local.reference
