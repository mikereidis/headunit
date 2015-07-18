
  #define LOGTAG "hu_jni"
  #include "hu_uti.h"
  #include "hu_aap.h"

  #include <stdio.h>
  #include <errno.h>
  #include <fcntl.h>
  #include <sys/stat.h>
  #include <sys/resource.h>

  #include <jni.h>


  char * copyright = "Copyright (c) 2011-2015 Michael A. Reid. All rights reserved.";

  int aap_state_start_error = 0;
  int aap_state_starts  = 0;

  int jni_aa_cmd (int cmd_len, char * cmd_buf, int res_max, char * res_buf) {
    if (ena_log_extra || cmd_len >= 1)
      logd ("cmd_len: %d  cmd_buf %p  res_max: %d  res_buf: %p", cmd_len, cmd_buf, res_max, res_buf);
    int res_len = 0;
    int ret = 0;

    if (cmd_buf != NULL && cmd_len == 3 && cmd_buf [0] == 121) {        // If onCreate() hu_tra:transport_start()
      byte ep_in_addr  = cmd_buf [1];
      byte ep_out_addr = cmd_buf [2];
      ret = hu_aap_start (ep_in_addr, ep_out_addr);                    // Start USB/ACC/OAP, AA Protocol
      aap_state_starts ++;  // Count error starts too
      if (ret == 0) {
        logd ("hu_aap_start() success aap_state_starts: %d", aap_state_starts);
      }
      else {
        loge ("hu_aap_start() error aap_state_starts: %d", aap_state_starts);
        aap_state_start_error = 1;
        return (-1);
      }
    }

    if (cmd_buf != NULL && cmd_len >= 4) {                              // If encrypted command to send...
      int chan = cmd_buf [0];
      ret = hu_aap_enc_send (chan, & cmd_buf [4], cmd_len - 4);        // Send

      if (cmd_buf != NULL && cmd_len  >= 8 && cmd_buf [5] == 15) {  // If byebye...
        loge ("Byebye");
        ms_sleep (100);
        ret = hu_aap_stop ();
      }

    }
    else {
      ret = hu_aap_recv_process ();                                    // Process 1 message
    }
    if (ret < 0) {
      return (ret);
    }

    byte * dq_buf = read_head_buffer_get (& res_len);
    if (ena_log_extra || (ena_log_verbo && dq_buf != NULL))
      logd ("dq_buf: %p", dq_buf);
    if (dq_buf == NULL || res_len <= 0) {
      if (ena_log_extra)
        logd ("No data dq_buf: %p  res_len: %d", dq_buf, res_len);
      return (0);
    }
    memcpy (res_buf, dq_buf, res_len);

    if (ena_log_verbo)
      logd ("res_len: %d", res_len);
    return (res_len);
  }

  JNIEXPORT jint Java_ca_yyx_hu_hu_1tra_native_1aa_1cmd (JNIEnv * env, jobject thiz, jint cmd_len, jbyteArray cmd_buf, jint res_len, jbyteArray res_buf) {

    if (ena_log_extra)
      logd ("cmd_buf: %p  cmd_len: %d  res_buf: %p  res_len: %d", cmd_buf, cmd_len,  res_buf, res_len);

    jbyte * aa_cmd_buf          = NULL;
    jbyte * aa_res_buf          = NULL;

    if (cmd_buf == NULL) {
      loge ("cmd_buf == NULL");
      return (-1);
    }

    if (res_buf == NULL) {
      loge ("res_buf == NULL");
      return (-1);
    }

    aa_cmd_buf = (*env)->GetByteArrayElements (env, cmd_buf, NULL);
    aa_res_buf = (*env)->GetByteArrayElements (env, res_buf, NULL);

    int len = jni_aa_cmd (cmd_len, aa_cmd_buf, res_len, aa_res_buf);

    if (cmd_buf != NULL)
      (*env)->ReleaseByteArrayElements (env, cmd_buf, aa_cmd_buf, 0);

    if (res_buf != NULL)
      (*env)->ReleaseByteArrayElements (env, res_buf, aa_res_buf, 0);

    return (len);
  }

