
    // Headunit app Transport: USB / Wifi

package ca.yyx.hu;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;

public class hu_tra {

  private Context         m_context;
  private hu_act          m_hu_act;                                     // Activity for callbacks
  private tra_thread      m_tra_thread;                                 // Transport Thread

  private UsbManager      m_usb_mgr;
  private usb_receiver    m_usb_receiver;
  private boolean         m_usb_connected;
  private UsbDevice       m_usb_device;

  private boolean         m_autolaunched = false;

  private static final int AA_CH_CTR = 0;                               // Sync with hu_tra.java, hu_aap.h and hu_aap.c:aa_type_array[]
  private static final int AA_CH_SEN = 1;
  private static final int AA_CH_VID = 2;
  private static final int AA_CH_TOU = 3;
  public  static final int AA_CH_AUD = 4;
  public  static final int AA_CH_AU1 = 5;
  public  static final int AA_CH_AU2 = 6;
  private static final int AA_CH_MIC = 7;
  private static final int AA_CH_MAX = 7;


  public hu_tra (hu_act hu_act) {
    m_hu_act = hu_act;
    m_context = (Context) hu_act;
    m_usb_mgr = (UsbManager) m_context.getSystemService (Context.USB_SERVICE);
    m_autolaunched = false;
  }

    // Native API:

  static {
    System.loadLibrary ("hu_jni");
  }

  private static native int native_aa_cmd         (int cmd_len, byte [] cmd_buf, int res_len, byte [] res_buf);


/*
? External APIs used from hu_act:
  public void media_decode (ByteBuffer content);                        // Decode audio or H264 video content
  mic_audio_read
  presets_update (usb_list_name);
  ui_video_started_set (true);                             // Enable video/disable log view
  mic_audio_stop ();
  out_audio_stop (int chan);
  sys_ui_hide ();
  Context.finish ();

External Data used from hu_act:
m_mic_bufsize
disable_video_started_set
PRESET_LEN_USB






7 Public APIs provided for hu_act:

  public      hu_tra          (hu_act hu_act);                          // Constructor
  public int  jni_aap_start   ();                                       // Start JNI Android Auto Protocol and Main Thread. Called only by usb_attach_handler(), usb_force() & hu_act.wifi_long_start()
  public void touch_send      (byte action, int x, int y);              // Touch event send. Called only by hu_act:touch_send()
  public int  transport_start (Intent intent);                          // USB Transport Start. Called only by hu_act:onCreate()
  public void transport_stop  ();                                       // USB Transport Stop. Called only by hu_act.all_stop()
  public void usb_force       ();                                       // Called only by hu_act:preset_select_lstnr:onClick()
  public void presets_select  (int idx);                                // Called only by hu_act:preset_select_lstnr:onClick()

Internal classes:
  private final class tra_thread extends Thread
  private final class usb_receiver extends BroadcastReceiver

*/

///*

  private byte [] mic_audio_buf = new byte [hu_act.m_mic_bufsize];
  private int mic_audio_len = 0;

  private byte [] test_buf = null;
  private int     test_len = 0;
  private boolean test_rdy = false;

  public void test_send (byte [] buf, int len) {
    test_buf = buf;
    test_len = len;
    test_rdy = true;
  }  

  private boolean m_mic_active = false;
  private boolean touch_sync = true;//      // Touch sync times out within 200 ms on second touch with TCP for some reason.
  private final class tra_thread extends Thread {                       // Main Transport Thread
    private volatile boolean m_stopping = false;                        // Set true when stopping
    public tra_thread () {
      super ("tra_thread");                                             // Name thread
    }
    @Override
    public void run () {
      int ret = 0;

      while (! m_stopping) {                                            // Loop until stopping...
//hu_uti.loge ("1");
        //synchronized (this) {

          if (test_rdy && test_len > 0 && test_buf != null) {
            ret = aa_cmd_send (test_len, test_buf, 0, null);            // Send test command
            test_rdy = false;
          }


          if (touch_sync && ! m_stopping && new_touch && len_touch > 0 && ba_touch != null) {
            ret = aa_cmd_send (len_touch, ba_touch, 0, null);           // Send touch event
            ba_touch = null;
            new_touch = false;
//            hu_uti.ms_sleep (1);
            continue;
          }
        //}

//hu_uti.loge ("2");
        if (! m_stopping && ret >= 0 && m_mic_active) {                 // If Mic active...
          mic_audio_len = m_hu_act.mic_audio_read (mic_audio_buf, hu_act.m_mic_bufsize);
          if (mic_audio_len >= 64) {                                    // If we read at least 64 bytes of audio data
            byte [] ba_mic = new byte [14 + mic_audio_len];//0x02, 0x0b, 0x03, 0x00,
            ba_mic [0] = AA_CH_MIC;// Mic channel
            ba_mic [1] = 0x0b;  // Flag filled here
            ba_mic [2] = 0x00;  // 2 bytes Length filled here
            ba_mic [3] = 0x00;
            ba_mic [4] = 0x00;  // Message Type = 0 for data, OR 32774 for Stop w/mandatory 0x08 int and optional 0x10 int (senderprotocol/aq -> com.google.android.e.b.ca)
            ba_mic [5] = 0x00;

            long ts = android.os.SystemClock.elapsedRealtime ();        // ts = Timestamp (uptime) in microseconds
            int ctr = 0;
            for (ctr = 7; ctr >= 0; ctr --) {                           // Fill 8 bytes backwards
              ba_mic [6 + ctr] = (byte) (ts & 0xFF);
              ts = ts >> 8;
            }

            for (ctr = 0; ctr < mic_audio_len; ctr ++)                  // Copy mic PCM data
              ba_mic [14 + ctr] = mic_audio_buf [ctr];

            //hu_uti.hex_dump ("MIC: ", ba_mic, 64);
            ret = aa_cmd_send (14 + mic_audio_len, ba_mic, 0, null);    // Send mic audio

          }
          else if (mic_audio_len > 0)
            hu_uti.loge ("!!!!!!!");
        }
//hu_uti.loge ("3");
        if (! m_stopping && ret >= 0)
          ret = aa_cmd_send (0, null, 0, null);                         // Null message to just poll
        if (ret < 0)
          m_stopping = true;
//hu_uti.loge ("4");

//Wifi messes this up
//        if (! m_stopping)
//          hu_uti.ms_sleep (1);//3);                                   // Up to 330 frames per second

//hu_uti.loge ("5");
      }

      byebye_send ();                                                   // If m_stopping then... Byebye
    }

    public void quit () {
      m_stopping = true;                                                // Terminate thread
    }
  }

  public int jni_aap_start () {                                         // Start JNI Android Auto Protocol and Main Thread. Called only by usb_attach_handler(), usb_force() & hu_act.wifi_long_start()

    if (m_hu_act.disable_video_started_set)
      m_hu_act.ui_video_started_set (true);                             // Enable video/disable log view

    byte [] cmd_buf = {121, -127, 2};                                   // Start Request w/ m_ep_in_addr, m_ep_out_addr
    cmd_buf [1] = (byte) m_ep_in_addr;
    cmd_buf [2] = (byte) m_ep_out_addr;
    int ret = aa_cmd_send (cmd_buf.length, cmd_buf, 0, null);           // Send: Start USB & AA

    if (ret == 0) {                                                     // If started OK...
      hu_uti.logd ("aa_cmd_send ret: " + ret);
      aap_running = true;
      m_tra_thread = new tra_thread ();                                   
      m_tra_thread.start ();                                            // Create and start Transport Thread
    }
    else
      hu_uti.loge ("aa_cmd_send ret:" + ret);
    return (ret);
  }

