
  /*


    N91:
      al    /data/app/com.google.android.gms-1/lib/arm/libsslwrapper_jni*
      #a cp  /data/app/com.google.android.gms-1/lib/arm/libsslwrapper_jni.so  /data/app/com.google.android.gms-1/lib/arm/libsslwrapper_jnio.so
      adb push               ~/dev/hu/libs/armeabi/libsslwrapper_jni.so  /data/app/com.google.android.gms-1/lib/arm/libsslwrapper_jni.so
      al   /data/app/com.google.android.gms-1/lib/arm/libsslwrapper_jni*

    OM8:
      al    /data/app/com.google.android.gms-1/lib/arm/libsslwrapper_jni*
      a su -c mount -o remount,rw /system
      adb push ~/dev/hu/libs/armeabi/ /data/local/tmp
      a su -c cp /data/local/tmp/libsslwrapper_jni.so /data/app/com.google.android.gms-1/lib/arm/
      al    /data/app/com.google.android.gms-1/lib/arm/libsslwrapper_jni*
      a ps|grep gms


    OLD:
      al    /system/priv-app/PrebuiltGmsCore/lib/arm//libsslwrapper_jni*
      a su -c mount -o remount,rw /system
      #a cp  /system/priv-app/PrebuiltGmsCore/lib/arm/libsslwrapper_jni.so  /system/priv-app/PrebuiltGmsCore/lib/arm//libsslwrapper_jnio.so
      adb push             ~/dev/hu/libs/armeabi/libsslwrapper_jni.so  /system/priv-app/PrebuiltGmsCore/lib/arm//libsslwrapper_jni.so
      al    /system/priv-app/PrebuiltGmsCore/lib/arm/libsslwrapper_jni*



    al /sys/kernel/debug/usb/usbmon/

    a cat /sys/kernel/debug/usb/usbmon/0u

    a cat /sys/kernel/debug/usb/devices

  GS3:

  cat /sys/kernel/debug/usb/usbmon/0u > /sdcard/aa0u &



  a "echo Y > sys/module/usbcore/parameters/usbfs_snoop"
  a cat sys/module/usbcore/parameters/usbfs_snoop



a cat /sys/kernel/debug/usb/devices

T:  Bus=02 Lev=00 Prnt=00 Port=00 Cnt=00 Dev#=  1 Spd=12   MxCh= 3
B:  Alloc=  0/900 us ( 0%), #Int=  0, #Iso=  0
D:  Ver= 1.10 Cls=09(hub  ) Sub=00 Prot=00 MxPS=64 #Cfgs=  1
P:  Vendor=1d6b ProdID=0001 Rev= 3.00                                 // Linux Foundation root hub
S:  Manufacturer=Linux 3.0.31-ArchiKernel-CM-g42e9525 ohci_hcd
S:  Product=s5p OHCI
S:  SerialNumber=s5p-ohci
C:* #Ifs= 1 Cfg#= 1 Atr=e0 MxPwr=  0mA
I:* If#= 0 Alt= 0 #EPs= 1 Cls=09(hub  ) Sub=00 Prot=00 Driver=hub
E:  Ad=81(I) Atr=03(Int.) MxPS=   2 Ivl=255ms

T:  Bus=01 Lev=00 Prnt=00 Port=00 Cnt=00 Dev#=  1 Spd=480  MxCh= 3
B:  Alloc=  0/800 us ( 0%), #Int=  0, #Iso=  0
D:  Ver= 2.00 Cls=09(hub  ) Sub=00 Prot=00 MxPS=64 #Cfgs=  1
P:  Vendor=1d6b ProdID=0002 Rev= 3.00                                 // Linux Foundation root hub
S:  Manufacturer=Linux 3.0.31-ArchiKernel-CM-g42e9525 ehci_hcd
S:  Product=S5P EHCI Host Controller
S:  SerialNumber=s5p-ehci
C:* #Ifs= 1 Cfg#= 1 Atr=e0 MxPwr=  0mA
I:* If#= 0 Alt= 0 #EPs= 1 Cls=09(hub  ) Sub=00 Prot=00 Driver=hub
E:  Ad=81(I) Atr=03(Int.) MxPS=   4 Ivl=256ms

T:  Bus=01 Lev=01 Prnt=01 Port=01 Cnt=01 Dev#=  2 Spd=480  MxCh= 0
D:  Ver= 2.00 Cls=02(comm.) Sub=00 Prot=00 MxPS=64 #Cfgs=  1
P:  Vendor=1519 ProdID=0020 Rev=12.74                                 // Telefon Aktiebolaget LM Ericsson
S:  Manufacturer=Comneon
S:  Product=HSIC Device
S:  SerialNumber=0123456789
C:* #Ifs= 8 Cfg#= 1 Atr=c0 MxPwr=100mA
A:  FirstIf#= 0 IfCount= 2 Cls=02(comm.) Sub=02 Prot=01
A:  FirstIf#= 2 IfCount= 2 Cls=02(comm.) Sub=02 Prot=01
A:  FirstIf#= 4 IfCount= 2 Cls=02(comm.) Sub=02 Prot=01
A:  FirstIf#= 6 IfCount= 2 Cls=02(comm.) Sub=02 Prot=01
I:* If#= 0 Alt= 0 #EPs= 1 Cls=02(comm.) Sub=02 Prot=01 Driver=cdc_modem
E:  Ad=85(I) Atr=03(Int.) MxPS=  64 Ivl=1ms
I:* If#= 1 Alt= 0 #EPs= 2 Cls=0a(data ) Sub=00 Prot=00 Driver=cdc_modem
E:  Ad=81(I) Atr=02(Bulk) MxPS= 512 Ivl=0ms
E:  Ad=01(O) Atr=02(Bulk) MxPS= 512 Ivl=0ms
I:* If#= 2 Alt= 0 #EPs= 1 Cls=02(comm.) Sub=02 Prot=01 Driver=cdc_modem
E:  Ad=86(I) Atr=03(Int.) MxPS=  64 Ivl=1ms
I:* If#= 3 Alt= 0 #EPs= 2 Cls=0a(data ) Sub=00 Prot=00 Driver=cdc_modem
E:  Ad=82(I) Atr=02(Bulk) MxPS= 512 Ivl=0ms
E:  Ad=02(O) Atr=02(Bulk) MxPS= 512 Ivl=0ms
I:* If#= 4 Alt= 0 #EPs= 1 Cls=02(comm.) Sub=02 Prot=01 Driver=cdc_modem
E:  Ad=87(I) Atr=03(Int.) MxPS=  64 Ivl=1ms
I:* If#= 5 Alt= 0 #EPs= 2 Cls=0a(data ) Sub=00 Prot=00 Driver=cdc_modem
E:  Ad=83(I) Atr=02(Bulk) MxPS= 512 Ivl=0ms
E:  Ad=03(O) Atr=02(Bulk) MxPS= 512 Ivl=0ms
I:* If#= 6 Alt= 0 #EPs= 1 Cls=02(comm.) Sub=02 Prot=01 Driver=cdc_modem
E:  Ad=88(I) Atr=03(Int.) MxPS=  64 Ivl=1ms
I:* If#= 7 Alt= 0 #EPs= 2 Cls=0a(data ) Sub=00 Prot=00 Driver=cdc_modem
E:  Ad=84(I) Atr=02(Bulk) MxPS= 512 Ivl=0ms
E:  Ad=04(O) Atr=02(Bulk) MxPS= 512 Ivl=0ms


  */


  #define LOGTAG "sslw"

  #include <dlfcn.h>
  #include <stdio.h>
  #include <errno.h>
  #include <fcntl.h>
  #include <sys/stat.h>
  #include <sys/resource.h>

  #include <jni.h>

  #include "hu_uti.h"
  #include "hu_aad.h"

  const char * copyright = "Copyright (c) 2011-2015 Michael A. Reid. All rights reserved.";
