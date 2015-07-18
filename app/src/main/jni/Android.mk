
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := hu_jni

LOCAL_CFLAGS := -fvisibility=hidden

LOCAL_SRC_FILES := hu_jni.c hu_usb.c hu_aap.c hu_ssl.c hu_aad.c hu_uti.c

LOCAL_LDLIBS    := -llog -l$(LOCAL_PATH)/prebuiltlib/libssl.a -l$(LOCAL_PATH)/prebuiltlib/libcrypto.a -l$(LOCAL_PATH)/prebuiltlib/libusbnok.a


include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := usb_reset
LOCAL_SRC_FILES := usb_reset.c
LOCAL_LDLIBS    := -llog 

include $(CLEAR_VARS)
LOCAL_MODULE    := hu
LOCAL_SRC_FILES := hu_mai.c hu_usb.c hu_aap.c hu_ssl.c hu_aad.c hu_uti.c
LOCAL_LDLIBS := -llog -l$(LOCAL_PATH)/prebuiltlib/libssl.so -l$(LOCAL_PATH)/prebuiltlib/libcrypto.so -l$(LOCAL_PATH)/prebuiltlib/libusbnok.so


  # SSL wrapper:
include $(CLEAR_VARS)
LOCAL_MODULE:= sslwrapper_jni
LOCAL_SRC_FILES:= sslwrapper_jni.c hu_aad.c hu_uti.c
LOCAL_LDLIBS := -llog





