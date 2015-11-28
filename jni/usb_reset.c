
  // usbreset -- send a USB port reset to a USB device

  #include <stdio.h>
  #include <fcntl.h>
  #include <errno.h>
  #include <sys/ioctl.h>
  //#include <linux/usbdevice_fs.h>
  #define USBDEVFS_RESET _IO('U', 20)

  #include <pthread.h>

  #include "hu_uti.c"
//#define logd  printf
//#define loge  printf


  int usb_reset (char * usb_device) {
    int fd = -1;
    int ret = 0;

    errno = 0;
    fd = open (usb_device, O_WRONLY);
    if (fd < 0) {
      logd ("open errno: %d (%s)\n", errno, strerror (errno));  // open errno: 2 (No such file or directory)  !! Reset changes directory entries for "/data/data/ca.yyx.hu/lib/libusb_reset.so /dev/bus/usb/*/*"
      return (0);//-1);
    }

    logd ("Resetting USB device %s\n", usb_device);

    errno = 0;
    ret = ioctl (fd, USBDEVFS_RESET, 0);
    if (ret < 0) {
      logd ("ioctl errno: %d (%s)\n", errno, strerror (errno));   // ioctl errno: 21 (Is a directory)         !! Reset changes directory entries for "/data/data/ca.yyx.hu/lib/libusb_reset.so /dev/bus/usb/*/*"
      return (0);//-2);
    }
    logd ("Reset successful\n");

    close (fd);
    return (0);
  }
/*
  unsigned long ms_get () {                                                      // !!!! Affected by time jumps ?
    struct timespec tspec = {0, 0};
    int res = clock_gettime (CLOCK_MONOTONIC, & tspec);
    //logd ("sec: %ld  nsec: %ld\n", tspec.tv_sec, tspec.tv_nsec);

    unsigned long millisecs = (tspec.tv_nsec / 1000000L);
    millisecs += (tspec.tv_sec * 1000L);       // remaining 22 bits good for monotonic time up to 4 million seconds =~ 46 days. (?? - 10 bits for 1024 ??)

    return (millisecs);
  }

  unsigned long ms_sleep (unsigned long ms) {
    usleep (ms * 1000L);
    return (0);
  }
*/


  int main (int argc, char ** argv) {
    char * usb_device;
    int ret = 0;

    int idx = 1;
    for (idx = 1; idx < argc; idx ++) {
      usb_device = argv [idx];
      ret = usb_reset (usb_device);
    }
    return (ret);


    if (argc == 2) {
      usb_device = argv [1];
      ret = usb_reset (usb_device);
      return (ret);
    }
/*
    if (argc != 2) {
      loge ("Usage: usb_reset usb_device\n");
      return (-1);
    }
*/
/*
    logd ("sleep (0): %d\n", sleep (0));
    logd ("sleep (1): %d\n", sleep (1));
    logd ("sleep (2): %d\n", sleep (2));
return (0);
*/

    if (argc == 3) {
      while (1) {
        usleep (10000L);
        printf (".\b");
      }
      return (ret);
    }
/*
    pthread_t busy_thr;
    if (pthread_create (& busy_thr, NULL, busy_thread, NULL) != 0) {
      loge ("pthread_create failed");
      return (-1);
    }
//*/
    logd ("1");
    sleep (1);
    logd ("2");
    sleep (1);
    logd ("3");
    sleep (1);

    unsigned long this_ms = 0;
    unsigned long sleep_val = 1L;
    unsigned long last_ms = ms_get ();
    //for (sleep_val = 1L; sleep_val <= 10000L; sleep_val *= 2L) {
    for (sleep_val = 64L; sleep_val <= 10000L; sleep_val += 64L) {
      //%3.3d (%32.32s)  errno, strerror (errno)
      ms_sleep (sleep_val);
      this_ms = ms_get ();
      logd ("Done ms_sleep (%6.6ld)  ms: %16ld\n", sleep_val, this_ms - last_ms);
      last_ms = this_ms;
    }

    return (0);
 }


/*
    long ms = 0;//us_get ();
    logd ("us_get: %ld  errno: %d (%s)", us_get (), errno, strerror (errno));                               // us_get:
    errno = 0;
    //ret = sleep (1);
    //logd ("ret: %5.5d  errno: %3.3d (%32.32s)  us_get: %16ld", ret, errno, strerror (errno), us_get ());


    unsigned long sleep_val = 1L;
    for (sleep_val = 1L; sleep_val <= 10000L; sleep_val *= 2L)
      logd ("ms_sleep (%ld): %5.5d  errno: %3.3d (%32.32s)  ms_get: %16ld", ms_sleep (sleep_val), sleep_val, errno, strerror (errno), ms_get ());    // ret: 00000  errno: 000 (                         Success)  us_get:         11358714

///*
    struct timespec tm;
    tm.tv_sec = 0;
    tm.tv_nsec = 1000000L;  // 1 ms
    //ret = nanosleep (& tm, NULL);
    //logd ("ret: %5.5d  errno: %3.3d (%32.32s)  us_get: %16ld", ret, errno, strerror (errno), us_get ());    // ret: 00000  errno: 000 (                         Success)  us_get:         11362714

    for (tm.tv_nsec = 1; tm.tv_nsec <= 100000000L; tm.tv_nsec *= 10)
      logd ("nanosleep (%ld): %5.5d  errno: %3.3d (%32.32s)  us_get: %16ld", nanosleep (& tm, NULL), tm.tv_nsec, errno, strerror (errno), us_get ());    // ret: 00000  errno: 000 (                         Success)  ms_get:         11358714
return (0);
//*/

