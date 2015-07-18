
    // Head Unit Transport

package ca.yyx.hu;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;

public class hu_tra {

  private Context         m_context;
  private hu_act          m_hu_act;                                     // Activity for callbacks
  private vid_thread      m_vid_thread;                                 // Video thread

  private UsbManager      m_usb_mgr;
  private usb_receiver    m_usb_receiver;
  private boolean         m_usb_connected;
  private UsbDevice       m_usb_device;

  private boolean         m_autolaunched = false;


  public hu_tra (hu_act hu_act) {
    m_hu_act = hu_act;
    m_context = (Context) hu_act;
    m_usb_mgr = (UsbManager) m_context.getSystemService (Context.USB_SERVICE);
    m_autolaunched = false;
  }

    // Datagram command API:

    // Native API:

  static {
    System.loadLibrary ("hu_jni");
  }

  private static native int native_aa_cmd         (int cmd_len, byte [] cmd_buf, int res_len, byte [] res_buf);


// 1 External API used:
  private void video_decode (ByteBuffer content) {                      // Decode H264 video in client
    if (m_hu_act != null)
      m_hu_act.video_decode (content);                                 // Call callback
  }

// 3 External APIs provided:

///*

  private boolean touch_sync = true;//false;
  private final class vid_thread extends Thread {                       // Video thread
    private volatile boolean m_stopping = false;                        // Set true when stopping
    public vid_thread () {
      /* super ("vid_thread");                     // Name video thread
      else*/                    super ("vid_thread");
    }
    @Override
    public void run () {
      int ret = 0;
      while (! m_stopping) {                                            // Loop until stopping...

        synchronized (this) {
          if (touch_sync && ! m_stopping && new_touch && len_touch > 0 && ba_touch != null) {
            ret = aa_cmd_send (len_touch, ba_touch, 0, null);           // Handle touch event
            ba_touch = null;
            new_touch = false;
            hu_uti.ms_sleep (1);
            continue;
          }
        }

        if (! m_stopping && ret >= 0)
          ret = aa_cmd_send (0, null, 0, null);                           // Null message to just poll
        if (ret < 0)
          m_stopping = true;
        if (! m_stopping)
          hu_uti.ms_sleep (1);//3);                                        // Up to 330 frames per second
      }
      byebye_send ();
    }
    public void quit () {
      m_stopping = true;                                                 // Terminate thread
    }
  } // final class vid_thread extends Thread {
//*/


  public int transport_stop () {
    hu_uti.logd ("");
    byebye_send ();
    hu_uti.logd ("calling usb_stop()");
    usb_stop ();
    if (m_vid_thread != null) {                                          // If video thread...
      m_vid_thread.quit ();                                              // Terminate video thread using it's quit() API
    }
    hu_uti.logd ("done");
    return (0);
  }


  public int transport_start (Intent intent) {                          // Called only by hu_act:onCreate()
    hu_uti.logd ("");// intent: " + intent);

    if (m_usb_connected) {
      hu_uti.loge ("Already m_usb_connected: " + m_usb_connected);
      return (-1);
    }
    hu_uti.logd ("calling usb_start()");
    usb_start (intent);

    if (! m_usb_connected) {
      hu_uti.logd ("done w/ USB NOT connected");
      return (-1);
    }
    hu_uti.logd ("done w/ USB connected");
    return (0);
  }

  public int jni_start () {

    byte [] cmd_buf = {121, -127, 2};                                   // Start Request w/ m_ep_in_addr, m_ep_out_addr
    cmd_buf [1] = (byte) m_ep_in_addr;
    cmd_buf [2] = (byte) m_ep_out_addr;
    int ret = aa_cmd_send (cmd_buf.length, cmd_buf, 0, null);           // Send: Start USB & AA

    if (ret == 0) {                                                     // If started OK...
      hu_uti.logd ("aa_cmd_send ret:" + ret);
      m_vid_thread = new vid_thread ();                                   
      m_vid_thread.start ();                                            // Create and start video thread
    }
    else
      hu_uti.loge ("aa_cmd_send ret:" + ret);
    return (ret);
  }

  private int byebye_send () {
    hu_uti.logd ("");
    byte [] cmd_buf = {0, 0x0b, 0, 4, 0, 15, 0x08, 0};                  // Byebye Request
    int ret = aa_cmd_send (cmd_buf.length, cmd_buf, 0, null);           // Send
    hu_uti.ms_sleep (100);                                              // Wait a bit for response
    return (ret);
  }