  private int byebye_send () {                                          // Send Byebye request. Called only by transport_stop (), tra_thread:run()
    hu_uti.logd ("");
    byte [] cmd_buf = {AA_CH_CTR, 0x0b, 0, 0, 0, 0x0f, 0x08, 0};          // Byebye Request:  000b0004000f0800  00 0b 00 04 00 0f 08 00
    int ret = aa_cmd_send (cmd_buf.length, cmd_buf, 0, null);           // Send
    hu_uti.ms_sleep (100);                                              // Wait a bit for response
    return (ret);
  }

  private byte [] fixed_cmd_buf = new byte [256];
  private byte [] fixed_res_buf = new byte [65536 * 16];
                                                                        // Send AA packet/HU command/mic audio AND/OR receive video/output audio/audio notifications
  private int aa_cmd_send (int cmd_len, byte [] cmd_buf, int res_len, byte [] res_buf) {
    //synchronized (this) {

    if (cmd_buf == null || cmd_len <= 0) {
      cmd_buf = fixed_cmd_buf;//new byte [256];// {0};                                  // Allocate fake buffer to avoid problems
      cmd_len = 0;//cmd_buf.length;
    }
    if (res_buf == null || res_len <= 0) {
      res_buf = fixed_res_buf;//new byte [65536 * 16];  // Seen up to 151K so far; leave at 1 megabyte
      res_len = res_buf.length;
    }

    int ret = native_aa_cmd (cmd_len, cmd_buf, res_len, res_buf);       // Send a command (or null command)

    if (ret == 1) {                                                     // If mic stop...
      hu_uti.logd ("Microphone Stop");
      m_mic_active = false;
      m_hu_act.mic_audio_stop ();
      return (0);
    }
    else if (ret == 2) {                                                // Else if mic start...
      hu_uti.logd ("Microphone Start");
      m_mic_active = true;
      return (0);
    }
    else if (ret == 3) {                                                // Else if audio stop...
      hu_uti.logd ("Audio Stop");
      m_hu_act.out_audio_stop (AA_CH_AUD);
      return (0);
    }
    else if (ret == 4) {                                                // Else if audio1 stop...
      hu_uti.logd ("Audio1 Stop");
      m_hu_act.out_audio_stop (AA_CH_AU1);
      return (0);
    }
    else if (ret == 5) {                                                // Else if audio2 stop...
      hu_uti.logd ("Audio2 Stop");
      m_hu_act.out_audio_stop (AA_CH_AU2);
      return (0);
    }
    else if (ret > 0) {                                                 // Else if audio or video returned...
      ByteBuffer bb = ByteBuffer.wrap (res_buf);
      bb.limit (ret);
      bb.position (0);
      if (m_hu_act != null)
        m_hu_act.media_decode (bb);                                     // Decode audio or H264 video content
    }
    return (ret);
    //}
  }



  private long last_move_ms = 0;
  private int len_touch = 0;
  private boolean new_touch = false;
  private byte [] ba_touch = null;

  public void touch_send (byte action, int x, int y) {                  // Touch event send. Called only by hu_act:touch_send()

    if (! aap_running)
      return;
                                                                        // 70 per second = 15 ms each

    int err_ctr = 0;
    while (new_touch) {                                                 // While previous touch not yet processed...
      if (err_ctr ++ % 5 == 0)
        hu_uti.logd ("Waiting for new_touch = false");
      if (err_ctr > 20) {
        hu_uti.loge ("Timeout waiting for new_touch = false");
      boolean touch_timeout_force = true;
      if (touch_timeout_force)
        new_touch = false;
      else
        return;
      }
      //hu_uti.loge ("BEFORE hu_uti.ms_sleep (50);");
      hu_uti.ms_sleep (50);                                             // Wait a bit
      //hu_uti.loge ("AFTER  hu_uti.ms_sleep (50);");
    }

    //synchronized (this) {
  /*  if (action == 2) {
        long this_move_ms = hu_uti.tmr_ms_get ();
        if (last_move_ms + 50 > this_move_ms) {
          hu_uti.loge ("Dropping motion this_move_ms: " + this_move_ms + "  last_move_ms: " + last_move_ms);
          return;
        }
        last_move_ms = this_move_ms;
      }*/


      ba_touch = new byte [] {AA_CH_TOU, 0x0b, 0x00, 0x00, -128, 0x01,   0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0, 0, 0,    0x1a, 0x0e,   0x0a, 0x08,   0x08, 0x2e, 0,   0x10, 0x2b, 0,   0x18, 0x00,   0x10, 0x00,   0x18, 0x00};

      //long ts = android.os.SystemClock.elapsedRealtimeNanos ();   // Timestamp in nanoseconds   Android 4.2+ only
      long ts = android.os.SystemClock.elapsedRealtime () * 1000000L;   // Timestamp in nanoseconds = microseconds x 1,000,000

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
      if (! touch_sync) {                                               // If allow sending from different thread
        ret = aa_cmd_send (idx, ba_touch, 0, null);                     // Send directly
        return;
      }

      len_touch = idx;
      new_touch = true;
    //}

  }


    // USB:

  public int transport_start (Intent intent) {                          // USB Transport Start. Called only by hu_act:onCreate()
    int ret = 0;
    hu_uti.logd ("");
    String action = "";
    if (intent != null)
      action = intent.getAction ();
    hu_uti.logd ("intent: " + intent);// + "  action: " + action);

    IntentFilter filter = new IntentFilter ();
    filter.addAction (UsbManager.ACTION_USB_DEVICE_ATTACHED);
    filter.addAction (UsbManager.ACTION_USB_DEVICE_DETACHED);
    filter.addAction (hu_uti.str_usb_perm);                             // Our App specific Intent for permission request
    m_usb_receiver = new usb_receiver ();                               // Register BroadcastReceiver for USB attached/detached
    Intent first_sticky_intent = m_context.registerReceiver (m_usb_receiver, filter);
    hu_uti.logd ("first_sticky_intent: " + first_sticky_intent);

    if (action != null &&
          action.equals (UsbManager.ACTION_USB_DEVICE_ATTACHED)) {      // If launched by a USB connection event... (Do nothing, let BCR handle)
      //m_autolaunched = true;
      UsbDevice device = intent.<UsbDevice>getParcelableExtra (UsbManager.EXTRA_DEVICE);
      hu_uti.logd ("Launched by USB connection event device: " + device);
    }
    else {                                                              // Else if we were launched manually, look through the USB device list...
      Map<String, UsbDevice> device_list = null;
      try {
        device_list = m_usb_mgr.getDeviceList ();                       // Emulator:  java.lang.NullPointerException: Attempt to invoke interface method 'void android.hardware.usb.IUsbManager.getDeviceList(android.os.Bundle)' on a null object reference
      }
      catch (Throwable e) {
        hu_uti.loge ("getDeviceList: " + e);                            // java.lang.IllegalArgumentException: device /dev/bus/usb/001/019 does not exist or is restricted
      }

      if (device_list == null) {
        hu_uti.loge ("device_list == null");
        return (0);
      }

      ret = device_list.size ();
      hu_uti.logd ("device_list.size() ret: " + ret);

      if (device_list != null && device_list.size () <= 0) {
        //wifi_init
      }
      else if (device_list != null && device_list.size () > 0) {
        for (UsbDevice device : device_list.values ()) {
/*
          if (dev_class == 0 && dev_sclass == 0 && dev_proto == 0) {
            if (is_android (dev_vend_id) ||
                dev_man.startsWith ("HTC") || dev_man.startsWith ("MOTO") || dev_man.startsWith ("SONY") || dev_man.startsWith ("SAM") || dev_man.startsWith ("ASUS") || dev_man.startsWith ("SONY") ||
                dev_man.startsWith ("ANDROID") || dev_man.startsWith ("GOOG") ||
                dev_prod.startsWith ("NEXUS") || dev_prod.startsWith ("ANDROID") || dev_prod.startsWith ("GOOG") || dev_prod.startsWith ("XPERIA") || dev_prod.startsWith ("GT-") ||
                dev_prod.startsWith ("ONE") || dev_prod.startsWith ("MOTO") || dev_prod.startsWith ("LT"))
*/
              if (! usb_attach_handler (device, true))                        // Handle as NEW attached device
                ret --;   // Reduce number of devices returned.
//          }
        }
      }
    }
    hu_uti.logd ("end");
    return (ret);
  }

