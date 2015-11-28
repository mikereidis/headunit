
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


    int vid_bufs = vid_buf_buf_tail - vid_buf_buf_head;
    int aud_bufs = vid_buf_buf_tail - vid_buf_buf_head;


    if (cmd_buf != NULL && cmd_len == 3 && cmd_buf [0] == 121) {        // If onCreate() hu_tra:transport_start()
      byte ep_in_addr  = cmd_buf [1];
      byte ep_out_addr = cmd_buf [2];                                   // Get endpoints passed

      ret = hu_aap_start (ep_in_addr, ep_out_addr);                     // Start USB/ACC/OAP, AA Protocol

      aap_state_starts ++;                                              // Count error starts too
      if (ret == 0) {
        logd ("hu_aap_start() success aap_state_starts: %d", aap_state_starts);
      }
      else {
        loge ("hu_aap_start() error aap_state_starts: %d", aap_state_starts);
        aap_state_start_error = 1;
        return (-1);
      }
    }

/* Functions                code                            Params:
    Transport Start         hu_aap_start()                  USB EPs
    Poll/Handle 1 Rcv Msg   hu_aap_recv_process()           -
    Send:
      Send Mic              hu_aap_enc_send()               mic data
      Send Touch            hu_aap_enc_send()               touch data
      Send Ctrl             hu_aap_enc_send()/hu_aap_stop() ctrl data

  Returns:
    Audio Mic Start/Stop
    Audio Out Start/Stop
    Video
    Audio
*/
    else if (cmd_buf != NULL && cmd_len >= 4) {                         // If encrypted command to send...
      int chan = 0;//cmd_buf [0];

      if (cmd_len > 63)                                                 // If Microphone audio...
        chan = AA_CH_MIC;
      else if (cmd_len > 6 && cmd_buf [4] == 0x80 && cmd_buf [5] == 1)  // If Touch event...
        chan = AA_CH_TOU;
      else                                                              // If Byebye or other control packet...
        chan = AA_CH_CTR;
      if (chan != cmd_buf [0]) {
        loge ("chan: %d != cmd_buf[0]: %d", chan, cmd_buf [0]);
        chan = cmd_buf [0];
      }

      //hex_dump ("JNITX: ", 16, cmd_buf, cmd_len);

      ret = hu_aap_enc_send (chan, & cmd_buf [4], cmd_len - 4);         // Send

      if (cmd_buf != NULL && cmd_len  >= 8 && cmd_buf [5] == 15) {      // If byebye...
        logd ("Byebye");
        ms_sleep (100);
        ret = hu_aap_stop ();
      }

    }
    else {
      if (vid_bufs > 0 || aud_bufs > 0)                                 // If any queue audio or video...
        ret = 0;                                                        // Do nothing (don't wait for recv iaap_tra_recv_tmo)
      else
        ret = hu_aap_recv_process ();                                   // Else Process 1 message w/ iaap_tra_recv_tmo
    }
    if (ret < 0) {
      return (ret);                                                     // If error then done w/ error
    }

    if (vid_bufs <= 0 && aud_bufs <= 0) {                               // If no queued audio or video...
      ret = hu_aap_mic_get ();
      if (ret >= 1) {// && ret <= 2) {                                  // If microphone start (2) or stop (1)...
        return (ret);                                                   // Done w/ mic notification: start (2) or stop (1)
      }
                                                                        // Else if no microphone state change...
      
      if (hu_aap_out_get (AA_CH_AUD) >= 0)                              // If audio out stop...
        return (3);                                                     // Done w/ audio out notification 0
      if (hu_aap_out_get (AA_CH_AU1) >= 0)                              // If audio out stop...
        return (4);                                                     // Done w/ audio out notification 1
      if (hu_aap_out_get (AA_CH_AU2) >= 0)                              // If audio out stop...
        return (5);                                                     // Done w/ audio out notification 2
    }

    byte * dq_buf = NULL;
    dq_buf = aud_read_head_buf_get (& res_len);                         // Get audio if ready

    if (dq_buf == NULL)                                                 // If no audio... (Audio has priority over video)
      dq_buf = vid_read_head_buf_get (& res_len);                       // Get video if ready

    else {                                                              // If audio
      if (dq_buf [0] == 0 && dq_buf [1] == 0 && dq_buf [2] == 0 && dq_buf [3] == 1) {
        dq_buf [3] = 0;                                                   // If audio happened to have magic video signature... (rare), then 0 the 1
        loge ("magic video signature in audio");
      }

      //hu_uti.c:  #define aud_buf_BUFS_SIZE    65536 * 4      // Up to 256 Kbytes
    }

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