  int aa_cmd_send (int cmd_len, byte [] cmd_buf, int res_len, byte [] res_buf) {    // Main processing loop

    //synchronized (this) {

    if (cmd_buf == null || cmd_len <= 0) {
      cmd_buf = new byte [256];// {0};
      cmd_len = 0;//cmd_buf.length;
    }
    if (res_buf == null || res_len <= 0) {
      res_buf = new byte [65536 * 16];  // Seen up to 151K so far; leave at 1 megabyte
      res_len = res_buf.length;
    }

    int ret = native_aa_cmd (cmd_len, cmd_buf, res_len, res_buf);                   // Send a command (or null command)

    if (ret > 0) {                                                                  // If video returned...
      ByteBuffer bb = ByteBuffer.wrap (res_buf);
      bb.limit (ret);
      bb.position (0);

      video_decode (bb);                                                            // Decode video
    }

    return (ret);
    //}
  }



  long last_move_ms = 0;

  int len_touch = 0;
  boolean new_touch = false;
  byte [] ba_touch = null;

  public void aa_touch (byte action, int x, int y) {                    // 70 per second = 15 ms each

    int err_ctr = 0;
    while (new_touch) {
      if (err_ctr ++ % 50 == 0)
        hu_uti.logd ("Waiting for new_touch = false");
      if (err_ctr > 200) {
        hu_uti.loge ("Timeout waiting for new_touch = false");
        return;
      }
      hu_uti.ms_sleep (1);
    }

    synchronized (this) {
  /*  if (action == 2) {
        long this_move_ms = hu_uti.tmr_ms_get ();
        if (last_move_ms + 50 > this_move_ms) {
          hu_uti.loge ("Dropping motion this_move_ms: " + this_move_ms + "  last_move_ms: " + last_move_ms);
          return;
        }
        last_move_ms = this_move_ms;
      }*/

      ba_touch = new byte [] {0x02, 0x0b, 0x03, 0x00, -128, 0x01,   0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0, 0, 0,    0x1a, 0x0e,   0x0a, 0x08,   0x08, 0x2e, 0,   0x10, 0x2b, 0,   0x18, 0x00,   0x10, 0x00,   0x18, 0x00};

      //long ts = android.os.SystemClock.elapsedRealtimeNanos ();   // Timestamp in nanoseconds   Android 4.2+ only
      long ts = android.os.SystemClock.elapsedRealtime () * 1000000L;   // Timestamp in nanoseconds

    //byte [] ba = {0x02, 0x0b, 0x03, 0x00, -128, 0x01,
                  //0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0, 0, 0,    0x1a, 0x0e,   0x0a, 0x08,   0x08, 0x2e, 0,   0x10, 0x2b, 0,   0x18, 0x00,   0x10, 0x00,   0x18, 0x00};
                    //0   1 - 9                                           10                          X:     15, 16    Y:     18, 19                                        25

      int siz_arr = 0;

      int idx = 1+6 + hu_uti.varint_encode (ts, ba_touch,  1+6);          // Encode timestamp

      ba_touch [idx ++] = 0x1a;                                           // Value 3 array
      int size1_idx = idx;                                                // Save size1_idx
      ba_touch [idx ++] = 0x0a;                                           // Default size 10
//
        ba_touch [idx ++] = 0x0a;                                           // Contents = 1 array
        int size2_idx = idx;                                                // Save size2_idx
        ba_touch [idx ++] = 0x04;                                           // Default size 4
  //
          ba_touch [idx ++] = 0x08;                                             // Value 1
          siz_arr = hu_uti.varint_encode ( x, ba_touch, idx);                 // Encode X
          idx += siz_arr;
          ba_touch [size1_idx] += siz_arr;                                    // Adjust array sizes for X
          ba_touch [size2_idx] += siz_arr;

          ba_touch [idx ++] = 0x10;                                             // Value 2
          siz_arr = hu_uti.varint_encode ( y, ba_touch, idx);                 // Encode Y
          idx += siz_arr;
          ba_touch [size1_idx] += siz_arr;                                    // Adjust array sizes for Y
          ba_touch [size2_idx] += siz_arr;

          ba_touch [idx ++] = 0x18;                                             // Value 3
          ba_touch [idx ++] = 0x00;                                           // Encode Z ?
  //
        ba_touch [idx ++] = 0x10;
        ba_touch [idx ++] = 0x00;

        ba_touch [idx ++] = 0x18;
        ba_touch [idx ++] = action;
//
      int ret = 0;
      if (! touch_sync) {
        ret = aa_cmd_send (idx, ba_touch, 0, null);
        return;
      }

      len_touch = idx;
      new_touch = true;
    }

  }