  private boolean aap_running = false;
  public void transport_stop () {                                       // USB Transport Stop. Called only by hu_act.all_stop()
    hu_uti.logd ("m_usb_receiver: " + m_usb_receiver + "  aap_running: " + aap_running);

    if (! aap_running)
      return;

    byebye_send ();                                                     // Terminate AA Protocol with ByeBye

    aap_running = false;

    if (m_tra_thread != null)                                           // If Transport Thread...
      m_tra_thread.quit ();                                             // Terminate Transport Thread using it's quit() API

    if (m_usb_receiver != null)
      m_context.unregisterReceiver (m_usb_receiver);
    m_usb_receiver = null;

    usb_disconnect ();                                                  // Disconnect
  }


  private final class usb_receiver extends BroadcastReceiver {          // USB Broadcast Receiver enabled by transport_start() & disabled by transport_stop()
    @Override
    public void onReceive (Context context, Intent intent) {
      UsbDevice device = intent.<UsbDevice>getParcelableExtra (UsbManager.EXTRA_DEVICE);
      hu_uti.logd ("USB BCR intent: " + intent);

      if (device != null) {
        String action = intent.getAction ();

        if (action.equals (UsbManager.ACTION_USB_DEVICE_DETACHED)) {    // If detach...
          usb_detach_handler (device);                                  // Handle detached device
        }

        else if (action.equals (UsbManager.ACTION_USB_DEVICE_ATTACHED)){// If attach...
          usb_attach_handler (device, true);                            // Handle New attached device
        }

        else if (action.equals (hu_uti.str_usb_perm)) {                 // If Our App specific Intent for permission request...
                                                                        // If permission granted...
          if (intent.getBooleanExtra (UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            hu_uti.logd ("USB BCR permission granted");
            hu_uti.logd ("m_hu_act.sys_ui_hide (): " + m_hu_act.sys_ui_hide ());
            usb_attach_handler (device, false);                         // Handle same as attached device except NOT NEW so don't add to USB device list
          }
          else {
            hu_uti.loge ("USB BCR permission denied");
          }
        }

      }
    }
  }

  private boolean usb_attach_handler (UsbDevice device, boolean add) {     // Handle attached device. Called only by:  transport_start() on autolaunch or device find, and...
                                                                                                                 // usb_receiver() on USB grant permission or USB attach
    String usb_dev_name = usb_dev_name_get (device);
    hu_uti.logd ("usb_attach_handler m_usb_connected: " + m_usb_connected);// + "  usb_dev_name: " + usb_dev_name);  // device: " + device + "  

    int dev_vend_id = device.getVendorId ();                            // mVendorId=2996               HTC
    int dev_prod_id = device.getProductId ();                           // mProductId=1562              OneM8
    //hu_uti.logd ("dev_vend_id: " + dev_vend_id + "  dev_prod_id: " + dev_prod_id);
    // om7/xz0 internal: dev_name: /dev/bus/usb/001/002  dev_class: 0  dev_sclass: 0  dev_dev_id: 1002  dev_proto: 0  dev_vend_id: 1478  dev_prod_id: 36936     0x05c6 : 0x9048   Qualcomm
    // gs3/no2 internal: dev_name: /dev/bus/usb/001/002  dev_class: 2  dev_sclass: 0  dev_dev_id: 1002  dev_proto: 0  dev_vend_id: 5401  dev_prod_id: 32        0x1519 : 0x0020   Comneon : HSIC Device

    if (dev_vend_id == 0x05c6 && dev_prod_id >= 0x9000)                 // Ignore Qualcomm OM7/XZ0 internal
      return (false);
    if (dev_vend_id == 0x1519)// && dev_prod_id == 0x020)               // Ignore Comneon  GS3/NO2 internal
      return (false);
    if (dev_vend_id == 0x0835)                                          // Ignore "Action Star Enterprise Co., Ltd" = USB Hub
      return (false);

    if (add)
      usb_add (usb_dev_name, device);                                   // Add USB Device to list

    //private boolean auto_start = true;
    //if (/*! auto_start ||*/ hu_uti.file_get ("/sdcard/hu_noas"))
    //  return (false);

    if (! m_usb_connected) {                                            // If not already connected...
      hu_uti.logd ("Try connect");
      usb_connect (device);                                             // Attempt to connect
    }

    if (m_usb_connected) {                                              // If connected now, or was connected already...
      hu_uti.logd ("Connected so start JNI");
      //hu_uti.ms_sleep (2000);                                         // Wait to settle
      //hu_uti.logd ("connected done sleep");
      jni_aap_start ();                                                 // Start JNI Android Auto Protocol and Main Thread
    }
    else
      hu_uti.logd ("Not connected");
    return (true);
  }

  private void usb_detach_handler (UsbDevice device) {                  // Handle detached device.  Called only by usb_receiver() if device detached while app is running (only ?)

    String usb_dev_name = usb_dev_name_get (device);
    hu_uti.logd ("usb_detach_handler m_usb_connected: " + m_usb_connected + "  usb_dev_name: " + usb_dev_name);  // device: " + device + "  m_usb_device: " + m_usb_device + "  
    usb_del (usb_dev_name, device);                                     // Delete USB Device from list

    if (/*m_usb_connected &&*/ m_usb_device != null && device.equals (m_usb_device)) {
      hu_uti.logd ("Disconnecting our connected device");
      usb_disconnect ();                                                // Disconnect

      Toast.makeText (m_context, "DISCONNECTED !!!", Toast.LENGTH_LONG).show ();

      hu_uti.ms_sleep (1000);                                           // Wait a bit
      //android.os.Process.killProcess (android.os.Process.myPid ());     // Kill self
      m_hu_act.finish ();
      return;
    }
    hu_uti.logd ("Not our device so ignore disconnect");
  }

