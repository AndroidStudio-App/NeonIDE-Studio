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

# libonig
include $(CLEAR_VARS)
LOCAL_MODULE := libonig
LOCAL_C_INCLUDES := $(LOCAL_PATH)/oniguruma/oniguruma/src
FILE_LIST := $(wildcard $(LOCAL_PATH)/oniguruma/oniguruma/src/*.c)
EXCLUDE_LIST := %/unicode_fold_data.c %/unicode_property_data.c %/unicode_property_data_posix.c %/unicode_wb_data.c %/unicode_egcb_data.c
LOCAL_SRC_FILES := $(filter-out $(EXCLUDE_LIST), $(FILE_LIST:$(LOCAL_PATH)/%=%))
LOCAL_CFLAGS := -O2 -Wno-unused-parameter -Wno-sign-compare -DHAVE_CONFIG_H
include $(BUILD_STATIC_LIBRARY)

# oniguruma-binding
include $(CLEAR_VARS)
LOCAL_MODULE := oniguruma-binding
LOCAL_SRC_FILES := oniguruma/binding.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/oniguruma/oniguruma/src
LOCAL_STATIC_LIBRARIES := libonig
LOCAL_LDLIBS := -llog -lc++
LOCAL_CPPFLAGS := -std=c++17
include $(BUILD_SHARED_LIBRARY)
