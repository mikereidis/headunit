
  //

  #define LOGTAG "hu_mai"
  #include "hu_uti.h"                                                  // Utilities
  #include "hu_aap.h"

  int main (int argc, char * argv []) {
    int ret = 0;
    errno = 0;
/*
    pthread_t busy_thr;
    if (pthread_create (& busy_thr, NULL, busy_thread, NULL) != 0) {
      loge ("pthread_create failed");
      return (-1);
    }
*/

    //record_enable = 1;

/*
  //int usb_perms_set () {
    #ifdef __ANDROID_API__    // Use sudo on Linux
    int ena_su_perm = 1;
    if (ena_su_perm) {    // Set Permission w/ SU:
      int ret = system ("su -c chmod -R 777 /dev/bus");                 // !! Binaries like ssd that write to stdout cause C system() to crash !
      logd ("iusb_init system() ret: %d", ret);
    }
    #endif
  //}
*/

    byte ep_in_addr  = -1;
    byte ep_out_addr = -1;

    ret = hu_aap_start (ep_in_addr, ep_out_addr);                      // Start AA Protocol
    if (ret < 0) {
      return (ret);
    }

    unsigned long last_log = 0;
    ret = 0;
    while (ret >= 0) {                                                  // While no errors...
      ret = hu_aap_recv_process ();                                    // Process 1 message
      if (ret != 0) {
        loge ("hu_aap_recv_process() ret: %p", ret);
        break;
      }
      else if (ena_log_extra)
        logd ("hu_aap_recv_process() ret: %p", ret);

      int res_len = 0;
      byte * dq_buf = vid_read_head_buf_get (& res_len);                // See if there is a video buffer queued to read from
      if (ena_log_extra || (ena_log_verbo && dq_buf != NULL))
        logd ("dq_buf: %p  res_len: %d", dq_buf, res_len);

      unsigned long this_log = ms_get ();
      if (last_log + 3000 < this_log) {                                 // Log "." every 3 seconds
        logd (".");
        last_log = this_log;
      }

      ms_sleep (30);                                                    // Up to 33 receives per second
    }

    ret = hu_aap_stop ();

    return (ret);
  }