  public void usb_start (Intent intent) {                               // Called only by transport_start()
    String action = "";
    if (intent != null)
      action = intent.getAction ();
    hu_uti.logd ("intent: " + intent);// + "  action: " + action);

    IntentFilter filter = new IntentFilter ();
    filter.addAction (UsbManager.ACTION_USB_DEVICE_ATTACHED);
    filter.addAction (UsbManager.ACTION_USB_DEVICE_DETACHED);
    filter.addAction (hu_uti.str_usb_perm);
    m_usb_receiver = new usb_receiver ();                                    // Register BroadcastReceiver for USB attached/detached
    Intent first_sticky_intent = m_context.registerReceiver (m_usb_receiver, filter);

    if (m_hu_act.ls_debug) m_hu_act.ld ("first_sticky_intent: " + first_sticky_intent);


    if (action.equals (UsbManager.ACTION_USB_DEVICE_ATTACHED)) {        // If we were launched by a USB connection event...
//      m_autolaunched = true;
      UsbDevice device = intent.<UsbDevice>getParcelableExtra (UsbManager.EXTRA_DEVICE);
      if (m_hu_act.ls_debug) m_hu_act.ld ("Launched by USB connection event device: " + device);
      if (device != null) {
        //!!!! Let BCR handle !!!!        usb_attach_handler (device, true);                              // Take action if our device, auto-launched


        boolean perm = m_usb_mgr.hasPermission (device);
        if (m_hu_act.ls_debug) m_hu_act.ld ("usb_start autolaunched  perm: " + perm);// device: " + device);
/*

      if (! hu_uti.file_get ("/data/data/ca.yyx.hu/files/first")) {
        hu_uti.file_create ("/data/data/ca.yyx.hu/files/first");
        //usb_attach_handler (device, true);
        //return;
        permission_request (device);                                      // Request permission
        return;                                                           // Wait for permission
      }
*/

//        if (! perm)
//          permission_request (device);                                      // Request permission
      //return;                                                           // Wait for permission

      }
    }
    else {                                                                                        // Else if we were launched manually, look through the USB device list...
      Map<String, UsbDevice> device_list = m_usb_mgr.getDeviceList ();
      //if (m_hu_act.ls_debug) m_hu_act.ld ("device_list: " + device_list);
      if (device_list != null) if (m_hu_act.ls_debug) m_hu_act.ld ("device_list.size(): " + device_list.size ());
      if (device_list == null) if (m_hu_act.ls_debug) m_hu_act.le ("device_list == null");

      if (device_list != null) {
        for (UsbDevice device : device_list.values ()) {

          String dev_name = device.getDeviceName ();                    // mName=/dev/bus/usb/003/007
          int dev_class   = device.getDeviceClass ();                   // mClass=255
          int dev_sclass  = device.getDeviceSubclass ();                // mSublass=255
          int dev_dev_id  = device.getDeviceId ();                      // mId=2
          int dev_proto   = device.getDeviceProtocol ();                // mProtocol=0
          int dev_vend_id = device.getVendorId ();                      // mVendorId=2996               HTC
          int dev_prod_id = device.getProductId ();                     // mProductId=1562              OneM8
          if (m_hu_act.ls_debug) m_hu_act.ld ("dev_name: " + dev_name + "  dev_class: " + dev_class + "  dev_sclass: " + dev_sclass + "  dev_dev_id: " + dev_dev_id + "  dev_proto: " + dev_proto + "  dev_vend_id: " + dev_vend_id + "  dev_prod_id: " + dev_prod_id);

          String dev_man  = "";
          String dev_prod = "";
          String dev_ser  = "";
          if (hu_uti.android_version >= 21) {                           // Android 5.0+ only
            dev_man  = device.getManufacturerName ();                   // mManufacturerName=HTC
            dev_prod = device.getProductName      ();                   // mProductName=Android Phone
            dev_ser  = device.getSerialNumber     ();                   // mSerialNumber=FA46RWM22264
            if (dev_man == null)
              dev_man = "";
            else
              dev_man = dev_man.toUpperCase (Locale.getDefault ());
            if (dev_prod == null)
              dev_prod = "";
            else
              dev_prod = dev_prod.toUpperCase (Locale.getDefault ());
            if (dev_ser == null)
              dev_ser = "";
            else
              dev_ser = dev_ser.toUpperCase (Locale.getDefault ());

            if (m_hu_act.ls_debug) m_hu_act.ld ("dev_man: " + dev_man + "  dev_prod: " + dev_prod + "  dev_ser: " + dev_ser);
          }

          if (dev_class == 0 && dev_sclass == 0 && dev_proto == 0) {
            if (is_android (dev_vend_id) || dev_man.startsWith ("HTC") || dev_man.startsWith ("MOTO") || dev_man.startsWith ("SONY") || dev_man.startsWith ("SAM") || dev_man.startsWith ("ASUS") || dev_man.startsWith ("SONY") || dev_man.startsWith ("ANDROID") || dev_man.startsWith ("GOOG")
                || dev_prod.startsWith ("NEXUS") || dev_prod.startsWith ("ANDROID") || dev_prod.startsWith ("GOOG") || dev_prod.startsWith ("XPERIA") || dev_prod.startsWith ("GT-") || dev_prod.startsWith ("ONE") || dev_prod.startsWith ("MOTO") || dev_prod.startsWith ("LT"))
              usb_attach_handler (device, false);                       // Take action if our device, manual launch
          }
        }
      }

    }

  }

