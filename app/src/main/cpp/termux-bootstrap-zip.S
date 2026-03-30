     .global blob
     .global blob_size
     .section .rodata

     // This file is generated at build time from the bootstrap zip SHA/size.
     // Including it forces a rebuild of this assembly when the bootstrap zip changes.
     .include "generated/bootstrap-stamp.S"

 blob:
 #if defined __i686__
     .incbin "bootstrap-i686.zip"
 #elif defined __x86_64__
     .incbin "bootstrap-x86_64.zip"
 #elif defined __aarch64__
     .incbin "bootstrap-aarch64.zip"
 #elif defined __arm__
     .incbin "bootstrap-arm.zip"
 #else
 # error Unsupported arch
 #endif
 1:
 blob_size:
     .int 1b - blob
