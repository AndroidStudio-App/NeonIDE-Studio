LOCAL_PATH := $(call my-dir)

TERMUX_COMMON_CFLAGS := -std=c11 -Wall -Wextra -Werror -Os -fno-stack-protector
TERMUX_COMMON_LDFLAGS := -Wl,--gc-sections

# libtermux-bootstrap
include $(CLEAR_VARS)
LOCAL_MODULE := libtermux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
# termux-bootstrap-zip.S embeds a bootstrap-<arch>.zip via `.incbin`.
# ndk-build does not automatically track that zip as an input, so changes to the
# bootstrap archive would NOT trigger a rebuild and could leave a stale/huge
# embedded bootstrap in the resulting .so.
#
# We only target arm64-v8a (aarch64) in this fork.
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/bootstrap-aarch64.zip $(LOCAL_PATH)/generated/bootstrap-stamp.S
LOCAL_CFLAGS += $(TERMUX_COMMON_CFLAGS)
LOCAL_LDFLAGS += $(TERMUX_COMMON_LDFLAGS)
include $(BUILD_SHARED_LIBRARY)

# local-socket (C++)
include $(CLEAR_VARS)
LOCAL_MODULE := local-socket
LOCAL_SRC_FILES := termux_shared/local-socket.cpp
LOCAL_CPPFLAGS += -std=c++17 -Wall -Os -fno-stack-protector
LOCAL_LDLIBS := -llog -lc++
LOCAL_LDFLAGS += $(TERMUX_COMMON_LDFLAGS)
include $(BUILD_SHARED_LIBRARY)

# libtermux (terminal emulator JNI)
include $(CLEAR_VARS)
LOCAL_MODULE := libtermux
LOCAL_SRC_FILES := terminal_emulator/termux.c
LOCAL_CFLAGS += $(TERMUX_COMMON_CFLAGS)
LOCAL_LDFLAGS += $(TERMUX_COMMON_LDFLAGS)
include $(BUILD_SHARED_LIBRARY)

# Oniguruma is now provided by Maven artifact (io.github.rosemoe:oniguruma-native)
# Removed: libonig and oniguruma-binding targets