  public void usb_stop () {                                             // Called only by transport_stop()

    hu_uti.logd ("m_usb_receiver: " + m_usb_receiver);

    if (m_usb_receiver != null)
      m_context.unregisterReceiver (m_usb_receiver);
    m_usb_receiver = null;

    usb_disconnect ();                                                  // Disconnect
  }

  private class usb_receiver extends BroadcastReceiver {
    @Override
    public void onReceive (Context context, Intent intent) {
      UsbDevice device = intent.<UsbDevice>getParcelableExtra (UsbManager.EXTRA_DEVICE);
      if (m_hu_act.ls_debug) m_hu_act.ld ("USB BCR intent: " + intent);

      if (device != null) {
        String action = intent.getAction ();

        if (action.equals (UsbManager.ACTION_USB_DEVICE_DETACHED)) {    // If detach...
          usb_detach_handler (device);
        }

        else if (action.equals (UsbManager.ACTION_USB_DEVICE_ATTACHED)){// If attach...
          usb_attach_handler (device, false);                           // Take action if our device
        }

        else if (action.equals (hu_uti.str_usb_perm)) {                               // If permission
          if (intent.getBooleanExtra (UsbManager.EXTRA_PERMISSION_GRANTED, false)) {  // If granted...
            if (m_hu_act.ls_debug) m_hu_act.ld ("USB BCR permission granted");
            usb_attach_handler (device, false);                                       // Take action if our device
          }
          else {
            if (m_hu_act.ls_debug) m_hu_act.le ("USB BCR permission denied");
          }
        }

      }
    }
  }


    // Called by usb_start() (at regular startup or auto-start if enabled)
    // Called by usb_receiver() if device attached while app is running, or when permission granted

  private void usb_attach_handler (UsbDevice device, boolean xyz) {                  // Take action if our device    Called only by:
                                                                                                                      // usb_start()    on autolaunch or device find, and...
                                                                                                                      // usb_receiver() on USB grant permission or USB attach
    if (m_hu_act.ls_debug) m_hu_act.ld ("usb_attach_handler m_usb_connected: " + m_usb_connected);  // device: " + device + "  

    if (! m_usb_connected) {                                            // If not already connected...
      if (m_hu_act.ls_debug) m_hu_act.ld ("Try connect");
      usb_connect (device);                                             // Attempt to connect
    }

    if (m_usb_connected) {                                              // If connected now, or was connected already...
      if (m_hu_act.ls_debug) m_hu_act.ld ("Connected so start JNI");
      //hu_uti.ms_sleep (2000);                                         // Wait to settle
      //if (m_hu_act.ls_debug) m_hu_act.ld ("connected done sleep");

      if (hu_act.m_disable_alleged_google_dda_4_4_violation) {
        if (m_hu_act.ls_debug) m_hu_act.ld ("Success so far, terminating startup due to m_disable_alleged_google_dda_4_4_violation");
        return;                                                         // Kill it so Google has no basis to claim DDA section 4.4 violation. Android Auto won't work and this app reduced to USB test
      }


      m_hu_act.ui_video_started_set (true);                             // Enable video/disable log view
      int ret = jni_start ();
      if (m_hu_act.ls_debug) m_hu_act.ld ("jni_start() ret: " + ret);
    }
    else
      if (m_hu_act.ls_debug) m_hu_act.ld ("Not connected");
  }

    // Called by usb_receiver() if device detached while app is running (only ?)
  private void usb_detach_handler (UsbDevice device) {
    if (m_hu_act.ls_debug) m_hu_act.ld ("usb_detach_handler m_usb_connected: " + m_usb_connected);  // device: " + device + "  m_usb_device: " + m_usb_device + "  
    if (/*m_usb_connected &&*/ m_usb_device != null && device.equals (m_usb_device)) {
      if (m_hu_act.ls_debug) m_hu_act.ld ("Disconnecting our connected device");
      usb_disconnect ();                                                // Disconnect

      Toast.makeText (m_context, "DISCONNECTED !!!", Toast.LENGTH_LONG).show ();

      hu_uti.ms_sleep (2000);                                           // Wait a bit
      android.os.Process.killProcess (android.os.Process.myPid ());

      return;
    }
    if (m_hu_act.ls_debug) m_hu_act.ld ("Not our device so ignore disconnect");
  }

