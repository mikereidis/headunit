
##
LOCAL_PATH:= $(call my-dir)

#include $(call all-subdir-makefiles)

# lul / libusb std linux
# lua / libusb std android          libusbnok: https://github.com/Gritzman/libusb
# luh / libusbhost google

    # JNI utilities
include $(CLEAR_VARS)
LOCAL_MODULE    := hu_jni

LOCAL_CFLAGS := -fvisibility=hidden

LOCAL_SRC_FILES := hu_jni.c hu_usb.c hu_aap.c hu_ssl.c hu_aad.c hu_uti.c

#LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libs/libssl.so -l/home/m/dev/hu/jni/libs/libcrypto.so -l/home/m/dev/hu/jni/libs/libusbnok.so
LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libs/libssl.a -l/home/m/dev/hu/jni/libs/libcrypto.a -l/home/m/dev/hu/jni/libs/libusbnok.a


#LOCAL_SHARED_LIBRARIES := libs/libusbnok.a
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := usb_reset
LOCAL_SRC_FILES := usb_reset.c
LOCAL_LDLIBS    := -llog 
##include $(BUILD_EXECUTABLE)

  # hu_usb AA Test for Linux   with exist libusb std:  lul / libusb std linux      ~/b/ba
  # hu_usb AA Test for Android with built libusb nok:  lua / libusb std android    ndk-build
include $(CLEAR_VARS)
LOCAL_MODULE    := hu
LOCAL_SRC_FILES := hu_mai.c hu_usb.c hu_aap.c hu_ssl.c hu_aad.c hu_uti.c
LOCAL_LDLIBS := -llog -l/home/m/dev/hu/jni/libs/libssl.so -l/home/m/dev/hu/jni/libs/libcrypto.so -l/home/m/dev/hu/jni/libs/libusbnok.so
#LOCAL_SHARED_LIBRARIES := /home/m/dev/hu/jni/libs/libusbnok.a
#LOCAL_SHARED_LIBRARIES := liblog
#LOCAL_STATIC_LIBRARIES
#include $(BUILD_EXECUTABLE)


  # SSL wrapper:
include $(CLEAR_VARS)
LOCAL_MODULE:= sslwrapper_jni
LOCAL_SRC_FILES:= sslwrapper_jni.c hu_aad.c hu_uti.c
LOCAL_LDLIBS := -llog
#LOCAL_SHARED_LIBRARIES := liblog
#include $(BUILD_SHARED_LIBRARY)



  # hu_ush AA Test for Android with exist libusbhost:  luh / libusbhost google     ndk-build
include $(CLEAR_VARS)
LOCAL_MODULE    := hu_ush
LOCAL_SRC_FILES := hu_ush.c hu_aad.c hu_uti.c
LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libs/libcrypto.a -l/home/m/dev/hu/jni/libs/libssl.a -l/home/m/dev/hu/jni/libs/libusbhost.so
#LOCAL_SHARED_LIBRARIES := liblog
#include $(BUILD_EXECUTABLE)