  // "Android", "Android Open Automotive Protocol", "Description", "VersionName", "https://developer.android.com/auto/index.html", "62skidoo"
  // "Android", "Android Auto", "Description", "VersionName", "https://developer.android.com/auto/index.html", "62skidoo"

  // Indexes for strings sent by the host via ACC_REQ_SEND_STRING:
  private static final int ACC_IDX_MAN = 0;
  private static final int ACC_IDX_MOD = 1;
  //private static final int ACC_IDX_DES = 2;
  //private static final int ACC_IDX_VER = 3;
  //private static final int ACC_IDX_URI = 4;
  //private static final int ACC_IDX_SER = 5;
                                                                        // OAP Control requests:
  private static final int ACC_REQ_GET_PROTOCOL = 51;
  private static final int ACC_REQ_SEND_STRING  = 52;
  private static final int ACC_REQ_START        = 53;
  //  private static final int ACC_REQ_REGISTER_HID       = 54;
  //  private static final int ACC_REQ_UNREGISTER_HID     = 55;
  //  private static final int ACC_REQ_SET_HID_REPORT_DESC= 56;
  //  private static final int ACC_REQ_SEND_HID_EVENT     = 57;
  //  private static final int ACC_REQ_AUDIO              = 58;

  private UsbDeviceConnection m_usb_dev_conn  = null;                   // USB Device connection
  private UsbInterface        m_usb_iface     = null;                   // USB Interface
  private UsbEndpoint         m_usb_ep_in     = null;                   // USB Input  endpoint
  private UsbEndpoint         m_usb_ep_out    = null;                   // USB Output endpoint
  private int m_ep_in_addr   = -1;                                      // Input  endpoint Value  129
  private int m_ep_out_addr  = -1;                                      // Output endpoint Value    2

  private void usb_disconnect () {
    hu_uti.logd ("usb_disconnect");// m_usb_device: " + m_usb_device);
    m_usb_connected = false;
    m_usb_device = null;
    usb_close ();
  }