  // "Android", "Android Open Automotive Protocol", "Description", "VersionName", "https://developer.android.com/auto/index.html", "62skidoo"
  // "Android", "Android Auto", "Description", "VersionName", "https://developer.android.com/auto/index.html", "62skidoo"

  private void usb_disconnect () {
    if (m_hu_act.ls_debug) m_hu_act.ld ("usb_disconnect");// m_usb_device: " + m_usb_device);
    m_usb_connected = false;
    m_usb_device = null;

    usb_close ();

    //transport_stop ();
  }

  private void usb_close () {
    if (m_hu_act.ls_debug) m_hu_act.ld ("usb_close");
    m_usb_ep_in            = null;
    m_usb_ep_out           = null;

    if (m_usb_dev_conn != null) {
      boolean bret = false;
      if (m_usb_iface != null) {
        bret = m_usb_dev_conn.releaseInterface (m_usb_iface);
      }
      if (bret)
        if (m_hu_act.ls_debug) m_hu_act.ld ("OK releaseInterface()");
      else
        if (m_hu_act.ls_debug) m_hu_act.le ("Error releaseInterface()");

      m_usb_dev_conn.close ();
    }
    m_usb_dev_conn = null;
    m_usb_iface = null;
  }

  private int usb_io (UsbEndpoint ep, byte [] buf, int len, int tmo) {
    if (m_usb_dev_conn == null) {
      if (m_hu_act.ls_debug) m_hu_act.le ("m_usb_dev_conn: " + m_usb_dev_conn);
      return (-1);
    }
    int ret = m_usb_dev_conn.bulkTransfer (ep, buf, len, tmo);      // This form 12+, IDX form 4.3+ only
    if (ret < 0) {
      if (m_hu_act.ls_debug) m_hu_act.le ("Bulk transfer failed ret: " + ret);
    }
    if (m_hu_act.ls_debug) m_hu_act.ld ("Bulk transfer ret: " + ret);
    return (ret);
  }

  private int usb_read (byte [] buf, int len) {
    int ret = usb_io (m_usb_ep_in,  buf, len, 3000);                   // -1 or 0 = wait forever
    return (ret);
  }

  private int usb_write (byte [] buf, int len) {
    int ret = usb_io (m_usb_ep_out, buf, len, 1000);                   // 1 second timeout
    return (ret);
  }

  private static final int USB_VID_GOO  = 0x18D1;   // 6353   Nexus or ACC mode, see PID to distinguish
  private static final int USB_VID_HTC  = 0x0bb4;   // 2996
  private static final int USB_VID_MOT  = 0x22b8;   // 8888
  private static final int USB_VID_SAM  = 0x04e8;   // 1256
  private static final int USB_VID_O1A  = 0xfff6;   // 65526    Samsung ?
  private static final int USB_VID_SON  = 0x0fce;   // 4046
  private static final int USB_VID_LGE  = 0xfff5;   // 65525


