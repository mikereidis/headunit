
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
      logd ("open errno: %d (%s)\n", errno, strerror (errno));  
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

    if (argc == 3) {
      while (1) {
        usleep (10000L);
        printf (".\b");
      }
      return (ret);
    }

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