  private void usb_connect (UsbDevice device) {                         // Attempt to connect. Called only by usb_attach_handler() & presets_select()
    hu_uti.logd ("usb_connect m_autolaunched: " + m_autolaunched);// device: " + device);

    m_usb_dev_conn = null;

//hu_uti.ms_sleep (1000);

    if (! usb_device_perm_get (device)) {                               // If we DON'T have permission to access the USB device...

// BAD: 500, OK: 1000ms, ??   !!!! NEED A DELAY HERE or plugging USB with app running results in Attach quickly followed by detach
//hu_uti.ms_sleep (1000);
/* Also:
07-10 17:53:51.770 D/             usb_connect(31496): Have Explicit USB Permission
07-10 17:53:51.771 E/UsbManager(31496): exception in UsbManager.openDevice
07-10 17:53:51.771 E/UsbManager(31496): java.lang.IllegalArgumentException: device /dev/bus/usb/001/002 does not exist or is restricted
07-10 17:53:51.771 E/UsbManager(31496): 	at android.os.Parcel.readException(Parcel.java:1550)
07-10 17:53:51.771 E/UsbManager(31496): 	at android.os.Parcel.readException(Parcel.java:1499)
07-10 17:53:51.771 E/UsbManager(31496): 	at android.hardware.usb.IUsbManager$Stub$Proxy.openDevice(IUsbManager.java:373)
07-10 17:53:51.771 E/UsbManager(31496): 	at android.hardware.usb.UsbManager.openDevice(UsbManager.java:265)
07-10 17:53:51.771 E/UsbManager(31496): 	at ca.yyx.hu.hu_tra.usb_open(hu_tra.java:564)
*/

      hu_uti.logd ("Request USB Permission");    // Request permission
      //a cat /system/etc/permissions/android.hardware.usb.host.xml|el
      //<permissions>    <feature name="android.hardware.usb.host" />   </permissions>
      Intent intent = new Intent (hu_uti.str_usb_perm);                 // Our App specific Intent for permission request
      intent.setPackage (m_context.getPackageName ());
      PendingIntent pendingIntent = PendingIntent.getBroadcast (m_context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
      m_usb_mgr.requestPermission (device, pendingIntent);              // Request permission. BCR called later if we get it.
      return;                                                           // Done for now. Wait for permission
    }

    if (m_autolaunched)                                                 // Not used now
      hu_uti.logd ("Have Implicit USB Permission");
    else
      hu_uti.logd ("Have Explicit USB Permission");

    int ret = usb_open (device);                                        // Open USB device & claim interface
    if (ret < 0) {                                                      // If error...
      usb_disconnect ();                                                // Ensure state is disconnected
      return;                                                           // Done
    }

    int dev_vend_id = device.getVendorId ();                            // mVendorId=2996               HTC
    int dev_prod_id = device.getProductId ();                           // mProductId=1562              OneM8
    hu_uti.logd ("dev_vend_id: " + dev_vend_id + "  dev_prod_id: " + dev_prod_id);

                                                                        // If in accessory mode...
    if (dev_vend_id == USB_VID_GOO && (dev_prod_id == USB_PID_ACC || dev_prod_id == USB_PID_ACC_ADB)) {
      ret = acc_mode_endpoints_set ();                                  // Set Accessory mode Endpoints
      if (ret < 0) {                                                    // If error...
        usb_disconnect ();                                              // Ensure state is disconnected
      }
      else {
        m_usb_connected = true;
        m_usb_device = device;
      }
      return;                                                           // Done
    }
                                                                        // Else if not in accessory mode...
    acc_mode_switch (m_usb_dev_conn);                                   // Do accessory negotiation and attempt to switch to accessory mode
    usb_disconnect ();                                                  // Ensure state is disconnected
    return;                                                             // Done, wait for accessory mode
  }

  private boolean usb_device_perm_get (UsbDevice device) {              // Get USB device permission state. Called only by usb_connect()
    boolean perm = m_usb_mgr.hasPermission (device);
/*  !! Doesn't work !!
      try {                                                             // hasPermission() lies so check via Throwable or null
        m_usb_dev_conn = m_usb_mgr.openDevice (device);                 // Open device for connection
      }
      catch (Throwable e) {
        hu_uti.loge ("m_autolaunched Throwable: " + e);   // java.lang.IllegalArgumentException: device /dev/bus/usb/001/019 does not exist or is restricted
        return (false);
      }*/
    hu_uti.logd ("perm: " + perm);// device: " + device);
    return (perm);
  }

  private void usb_close () {                                           // Release interface and close USB device connection. Called only by usb_disconnect()
    hu_uti.logd ("usb_close");
    m_usb_ep_in   = null;                                               // Input  EP
    m_usb_ep_out  = null;                                               // Output EP
    m_ep_in_addr  = -1;                                                 // Input  endpoint Value
    m_ep_out_addr = -1;                                                 // Output endpoint Value

    if (m_usb_dev_conn != null) {
      boolean bret = false;
      if (m_usb_iface != null) {
        bret = m_usb_dev_conn.releaseInterface (m_usb_iface);
      }
      if (bret) {
        hu_uti.logd ("OK releaseInterface()");
      }
      else {
        hu_uti.loge ("Error releaseInterface()");
      }

      m_usb_dev_conn.close ();                                        //
    }
    m_usb_dev_conn = null;
    m_usb_iface = null;
  }

  private int usb_open (UsbDevice device) {                             // Open USB device connection & claim interface. Called only by usb_connect()
    try {
      if (m_usb_dev_conn == null)
        m_usb_dev_conn = m_usb_mgr.openDevice (device);                 // Open device for connection
    }
    catch (Throwable e) {
      hu_uti.loge ("Throwable: " + e);                                  // java.lang.IllegalArgumentException: device /dev/bus/usb/001/019 does not exist or is restricted
    }

    if (m_usb_dev_conn == null) {
      hu_uti.logw ("Could not obtain m_usb_dev_conn for device: " + device);
      return (-1);                                                      // Done error
    }
    hu_uti.logd ("Device m_usb_dev_conn: " + m_usb_dev_conn);

    try {
      int iface_cnt = device.getInterfaceCount ();
      if (iface_cnt <= 0) {
        hu_uti.loge ("iface_cnt: " + iface_cnt);
        return (-1);                                                    // Done error
      }
      hu_uti.logd ("iface_cnt: " + iface_cnt);
      m_usb_iface = device.getInterface (0);                            // java.lang.ArrayIndexOutOfBoundsException: length=0; index=0

      if (! m_usb_dev_conn.claimInterface (m_usb_iface, true)) {        // Claim interface, if error...   true = take from kernel
        hu_uti.loge ("Error claiming interface");
        return (-1);
      }
      hu_uti.logd ("Success claiming interface");
    }
    catch (Throwable e) {
      hu_uti.loge ("Throwable: " + e);           // Nexus 7 2013:    Throwable: java.lang.ArrayIndexOutOfBoundsException: length=0; index=0
      return (-1);                                                      // Done error
    }
    return (0);                                                         // Done success
  }

  private int acc_mode_endpoints_set () {                               // Set Accessory mode Endpoints. Called only by usb_connect()
    hu_uti.logd ("In acc so get EPs...");
    m_usb_ep_in   = null;                                               // Setup bulk endpoints.
    m_usb_ep_out  = null;
    m_ep_in_addr  = -1;     // 129
    m_ep_out_addr = -1;     // 2
    
    for (int i = 0; i < m_usb_iface.getEndpointCount (); i ++) {        // For all USB endpoints...
      UsbEndpoint ep = m_usb_iface.getEndpoint (i);
      if (ep.getDirection () == UsbConstants.USB_DIR_IN) {              // If IN
        if (m_usb_ep_in == null) {                                      // If Bulk In not set yet...
          m_ep_in_addr = ep.getAddress ();
          hu_uti.logd (String.format ("Bulk IN m_ep_in_addr: %d  %d", m_ep_in_addr, i));
          m_usb_ep_in = ep;                                             // Set Bulk In
        }
      }
      else {                                                            // Else if OUT...
        if (m_usb_ep_out == null) {                                     // If Bulk Out not set yet...
          m_ep_out_addr = ep.getAddress ();
          hu_uti.logd (String.format ("Bulk OUT m_ep_out_addr: %d  %d", m_ep_out_addr, i));
          m_usb_ep_out = ep;                                            // Set Bulk Out
        }
      }
    }
    if (m_usb_ep_in == null || m_usb_ep_out == null) {
      hu_uti.loge ("Unable to find bulk endpoints");
      return (-1);                                                      // Done error
    }

    hu_uti.logd ("Connected have EPs");
    return (0);                                                         // Done success
  }

  private void acc_mode_switch (UsbDeviceConnection conn) {             // Do accessory negotiation and attempt to switch to accessory mode. Called only by usb_connect()
    hu_uti.logd ("Attempt acc");

    int len = 0;
    byte buffer  [] = new byte  [2];
    len = conn.controlTransfer (UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR, ACC_REQ_GET_PROTOCOL, 0, 0, buffer, 2, 10000);
    if (len != 2) {
      hu_uti.loge ("Error controlTransfer len: " + len);
      return;
    }
    int acc_ver = (buffer [1] << 8) | buffer  [0];                      // Get OAP / ACC protocol version
    hu_uti.logd ("Success controlTransfer len: " + len + "  acc_ver: " + acc_ver);
    if (acc_ver < 1) {                                                  // If error or version too low...
      hu_uti.loge ("No support acc");
      return;
    }
    hu_uti.logd ("acc_ver: " + acc_ver);

                                                                        // Send all accessory identification strings
    usb_acc_string_send (conn, ACC_IDX_MAN, hu_uti.str_MAN);            // Manufacturer
    usb_acc_string_send (conn, ACC_IDX_MOD, hu_uti.str_MOD);            // Model
    //usb_acc_string_send (conn, ACC_IDX_DES, hu_uti.str_DES);
    //usb_acc_string_send (conn, ACC_IDX_VER, hu_uti.str_VER);
    //usb_acc_string_send (conn, ACC_IDX_URI, hu_uti.str_URI);
    //usb_acc_string_send (conn, ACC_IDX_SER, hu_uti.str_SER);

    hu_uti.logd ("Sending acc start");           // Send accessory start request. Device should re-enumerate as an accessory.
    len = conn.controlTransfer (UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, ACC_REQ_START, 0, 0, null, 0, 10000);
    if (len != 0) {
      hu_uti.loge ("Error acc start");
    }
    else {
      hu_uti.logd ("OK acc start. Wait to re-enumerate...");
    }
  }

                                                                        // Send one accessory identification string.    Called only by acc_mode_switch()
  private void usb_acc_string_send (UsbDeviceConnection conn, int index, String string) {
    byte  [] buffer = (string + "\0").getBytes ();
    int len = conn.controlTransfer (UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, ACC_REQ_SEND_STRING, 0, index, buffer, buffer.length, 10000);
    if (len != buffer.length) {
      hu_uti.loge ("Error controlTransfer len: " + len + "  index: " + index + "  string: \"" + string + "\"");
    }
    else {
      hu_uti.logd ("Success controlTransfer len: " + len + "  index: " + index + "  string: \"" + string + "\"");
    }
  }


/* Port 30515

           <intent-filter>
                <action android:name="android.nfc.action.NDEF_DISCOVERED"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="application/com.google.android.gms.car"/>
            </intent-filter>

*/

/*

07-09 02:23:35.027 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/002,mVendorId=5118,mProductId=14336,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=MUSHKIN,mProductName=MKNUFDMH16GB,mSerialNumber=070B3CD07A828030,mConfigurations=[
07-09 02:23:35.027 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=128,mMaxPower=100,mInterfaces=[
07-09 02:23:35.027 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=null,mClass=8,mSubclass=6,mProtocol=80,mEndpoints=[
07-09 02:23:35.027 D/UsbHostManager(  572): UsbEndpoint[mAddress=129,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:23:35.027 D/UsbHostManager(  572): UsbEndpoint[mAddress=2,mAttributes=2,mMaxPacketSize=512,mInterval=0]]]]
07-09 02:19:12.272 D/             screen_logd( 4548): dev_name: /dev/bus/usb/001/002  dev_class: 0  dev_sclass: 0  dev_dev_id: 1002  dev_proto: 0  dev_vend_id: 5118  dev_prod_id: 14336
07-09 02:19:12.273 D/             screen_logd( 4548): dev_man: MUSHKIN  dev_prod: MKNUFDMH16GB  dev_ser: 070B3CD07A828030

07-09 02:22:05.621 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/002,mVendorId=5118,mProductId=12544,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=        ,mProductName=Patriot Memory,mSerialNumber=07AB10013A50869B,mConfigurations=[
07-09 02:22:05.621 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=128,mMaxPower=150,mInterfaces=[
07-09 02:22:05.621 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=null,mClass=8,mSubclass=6,mProtocol=80,mEndpoints=[
07-09 02:22:05.621 D/UsbHostManager(  572): UsbEndpoint[mAddress=129,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:22:05.621 D/UsbHostManager(  572): UsbEndpoint[mAddress=2,mAttributes=2,mMaxPacketSize=512,mInterval=0]]]]
07-09 02:22:45.368 D/             screen_logd( 4755): dev_name: /dev/bus/usb/001/002  dev_class: 0  dev_sclass: 0  dev_dev_id: 1002  dev_proto: 0  dev_vend_id: 5118  dev_prod_id: 12544
07-09 02:22:45.369 D/             screen_logd( 4755): dev_man:           dev_prod: PATRIOT MEMORY  dev_ser: 07AB10013A50869B

07-09 02:24:29.420 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/002,mVendorId=4046,mProductId=57652,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=SEMC,mProductName=CCR-80,mSerialNumber=105207EC06FE662,mConfigurations=[
07-09 02:24:29.420 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=128,mMaxPower=250,mInterfaces=[
07-09 02:24:29.420 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=null,mClass=8,mSubclass=6,mProtocol=80,mEndpoints=[
07-09 02:24:29.420 D/UsbHostManager(  572): UsbEndpoint[mAddress=129,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:24:29.420 D/UsbHostManager(  572): UsbEndpoint[mAddress=2,mAttributes=2,mMaxPacketSize=512,mInterval=0]]]]
07-09 02:24:51.014 D/             screen_logd( 4908): dev_name: /dev/bus/usb/001/002  dev_class: 0  dev_sclass: 0  dev_dev_id: 1002  dev_proto: 0  dev_vend_id: 4046  dev_prod_id: 57652
07-09 02:24:51.014 D/             screen_logd( 4908): dev_man: SEMC  dev_prod: CCR-80  dev_ser: 105207EC06FE662


07-09 02:27:43.824 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/002,mVendorId=1256,mProductId=26716,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=samsung,mProductName=GT-N7100,mSerialNumber=4df7da0f442fbf49,mConfigurations=[
07-09 02:27:43.824 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=48,mInterfaces=[
07-09 02:27:43.824 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=MTP,mClass=255,mSubclass=255,mProtocol=0,mEndpoints=[
07-09 02:27:43.824 D/UsbHostManager(  572): UsbEndpoint[mAddress=130,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:43.824 D/UsbHostManager(  572): UsbEndpoint[mAddress=4,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:43.824 D/UsbHostManager(  572): UsbEndpoint[mAddress=131,mAttributes=3,mMaxPacketSize=28,mInterval=6]]]]

07-09 02:27:46.495 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/003,mVendorId=1256,mProductId=26716,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=samsung,mProductName=GT-N7100,mSerialNumber=4df7da0f442fbf49,mConfigurations=[
07-09 02:27:46.495 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=48,mInterfaces=[
07-09 02:27:46.495 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=MTP,mClass=255,mSubclass=255,mProtocol=0,mEndpoints=[
07-09 02:27:46.495 D/UsbHostManager(  572): UsbEndpoint[mAddress=130,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:46.495 D/UsbHostManager(  572): UsbEndpoint[mAddress=4,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:46.495 D/UsbHostManager(  572): UsbEndpoint[mAddress=131,mAttributes=3,mMaxPacketSize=28,mInterval=6]]]]

07-09 02:27:47.199 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/004,mVendorId=1256,mProductId=26716,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=samsung,mProductName=GT-N7100,mSerialNumber=4df7da0f442fbf49,mConfigurations=[
07-09 02:27:47.199 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=48,mInterfaces=[
07-09 02:27:47.199 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=MTP,mClass=255,mSubclass=255,mProtocol=0,mEndpoints=[
07-09 02:27:47.199 D/UsbHostManager(  572): UsbEndpoint[mAddress=130,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:47.199 D/UsbHostManager(  572): UsbEndpoint[mAddress=4,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:47.199 D/UsbHostManager(  572): UsbEndpoint[mAddress=131,mAttributes=3,mMaxPacketSize=28,mInterval=6]]]]

07-09 02:27:47.765 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/005,mVendorId=1256,mProductId=26716,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=samsung,mProductName=GT-N7100,mSerialNumber=4df7da0f442fbf49,mConfigurations=[
07-09 02:27:47.765 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=48,mInterfaces=[
07-09 02:27:47.765 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=MTP,mClass=255,mSubclass=255,mProtocol=0,mEndpoints=[
07-09 02:27:47.765 D/UsbHostManager(  572): UsbEndpoint[mAddress=130,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:47.765 D/UsbHostManager(  572): UsbEndpoint[mAddress=4,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:47.765 D/UsbHostManager(  572): UsbEndpoint[mAddress=131,mAttributes=3,mMaxPacketSize=28,mInterval=6]]]]

07-09 02:27:48.655 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/006,mVendorId=1256,mProductId=26716,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=samsung,mProductName=GT-N7100,mSerialNumber=4df7da0f442fbf49,mConfigurations=[
07-09 02:27:48.655 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=48,mInterfaces=[
07-09 02:27:48.655 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=MTP,mClass=255,mSubclass=255,mProtocol=0,mEndpoints=[
07-09 02:27:48.655 D/UsbHostManager(  572): UsbEndpoint[mAddress=130,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:48.655 D/UsbHostManager(  572): UsbEndpoint[mAddress=4,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:48.655 D/UsbHostManager(  572): UsbEndpoint[mAddress=131,mAttributes=3,mMaxPacketSize=28,mInterval=6]]]]

07-09 02:27:50.365 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/007,mVendorId=1256,mProductId=26716,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=samsung,mProductName=GT-N7100,mSerialNumber=4df7da0f442fbf49,mConfigurations=[
07-09 02:27:50.365 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=48,mInterfaces=[
07-09 02:27:50.365 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=MTP,mClass=255,mSubclass=255,mProtocol=0,mEndpoints=[
07-09 02:27:50.365 D/UsbHostManager(  572): UsbEndpoint[mAddress=130,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:50.365 D/UsbHostManager(  572): UsbEndpoint[mAddress=4,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:27:50.365 D/UsbHostManager(  572): UsbEndpoint[mAddress=131,mAttributes=3,mMaxPacketSize=28,mInterval=6]]]]


07-09 02:32:47.085 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/002,mVendorId=1256,mProductId=26716,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=samsung,mProductName=GT-N7100,mSerialNumber=4df7da0f442fbf49,mConfigurations=[
07-09 02:32:47.085 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=48,mInterfaces=[
07-09 02:32:47.085 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=MTP,mClass=255,mSubclass=255,mProtocol=0,mEndpoints=[
07-09 02:32:47.085 D/UsbHostManager(  572): UsbEndpoint[mAddress=130,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:32:47.085 D/UsbHostManager(  572): UsbEndpoint[mAddress=4,mAttributes=2,mMaxPacketSize=512,mInterval=0]
07-09 02:32:47.085 D/UsbHostManager(  572): UsbEndpoint[mAddress=131,mAttributes=3,mMaxPacketSize=28,mInterval=6]]]]
07-09 02:30:57.270 D/             screen_logd( 5293): dev_name: /dev/bus/usb/001/008  dev_class: 0  dev_sclass: 0  dev_dev_id: 1008  dev_proto: 0  dev_vend_id: 1256  dev_prod_id: 26716
07-09 02:30:57.271 D/             screen_logd( 5293): dev_man: SAMSUNG  dev_prod: GT-N7100  dev_ser: 4DF7DA0F442FBF49


07-09 02:34:29.308 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/003,mVendorId=2101,mProductId=34049,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=Action Star,mProductName=USB HID,mSerialNumber=null,mConfigurations=[
07-09 02:34:29.308 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=10,mInterfaces=[
07-09 02:34:29.308 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=null,mClass=3,mSubclass=1,mProtocol=1,mEndpoints=[
07-09 02:34:29.308 D/UsbHostManager(  572): UsbEndpoint[mAddress=130,mAttributes=3,mMaxPacketSize=8,mInterval=8]]]]

07-09 02:34:30.575 D/UsbHostManager(  572): Added device UsbDevice[mName=/dev/bus/usb/001/005,mVendorId=2101,mProductId=34050,mClass=0,mSubclass=0,mProtocol=0,mManufacturerName=Action Star,mProductName=USB HID,mSerialNumber=null,mConfigurations=[
07-09 02:34:30.575 D/UsbHostManager(  572): UsbConfiguration[mId=1,mName=null,mAttributes=192,mMaxPower=10,mInterfaces=[
07-09 02:34:30.575 D/UsbHostManager(  572): UsbInterface[mId=0,mAlternateSetting=0,mName=null,mClass=3,mSubclass=1,mProtocol=1,mEndpoints=[
07-09 02:34:30.575 D/UsbHostManager(  572): UsbEndpoint[mAddress=130,mAttributes=3,mMaxPacketSize=8,mInterval=8]]]]

07-09 02:35:29.610 D/             screen_logd( 5557): dev_name: /dev/bus/usb/001/005  dev_class: 0  dev_sclass: 0  dev_dev_id: 1005  dev_proto: 0  dev_vend_id: 2101  dev_prod_id: 34050
07-09 02:35:29.610 D/             screen_logd( 5557): dev_man: ACTION STAR  dev_prod: USB HID  dev_ser: 
07-09 02:35:29.611 D/             screen_logd( 5557): dev_name: /dev/bus/usb/001/003  dev_class: 0  dev_sclass: 0  dev_dev_id: 1003  dev_proto: 0  dev_vend_id: 2101  dev_prod_id: 34049
07-09 02:35:29.611 D/             screen_logd( 5557): dev_man: ACTION STAR  dev_prod: USB HID  dev_ser: 

*/

  private static final int USB_PID_ACC_MIN     = 0x2D00;      // 11520   Product ID to use when in accessory mode without ADB
  private static final int USB_PID_ACC_MAX     = 0x2D05;

  private static final int USB_PID_ACC         = 0x2D00;      // Accessory                  100
  private static final int USB_PID_ACC_ADB     = 0x2D01;      // Accessory + ADB            110
  private static final int USB_PID_AUD         = 0x2D02;      //                   Audio    001
  private static final int USB_PID_AUD_ADB     = 0x2D03;      //             ADB + Audio    011
  private static final int USB_PID_ACC_AUD     = 0x2D04;      // Accessory       + Audio    101
  private static final int USB_PID_ACC_AUD_ADB = 0x2D05;      // Accessory + ADB + Audio    111

  private String prod_id_name_get (int prod_id) {
    if (prod_id == USB_PID_ACC)
      return ("ACC");
    else if (prod_id == USB_PID_ACC_ADB)
      return ("ADB");//ACC_ADB");
    else if (prod_id == USB_PID_AUD)
      return ("AUD");
    else if (prod_id == USB_PID_AUD_ADB)
      return ("AUA");//AUD_ADB");
    else if (prod_id == USB_PID_ACC_AUD_ADB)
      return ("ALL");//ACC_AUD_ADB");
    else
      //return ("0x" + hu_uti.hex_get (prod_id));
      return ("" + prod_id);
  }

  private static final int USB_VID_GOO  = 0x18D1;   // 6353   Nexus or ACC mode, see PID to distinguish
  private static final int USB_VID_HTC  = 0x0bb4;   // 2996
  private static final int USB_VID_SAM  = 0x04e8;   // 1256
  private static final int USB_VID_O1A  = 0xfff6;   // 65526    Samsung ?
  private static final int USB_VID_SON  = 0x0fce;   // 4046
  private static final int USB_VID_LGE  = 0xfff5;   // 65525
  private static final int USB_VID_MOT  = 0x22b8;   // 8888


  private String vend_id_name_get (int vend_id) {
    if (vend_id == USB_VID_GOO)
      return ("GOO");//GLE");
    else if (vend_id == USB_VID_HTC)
      return ("HTC");
    else if (vend_id == USB_VID_SAM)
      return ("SAM");//SUNG");
    else if (vend_id == USB_VID_SON)
      return ("SON");//Y");
    else if (vend_id == USB_VID_MOT)
      return ("MOT");//OROLA");
    else if (vend_id == USB_VID_LGE)
      return ("LGE");
    else if (vend_id == USB_VID_O1A)
      return ("O1A");
    else
      //return ("0x" + hu_uti.hex_get (vend_id));
      return ("" + vend_id);
  }
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

  private String usb_man_get (UsbDevice device) {                       // Use reflection to avoid ASUS tablet problem
    String ret = "";
    //ret = device.getManufacturerName ();                              // mManufacturerName=HTC
    try {
      Class<?> c = Class.forName ("android.hardware.usb.UsbDevice");
      java.lang.reflect.Method get = c.getMethod ("getManufacturerName");
      ret = (String) get.invoke (device);
      hu_uti.logd ("ret: " + ret);
    }
    catch (Throwable t) {
      hu_uti.loge ("Throwable t: " + t);
    }
    return (ret);
  }
  private String usb_pro_get (UsbDevice device) {
    String ret = "";
    //ret = device.getProductName      ();                              // mProductName=Android Phone
    try {
      Class<?> c = Class.forName ("android.hardware.usb.UsbDevice");
      java.lang.reflect.Method get = c.getMethod ("getProductName");
      ret = (String) get.invoke (device);
      hu_uti.logd ("ret: " + ret);
    }
    catch (Throwable t) {
      hu_uti.loge ("Throwable t: " + t);
    }
    return (ret);
  }
  private String usb_ser_get (UsbDevice device) {
    String ret = "";
    //ret = device.getSerialNumber     ();                              // mSerialNumber=FA46RWM22264
    try {
      Class<?> c = Class.forName ("android.hardware.usb.UsbDevice");
      java.lang.reflect.Method get = c.getMethod ("getSerialNumber");
      ret = (String) get.invoke (device);
      hu_uti.logd ("ret: " + ret);
    }
    catch (Throwable t) {
      hu_uti.loge ("Throwable t: " + t);
    }
    return (ret);
  }

  private String usb_dev_name_get (UsbDevice device) {
    String usb_dev_name = "";
    String dev_name = device.getDeviceName ();                          // mName=/dev/bus/usb/003/007
    int dev_class   = device.getDeviceClass ();                         // mClass=255
    int dev_sclass  = device.getDeviceSubclass ();                      // mSublass=255
    int dev_dev_id  = device.getDeviceId ();                            // mId=2                        1003 for /dev/bus/usb/001/003
    int dev_proto   = device.getDeviceProtocol ();                      // mProtocol=0
    int dev_vend_id = device.getVendorId ();                            // mVendorId=2996               HTC
    int dev_prod_id = device.getProductId ();                           // mProductId=1562              OneM8
    hu_uti.logd ("dev_name: " + dev_name + "  dev_class: " + dev_class + "  dev_sclass: " + dev_sclass + "  dev_dev_id: " + dev_dev_id + "  dev_proto: " + dev_proto + "  dev_vend_id: " + dev_vend_id + "  dev_prod_id: " + dev_prod_id);
// om7/xz0 internal: dev_name: /dev/bus/usb/001/002  dev_class: 0  dev_sclass: 0  dev_dev_id: 1002  dev_proto: 0  dev_vend_id: 1478  dev_prod_id: 36936     0x05c6 : 0x9048   Qualcomm
// gs3/no2 internal: dev_name: /dev/bus/usb/001/002  dev_class: 2  dev_sclass: 0  dev_dev_id: 1002  dev_proto: 0  dev_vend_id: 5401  dev_prod_id: 32        0x1519 : 0x0020   Comneon : HSIC Device

    String dev_man  = "";
    String dev_prod = "";
    String dev_ser  = "";
    if (hu_uti.android_version >= 21) {                                 // Android 5.0+ only
      try {
        dev_man  = usb_man_get (device);                                // mManufacturerName=HTC
        dev_prod = usb_pro_get (device);                                // mProductName=Android Phone
        dev_ser  = usb_ser_get (device);                                // mSerialNumber=FA46RWM22264
      }
      catch (Throwable e) {
        hu_uti.loge ("Throwable: " + e);
      }
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

      hu_uti.logd ("dev_man: " + dev_man + "  dev_prod: " + dev_prod + "  dev_ser: " + dev_ser);
    }
/*  if (dev_man.equals (""))
      usb_dev_name += vend_id_name_get (dev_vend_id);
    else
      usb_dev_name += dev_man;
    if (dev_prod.equals (""))
      usb_dev_name += "\n" + prod_id_name_get (dev_prod_id);
    else
      usb_dev_name += "\n" + dev_prod;*/

    usb_dev_name += vend_id_name_get (dev_vend_id);
    usb_dev_name += ":";
    usb_dev_name += prod_id_name_get (dev_prod_id);
    usb_dev_name += "\n";

    usb_dev_name += hu_uti.hex_get ((short) dev_vend_id);
    usb_dev_name += ":";
    usb_dev_name += hu_uti.hex_get ((short) dev_prod_id);
    usb_dev_name += "\n";

    usb_dev_name += "" + dev_dev_id;

    return (usb_dev_name);
  }

  private int usb_list_num = 0;
  private String [] usb_list_name = new String [hu_act.PRESET_LEN_USB];
  private UsbDevice [] usb_list_device = new UsbDevice [hu_act.PRESET_LEN_USB];

  public void usb_force () {                                            // Called only by hu_act:preset_select_lstnr:onClick()
    if (m_usb_connected)
      return;
  
    if (hu_uti.su_installed_get ()) {
      String cmd = "setenforce 0 ; chmod -R 777 /dev/bus 1>/dev/null 2>/dev/null";
      hu_uti.sys_run (cmd, true);
    }

    m_ep_in_addr  = 255;
    m_ep_out_addr = 0;  // USB Force
    jni_aap_start ();
  }

  public void presets_select (int idx) {                                // Called only by hu_act:preset_select_lstnr:onClick()
    hu_uti.logd ("idx: " + idx + "  name: " + usb_list_name [idx] + " device : " + usb_list_device [idx]);
    if (! m_usb_connected && idx < usb_list_num && usb_list_device [idx] != null)
      usb_connect (usb_list_device [idx]);
  }

  private void usb_add (String name, UsbDevice device) {
    if (usb_list_num >= hu_act.PRESET_LEN_USB) {
      hu_uti.loge ("usb_list_num >= hu_act.PRESET_LEN_USB");
      return;
    }
    usb_list_name [usb_list_num] = name;
    usb_list_device [usb_list_num] = device;
    usb_list_num ++;
    m_hu_act.presets_update (usb_list_name);
    usb_log ();
  }
  private void usb_del (String name, UsbDevice device) {
    int idx = 0;
    boolean moving = false;
    for (idx = 0; idx < usb_list_num; idx ++) {
      //hu_uti.logd ("idx: " + idx + "  name: " + usb_list_name [idx]);
      if (name.equals (usb_list_name [idx])) {
        moving = true;          
      }
      if (moving) {
        if (idx >= usb_list_num - 1) {
          usb_list_name [idx] = "";
          usb_list_device [idx] = null;
        }
        else {
          usb_list_name [idx] = usb_list_name [idx + 1];
          usb_list_device [idx] = usb_list_device [idx + 1];
        }
      }
    }
    if (moving)
      usb_list_num --;
    m_hu_act.presets_update (usb_list_name);
    usb_log ();
  }
  private void usb_log () {
    int idx = 0;
    for (idx = 0; idx < usb_list_num; idx ++) {
      hu_uti.logd ("idx: " + idx + "  name: " + usb_list_name [idx]);
    }
  }

/* Not used
  private int usb_io (UsbEndpoint ep, byte [] buf, int len, int tmo) {
    if (m_usb_dev_conn == null) {
      hu_uti.loge ("m_usb_dev_conn: " + m_usb_dev_conn);
      return (-1);
    }
    int ret = m_usb_dev_conn.bulkTransfer (ep, buf, len, tmo);          // This form good for API level 12+, IDX form 4.3+ only
    if (ret < 0) {
      hu_uti.loge ("Bulk transfer failed ret: " + ret);
    }
    hu_uti.logd ("Bulk transfer ret: " + ret);
    return (ret);
  }

  private int usb_read (byte [] buf, int len) {
    int ret = usb_io (m_usb_ep_in,  buf, len, 3000);                    // -1 or 0 = wait forever
    return (ret);
  }

  private int usb_write (byte [] buf, int len) {
    int ret = usb_io (m_usb_ep_out, buf, len, 1000);                    // 1 second timeout
    return (ret);
  }
*/

}