/*
  char prop_buf    [DEF_BUF] = "";

  //private native int native_daemon_cmd      (int cmd_len, byte [] cmd_buf, int res_len, byte [] res_buf, int rx_tmo);
  int native_daemon_cmd (int cmd_len, char * cmd_buf, int res_max, char * res_buf, int net_port, int rx_tmo) {
    //logd ("native_daemon_cmd cmd_len: %d  cmd_buf \"%s\"  res_max: %d  res_buf: %p  net_port: %d  rx_tmo: %d", cmd_len, cmd_buf, res_max, res_buf, net_port, rx_tmo);
    int res_len = gen_client_cmd ((unsigned char *) cmd_buf, cmd_len, (unsigned char *) res_buf, res_max, net_port, rx_tmo);
    //logd ("native_daemon_cmd res_len: %d  res_buf \"%s\"", res_len, res_buf);
    return (res_len);
  }

  jbyte *   jb_cmd_buf          = NULL;
  jbyte     fake_cmd_buf [8192] = {0};

  jbyte *   jb_res_buf          = NULL;
  jbyte     fake_res_buf [8192] = {0};

  jint Java_fm_a2d_s2_com_1uti_native_1daemon_1cmd (JNIEnv * env, jobject thiz, jint cmd_len, jbyteArray cmd_buf, jint res_len, jbyteArray res_buf, jint net_port, jint rx_tmo) {
    ssl_init ();
    if (cmd_buf == NULL) {
      loge ("native_daemon_cmd cmd_buf == NULL");
      //return (-1);
      jb_cmd_buf = fake_cmd_buf;
    }
    else
      jb_cmd_buf = (*env)->GetByteArrayElements (env, cmd_buf, NULL);
    if (res_buf == NULL) {
      loge ("native_daemon_cmd res_buf == NULL");
      //return (-1);
      jb_res_buf = fake_res_buf;
    }
    else
      jb_res_buf = (*env)->GetByteArrayElements (env, res_buf, NULL);
    int len = native_daemon_cmd (cmd_len, jb_cmd_buf, res_len, jb_res_buf, net_port, rx_tmo);
    if (cmd_buf != NULL)
      (*env)->ReleaseByteArrayElements (env, cmd_buf, jb_cmd_buf, 0);
    if (res_buf != NULL)
      (*env)->ReleaseByteArrayElements (env, res_buf, jb_res_buf, 0);
    return (len);
  }
*/

  int ssl_init_done = 0;
  void * ssl_fd     = NULL;
  char lib_name1 [DEF_BUF] = "/data/app/com.google.android.gms-1/lib/arm/libsslwrapper_jnio.so";
  char lib_name2 [DEF_BUF] = "/data/app/com.google.android.gms-2/lib/arm/libsslwrapper_jnio.so"; 
  char * lib_name = lib_name1;

  //typedef jint (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit_t) (JNIEnv * env, jobject thiz, jstring pemPublicKeyCert1, jstring pemPublicKeyCert2, jbyteArray binPrivateKey);
  //Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit_t Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit_o = NULL;

  jint (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit_o                      ) (JNIEnv * env, jobject thiz, jstring pemPublicKeyCert1, jstring pemPublicKeyCert2, jbyteArray binPrivateKey);

  jint (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataEnqueue_o      ) (JNIEnv * env, jobject thiz, jobject bBuf, jint idx, jint len);
  jint (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshake_o                 ) (JNIEnv * env, jobject thiz)                                  ;
  void (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataDequeue_o      ) (JNIEnv * env, jobject thiz, jobject bBuf, jint len)          ; // Get handshake response

  jint (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineEnqueue_o ) (JNIEnv * env, jobject thiz, jobject bBuf, jint len)          ; // Return len
  jint (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelinePending_o ) (JNIEnv * env, jobject thiz)                                  ; // Return len
  jint (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineDequeue_o ) (JNIEnv * env, jobject thiz, jobject bBuf, jint len)          ; // Return len

  jint (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineEnqueue_o ) (JNIEnv * env, jobject thiz, jobject bBuf, jint idx, jint len); // Return len
  jint (* Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineDequeue_o ) (JNIEnv * env, jobject thiz, jobject bBuf, jint len)          ; // Return len

  int neuter = 0;     // 0 = Shim mode to monitor AA<>HU      1 = Neuter encryption for HUE testing

  int ssl_init () {

    if (ssl_init_done)
      return (0);

    ssl_init_done = 1;

    if (file_get ("/sdcard/aaneuter")) {
      neuter = 1;
      logd ("neuter encryption mode");
      return (0);
    }
    else {
      neuter = 0;
      logd ("pass-through shim mode");
    }

    lib_name = lib_name1;
    if (! file_get (lib_name))
      lib_name = lib_name2;

    ssl_fd = dlopen (lib_name, RTLD_LAZY);                              // Load library
    if (ssl_fd == NULL) {
      loge ("Could not load library '%s'", lib_name);
      return (-1);
    }
    logd ("Loaded %s  ssl_fd: %d", lib_name, ssl_fd);

    Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit_o                      = dlsym (ssl_fd, "Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit");
    Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataEnqueue_o      = dlsym (ssl_fd, "Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataEnqueue");
    Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshake_o                 = dlsym (ssl_fd, "Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshake");
    Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataDequeue_o      = dlsym (ssl_fd, "Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataDequeue");
    Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineEnqueue_o = dlsym (ssl_fd, "Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineEnqueue");
    Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelinePending_o = dlsym (ssl_fd, "Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelinePending");
    Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineDequeue_o = dlsym (ssl_fd, "Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineDequeue");
    Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineEnqueue_o = dlsym (ssl_fd, "Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineEnqueue");
    Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineDequeue_o = dlsym (ssl_fd, "Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineDequeue");


    if (! Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit_o                     ) loge ("No Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit");
    if (! Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataEnqueue_o     ) loge ("No Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataEnqueue");
    if (! Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshake_o                ) loge ("No Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshake");
    if (! Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataDequeue_o     ) loge ("No Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataDequeue");
    if (! Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineEnqueue_o) loge ("No Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineEnqueue");
    if (! Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelinePending_o) loge ("No Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelinePending");
    if (! Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineDequeue_o) loge ("No Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineDequeue");
    if (! Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineEnqueue_o) loge ("No Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineEnqueue");
    if (! Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineDequeue_o) loge ("No Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineDequeue");
  }



  int en_dump_init = 1;
  int en_dump_hs   = 1;
  int en_dump_data = 1;

  unsigned char bytear_buf [65536] = {0};
  int max_dump_ba = 65536;

  int logd_jstring (JNIEnv * env, char * prefix, jstring java_string) {
    const char * c_string = (*env)->GetStringUTFChars (env, java_string, 0);
    logd ("%s %s  ", prefix, c_string);
   (*env)->ReleaseStringUTFChars (env, java_string, c_string);
    return (0);
  }

  int logd_jbytear (JNIEnv * env, char * prefix, jbyteArray java_bytear) {
    jint len = (*env)->GetArrayLength (env, java_bytear);
    (*env)->GetByteArrayRegion (env, java_bytear, 0, len, (jbyte *) bytear_buf);
    logd ("%s len: %d", prefix, len);
    if (len > max_dump_ba)
      len = max_dump_ba;
    hex_dump (prefix, 16, (unsigned char *) & bytear_buf, len);
    return (0);
  }


  int jbytebu_num_dumps = 0;
  int jbytebu_max_dumps = 64;//256;//1024;
  int max_dump_jbytebu_hs = 16384;//4096;//64;  // max bytes to dump

  int logd_jbytebu (JNIEnv * env, char * prefix, jobject java_bytebu, jint idx, jint len, unsigned char * src) {
    if (jbytebu_num_dumps ++ >= jbytebu_max_dumps) {
      if (! file_get ("/sdcard/aanomax"))
        return (0);
    }
    unsigned char * buf = (unsigned char *) (*env)->GetDirectBufferAddress (env, java_bytebu);// ByteBuffer must be created using an allocateDirect() factory method

    if (jbytebu_num_dumps > 64 && ! file_get ("/sdcard/aaall")) {
      if (* src == 'A' && buf [0] == 0x00 && (buf [1] == 0 || buf [1] == 1)) // Tx Video
        return (0);
      if (* src == 'H' && buf [0] == 0x80 && buf [1] == 0x04)                // Rx Video Ack
        return (0);
      if (* src == 'H' && buf [0] == 0x00 && buf [1] == 0x0b)                // Rx Ping request
        return (0);
      if (* src == 'A' && buf [0] == 0x00 && buf [1] == 0x0c)                // Tx Ping response
        return (0);
    }

    if (ena_log_verbo || len > 48)
      logd ("%s buf: %p  idx: %d  len: %d", prefix, buf, idx, len);
    if (buf == NULL || len <= 0)
      return (0);

    if (* src == 'H' || * src == 'A')                                         // If Rx or Tx...
      hu_aad_dmp (prefix, src, 0, 0, & buf [idx], len);
    else {
      int dumplen = len;
      if (len > max_dump_jbytebu_hs)
        dumplen = max_dump_jbytebu_hs;
      hex_dump (prefix, 16, & buf [idx], dumplen);
    }

    return (0);
  }

  jint Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit (JNIEnv * env, jobject thiz, jstring pemPublicKeyCert1, jstring pemPublicKeyCert2, jbyteArray binPrivateKey) {
    ssl_init ();

    if (en_dump_init) {
      logd_jstring (env, "Init pemPublicKeyCert1: ", pemPublicKeyCert1);
      logd_jstring (env, "Init pemPublicKeyCert2: ", pemPublicKeyCert2);
      logd_jbytear (env, "Init binPrivateKey:     ", binPrivateKey);
    }

    jint ret = -1;
    if (neuter) {
      ret = JNI_TRUE;
      logd ("INIT !!!!!!!!!!!!!!!!!!!!!!!!    Init ret: %d", ret);
      return (ret);                                                // Return true on success
    }

    if (Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit_o)
      ret = Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeInit_o (env, thiz, pemPublicKeyCert1, pemPublicKeyCert2, binPrivateKey);
    else
      loge ("NO %s", __func__);
    logd ("Init ret: %d", ret);
    return (ret);
  }

  void Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataEnqueue (JNIEnv * env, jobject thiz, jobject bBuf, jint idx, jint len) { // Pass handshake request

    if (en_dump_hs)
      logd_jbytebu (env, "HE: ", bBuf, idx, len, "AA");
    else
      logd ("HE bBuf: %p  idx: %d  len: %d", bBuf, idx, len);

    if (neuter) 
      return;

    if (Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataEnqueue_o)
      Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataEnqueue_o (env, thiz, bBuf, idx, len);
    else
      loge ("NO %s", __func__);
  }

  jint Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshake (JNIEnv * env, jobject thiz) {                                    // Return len of handshake response
    jint retlen = -1;
    if (neuter) {
      retlen = 8;
      logd ("HS retlen: %d", retlen);
      return (retlen);
    }

    if (Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshake_o)
      retlen = Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshake_o (env, thiz);
    else
      loge ("NO %s", __func__);

    logd ("HS retlen: %d", retlen);
    return (retlen);
  }
  void Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataDequeue (JNIEnv * env, jobject thiz, jobject bBuf, jint len) {           // Get handshake response

    if (neuter) {
      logd ("HD passed len: %d", len);
      return;
    }

    if (Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataDequeue_o)
      Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeHandshakeDataDequeue_o (env, thiz, bBuf, len);
    else
      loge ("NO %s", __func__);

    if (en_dump_hs)
      logd_jbytebu (env, "HD: ", bBuf, 0, len, "AA");
    else
      logd ("HD passed len: %d", len);
  }

  #define MAX_BUF 65536
  jbyte decrypt_buf [MAX_BUF] = {0};
  jint last_decrypt_len = 8;

  jint Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineEnqueue (JNIEnv * env, jobject thiz, jobject bBuf, jint len) {           // Return len
    int retlen = -1;
    if (ena_log_verbo)
      logd ("DE Rx encrypted passed len: %d", len);

    if (neuter) {
      jbyte * buf = (jbyte *) (* env)->GetDirectBufferAddress (env, bBuf);// ByteBuffer must be created using an allocateDirect() factory method
      last_decrypt_len = len;
      if (len > MAX_BUF) {
        loge ("DecryptionPipelineEnqueue len too large");
        return (-1);
      }
      memcpy (decrypt_buf, buf, len);
      retlen = len;
      if (ena_log_verbo)
        logd ("DE Rx retlen: %d", retlen);
      return (retlen);
    }

    if (Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineEnqueue_o)
      retlen = Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineEnqueue_o (env, thiz, bBuf, len);
    else
      loge ("NO %s", __func__);
    if (ena_log_verbo)
      logd ("DE Rx retlen: %d", retlen);
    return (retlen);
  }

  jint Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelinePending  (JNIEnv * env, jobject thiz) {                                    // Return len
    int retlen = -1;
    if (neuter) {
      retlen = last_decrypt_len;
      if (ena_log_verbo)
        logd ("DP Rx pending retlen: %d", retlen);
      return (retlen);
    }

    if (Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelinePending_o)
      retlen = Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelinePending_o (env, thiz);
    else
      loge ("NO %s", __func__);
    if (ena_log_verbo)
      logd ("DP Rx pending retlen: %d", retlen);
    return (retlen);
  }

  jint Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineDequeue (JNIEnv * env, jobject thiz, jobject bBuf, jint len) {           // Return len
    int retlen = -1;
    if (neuter) {
      if (len > MAX_BUF) {
        loge ("DecryptionPipelineDequeue len too large");
        return (-1);
      }
      jbyte * buf = (jbyte *) (* env)->GetDirectBufferAddress (env, bBuf);// ByteBuffer must be created using an allocateDirect() factory method
      retlen = last_decrypt_len;
      last_decrypt_len = 0;
      memcpy (buf, decrypt_buf, len);
      if (en_dump_data)
        logd_jbytebu (env, "rx: ", bBuf, 0, len, "HU");
      if (ena_log_verbo)
        logd ("DD Rx decrypted len: %d  retlen: %d", len, retlen);

      return (len); // len is whatever we returned as pending
    }

    if (Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineDequeue_o)
      retlen = Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeDecryptionPipelineDequeue_o (env, thiz, bBuf, len);
    else
      loge ("NO %s", __func__);
    if (en_dump_data)
      logd_jbytebu (env, "rx: ", bBuf, 0, retlen, "AA");
    if (ena_log_verbo)
      logd ("DD Rx decrypted len: %d  retlen: %d", len, retlen);
    return (retlen);
  }  

  //jbyte encrypt_buf [MAX_BUF] = {0};

  jint Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineEnqueue  (JNIEnv * env, jobject thiz, jobject bBuf, jint idx, jint len) { // Return len
    int retlen = -1;
    if (en_dump_data)
      logd_jbytebu (env, "TX: ", bBuf, idx, len, "AA");
    if (neuter) {
      jbyte * encrypt_buf = vid_write_tail_buf_get (len);
      if (ena_log_verbo)
        logd ("EE Tx encrypt_buf: %p", encrypt_buf);
      if (encrypt_buf == NULL) {
        return (0);
      }

      jbyte * buf = (jbyte *) (* env)->GetDirectBufferAddress (env, bBuf);// ByteBuffer must be created using an allocateDirect() factory method
      //logd ("EncryptionPipelineEnqueue buf: %p  idx: %d  len: %d", buf, idx, len);
      if (len > MAX_BUF) {
        loge ("EncryptionPipelineEnqueue len too large");
        return (-1);
      }
      memcpy (encrypt_buf, & buf [idx], len);
      if (en_dump_data && ena_log_verbo)
        hex_dump ("TX: ", 16, encrypt_buf, len);
        //logd_jbytebu (env, "Tx: ", bBuf, idx, len, "AA");
      return (len);
    }

    if (Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineEnqueue_o)
      retlen = Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineEnqueue_o (env, thiz, bBuf, idx, len);
    else
      loge ("NO %s", __func__);
    if (ena_log_verbo)
      logd ("EE Tx retlen: %d", retlen);
    return (retlen);
  }

  jint Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineDequeue (JNIEnv * env, jobject thiz, jobject bBuf, jint len) {           // Return len
    if (ena_log_verbo)
      logd ("ED Tx passed len: %d", len);
    if (neuter) {
      int retlen = -1;
      jbyte * encrypt_buf = vid_read_head_buf_get (& retlen);
      if (ena_log_verbo)
        logd ("ED Tx encrypt_buf: %p", encrypt_buf);
      if (encrypt_buf == NULL) {
        return (0);
      }

      jbyte * buf = (jbyte *) (* env)->GetDirectBufferAddress (env, bBuf);// ByteBuffer must be created using an allocateDirect() factory method
      //logd ("EncryptionPipelineDequeue buf: %p  len: %d", buf, len);
      if (len > MAX_BUF) {
        loge ("EncryptionPipelineDequeue len too large");
        return (-1);
      }
      memcpy (buf, encrypt_buf, retlen);
      if (ena_log_verbo)
        logd ("ED Tx encrypted retlen: %d", retlen);
      if (en_dump_data && ena_log_verbo)
        hex_dump ("ED Tx encrypt_buf: ", 16, encrypt_buf, retlen);
      return (retlen);
    }

    int retlen = -1;
    if (Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineDequeue_o)
      retlen = Java_com_google_android_gms_car_senderprotocol_SslWrapper_nativeEncryptionPipelineDequeue_o (env, thiz, bBuf, len);
    else
      loge ("NO %s", __func__);
    if (ena_log_verbo)
      logd ("ED Tx encrypted retlen: %d", retlen);
    return (len);
  }

