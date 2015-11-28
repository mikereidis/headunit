
  //

  #define LOGTAG "hu_ush"
  #include "hu_uti.h"

  #include "hu_oap.h"

  #ifdef __ANDROID_API__
  #include "usbhost.h"        // /home/m/android/system/system/core/include/usbhost/usbhost.h
  #else

  #endif

  #include "libusb.h" // Use for defines

  struct usb_device * current_device = NULL;
  uint8_t read_ep  = 255;
  uint8_t write_ep = 255;

  //pthread_mutex_t device_mutex = PTHREAD_MUTEX_INITIALIZER;
  //pthread_cond_t  device_cond   = PTHREAD_COND_INITIALIZER;


  int hu_bulk_recv (byte * buf, int len) {
    int ret = usb_device_bulk_transfer (current_device, read_ep, buf, len, 1000);
    return (ret);
  }

  int hu_bulk_send (byte * buf, int len) {
    int ret = usb_device_bulk_transfer (current_device, write_ep, buf, len, 1000);
    return (ret);
  }

  void send_string (struct usb_device * device, int index, const char * string) {
    usb_device_control_transfer (device, USB_DIR_OUT | USB_TYPE_VENDOR, ACC_REQ_SEND_STRING, 0, index, (void *) string, strlen (string) + 1, 0);
    ms_sleep (10);                                                      // Some devices can't handle back-to-back requests, so delay a bit
  }

  // Callback indicating that initial device discovery is done. Return true to exit from usb_host_run.

  int usb_discovery_done (void * client_data) {
    logd ("usb_discovery_done client_data: %p", client_data);
    return (0);                                                       // 0 = Leave usb_host_run() running
  }

    // Callback for notification when USB devices are removed. Return true to exit from usb_host_run.
  int usb_device_removed (const char * devname, void * client_data) {
    logd ("usb_device_removed client_data: %p  devname: %s", client_data, devname);

    //pthread_mutex_lock (& device_mutex);
                                                                        //
    if  (current_device) {                                              // If we have a current device...
      const char * curr_devname = usb_device_get_name (current_device);
      if (! strcmp (curr_devname, devname)) {                           // If current device was removed...
        logd ("usb_device_removed current device disconnected");
        usb_device_close (current_device);
        current_device = NULL;
      }
    }

    //pthread_mutex_unlock (& device_mutex);
    return (0);                                                       // 0 = Leave usb_host_run() running
  }

  int ena_su_perm = 1;

    // Callback for notification when new USB devices are attached. Return true to exit from usb_host_run.
  int usb_device_added (const char * devname, void * client_data) {
    logd ("\n");
    //logd ("client_data: %p  devname: %s", client_data, devname);
    logd ("devname: %s", devname);

    uint16_t usb_vid, usb_pid;
    int ret;
    //int unused = (int) client_data;

    if (ena_su_perm) {    // Set Permission w/ SU:
      char cmd [256] = "su -c chmod 777 ";
      strlcat (cmd, devname, sizeof (cmd));
      //strlcat (cmd, " 2>&1 > /sdcard/hu_res", sizeof (cmd));
      errno = 0;
      ret = system (cmd);                                             // !! Binaries like ssd that write to stdout cause C system() to crash !
      logd ("system() ret: %d  errno: %d (%s)", ret, errno, strerror (errno));
    }

    struct usb_device * device = usb_device_open (devname);
    if  (! device) {
      loge ("usb_device_open failed");
      return (0);                                                       // 0 = Leave usb_host_run() running
    }

    usb_vid = usb_device_get_vendor_id  (device);
    usb_pid = usb_device_get_product_id (device);
    char * usb_ser = usb_device_get_serial (device);

    logd ("usb_vid: 0x%x (%d - %s)  usb_pid: 0x%x  usb_ser: %s", usb_vid, usb_vid, usb_vid_get (usb_vid), usb_pid, usb_ser);

    if (current_device != NULL) {                                       // If we already have a device, just ignore
      logd ("Ignoring have current_device: %p", current_device);
      usb_device_close (device);
      return (0);                                                       // 0 = Leave usb_host_run() running
    }

    if  (usb_vid != USB_VID_GOO || usb_pid >= USB_PID_ACC_MIN ||  usb_pid <= USB_PID_ACC_MAX) { // If not in Google Accessory mode...
      logd ("Found new device - attempting to switch to accessory mode");

      uint16_t protocol = -1;
      errno = 0;
      ret = usb_device_control_transfer (device, USB_DIR_IN | USB_TYPE_VENDOR, ACC_REQ_GET_PROTOCOL, 0, 0, & protocol, sizeof (protocol), 1000);
      if  (ret < 0) {
        loge ("No Acc OAP protocol ret: %d  errno: %d", ret, errno);
        usb_device_close (device);
        return (0);                                                     // 0 = Leave usb_host_run() running
      }
      if  (protocol < 2) {
        loge ("Acc OAP protocol version %d", protocol);
        usb_device_close (device);
        return (0);                                                   // 0 = Leave usb_host_run() running
      }
      logd ("Acc OAP protocol version %d", protocol);

      send_string (device, ACC_IDX_MAN, AAP_VAL_MAN);                 // Send Acc strings
      send_string (device, ACC_IDX_MOD, AAP_VAL_MOD);
      //send_string (device, ACC_IDX_DES, AAP_VAL_DES);
      //send_string (device, ACC_IDX_VER, AAP_VAL_VER);
      //send_string (device, ACC_IDX_URI, AAP_VAL_URI);
      //send_string (device, ACC_IDX_SER, AAP_VAL_SER);

      errno = 0;
      ret = usb_device_control_transfer (device, USB_DIR_OUT | USB_TYPE_VENDOR, ACC_REQ_START, 0, 0, NULL, 0, 1000);
      if  (ret < 0)
        loge ("Acc Start ret: %d errno: %d", ret, errno);
      else
        logd ("Acc Start ret: %d", ret);


    }

    logd ("Found android device in accessory mode");

    //pthread_mutex_lock (& device_mutex);
    //pthread_cond_broadcast (& device_cond);
    //pthread_mutex_unlock (& device_mutex);

    struct usb_descriptor_iter        iter;
    struct usb_descriptor_header    * desc  = NULL;
    struct usb_interface_descriptor * intf  = NULL;
    struct usb_endpoint_descriptor  * ep   = NULL;

    usb_descriptor_iter_init (device, & iter);                        // Init for iterating USB descriptors
    
    read_ep  = 255;
    write_ep = 255;

    while  ((desc = usb_descriptor_iter_next (& iter)) != NULL) {
      if (0);
      else if  (desc->bDescriptorType == LIBUSB_DT_DEVICE) {
        logd ("Device desc: %p", desc);
      }
      else if  (desc->bDescriptorType == LIBUSB_DT_CONFIG) {
        logd ("Config desc: %p", desc);
      }
      else if  (desc->bDescriptorType == LIBUSB_DT_STRING) {
        logd ("String desc: %p", desc);
      }
      else if  (desc->bDescriptorType == LIBUSB_DT_HID) {
        logd ("HID desc: %p", desc);
      }
      else if  (desc->bDescriptorType == LIBUSB_DT_REPORT) {
        logd ("HID Report desc: %p", desc);
      }
      else if  (desc->bDescriptorType == LIBUSB_DT_PHYSICAL) {
        logd ("Physical desc: %p", desc);
      }
      else if  (desc->bDescriptorType == LIBUSB_DT_HUB) {
        logd ("Hub desc: %p", desc);
      }
      else if  (desc->bDescriptorType == LIBUSB_DT_INTERFACE) {
        intf =  (struct usb_interface_descriptor *) desc;
        logd ("Interface desc/intf: %p", desc);
      }
      else if  (desc->bDescriptorType == LIBUSB_DT_ENDPOINT) {
        logd ("Endpoint  desc/ep:   %p", desc);
        ep = (struct usb_endpoint_descriptor *) desc;
        uint8_t ep_addr = ep->bEndpointAddress;
        if  ((ep_addr & USB_ENDPOINT_DIR_MASK) == USB_DIR_IN) {
          if (read_ep == 255) {
            read_ep  = ep_addr;
            logd ("Set as first read endpoint ep_addr: %d");
          }
          else
            loge ("Ignored already have read endpoint ep_addr: %d");
        }
        else {
          if (write_ep == 255) {
            write_ep = ep_addr;
            logd ("Set as first write endpoint ep_addr: %d");
          }
          else
            loge ("Ignored already have write endpoint ep_addr: %d");
        }
      }
      else
        loge ("Unknown desc->bDescriptorType: %d", desc->bDescriptorType);
    }

    if  (! intf) {
      loge ("interface not found");
      usb_device_close (device);
      return (0);                                                     // 0 = Leave usb_host_run() running
    }
    if  (read_ep == 255 || write_ep == 255) {
      loge ("Read and/or write endpoints not found");
      usb_device_close (device);
      return (0);                                                     // 0 = Leave usb_host_run() running
    }

    errno = 0;
    ret = usb_device_claim_interface (device, intf->bInterfaceNumber);
    if  (ret) {
      loge ("Error usb_device_claim_interface() errno: %d", errno);
      usb_device_close (device);
      return (0);                                                     // 0 = Leave usb_host_run() running
    }

    current_device = device;

    loge ("Success usb_device_claim_interface() : Can start AA protocol now");

    //ret = hu_aap_start ();

    byte vr_buf [] = {0, 3, 0, 6, 0, 1, 0, 1, 0, 1};                    // Version Request
    //ret = hu_aap_usb_send (vr_buf, sizeof (vr_buf), 1000);             // Send Version Request
    errno = 0;
    ret = hu_bulk_send (vr_buf, sizeof (vr_buf));
    if (ret) {
      loge ("Error hu_bulk_send() ret: %d  errno: %d (%s)", ret, errno, strerror (errno));
      usb_device_close (device);
      return (0);                                                     // 0 = Leave usb_host_run() running
    }
    logd ("Success hu_bulk_send()");

    return (0);                                                         // 0 = Leave usb_host_run() running
  }


  int hu_usb_run () {                                                 // Android specific C level libusbhost API requires thread for it's processing, and calling of the added/removed/disc-done callbacks
    struct usb_host_context * context = usb_host_init ();
    usb_host_run (context, usb_device_added, usb_device_removed, usb_discovery_done,  (void * ) 0);
    usb_host_cleanup (context);
    return (0);
  }

  void * hu_usb_thread (void * arg) {
    hu_usb_run ();
  }
  int hu_usb_start () {
    int ret = 0;
    pthread_t th;
    pthread_create (& th, NULL, hu_usb_thread, NULL);
    #ifndef __ANDROID_API__
    pthread_setname_np (th, "hu_usb_thread");
    #endif
    return (0);
  }


#ifndef AA_NO_MAIN
  int main (int argc, char * argv []) {
    int ret = hu_usb_run ();
    while (1) {
      //aa_com_process ();                                    // Process 1 message
      ms_sleep (30);
    }

    return (ret);
  }
#endif

