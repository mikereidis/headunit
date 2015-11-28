
##
LOCAL_PATH:= $(call my-dir)

#include $(call all-subdir-makefiles)

# lul / libusb std linux
# lua / libusb std android          libusbnok: https://github.com/Gritzman/libusb
# luh / libusbhost google

ifeq        ($(TARGET_ARCH_ABI), armeabi)
  include $(CLEAR_VARS)
  LOCAL_MODULE    := libusbnok
  LOCAL_SRC_FILES := libs/libusbnok.a
  include $(PREBUILT_STATIC_LIBRARY)
  include $(CLEAR_VARS)
  LOCAL_MODULE    := libssl
  LOCAL_SRC_FILES := libs/libssl.a
  include $(PREBUILT_STATIC_LIBRARY)
  include $(CLEAR_VARS)
  LOCAL_MODULE    := libcrypto
  LOCAL_SRC_FILES := libs/libcrypto.a
  include $(PREBUILT_STATIC_LIBRARY)
#else ifeq  ($(TARGET_ARCH_ABI), armeabi-v7a)
else ifeq   ($(TARGET_ARCH_ABI), arm64-v8a)
  include $(CLEAR_VARS)
  LOCAL_MODULE    := libusbnok
  LOCAL_SRC_FILES := libsa64/libusbnok.a
  include $(PREBUILT_STATIC_LIBRARY)
  #include $(CLEAR_VARS)
  #LOCAL_MODULE    := libssl
  #LOCAL_SRC_FILES := libsa64/libssl.so
  #include $(PREBUILT_SHARED_LIBRARY)
  #include $(CLEAR_VARS)
  #LOCAL_MODULE    := libcrypto
  #LOCAL_SRC_FILES := libsa64/libcrypto.so
  #include $(PREBUILT_SHARED_LIBRARY)
else ifeq   ($(TARGET_ARCH_ABI), x86)
  include $(CLEAR_VARS)
  LOCAL_MODULE    := libusbnok
  LOCAL_SRC_FILES := libsx86/libusbnok.a
  include $(PREBUILT_STATIC_LIBRARY)
  include $(CLEAR_VARS)
  LOCAL_MODULE    := libssl
  LOCAL_SRC_FILES := libsx86/libssl.so
  include $(PREBUILT_SHARED_LIBRARY)
  include $(CLEAR_VARS)
  LOCAL_MODULE    := libcrypto
  LOCAL_SRC_FILES := libsx86/libcrypto.so
  include $(PREBUILT_SHARED_LIBRARY)
else
  $(error Not a supported TARGET_ARCH_ABI: $(TARGET_ARCH_ABI))
endif





    # HU JNI:
include $(CLEAR_VARS)
LOCAL_MODULE    := hu_jni
#LOCAL_CFLAGS := -fvisibility=hidden

LOCAL_SRC_FILES := hu_jni.c hu_tcp.c hu_usb.c hu_aap.c hu_ssl.c hu_aad.c hu_uti.c

#LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libs/libssl.so     -l/home/m/dev/hu/jni/libs/libcrypto.so    -l/home/m/dev/hu/jni/libs/libusbnok.so

#LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libs/libssl.a      -l/home/m/dev/hu/jni/libs/libcrypto.a     -l/home/m/dev/hu/jni/libs/libusbnok.a
#LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libs/libssl.a      -l/home/m/dev/hu/jni/libs/libcrypto.a     -l/home/m/dev/hu/jni/libsa64/libusbnok.a
#LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libs/libssl.so     -l/home/m/dev/hu/jni/libs/libcrypto.so    -l/home/m/dev/hu/jni/libs/libusbnok.a
#LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libsx86/libssl.so  -l/home/m/dev/hu/jni/libsx86/libcrypto.so -l/home/m/dev/hu/jni/libsx86/libusbnok.a


ifeq        ($(TARGET_ARCH_ABI), armeabi)
  LOCAL_LDLIBS    := -llog
  LOCAL_STATIC_LIBRARIES := libusbnok libssl libcrypto

else ifeq   ($(TARGET_ARCH_ABI), arm64-v8a) #  LOCAL_LDLIBS    := -llog -lssl -lcrypto  #  #LOCAL_SHARED_LIBRARIES := libssl libcrypto  #  LOCAL_STATIC_LIBRARIES := libusbnok #  LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libsa64/libssl.so  -l/home/m/dev/hu/jni/libsa64/libcrypto.so -l/home/m/dev/hu/jni/libsa64/libusbnok.a
  LOCAL_LDLIBS    := -llog

  LOCAL_STATIC_LIBRARIES := libusbnok libssl libcrypto
else ifeq   ($(TARGET_ARCH_ABI), x86)
  LOCAL_LDLIBS    := -llog -l/home/m/dev/hu/jni/libsx86/libssl.so  -l/home/m/dev/hu/jni/libsx86/libcrypto.so -l/home/m/dev/hu/jni/libsx86/libusbnok.a  #LOCAL_LDLIBS    := -llog #-lssl -lcrypto
  #LOCAL_SHARED_LIBRARIES := libssl libcrypto
  #LOCAL_STATIC_LIBRARIES := libusbnok    #else ifeq  ($(TARGET_ARCH_ABI), armeabi-v7a)
else
  $(error Not a supported TARGET_ARCH_ABI: $(TARGET_ARCH_ABI))
endif


include $(BUILD_SHARED_LIBRARY)
  # USB reset executable:
include $(CLEAR_VARS)
LOCAL_MODULE    := usb_reset
LOCAL_SRC_FILES := usb_reset.c
LOCAL_LDLIBS    := -llog 
include $(BUILD_EXECUTABLE)
#include $(BUILD_SHARED_LIBRARY)

  # hu_usb AA Test for Linux   with exist libusb std:  lul / libusb std linux      ~/b/ba
  # hu_usb AA Test for Android with built libusb nok:  lua / libusb std android    ndk-build
include $(CLEAR_VARS)
LOCAL_MODULE    := hu
LOCAL_SRC_FILES := hu_mai.c hu_usb.c hu_aap.c hu_ssl.c hu_aad.c hu_uti.c
LOCAL_LDLIBS := -llog -l/home/m/dev/hu/jni/libs/libssl.so -l/home/m/dev/hu/jni/libs/libcrypto.so -l/home/m/dev/hu/jni/libs/libusbnok.so
#LOCAL_SHARED_LIBRARIES := /home/m/dev/hu/jni/libs/libusbnok.a
#LOCAL_SHARED_LIBRARIES := liblog
#LOCAL_STATIC_LIBRARIES
##include $(BUILD_EXECUTABLE)


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


