
    // OLD Java based protocol processing   Head Unit Transport
    // Not used since functionality moved to JNI/C
    // Could be resurrected (especially for AA over WiFi), but OpenSSL via JNI could still be needed

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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class hu_tro {

  private int               max_content_size = 16384;
  private rx_handler        m_rx_handler;                               // Transport thread looper and handler
  private final Object      m_lock = new Object ();                     // Lock to guard all mutable state
  private ByteBuffer        m_bb_out;                                   // 1 Output buffer, set to null when the transport is closed
  private static final int  m_max_input_buffers = 8;
  private rx_thread         m_rx_thread;                                // Rx thread

  private UsbDeviceConnection mConnection;
  private UsbEndpoint         mBulkInEndpoint;
  private UsbEndpoint         mBulkOutEndpoint;
  private hu_act             m_hu_act;

  private static final int OAP_VID_GOOG    = 0x18D1; // Use Google Vendor ID when in accessory mode
  private static final int OAP_PID_OAP_NUL = 0x2D00; // Product ID to use when in accessory mode without ADB
  private static final int OAP_PID_OAP_ADB = 0x2D01; // Product ID to use when in accessory mode and adb is enabled

  // Indexes for strings sent by the host via OAP_SEND_STRING:
  private static final int OAP_STR_MANUFACTURER = 0;
  private static final int OAP_STR_MODEL = 1;
  private static final int OAP_STR_DESCRIPTION = 2;
  private static final int OAP_STR_VERSION = 3;
  private static final int OAP_STR_URI = 4;
  private static final int OAP_STR_SERIAL = 5;
                                                                        // OAP Control requests:
  private static final int OAP_GET_PROTOCOL = 51;
  private static final int OAP_SEND_STRING = 52;
  private static final int OAP_START = 53;


  private UsbManager      m_usb_mgr;
  private usb_receiver    m_usb_receiver;
  private boolean         m_usb_connected;
  private UsbDevice       m_usb_device;
  private Context         m_context;


  public hu_tro (hu_act hu_act) {
    m_hu_act = hu_act;
    m_context = (Context) hu_act;
    m_usb_mgr = (UsbManager) m_context.getSystemService (Context.USB_SERVICE);
  }

  public void usb_setup (UsbDeviceConnection connection, UsbEndpoint bulkInEndpoint, UsbEndpoint bulkOutEndpoint) {
    mConnection = connection;                                           // Setup USB IO parameters
    mBulkInEndpoint = bulkInEndpoint;
    mBulkOutEndpoint = bulkOutEndpoint;

    m_rx_handler = new rx_handler ();
    m_bb_out = ByteBuffer.allocate (max_content_size);                  // Allocate 1 output buffer

    mInitialBufferSize = max_content_size;                                         // Input Buffers start at this minimum size
    mMaxBufferSize =    4 * max_content_size;                                         // Input Buffers may grow to this maximum size
    mBuffers = new ByteBuffer [m_max_input_buffers];                      // Allocate input buffers
  }

  private int           mInitialBufferSize;
  private int           mMaxBufferSize;
  private ByteBuffer [] mBuffers;                                 // Pool of buffers
  private int           mAllocated;                               // Number of buffers in use
  private int           mAvailable;                               // Number of free buffers available to use

  private ByteBuffer acquire (int needed) {                              // Get a buffer of needed size
    synchronized (this) {
      for (;;) {
        if (mAvailable != 0) {
          mAvailable -= 1;
          return (grow (mBuffers [mAvailable], needed));
        }

        if (mAllocated < mBuffers.length) {                             // If a buffer is available...
          mAllocated += 1;                                              // Allocate and return buffer
          return (ByteBuffer.allocate (chooseCapacity (mInitialBufferSize, needed)));
        }

        try {
          wait ();                                                      // Block until another thread calls notify() or notifyAll( ) 
        }
        catch (InterruptedException ex) {
        }
      }
    }
  }

  private void release (ByteBuffer buffer) {                             // Return a buffer
    synchronized (this) {
      try {
        buffer.clear ();
        mBuffers [mAvailable ++] = buffer;                                // Return buffer to pool
        notifyAll ();                                                     // Notify any thread blocked in acquire() that a buffer is now available
      }
      catch (Throwable e) {
        //hu_uti.loge ("Exception: " + e);
      }
    }
  }

  private ByteBuffer grow (ByteBuffer buffer, int needed) {              // Grow buffer to needed size
    int capacity = buffer.capacity ();
    if (capacity < needed) {                                            // If we need to grow the buffer
      final ByteBuffer oldBuffer = buffer;
      capacity = chooseCapacity (capacity, needed);
      buffer = ByteBuffer.allocate (capacity);
      oldBuffer.flip ();
      buffer.put (oldBuffer);
    }
    return (buffer);
  }

  private int chooseCapacity (int capacity, int needed) {               // Get power of 2 size >= needed size
    while (capacity < needed) {
      capacity *= 2;                                                    // Ensure capacity is >= needed and power of 2
    }
    if (capacity > mMaxBufferSize) {
      if (needed > mMaxBufferSize) {
        throw new IllegalArgumentException ("");//Requested size " + needed + " is larger than maximum buffer size " + mMaxBufferSize + ".");
      }
      capacity = mMaxBufferSize;
    }
    return (capacity);
  }


// 1 External API used:
  private void media_decode (ByteBuffer content) {                      // Decode audio or H264 video in client
    if (m_hu_act != null)
      m_hu_act.media_decode (content);                                  // Call callback
  }

// 5 External APIs provided:
  public void transport_start () {                                      // Start Transport ; messages on reader thread
    synchronized (m_lock) {
      if (m_bb_out == null) {
        throw new IllegalStateException ("");//Transport has been closed");
      }

      m_rx_thread = new rx_thread ();
      m_rx_thread.start ();                                                 // Create reader thread and start
    }
  }

  public void transport_stop () {                                       // Stop Transport
    synchronized (m_lock) {
      if (m_bb_out != null) {                                           // If we allocated the output buffer...
        if (m_rx_thread == null) {                                          // If no reader thread...
          io_close ();                                                   // Close IO
        }
        else {                                                          // Else if the reader thread was started then it will be responsible for closing the stream when it quits
                                                                        //  because it may currently be in the process of reading from the stream so we can't simply shut it down right now.

          m_rx_thread.quit ();                                              // Terminate reader thread using it's quit() API
        }
        m_bb_out = null;
      }
    }
  }

  public void aa_start () {
    byte [] ba = {0, 1, 0, 1};                                        // 1.1
    aa_send (0, 1, ByteBuffer.wrap (ba), ba.length);       // Version Request
  }

  public void aa_touch (byte action, int x, int y) {
    long ts = android.os.SystemClock.elapsedRealtimeNanos ();   // Timestamp in nanoseconds
                  //0   1 - 9                                           10                          X:     15, 16    Y:     18, 19                                        25
  //byte [] ba = {0x08, 0xe9, 0xf6, 0xb7, 0x8b, 0x8d, 0x2e, 0, 0, 0,    0x1a, 0x0e,   0x0a, 0x08,   0x08, 0x2e, 0,   0x10, 0x2b, 0,   0x18, 0x00,   0x10, 0x00,   0x18, 0x00};
    byte [] ba = {0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0, 0, 0,    0x1a, 0x0e,   0x0a, 0x08,   0x08, 0x2e, 0,   0x10, 0x2b, 0,   0x18, 0x00,   0x10, 0x00,   0x18, 0x00};
    int siz_arr = 0;

    int idx = 1 + hu_uti.varint_encode (ts, ba,  1);

    ba [idx ++] = 0x1a;
    int size1_idx = idx;
    ba [idx ++] = 0x0a;

    ba [idx ++] = 0x0a;
    int size2_idx = idx;
    ba [idx ++] = 0x04;

    ba [idx ++] = 0x08;
    siz_arr = hu_uti.varint_encode ( x, ba, idx);
    idx += siz_arr;
    ba [size1_idx] += siz_arr;
    ba [size2_idx] += siz_arr;

    ba [idx ++] = 0x10;
    siz_arr = hu_uti.varint_encode ( y, ba, idx);
    idx += siz_arr;
    ba [size1_idx] += siz_arr;
    ba [size2_idx] += siz_arr;

    ba [idx ++] = 0x18;
    ba [idx ++] = 0x00;

    ba [idx ++] = 0x10;
    ba [idx ++] = 0x00;

    ba [idx ++] = 0x18;
    ba [idx ++] = action;

    int chan = 2; // !!!!
    aa_send (chan, 32769, ByteBuffer.wrap (ba), idx);//ba.length);   // Touch report
  }

  public boolean aa_send (int channel, int msg_type, ByteBuffer content, int size) {
    try {
      synchronized (m_lock) {
        if (m_bb_out == null) {
          hu_uti.loge ("Send message failed because transport was closed.");
          return (false);
        }
        final byte [] ba_out = m_bb_out.array ();
        m_bb_out.clear ();
        m_bb_out.put ((byte) channel);                                  // Channel

        byte flag = (byte) 3;                                           // Flag: 0011                      Last Frag | First Frag
        if (channel == 0) {                                             // If default/control channel...
          if (msg_type > 4)                                             // If Service Discovery Request or higher     !!! Need to encrypt later ping responses or requests !!!
            flag = (byte) 0x0b;                                         // Flag: 1011  Encrypt |           Last Frag | First Frag
        }
        else {                                                          // Else for other channels...
          if (msg_type < 100)                                           // If a control message...
            flag = (byte) 0x0f;                                         // Flag: 1111  Encrypt | Control | Last Frag | First Frag
          else
            flag = (byte) 0x0b;                                         // Flag: 1011  Encrypt |           Last Frag | First Frag
        }
        m_bb_out.put (flag);                                            // Flags

        m_bb_out.putShort ((short) (size + 2));                         // short: size of following content
        m_bb_out.putShort ((short) msg_type);                           // short: message type

        if (content != null)                                            // If content...
          m_bb_out.put (content);                                       // Put the content

        int send_size = size + 6;// m_bb_out.position ();

        if (is_loggable_tx (channel, msg_type, content, send_size)) {   // If transmit loggable...
          hu_uti.logd ("TX: chan: " + channel + "  flag: " + flag + "  msg_type: " + msg_type + "  size: " + size + "  send_size: " + send_size);
          int max_dump_size = 64;
          int dump_size = send_size;
          if (dump_size > max_dump_size)
            dump_size = max_dump_size;
          hu_uti.hex_dump ("TX: ", m_bb_out, dump_size);
        }
        io_write (ba_out, 0, send_size);                                // Send packet
        return (true);                                                  // Done OK
      }
    }
    catch (IOException ex) {
      hu_uti.loge ("Send message failed: " + ex);
      return (false);                                                   // Done error
    }
  }


  private boolean log_all = false;//true;
  private boolean is_loggable_tx (int chan, int msg_type, ByteBuffer content, int size) {
    //byte [] buf = content.array ();
    //int len = buf.length;
    if (log_all)
      return (true);
    if (size > 2000)
      return (false);
    if (msg_type == 0x8004)  //buf [0] == 0x80 && buf [1] == 0x04)                // Tx Video Ack
      return (false);
    if (msg_type == 0x000c)  //buf [0] == 0x00 && buf [1] == 0x0c)                // Tx Ping response
      return (false);
    return (true);
  }

  private boolean is_loggable_rx (int chan, int msg_type, ByteBuffer content, int size) {
    //byte [] buf = content.array ();
    //int len = buf.length;
    if (log_all)
      return (true);
    if (size > 2000)
      return (false);
    if (chan == 1)    // For LastFrag
      return (false);
    if (msg_type == 0x0000 || msg_type == 0x0001)  //buf [0] == 0x00 && (buf [1] == 0 || buf [1] == 1)) // Rx Video
      return (false);
    if (msg_type == 0x000b)  //buf [0] == 0x00 && buf [1] == 0x0b)                // Rx Ping request
      return (false);
    return (true);
  }


  private final class rx_handler extends Handler {
    byte [] assy = new byte [65536 * 16];
    int assy_size = 0;

    byte [] assyf = null;//new byte [65536];

    @Override
    public void handleMessage (Message msg) {
      ByteBuffer buffer = (ByteBuffer) msg.obj;                         // Message is byte buffer with packet

      try {
        final int limit = buffer.limit ();
        int position = buffer.position ();
        byte [] baa = buffer.array ();
        int size = limit;
        size = 4 + hu_uti.int_get (baa [2], baa [3]);

        int max_size = 16384;
        if (size < 0 || size > max_size) {
          hu_uti.loge ("!!!!!!!!!!! size: " + size + "  max_size: " + max_size);
          size = max_size;
        }
        buffer.limit (size);

        int msg_type = hu_uti.int_get (baa [4], baa [5]);              // msg_type is not valid if not first fragment !
        int chan = (int) baa [0];
        int flag = (int) baa [1];

        if (is_loggable_rx (chan, msg_type, /*ByteBuffer content*/null, size)) {
          if (flag == 8)
            hu_uti.logd ("rx: chan: " + baa [0] + "  flag: " + baa [1] + "  msg_type: " + "MiddleFrag"  + "  size: " + size + "  position: " + position);
          else if (flag == 10)
            hu_uti.logd ("rx: chan: " + baa [0] + "  flag: " + baa [1] + "  msg_type: " + "LastFrag"    + "  size: " + size + "  position: " + position);
          else
            hu_uti.logd ("rx: chan: " + baa [0] + "  flag: " + baa [1] + "  msg_type: " + msg_type       + "  size: " + size + "  position: " + position);
          int max_dump_size = 64;
          int dump_size = size;
          if (dump_size > max_dump_size)
            dump_size = max_dump_size;
          hu_uti.hex_dump ("rx: ", buffer, dump_size);
        }


        if (msg_type == 0 || msg_type == 1 || flag == 8 || flag == 9 || flag == 10 ) {      // If Video...
          byte [] ba = {0x08, 0, 0x10,  1};                                               // Ack 0, 1
          aa_send (chan, 32772, ByteBuffer.wrap (ba), ba.length);                         // Respond with Ack

          if (chan != 1)
            hu_uti.loge ("Video unexpected channel");

          boolean bypass_vid = false;
          if (bypass_vid) {
            hu_uti.logd ("bypass_vid");
          }
          else if (flag == 11 && (msg_type == 0 || msg_type == 1) && (baa [14] == 0 && baa [15] == 0 && baa [16] == 0 && baa [17] == 1)) {  // If Not fragmented Video
            buffer.position (14);
            media_decode (buffer);                                      // Decode H264 video in client
          }
          else if (flag == 9 && (msg_type == 0 || msg_type == 1) && (baa [18] == 0 && baa [19] == 0 && baa [20] == 0 && baa [21] == 1)) {   // If First fragment Video
            System.arraycopy (baa, 18, assy, 0, size - 18 + 4); // !!!! ???? Why + 4 ?. because size in bytes 2,3 doesn't include 4 bytes size at 4,5,6,7
            assy_size = size - 18 + 4;
          }
          else if (flag == 11 && msg_type == 1 && (baa [6] == 0 && baa [7] == 0 && baa [8] == 0 && baa [9] == 1)) {                         // If Not fragmented First video config packet
            buffer.position (6);
            media_decode (buffer);                                      // Decode H264 video in client
          }
          else if (flag == 8) {                                                                                                             // If Middle fragment Video
            System.arraycopy (baa, 4, assy, assy_size, size - 4);
            assy_size += size - 4;
          }
          else if (flag == 10) {                                                                                                            // If Last fragment Video
            System.arraycopy (baa, 4, assy, assy_size, size - 4);
            assy_size += size - 4;

            assyf = new byte [assy_size];
            System.arraycopy (assy, 0, assyf, 0, assy_size);
            ByteBuffer bb_assyf = ByteBuffer.wrap (assyf);
            bb_assyf.limit (assy_size);
            bb_assyf.position (0);

            media_decode (bb_assyf);                                    // Decode H264 video in client
          }
          else
            hu_uti.loge ("Video error");


        }
        //else if......

/*
1   Ver Req     HU > AA
2   Ver Resp    AA > HU

3   HS          HU > AA
3   HS          AA > HU

4   Auth Comp   HU > AA*/
          // Version request:
          // 04-19 06:40:22.062 D/hu_act ( 6790): SendAaMessage channel: 0  msg_type: 1  size: 4  send_size: 10
          // 04-19 06:40:22.063 D/hu_act ( 6790): SendAaMessage HEX 00000000: 00 03 00 06 00 01 00 01 00 01 

        else if (msg_type == 2) {                                         // If Version response...
          // Version response:
          // 04-19 06:40:22.112 D/hu_act ( 6790): handleMessage msg_type: 2  size: 12  limit: 12  position: 0
          // 04-19 06:40:22.112 D/hu_act ( 6790): handleMessage HEX 00000000: 00 03 00 08 00 02 00 01 00 01 00 00 
          byte [] ba = {4, 3, 2, 1};                                    // ?
          aa_send (0, 3, ByteBuffer.wrap (ba), ba.length);        // Respond with SSL Handshake
          // SSL Handshake request:
          // 04-19 06:40:22.112 D/hu_act ( 6790): SendAaMessage channel: 0  msg_type: 3  size: 4  send_size: 10
          // 04-19 06:40:22.112 D/hu_act ( 6790): SendAaMessage HEX 00000000: 00 03 00 06 00 03 04 03 02 01 
        }
        else if (msg_type == 3) {                                        // If SSL Handshake...   //          if (size >= 14 && size <= 14) {
          // SSL Handshake response:
          // 04-19 06:40:22.124 D/hu_act ( 6790): handleMessage msg_type: 3  size: 14  limit: 16384  position: 0
          // 04-19 06:40:22.124 D/hu_act ( 6790): handleMessage HEX 00000000: 00 03 00 0A 00 03 00 03 04 03 02 01 00 00 
          byte [] ba = {8, 0};                                          // Status 0 = OK
          aa_send (0, 4, ByteBuffer.wrap (ba), ba.length);        // Respond with Auth Complete
          // Auth Complete response
          // 04-19 06:40:22.124 D/hu_act ( 6790): SendAaMessage channel: 0  msg_type: 4  size: 2  send_size: 8
          // 04-19 06:40:22.124 D/hu_act ( 6790): SendAaMessage HEX 00000000: 00 03 00 04 00 04 08 00 
        }
        else if (msg_type == 5) {                                        // If Service Discovery Request (w/ 3 PNG Icons & "Android" string)...
          // Service Discovery Request:
          // 04-19 06:40:22.142 D/hu_act ( 6790): handleMessage msg_type: 5  size: 5144  limit: 5144  position: 0
          // 04-19 06:40:22.142 D/hu_act ( 6790): handleMessage HEX 00000000: 00 0B 14 14 00 05 0A 80 08 89 50 4E 47 0D 0A 1A ........

          // 04-08 03:49:56.637 D/hu_act ( 4616): handleMessage HEX 00000000: 00 0B 14 14 00 05                             0A 80 08    89 50 4E 47 0D 0A 1A  Len 0x0400 fr 0x0009 - 0x0408    PNG   (9 - 0x3ed = 997 bytes
          // 04-08 03:49:56.651 D/hu_act ( 4616): handleMessage HEX 00000400: 00 00 00 00 00 00 00 00 00                    12 80 10    89 50 4E 47           Len 0x0800 fr 0x040c - 0x0c0b    PNG
          // 04-08 03:49:56.678 D/hu_act ( 4616): handleMessage HEX 00000C00: C0 7D 31 90 04 F4 2C 10 D8 0F 07 1C           1A 80 10    89                    Len 0x0800 fr 0x0c0f - 0x140e    PNG
          // 04-08 03:49:56.696 D/hu_act ( 4616): handleMessage HEX 00001400: F4 FF 48 44 7C 07 1C F3 63 28 0C AB 68 40 36  22 07       41 6E 64 72 6F 69 64  Len 0x0007 fr 0x1401 - 0x1407

          // Service Discovery Response:
          byte [] ba = {
/*
cq  (co[  (str  (str  (str  (str  (int  (str  (str  (str  (str  (boo  (boo    MsgServiceDiscoveryResponse

  co  int (cm (bd (ak (bi (m  (ce (bq (bb (cb (av (cy (ad       co[] a()      MsgAllServices

    cm  (cn[                                                                  MsgSensors
      cn  int                                                   cn[] a()      MsgSensorSourceService  Fix name to MsgSensor

    bd  (int  (int  (f[ (cz[  (boo                                            MsgMediaSinkService
       f  int   int   int                                        f[] a()      MsgAudCfg   See same below
      cz  (int  (int  (int  (int  (int  (int                    cz[] a()      MsgVidCfg

    ak  (int[ (am[  (al[                                                      MsgInputSourceService   int[] = keycodes    Graphics Points ?
      am  int   int                                             am[] a()      TouchScreen width, height
      al  int   int                                             al[] a()      TouchPad    width, height

Audio Config:
  sampleRate
  channelConfig
  audioFormat

public final class MsgMediaSinkService extends k                        // bd/MsgMediaSinkService extends k/com.google.protobuf.nano.MessageNano
{
  public int      a                 = 0;                                // a
  public int      mCodecType        = 1;                                // b
  public int      mAudioStreamType  = 1;                                // c
  public f[]      mAudioStreams     = f.a();                            // f[]:d    a:samplingRate    b:numBits     c:channels
  public cz[]     mCodecs           = cz.a();                           // cz[]:e   b:codecResolution 1=800x480 2=1280x720 3=1920x1080
                                                                                //  c:0/1 for 30/60 fps   d:widthMargin e:heightMargin f:density/fps g: ?
  private boolean f                 = false;                            // f

*/
// D/CAR.GAL ( 3804): Service id=1 type=MediaSinkService { codec type=1 { codecResolution=1 widthMargin=0 heightMargin=0 density=30}}

///*                      //cq/co[]
            // CH 1 SENSORS:
                        0x0A, 4 + 4*1,//co: int, cm/cn[]
                                      0x08, 3,  0x12, 4*1,
                                                          0x0A, 2,
                                                                    0x08, 11, // SENSOR_TYPE_DRIVING_STATUS 12  */
/*  Requested Sensors: 10, 9, 2, 7, 6:
                        0x0A, 4 + 4*5,     //co: int, cm/cn[]
                                      0x08, 1,  0x12, 4*5,
                                                          0x0A, 2,
                                                                    0x08,  3, // SENSOR_TYPE_RPM            2
                                                          0x0A, 2,
                                                                    0x08,  8, // SENSOR_TYPE_DIAGNOSTICS    7
                                                          0x0A, 2,
                                                                    0x08,  7, // SENSOR_TYPE_GEAR           6
                                                          0x0A, 2,
                                                                    0x08,  1, // SENSOR_TYPE_COMPASS       10
                                                          0x0A, 2,
                                                                    0x08, 10, // SENSOR_TYPE_LOCATION       9   */
            // CH 2 Video Sink:
///*                        
                        0x0A, 4+4+11, 0x08, 1,    // Channel 1

                                      0x1A, 4+11, // Sink: Video
                                                  0x08, 3,    // int (codec type)
                                                  //0x10, 1,    // int (audio stream type) (1-4)
//                                                  0x1a, 8,    // f        //I44100 = 0xAC44 = 10    10 1  100 0   100 0100  :  -60, -40, 2
                                                                            // 48000 = 0xBB80 = 10    111 0111   000 0000     :  -128, -9, 2
                                                                            // 16000 = 0x3E80 = 11 1110 1   000 0000          :  -128, -6
                                                  0x22, 11,   // cz
                                                              0x08, 1, 0x10, 1, 0x18, 0, 0x20, 0, 0x28, -96, 1,   //0x30, 0,      // res800x480, 30 fps, 0, 0, 160 dpi    0xa0
                                                              //0x08, 1, 0x10, 2, 0x18, 0, 0x20, 0, 0x28, -96, 1,   //0x30, 0,      // res800x480, 60 fps, 0, 0, 160 dpi    0xa0
                                                              //0x08, 2, 0x10, 1, 0x18, 0, 0x20, 0, 0x28, -96, 1,   //0x30, 0,      // res1280x720, 30 fps, 0, 0, 160 dpi    0xa0
                                                              //0x08, 3, 0x10, 1, 0x18, 0, 0x20, 0, 0x28, -96, 1,   //0x30, 0,      // res1920x1080, 30 fps, 0, 0, 160 dpi    0xa0        

///*
            // CH 3 TouchScreen:
                        0x0A, 4+2+6, 0x08, 2,  // Channel 2

//                                                              0x08, -128, -9, 2,    0x10, 16,   0x18, 2,
                                                  //0x28, 0, //1,   boolean


                                      0x22, 2+6,//+2+16, // ak  Input
                                                  //0x0a, 16,   0x03, 0x54, 0x55, 0x56, 0x57, 0x58, 0x7e, 0x7f,   -47, 1,   -127, -128, 4,    -124, -128, 4,
                                                  0x12,  6,        // no int[], am      // 800 = 0x0320 = 11 0    010 0000 : 32+128(-96), 6
                                                                      // 480 = 0x01e0 = 1 1     110 0000 =  96+128 (-32), 3
                                                              0x08, -96, 6,    0x10, -32, 3,        // 800x480
                                                              //0x08, -128, 10,    0x10, -48, 5,        // 1280x720     0x80, 0x0a   0xd0, 5
                                                              //0x08, -128, 15,      0x10,  -72, 8, // 1920x1080      0x80, 0x0f      0xb8, 8      

//*/                                                                                        
                        0x12, 1, 'A', // Car Manuf
                        0x1A, 1, 'B', // Car Model
                        0x22, 1, 'C', // Car Year
                        0x2A, 1, 'D', // Car Serial
                        0x30, 0,      // driverPosition
                        0x3A, 1, 'E', // HU  Make / Manuf
                        0x42, 1, 'F', // HU  Model
                        0x4A, 1, 'G', // HU  SoftwareBuild
                        0x52, 1, 'H', // HU  SoftwareVersion
                        0x58, 1,//0,//1,       // ? bool (or int )    canPlayNativeMediaDuringVr
                        0x60, 1,//0,//1        // mHideProjectedClock

// 04-22 03:43:38.049 D/CAR.SERVICE( 4306): onCarInfo com.google.android.gms.car.CarInfoInternal[dbId=0,manufacturer=A,model=B,headUnitProtocolVersion=1.1,modelYear=C,vehicleId=null,
// bluetoothAllowed=false,hideProjectedClock=false,driverPosition=0,headUnitMake=E,headUnitModel=F,headUnitSoftwareBuild=G,headUnitSoftwareVersion=H,canPlayNativeMediaDuringVr=false]

                 };

          aa_send (0, 6, ByteBuffer.wrap (ba), ba.length);           // Respond with Service Discovery Response

          // 04-19 06:40:22.198 D/hu_act ( 6790): SendAaMessage channel: 0  msg_type: 6  size: 52  send_size: 58
          // 04-19 06:40:22.198 D/hu_act ( 6790): SendAaMessage HEX 00000000: 00 0B 00 36 00 06 0A 14 08 01 1A 10 08 01 10 01 
          // 04-19 06:40:22.198 D/hu_act ( 6790): SendAaMessage HEX 00000010: 22 0A 08 01 10 00 18 00 20 00 28 1E 12 01 41 1A 
          // 04-19 06:40:22.198 D/hu_act ( 6790): SendAaMessage HEX 00000020: 01 42 22 01 43 2A 01 44 30 7F 3A 01 45 42 01 46 
          // 04-19 06:40:22.199 D/hu_act ( 6790): SendAaMessage HEX 00000030: 4A 01 47 52 01 48 58 7F 60 7F 

        }

          /* Channel Open Request for sensors when channel 5 specified in service discovery response:
             04-18 23:00:26.539 D/hu_act (23350): handleMessage msg_type: 7  size: 10  limit: 10  position: 0
             04-18 23:00:26.539 D/hu_act (23350): handleMessage HEX 00000000: 05 0F 00 06 00 07 08 00 10 05      Int:0   Int:5   (OK, Channel 5) */

        else if (msg_type == 7) {                                        // If Channel Open Request...
          // 04-19 06:40:22.373 D/hu_act ( 6790): handleMessage msg_type: 7  size: 10  limit: 10  position: 0
          // 04-19 06:40:22.373 D/hu_act ( 6790): handleMessage HEX 00000000: 01 0F 00 06 00 07 08 00 10 01      Int:0   Int:1   (OK, Channel 1)

          byte [] ba = {8, 0};                                          // Status 0 = OK
          aa_send (chan, 8, ByteBuffer.wrap (ba), ba.length);        // Respond with Channel Open OK
          // 04-19 06:40:22.373 D/hu_act ( 6790): SendAaMessage channel: 1  msg_type: 8  size: 2  send_size: 8
          // 04-19 06:40:22.373 D/hu_act ( 6790): SendAaMessage HEX 00000000: 01 0F 00 04 00 08 08 00            Int:0 = OK

          if (chan == 3) {                                              // If Sensors...
            hu_uti.ms_sleep (20);
            //byte [] bg = {0x42, 2, 8, 101};                           // Gear = GEAR_PARK
            byte [] bg = {0x6a, 2, 8, 0};                               // Driving Status = Parked (1 = Moving)
            aa_send (chan, 32771, ByteBuffer.wrap (bg), bg.length);     // Respond with Sensor Batch 
          }
        }

        else if (msg_type == 11) {                                       // If Ping Request...   Every 1 second
          // 04-23 21:40:32.386 W/sslw    (16193): rx:  00000000 00 0b 08 d0 d0 e2 e6 bb 15 10 00 
          // 04-23 21:40:32.387 D/CAR.DIAGNOSTICS(16193): onPingRequest: timestamp=737607723088
          // 04-23 21:40:32.388 W/sslw    (16193): TX:  00000000 00 0c 08 d0 d0 e2 e6 bb 15 

          //aa_send (chan, 12, ByteBuffer.wrap (baa), baa.length - 2);  // Respond with Ping Response
          aa_send (chan, 12, ByteBuffer.wrap (baa), size - 2);          // Respond with Ping Response
        }
        else if (msg_type == 12) {                                       // If Ping Response...
        }

        else if (msg_type == 15) {                                       // If ByeBye Request...
          // 04-23 21:37:40.674 D/CAR.SERVICE(12332): onSetupFailure
          // 04-23 21:37:40.674 D/CAR.GAL (12332): send ByeByeRequest
          // 04-23 21:37:40.674 W/sslw    (12332): TX:  00000000 00 0f 08 01 
          byte [] ba = {8, 0};                                          // Status 0 = OK
          aa_send (chan, 16, ByteBuffer.wrap (ba), ba.length);    // Respond with ByeBye Response
        }


        else if (msg_type == 32768) {                                    // If Media Setup Request...
          // 04-22 01:13:35.384 D/hu_act (18103): handleMessage chan: 1  flag: 11  msg_type: 32768  size: 8  position: 0
          // 04-22 01:13:35.384 D/hu_act (18103): handleMessage 00000000: 01 0B 00 04 80 00 08 03             // Int:3 = MediaType Video

          byte [] ba = {0x08, 2, 0x10, 48, 0x18, 0};//0x1a, 4, 0x08, 1, 0x10, 2};      // 1/2, MaxUnack, int[] 1        2, 0x08, 1};//
          aa_send (chan, 32771, ByteBuffer.wrap (ba), ba.length);    // Respond with Config Response
          // 04-19 06:40:18.929 D/CAR.MEDIA(11089): configMessage, MAX_UNACK:3

          if (chan == 1) {                                              // If Video...
            //80 08 08 01 10 01              1,  1        VideoFocus gained focusState=0 unsolicited=true
            hu_uti.ms_sleep (20);
            byte [] bg = {0x08, 1, 0x10, 1};                            // 1, 1
            aa_send (chan, 32776, ByteBuffer.wrap (bg), bg.length);     // Respond with VideoFocus gained/notif focusState=0 unsolicited=true
          }
        }

        else if (msg_type == 32770) {                                    // If TouchScreen/Input Start Request...    Or "send setup, ch:X" for channel X
          // 04-22 01:13:35.480 D/hu_act (18103): handleMessage chan: 2  flag: 11  msg_type: 32770  size: 6  position: 0
          // 04-22 01:13:35.480 D/hu_act (18103): handleMessage 00000000: 02 0B 00 02 80 02 

          byte [] ba = {0x08, 0};                                       // 
          aa_send (chan, 32771, ByteBuffer.wrap (ba), ba.length); // Respond with Key Binding Response = OK

          // Keycodes:
          // 04-21 05:23:10.419 D/hu_act (15493): handleMessage chan: 2  flag: 11  msg_type: 32770  size: 24  position: 0
          // 04-21 05:23:10.419 D/hu_act (15493): handleMessage 00000000: 02 0B 00 14 80 02 0A 10 03 54 55 56 57 58 7E 7F 
          // 04-21 05:23:10.419 D/hu_act (15493): handleMessage 00000010: D1 01 81 80 04 84 80 04 
        }

        else if (msg_type == 32769) {                                    // If Sensor Start Request...
                // ?? Also: Input Keycodes Start ?    Respond with: 00000000 80 02 08 00 10 01    32770     0, 1 ?
          if (chan == 3) {
            // Start Sensors, rate = 3:
            // 04-18 01:22:36.317 D/hu_act (26343): handleMessage size: 10  limit: 10  position: 0
            // 04-18 01:22:36.317 D/hu_act (26343): handleMessage HEX 00000000: 01 0B 00 06 80 01 08 07 10 00   // 32769: Int:7 Int:0     7 = car.CarSensorManager.SENSOR_TYPE_GEAR
            byte [] ba = {8, 0};                                          // Status 0 = OK
            aa_send (chan, 32770, ByteBuffer.wrap (ba), ba.length);    // Respond with Sensor Response

            hu_uti.ms_sleep (20);
            //byte [] bg = {66, 2, 8, 101};                                         // Gear = GEAR_PARK
            //byte [] bg = {66, /*4, 0x0a,*/ 2, 8, 101};                                         // Gear = GEAR_PARK
            byte [] bg = {106, /*4, 0x0a,*/ 2, 8, 1}; // Driving Status = Stopped
            aa_send (chan, 32771, ByteBuffer.wrap (bg), bg.length);    // Respond with Sensor Batch
          }
        }

      }
      finally {
        release (buffer);
        buffer_busy = false;
      }
    }
  } // final class rx_handler extends Handler {


  private boolean buffer_busy = false;

  private final class rx_thread extends Thread {                             // Reader thread

    private volatile boolean mQuitting;                                 // Set true when quitting
/*
    public rx_thread () {
      //super ("MyReader");                                               // Name thread
    }
*/
    @Override
    public void run () {
      loop ();                                                          // Loop until quitting...
      io_close ();                                                       // Then close IO and terminate thread
    }

    private int header_size = 4;
    private void loop () {
      ByteBuffer buffer = null;
int length = 6;//65536;//header_size;                                 // Length = size of 4 bytes header to start
      int contentSize = -1;

      boolean have_buffer_already = false;
      outer: while (! mQuitting) {                                      // While not quiting...

        int ctr = 0;
        while (! have_buffer_already && buffer_busy && ctr ++ < 200) {
          hu_uti.ms_sleep (10);
        }
        buffer_busy = true;

        have_buffer_already = false;

        if (buffer == null) {                                           // Acquire or grow a buffer as needed
          //buffer = ByteBuffer.allocate (65536);
          buffer = acquire (length);
        }
        //buffer.limit (65536);


        // Read more data until needed number of bytes obtained.
        int position = buffer.position ();                              // Get current position
        int bytes_read;
        try {                                                           // Read to current position
          bytes_read = io_read (buffer.array (), position, buffer.capacity () - position);
          if (bytes_read < 0) {
            break;                                                      // Done thread if end of stream
          }
        }
        catch (IOException ex) {
          hu_uti.loge ("Read failed: " + ex);
          break;                                                        // Done thread if error
        }
        position += bytes_read;
        buffer.position (position);                                     // Set position to end of newly read data (for next io_read)

        if (length == header_size && position >= header_size) {       // If first and have a header...
          contentSize = (int) buffer.getShort (2);                      // 16 bit int: size
          if (contentSize < 0 || contentSize > max_content_size) {
            hu_uti.loge ("Encountered invalid content size 1: " + contentSize);
            break; // malformed stream
          }
          length += contentSize;    // length of 4 byte header and content
        }
        else if (position < header_size) {
          hu_uti.loge ("position < header_size    position: " + position + "  length: " + length);
        }

        if (position < length) {
          have_buffer_already = true;
          hu_uti.loge ("Need more data position: " + position + "  length: " + length);
          continue; // need more data
        }

        // There is one complete message in the buffer.
length = 6;//65536;//header_size; // For next time

        buffer.limit (length);
        buffer.rewind ();
        m_rx_handler.obtainMessage (0, buffer).sendToTarget ();             // Obtain byte buffer message and send to target message handler

      }

      if (buffer != null) {
        release (buffer);                                 // Release buffer
        //buffer.clear ();
        ////buffer = null;
      }
    }

    public void quit () {
      mQuitting = true;                                                 // Terminate thread
    }

  } // final class rx_thread extends Thread {



  private void io_close () {
    mConnection = null;
    mBulkInEndpoint = null;
    mBulkOutEndpoint = null;
  }

  private int io_read (byte [] buffer, int offset, int count) throws IOException {
    if (mConnection == null) {
      throw new IOException ("");//Connection was closed.");
    }
    return (mConnection.bulkTransfer (mBulkInEndpoint, buffer, offset, count, -1));
  }

  private void io_write (byte [] buffer, int offset, int count) throws IOException {
    if (mConnection == null) {
      throw new IOException ("");//Connection was closed.");
    }
    int result = mConnection.bulkTransfer (mBulkOutEndpoint, buffer, offset, count, 1000);  // 1 second timeout
    if (result < 0) {
      throw new IOException ("");//Bulk transfer failed.");
    }
  }



  public void usb_start (Intent intent) {
    hu_uti.logd ("Waiting for accessory display source to be attached to USB...");

    IntentFilter filter = new IntentFilter ();
    filter.addAction (UsbManager.ACTION_USB_DEVICE_ATTACHED);
    filter.addAction (UsbManager.ACTION_USB_DEVICE_DETACHED);
    filter.addAction (hu_uti.str_usb_perm);
    m_usb_receiver = new usb_receiver ();                                    // Register BroadcastReceiver for USB attached/detached
    m_context.registerReceiver (m_usb_receiver, filter);

    String action = intent.getAction ();
    hu_uti.logd ("intent: " + intent + "  action: " + action);

    if (action.equals (UsbManager.ACTION_USB_DEVICE_ATTACHED)) {        // If we were launched by a USB connection event...
      UsbDevice device = intent.<UsbDevice>getParcelableExtra (UsbManager.EXTRA_DEVICE);
      hu_uti.logd ("Launched by USB connection event device: " + device);
      if (device != null) {
        usb_attach_handler (device);                                        // Take action if our device
      }
    }
    else {                                                              // Else look through the USB device list...
      Map<String, UsbDevice> devices = m_usb_mgr.getDeviceList ();
      hu_uti.logd ("Launched by hand, check for devices: " + devices);
      if (devices != null) {
        for (UsbDevice device : devices.values ()) {
          usb_attach_handler (device);                                      // Take action if our device
        }
      }
    }
  }

  public void usb_stop () {

    byte [] ba = {8, 0};                                                // Status 0 = OK
    aa_send (0, 15, ByteBuffer.wrap (ba), ba.length);                   // ByeBye Request

    hu_uti.ms_sleep (200);                                             // Wait for response

    if (m_usb_receiver != null)
      m_context.unregisterReceiver (m_usb_receiver);
    m_usb_receiver = null;
  }

  private class usb_receiver extends BroadcastReceiver {
    @Override
    public void onReceive (Context context, Intent intent) {
      UsbDevice device = intent.<UsbDevice>getParcelableExtra (UsbManager.EXTRA_DEVICE);
      hu_uti.logd ("USB BCR device: " + device);

      if (device != null) {
        String action = intent.getAction ();
        hu_uti.logd ("USB BCR action: " + action);
        if (action.equals (UsbManager.ACTION_USB_DEVICE_DETACHED)) {    // If detach...
          usb_detach_handler (device);
        }
        else if (action.equals (UsbManager.ACTION_USB_DEVICE_ATTACHED)){// If attach...
          usb_attach_handler (device);                                  // Take action if our device
        }
        else if (action.equals (hu_uti.str_usb_perm)) {                 // If permission
          if (intent.getBooleanExtra (UsbManager.EXTRA_PERMISSION_GRANTED, false)) {  // If granted...
            hu_uti.logd ("Device permission granted: " + device);
            usb_attach_handler (device);                                // Take action if our device
          }
          else {
            hu_uti.loge ("Device permission denied: " + device);
          }
        }
      }
    }
  }

  private void usb_attach_handler (UsbDevice device) {                  // Take action if our device
    hu_uti.logd ("USB usb_attach_handler device: " + device + "  m_usb_connected: " + m_usb_connected);
    if (! m_usb_connected) {                                            // If not already connected...
      usb_connect (device);                                             // Attempt to connect
    }
    if (m_usb_connected) {                                              // If connected now, or was connected already...
      hu_uti.logd ("USB usb_attach_handler connected, sending version request");
      hu_uti.ms_sleep (1000);                                           // Wait to settle
      aa_start ();
    }
    else
      hu_uti.loge ("USB usb_attach_handler NOT connected");
  }

  private void usb_detach_handler (UsbDevice device) {
    hu_uti.logd ("USB usb_detach_handler device: " + device + "  m_usb_device: " + m_usb_device + "  m_usb_connected: " + m_usb_connected);
    if (m_usb_connected && device.equals (m_usb_device)) {
      usb_disconnect ();
    }
  }

  // "Android", "Android Open Automotive Protocol", "Description", "VersionName", "https://developer.android.com/auto/index.html", "62skidoo"
  // "Android", "Android Auto", "Description", "VersionName", "https://developer.android.com/auto/index.html", "62skidoo"

  private boolean usb_perm_enable = true;//false;
  private boolean chk_perm_enable = true;//false;

  private void usb_connect (UsbDevice device) {                         // Attempt to connect
    hu_uti.logd ("connect device: " + device);
    if (m_usb_connected) {                                              // If already connected... (should not happen, untested code)
      hu_uti.loge ("Already connected so disconnect first !!");
      usb_disconnect ();                                                // Disconnect first
    }
/*
    if (chk_perm_enable && m_usb_mgr.hasPermission (device)) {                             // If we have permission to access the USB device...
      hu_uti.logd ("Have USB Permission");
    }
    else if (usb_perm_enable) {                                         // Else if we don't have permission to access the USB device but permission request is enabled...
      hu_uti.logd ("No  USB Permission; Prompting the user for access to the device");
      Intent intent = new Intent (hu_uti.str_usb_perm);
      intent.setPackage (m_context.getPackageName ());
      PendingIntent pendingIntent = PendingIntent.getBroadcast (m_context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
      m_usb_mgr.requestPermission (device, pendingIntent);              // Request permission. Come back later if we get it.
      return;
    }
    else {
      hu_uti.loge ("No  USB Permission !!!; Prompting the user disabled");
    }
*/

    // Claim the device.
    UsbDeviceConnection connection = m_usb_mgr.openDevice (device);
    if (connection == null) {
      hu_uti.loge ("Could not obtain device connection");
      return;
    }
    UsbInterface iface;
    try {
      iface = device.getInterface (0);         // java.lang.ArrayIndexOutOfBoundsException: length=0; index=0
      UsbEndpoint controlEndpoint = iface.getEndpoint (0);
      if (! connection.claimInterface (iface, true)) {
        hu_uti.loge ("Could not claim interface");
        return;
      }
    }
    catch (Throwable e) {
      hu_uti.loge ("Throwable: " + e);
      return;
    }
    try {
      final int vid = device.getVendorId ();
      final int pid = device.getProductId ();

      if (vid == OAP_VID_GOOG && (pid == OAP_PID_OAP_NUL || pid == OAP_PID_OAP_ADB)) {  // If already in accessory mode, then connect to the device.
        hu_uti.logd ("Already in accessory mode, Connecting to accessory...");

        int protocolVersion = usb_oap_version_get (connection);
        if (protocolVersion < 1) {
          hu_uti.loge ("Device does not support accessory protocol.");
          return;
        }
        hu_uti.logd ("version: " + protocolVersion);

        UsbEndpoint bulkIn = null;                                      // Setup bulk endpoints.
        UsbEndpoint bulkOut = null;
        for (int i = 0; i < iface.getEndpointCount (); i ++) {          // For all USB endpoints...
          UsbEndpoint ep = iface.getEndpoint (i);
          if (ep.getDirection () == UsbConstants.USB_DIR_IN) {          // If IN
            if (bulkIn == null) {                                       // If Bulk In not set yet...
              hu_uti.logd (String.format ("Bulk IN endpoint: %d", i));
              bulkIn = ep;                                              // Set Bulk In
            }
          }
          else {                                                        // Else if OUT...
            if (bulkOut == null) {                                      // If Bulk Out not set yet...
              hu_uti.logd (String.format ("Bulk OUT endpoint: %d", i));
              bulkOut = ep;                                             // Set Bulk Out
            }
          }
        }
        if (bulkIn == null || bulkOut == null) {
          hu_uti.loge ("Unable to find bulk endpoints");
          return;
        }

        hu_uti.logd ("Connected");
        m_usb_connected = true;
        m_usb_device = device;

        usb_setup (connection, bulkIn, bulkOut);         // Setup transport


        //m_hu_act.do_surface_view_set ();        // Set Surface view for H264 decoding

        transport_start ();                                             // Start transport

        return;                                                         // Done
      }

      // Else if not already in accessory mode, do accessory negotiation and attempt to switch to accessory mode:
      hu_uti.logd ("Attempting to switch device to accessory mode...");

      // Send get protocol.
      int oap_proto_ver = usb_oap_version_get (connection);
      if (oap_proto_ver < 1) {
        hu_uti.loge ("Device does not support accessory protocol.");
        return;
      }
      hu_uti.logd ("oap_proto_ver: " + oap_proto_ver);

      // Send identifying strings.
      usb_oap_string_send (connection, OAP_STR_MANUFACTURER,hu_uti.str_MAN);
      usb_oap_string_send (connection, OAP_STR_MODEL,       hu_uti.str_MOD);
      usb_oap_string_send (connection, OAP_STR_DESCRIPTION, hu_uti.str_DES);
      usb_oap_string_send (connection, OAP_STR_VERSION,     hu_uti.str_VER);
      usb_oap_string_send (connection, OAP_STR_URI,         hu_uti.str_URI);
      usb_oap_string_send (connection, OAP_STR_SERIAL,      hu_uti.str_SER);

      // Send start. The device should re-enumerate as an accessory.
      hu_uti.logd ("Sending accessory start request.");
      int len = connection.controlTransfer (UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, OAP_START, 0, 0, null, 0, 10000);
      if (len != 0) {
        hu_uti.loge ("Device refused to switch to accessory mode.");
      }
      else {
        hu_uti.logd ("Waiting for device to re-enumerate...");
      }
    }

    finally {
      if (! m_usb_connected) {                                               // If not connected, release Interface
        connection.releaseInterface (iface);
      }
    }
  }

  private void usb_disconnect () {
    hu_uti.logd ("usb_disconnect m_usb_device: " + m_usb_device);
    m_usb_connected = false;
    m_usb_device = null;
    transport_stop ();
  }

  private int usb_oap_version_get (UsbDeviceConnection connection) {                   // Get OAP protocol version
    byte buffer  [] = new byte  [2];
    int len = connection.controlTransfer (UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR, OAP_GET_PROTOCOL, 0, 0, buffer, 2, 10000);
    if (len != 2) {
      return -1;
    }
    return (buffer  [1] << 8) | buffer  [0];
  }

  private void usb_oap_string_send (UsbDeviceConnection connection, int index, String string) {
    byte  [] buffer = (string + "\0").getBytes ();
    int len = connection.controlTransfer (UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, OAP_SEND_STRING, 0, index, buffer, buffer.length, 10000);
    if (len != buffer.length) {
      hu_uti.loge ("Failed to send string " + index + ": \"" + string + "\"");
    }
    else {
      hu_uti.logd ("Sent string " + index + ": \"" + string + "\"");
    }
  }


}

