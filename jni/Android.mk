LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libusb-1.0
LOCAL_SRC_FILES := libusb-1.0/lib/$(TARGET_ARCH_ABI)/libusb-1.0.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/libusb-1.0/include/libusb-1.0
LOCAL_EXPORT_LDLIBS := -llog
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libsamplerate
LOCAL_ARM_NEON := true
LOCAL_CFLAGS := -O3 -ffast-math
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/libsamplerate
LOCAL_SRC_FILES := libsamplerate/samplerate.c  libsamplerate/src_linear.c \
    libsamplerate/src_sinc.c  libsamplerate/src_zoh.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libfreedv
LOCAL_LDLIBS := -llog
LOCAL_ARM_NEON := true
LOCAL_CFLAGS := -O3 -ffast-math -DNDEBUG
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/freedv
LOCAL_SRC_FILES := freedv/codebook.c freedv/codebookd.c freedv/codebookdt.c \
    freedv/codebookge.c freedv/codebookjnd.c freedv/codebookjvm.c \
    freedv/codebookvqanssi.c freedv/codebookvq.c freedv/codec2.c \
    freedv/comp.c freedv/fdmdv.c freedv/interp.c freedv/kiss_fft.c \
    freedv/lpc.c freedv/lsp.c freedv/nlp.c freedv/pack.c freedv/phase.c \
    freedv/postfilter.c freedv/quantise.c freedv/sine.c freedv/varicode.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_CFLAGS := -Wall -O3 -ffast-math -DNDEBUG
LOCAL_MODULE := libdroidfreedv
LOCAL_ARM_NEON := true
LOCAL_SHARED_LIBRARIES := libusb-1.0 freedv samplerate
LOCAL_LDLIBS := -llog
LOCAL_SRC_FILES := freedv_jni.c freedv_usb.c freedv_rx.c
include $(BUILD_SHARED_LIBRARY)
