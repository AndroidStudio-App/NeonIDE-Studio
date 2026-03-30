; Code block patterns for sora-editor (Kotlin)
; Capture names don't matter much; '.marked' means end is last terminal child's start.

(class_declaration
  (class_body) @scope.marked)

(object_declaration
  (class_body) @scope.marked)

(function_declaration
  (function_body) @scope.marked)

(property_declaration
  (getter
    (function_body) @scope.marked))

(property_declaration
  (setter
    (function_body) @scope.marked))

(control_structure_body) @scope.marked

(statements) @scope.marked

(lambda_literal) @scope.marked