  private boolean is_android (int vend_id) {  // Nexus also use VID_GOO
    if (vend_id == USB_VID_GOO || vend_id == USB_VID_HTC || vend_id == USB_VID_SAM || vend_id == USB_VID_SON || vend_id == USB_VID_MOT || vend_id == USB_VID_LGE || vend_id == USB_VID_O1A)
      return (true);
    return (false);
  }
/*
06-15 00:16:38.838 D/                       d( 4777): Launched by USB connection event device: UsbDevice[mName=/dev/bus/usb/001/005,mVendorId=2996,mProductId=3877,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=HTC,mProductName=Android Phone,mSerialNumber=FA46RWM22264,mConfigurations=[
06-15 00:16:38.838 D/                       d( 4777): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=250,mInterfaces=[
06-15 00:16:38.838 D/                       d( 4777): UsbInterface[mId=0,mAlternateSetting=0,mName=MTP,mClass=6,mSubclass=1,mProtocol=1,mEndpoints=[
06-15 00:16:38.838 D/                       d( 4777): UsbEndpoint[mAddress=129,mAttributes=2,mMaxPacketSize=512,mInterval=0]
06-15 00:16:38.838 D/                       d( 4777): UsbEndpoint[mAddress=1,mAttributes=2,mMaxPacketSize=512,mInterval=0]
06-15 00:16:38.838 D/                       d( 4777): UsbEndpoint[mAddress=130,mAttributes=3,mMaxPacketSize=28,mInterval=6]]
06-15 00:16:38.838 D/                       d( 4777): UsbInterface[mId=1,mAlternateSetting=0,mName=null,mClass=255,mSubclass=255,mProtocol=0,mEndpoints=[
06-15 00:16:38.838 D/                       d( 4777): UsbEndpoint[mAddress=131,mAttributes=2,mMaxPacketSize=512,mInterval=0]
06-15 00:16:38.838 D/                       d( 4777): UsbEndpoint[mAddress=2,mAttributes=2,mMaxPacketSize=512,mInterval=1]]]]
06-15 00:16:38.838 D/                       d( 4777): usb_attach_handler m_usb_connected: false
06-15 00:16:38.838 D/                       d( 4777): Try connect
06-15 00:16:38.838 D/                       d( 4777): usb_connect
06-15 00:16:38.838 D/                       d( 4777): Have Implicit USB Permission m_autolaunched: true  perm: true
06-15 00:16:38.838 D/                       d( 4777): Not connected
*/
    //a cat /system/etc/permissions/android.hardware.usb.host.xml|el
    //<permissions>    <feature name="android.hardware.usb.host" />   </permissions>
  //private boolean disable_permission_request = true;
  private void permission_request (UsbDevice device) {
    //if (disable_permission_request)
    //  return;
    if (m_hu_act.ls_debug) m_hu_act.ld ("Request USB Permission");
    Intent intent = new Intent (hu_uti.str_usb_perm);//ACTION_USB_DEVICE_PERMISSION);
    intent.setPackage (m_context.getPackageName ());
    PendingIntent pendingIntent = PendingIntent.getBroadcast (m_context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
    m_usb_mgr.requestPermission (device, pendingIntent);              // Request permission. Come back later if we get it.
  }

  private static final int USB_PID_OAP_NUL = 0x2D00; // 11520   Product ID to use when in accessory mode without ADB
  private static final int USB_PID_OAP_ADB = 0x2D01; // 11521   Product ID to use when in accessory mode and adb is enabled

  // Indexes for strings sent by the host via OAP_SEND_STRING:
  private static final int OAP_STR_MANUFACTURER = 0;
  private static final int OAP_STR_MODEL        = 1;
  //private static final int OAP_STR_DESCRIPTION  = 2;
  //private static final int OAP_STR_VERSION      = 3;
  //private static final int OAP_STR_URI          = 4;
  //private static final int OAP_STR_SERIAL       = 5;
                                                                        // OAP Control requests:
  private static final int OAP_GET_PROTOCOL = 51;
  private static final int OAP_SEND_STRING  = 52;
  private static final int OAP_START        = 53;

  private UsbDeviceConnection m_usb_dev_conn  = null;
  private UsbEndpoint         m_usb_ep_in     = null;                   // Bulk endpoints.
  private UsbEndpoint         m_usb_ep_out    = null;
  private UsbInterface        m_usb_iface     = null;

  private int m_ep_in_addr   = -1;                                      // 129
  private int m_ep_out_addr  = -1;                                      //   2

  private void usb_connect (UsbDevice device) {                         // Attempt to connect     Called only by usb_attach_handler()
    boolean perm = m_usb_mgr.hasPermission (device);
    if (m_hu_act.ls_debug) m_hu_act.ld ("usb_connect m_autolaunched: " + m_autolaunched + "  perm: " + perm);// device: " + device);

    m_usb_dev_conn = null;

    if (m_autolaunched) {
      if (m_hu_act.ls_debug) m_hu_act.ld ("Have Implicit USB Permission");
/*
      if (! hu_uti.file_get ("first")) {
        hu_uti.file_create ("first");
        permission_request (device);                                      // Request permission
        return;                                                           // Wait for permission
      }
*/
      //permission_request (device);                                      // Request permission
      //return;                                                           // Wait for permission
/*
      // hasPermission() lies so check via Throwable or null
      try {
        m_usb_dev_conn = m_usb_mgr.openDevice (device);                 // Open device for connection
      }
      catch (Throwable e) {
        if (m_hu_act.ls_debug) m_hu_act.le ("m_autolaunched Throwable: " + e);   // java.lang.IllegalArgumentException: device /dev/bus/usb/001/019 does not exist or is restricted
        m_usb_dev_conn = null;
      }
      if (m_usb_dev_conn == null) {
        if (m_hu_act.ls_debug) m_hu_act.ld ("m_autolaunched m_usb_dev_conn == null");
        permission_request (device);                                    // Request permission
        return;                                                         // Wait for permission
      }
      if (m_hu_act.ls_debug) m_hu_act.ld ("m_autolaunched openDevice() OK");
*/
    }

    else if (perm) {                                                    // If we have permission to access the USB device...
      if (m_hu_act.ls_debug) m_hu_act.ld ("Have USB Permission");
    }
    else {                                                              // Else if we don't have permission to access the USB device...
      permission_request (device);                                      // Request permission
      return;                                                           // Wait for permission
    }

    if (usb_open (device) < 0) {
      usb_disconnect ();                                                // Ensure state is disconnected
      return;
    }

    int dev_vend_id = device.getVendorId ();                            // mVendorId=2996               HTC
    int dev_prod_id = device.getProductId ();                           // mProductId=1562              OneM8
    if (m_hu_act.ls_debug) m_hu_act.ld ("dev_vend_id: " + dev_vend_id + "  dev_prod_id: " + dev_prod_id);

    if (dev_vend_id == USB_VID_GOO && (dev_prod_id == USB_PID_OAP_NUL || dev_prod_id == USB_PID_OAP_ADB)) {  // If in accessory mode... !! VID_GOO also used by Nexus and NO2/CM11 in non-Acc mode
      int ret = acc_mode_connect ();                                              // Connect
      if (ret < 0) {
        usb_disconnect ();                                                // Ensure state is disconnected
      }
      else {
        m_usb_connected = true;
        m_usb_device = device;
      }
      return;                                                           // Done
    }
                                                                        // Else if not in accessory mode...
    acc_mode_switch ();                                                 // Do accessory negotiation and attempt to switch to accessory mode
    usb_disconnect ();                                                  // Ensure state is disconnected
  }

  private int usb_open (UsbDevice device) {
        // Try to connect:
    try {
      if (m_usb_dev_conn == null)
        m_usb_dev_conn = m_usb_mgr.openDevice (device);                 // Open device for connection
    }
    catch (Throwable e) {
      if (m_hu_act.ls_debug) m_hu_act.le ("Throwable: " + e);           // java.lang.IllegalArgumentException: device /dev/bus/usb/001/019 does not exist or is restricted
    }

    if (m_usb_dev_conn == null) {
      if (m_hu_act.ls_debug) m_hu_act.le ("Could not obtain device m_usb_dev_conn");
      return (-1);                                                      // Done error
    }
    if (m_hu_act.ls_debug) m_hu_act.ld ("Device m_usb_dev_conn: " + m_usb_dev_conn);


    try {
      int iface_cnt = device.getInterfaceCount ();
      if (iface_cnt <= 0) {
        if (m_hu_act.ls_debug) m_hu_act.le ("iface_cnt: " + iface_cnt);
        return (-1);                                                    // Done error
      }
      if (m_hu_act.ls_debug) m_hu_act.ld ("iface_cnt: " + iface_cnt);
      m_usb_iface = device.getInterface (0);                            // java.lang.ArrayIndexOutOfBoundsException: length=0; index=0
      UsbEndpoint controlEndpoint = m_usb_iface.getEndpoint (0);
      if (! m_usb_dev_conn.claimInterface (m_usb_iface, true)) {        // Claim interface, if error...   true = take from kernel
        if (m_hu_act.ls_debug) m_hu_act.le ("Error claiming interface");
        return (-1);
      }
      if (m_hu_act.ls_debug) m_hu_act.ld ("Success claiming interface");
    }
    catch (Throwable e) {
      if (m_hu_act.ls_debug) m_hu_act.le ("Throwable: " + e);           // Nexus 7 2013:    Throwable: java.lang.ArrayIndexOutOfBoundsException: length=0; index=0
      return (-1);                                                      // Done error
    }
    return (0);                                                         // Done success
  }

  private int acc_mode_connect () {
    if (m_hu_act.ls_debug) m_hu_act.ld ("In acc so connect...");

    int acc_ver = usb_acc_version_get (m_usb_dev_conn);
    if (acc_ver < 1) {
      if (m_hu_act.ls_debug) m_hu_act.le ("No support acc");
      return (-1);                                                      // Done error
    }
    if (m_hu_act.ls_debug) m_hu_act.ld ("acc_ver: " + acc_ver);

    m_usb_ep_in  = null;                                                // Setup bulk endpoints.
    m_usb_ep_out = null;
    m_ep_in_addr  = -1;     // 129
    m_ep_out_addr = -1;     // 2
    
    for (int i = 0; i < m_usb_iface.getEndpointCount (); i ++) {        // For all USB endpoints...
      UsbEndpoint ep = m_usb_iface.getEndpoint (i);
      if (ep.getDirection () == UsbConstants.USB_DIR_IN) {              // If IN
        if (m_usb_ep_in == null) {                                      // If Bulk In not set yet...
          m_ep_in_addr = ep.getAddress ();
          if (m_hu_act.ls_debug) m_hu_act.ld (String.format ("Bulk IN m_ep_in_addr: %d  %d", m_ep_in_addr, i));
          m_usb_ep_in = ep;                                             // Set Bulk In
        }
      }
      else {                                                            // Else if OUT...
        if (m_usb_ep_out == null) {                                     // If Bulk Out not set yet...
          m_ep_out_addr = ep.getAddress ();
          if (m_hu_act.ls_debug) m_hu_act.ld (String.format ("Bulk OUT m_ep_out_addr: %d  %d", m_ep_out_addr, i));
          m_usb_ep_out = ep;                                            // Set Bulk Out
        }
      }
    }
    if (m_usb_ep_in == null || m_usb_ep_out == null) {
      if (m_hu_act.ls_debug) m_hu_act.le ("Unable to find bulk endpoints");
      return (-1);                                                      // Done error
    }

    if (m_hu_act.ls_debug) m_hu_act.ld ("Connected have EPs");
    return (0);                                                         // Done success
  }

  private void acc_mode_switch () {                                     // Do accessory negotiation and attempt to switch to accessory mode
    if (m_hu_act.ls_debug) m_hu_act.ld ("Attempt acc");

    int acc_ver = usb_acc_version_get (m_usb_dev_conn);                 // Get protocol version
    if (acc_ver < 1) {                                                  // If error or version too low...
      if (m_hu_act.ls_debug) m_hu_act.le ("No support acc");
      return;
    }
    if (m_hu_act.ls_debug) m_hu_act.ld ("acc_ver: " + acc_ver);

    usb_acc_strings_send ();                                            // Send identifying strings.

    // Send start. The device should re-enumerate as an accessory.
    if (m_hu_act.ls_debug) m_hu_act.ld ("Sending acc start");
    int len = m_usb_dev_conn.controlTransfer (UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, OAP_START, 0, 0, null, 0, 10000);
    if (len != 0) {
      if (m_hu_act.ls_debug) m_hu_act.le ("Error acc start");
    }
    else {
      if (m_hu_act.ls_debug) m_hu_act.ld ("OK acc start. Wait to re-enumerate...");
    }
  }

  private int usb_acc_version_get (UsbDeviceConnection connection) {    // Get OAP / ACC protocol version
    byte buffer  [] = new byte  [2];
    int len = connection.controlTransfer (UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR, OAP_GET_PROTOCOL, 0, 0, buffer, 2, 10000);
    if (len != 2) {
      if (m_hu_act.ls_debug) m_hu_act.le ("Error controlTransfer len: " + len);
      return -1;
    }
    int oap_version = (buffer [1] << 8) | buffer  [0];
    hu_uti.logd ("Success controlTransfer len: " + len + "  oap_version: " + oap_version);
    return (oap_version);
  }

  private void usb_acc_strings_send () {                                // Send identifying strings.
    usb_acc_string_send (m_usb_dev_conn, OAP_STR_MANUFACTURER,hu_uti.str_MANUFACTURER);
    usb_acc_string_send (m_usb_dev_conn, OAP_STR_MODEL,       hu_uti.str_MODEL);
    //usb_acc_string_send (m_usb_dev_conn, OAP_STR_DESCRIPTION, hu_uti.str_DESCRIPTION);
    //usb_acc_string_send (m_usb_dev_conn, OAP_STR_VERSION,     hu_uti.str_VERSION);
    //usb_acc_string_send (m_usb_dev_conn, OAP_STR_URI,         hu_uti.str_URI);
    //usb_acc_string_send (m_usb_dev_conn, OAP_STR_SERIAL,      hu_uti.str_SERIAL);
  }
  private void usb_acc_string_send (UsbDeviceConnection connection, int index, String string) {
    byte  [] buffer = (string + "\0").getBytes ();
    int len = connection.controlTransfer (UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, OAP_SEND_STRING, 0, index, buffer, buffer.length, 10000);
    if (len != buffer.length) {
      if (m_hu_act.ls_debug) m_hu_act.le ("Error controlTransfer len: " + len + "  index: " + index + "  string: \"" + string + "\"");
    }
    else {
      hu_uti.logd ("Success controlTransfer len: " + len + "  index: " + index + "  string: \"" + string + "\"");
    }
  }

}

