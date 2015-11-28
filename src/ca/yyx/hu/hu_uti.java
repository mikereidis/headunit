
    // General utility functions

// hu_uti.log


package ca.yyx.hu;

import android.bluetooth.BluetoothAdapter;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.pm.PackageInfo;

import java.net.SocketTimeoutException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.io.FileInputStream;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


public final class hu_uti  {

    // Stats:
  private static int            m_obinits = 0;

  public  static final int      android_version = android.os.Build.VERSION.SDK_INT;

  public hu_uti () {                                                    // Default constructor

    //hu_uti.logd ("m_obinits: " + m_obinits++);   // !! Can't log from internal code, class is not set up yet !!
    final String tag = tag_prefix_get () + "comuti";
    m_obinits ++;
    Log.d (tag, "m_obinits: " + m_obinits);


    Thread.setDefaultUncaughtExceptionHandler (new Thread.UncaughtExceptionHandler () {
      public void uncaughtException (Thread aThread, Throwable aThrowable) {
        //hu_uti.loge ("!!!!!!!! Uncaught exception: " + aThrowable);
        Log.e (tag, "!!!!!!!! Uncaught exception: " + aThrowable);
      }
    });
    //hu_uti.loge ("done");

    Log.e (tag, "done");
  }

    // Android Logging Levels:
  public  static final boolean ena_log_verbo = false;
  private static final boolean ena_log_debug = true;//false;//true;
  private static final boolean ena_log_warni = true;//false;//true;
  private static final boolean ena_log_error = true;

  private static String tag_prefix = "";
  private static final int max_log_char = 7;//8;

  private static String tag_prefix_get () {
    try {
      if (tag_prefix != null && ! tag_prefix.equals (""))
        return (tag_prefix);
      String pkg = "ca.yyx.hu";
      tag_prefix = pkg.substring (7);
      if (tag_prefix.equals (""))
        tag_prefix = "s!";
    }
    catch (Throwable e) {
      //hu_uti.loge ("Throwable e: " + e);
      Log.e ("hu_uti", "Throwable e: " + e);
      e.printStackTrace ();
      tag_prefix = "E!";
    }
    return (tag_prefix);
  }

  private static boolean old_log = false;
  private static void log (int level, String text) {
    String full_tag = "";
    String full_txt = "";
    //final StackTraceElement   stack_trace_el2 = new Exception ().getStackTrace () [3];
    //String method2 = stack_trace_el2.getMethodName ();
    final StackTraceElement   stack_trace_el = new Exception ().getStackTrace () [2];
    String method = stack_trace_el.getMethodName ();

    if (old_log) {
      String tag = stack_trace_el.getFileName ().substring (0, max_log_char);
      int idx = tag.indexOf (".");
      if (idx > 0 && idx < max_log_char)
        tag = tag.substring (0, idx);
      int index = 3;
      String tag2 = tag.substring (0, index) + tag.substring (index + 1);
      full_tag = tag_prefix_get () + tag2;
      full_txt = method + ": " + text;
    }
    else {
      full_tag = String.format ("%36.36s", method);
      full_txt = text;
    }

    if (level == Log.ERROR)
      Log.e (full_tag, full_txt);
    else if (level == Log.WARN)
      Log.w (full_tag, full_txt);
    else if (level == Log.DEBUG)
      Log.d (full_tag, full_txt);
    else if (level == Log.VERBOSE)
      Log.v (full_tag, full_txt);
  }

  public static void logv (String text) {
    if (ena_log_verbo)
      log (Log.VERBOSE, text);
  }
  public static void logd (String text) {
    if (ena_log_debug)
      log (Log.DEBUG, text);
  }
  public static void logw (String text) {
    if (ena_log_warni)
      log (Log.WARN, text);
  }
  public static void loge (String text) {
    if (ena_log_error)
      log (Log.ERROR, text);
  }


  public static String app_version_get (Context act) {                                             // Get versionName (from AndroidManifest.xml)
    String version = "";
    PackageInfo package_info;
    try {
      package_info = act.getPackageManager ().getPackageInfo (act.getPackageName (), 0);
      version = package_info.versionName;
    }
    catch (Exception e) {//NameNotFoundException e) {
      //e.printStackTrace ();
    }
    return (version);
  }


  public static boolean main_thread_get (String source) {
    boolean ret = (Looper.myLooper () == Looper.getMainLooper ());
    if (ret)
      hu_uti.logd ("YES MAIN THREAD source: " + source);
    //else
    //  hu_uti.logd ("Not main thread source: " + source);
    return (ret);
  }

  //public static boolean strict_mode = false;
  //StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
  //StrictMode.setThreadPolicy(policy);

  public static void strict_mode_set (boolean strict_mode) {
    if (! strict_mode) {
      StrictMode.setThreadPolicy (new StrictMode.ThreadPolicy.Builder ()
      .permitAll ()
      .build () );

      return;
    }

    StrictMode.setThreadPolicy (new StrictMode.ThreadPolicy.Builder ()
      .detectDiskReads ()
      .detectDiskWrites ()
      .detectNetwork ()
      .detectAll ()                                                     // For all detectable problems
      .penaltyLog ()
      .build () );

    StrictMode.setVmPolicy (new StrictMode.VmPolicy.Builder ()
      .detectLeakedSqlLiteObjects ()
      .detectLeakedClosableObjects ()
      .penaltyLog ()
      //.penaltyDeath ()
      //.penaltyDialog ()   ????
      .build () );
  }

  public static long ms_sleep (long ms) {
    //main_thread_get ("ms_sleep ms: " + ms);

    //hu_uti.logw ("ms: " + ms);                                          // Warning

    try {
      Thread.sleep (ms);                                                // Wait ms milliseconds
      return (ms);
    }
    catch (InterruptedException e) {
      //Thread.currentThread().interrupt();
      e.printStackTrace ();
      hu_uti.loge ("Exception e: " + e);
      return (0);
    }
  }

  public static long tmr_ms_get () {        // Current timestamp of the most precise timer available on the local system, in nanoseconds. Equivalent to Linux's CLOCK_MONOTONIC. 
    long ms = System.nanoTime () / 1000000; // Should only be used to measure a duration by comparing it against another timestamp on the same device.
                                            // Values returned by this method do not have a defined correspondence to wall clock times; the zero value is typically whenever the device last booted
    //hu_uti.logd ("ms: " + ms);           // Changing system time will not affect results.
    return (ms);
  }

  public static long utc_ms_get () {        // Current time in milliseconds since January 1, 1970 00:00:00.0 UTC.
    long ms = System.currentTimeMillis ();  // Always returns UTC times, regardless of the system's time zone. This is often called "Unix time" or "epoch time".
    //hu_uti.logd ("ms: " + ms);           // This method shouldn't be used for measuring timeouts or other elapsed time measurements, as changing the system time can affect the results.
    return (ms);
  }


  public static byte [] hexstr_to_ba (String s) {
    int len = s.length ();
    byte [] data = new byte [len / 2];
    for (int i = 0; i < len; i += 2)
      data [i / 2] = (byte) ((Character.digit (s.charAt (i), 16) << 4) + Character.digit(s.charAt (i + 1), 16));
    return (data);
  }

  public static String str_to_hexstr (String s) {
    byte [] ba = str_to_ba (s);
    return (ba_to_hexstr (ba));
  }

  public static String ba_to_hexstr (byte [] ba) {
    String hex = "";
    for (int ctr = 0; ctr < ba.length; ctr ++)
      hex += hex_get (ba [ctr]);    //hex += "" + hex_get ((byte) (ba [ctr] >> 4));
    return (hex.toString());
  }

  public static String hex_get (byte b) {
    byte c1 = (byte)((b & 0x00F0) >> 4);
    byte c2 = (byte)((b & 0x000F) >> 0);

    byte [] buffer = new byte [2];

    if (c1 < 10)
      buffer[0] = (byte)(c1 + '0');
    else
      buffer[0] = (byte)(c1 + 'A' - 10);
    if (c2 < 10)
      buffer[1] = (byte)(c2 + '0');
    else
      buffer[1] = (byte)(c2 + 'A' - 10);

    String str = new String (buffer);

    return (str);
  }

  public static String hex_get (short s) {
    byte byte_lo = (byte) (s >> 0 & 0xFF);
    byte byte_hi = (byte) (s >> 8 & 0xFF);
    String res = hex_get (byte_hi) + hex_get (byte_lo);
    return (res);
  }

  public static String hex_get (int i) {
    byte byte_0 = (byte) (i >> 0 & 0xFF);
    byte byte_1 = (byte) (i >> 8 & 0xFF);
    byte byte_2 = (byte) (i >>16 & 0xFF);
    byte byte_3 = (byte) (i >>24 & 0xFF);
    String res = hex_get (byte_3) + hex_get (byte_2) + hex_get (byte_1) + hex_get (byte_0);
    return (res);
  }

  public static void hex_dump (String prefix, byte [] ba, int size) {
    int len = ba.length;
    String str = "";
    int idx = 0;
    for (idx = 0; idx < len && idx < size; idx ++) {
      str += hex_get (ba [idx]) + " ";
      if (idx % 16 == 15) {
        hu_uti.logd (prefix + " " + hex_get ((idx / 16) * 16) + ": " + str);
        str = "";
      }
    }
    if (! str.equals ("")) {
      hu_uti.logd (prefix + " " + hex_get ((idx / 16) * 16) + ": " + str);
    }
  }

  public static void hex_dump (String prefix, ByteBuffer content, int size) {
    hu_uti.hex_dump (prefix, content.array (), size);
  }

/*
  private static byte [] str_to_ba (String s) {                          // String to byte array
    //s += "�";     // RDS test ?
    char [] buffer = s.toCharArray ();
    byte [] content = new byte [buffer.length];
    for (int i = 0; i < content.length; i ++) {
      content [i] = (byte) buffer [i];
      //if (content [i] == -3) {            // ??
      //  hu_uti.loge ("s: " + s);//content [i]);
      //  content [i] = '~';
      //}
    }
    return (content);
  }
*/
  private static int int_get (byte lo) {
    int ret = lo;
    if (ret < 0)
      ret += 256;
    return (ret);
  }
  public static int int_get (byte hi, byte lo) {
    int ret = int_get (lo);
    ret += 256 * int_get (hi);
    return (ret);
  }

  public static int varint_encode (int val, byte [] ba, int idx) {
    if (val >= 1 << 14) {
      hu_uti.loge ("Too big");
      return (1);
    }
    ba [idx+0] = (byte) (0x7f & (val >> 0));
    ba [idx+1] = (byte) (0x7f & (val >> 7));
    if (ba [idx+1] != 0) {
      ba [idx+0] |= 0x80;
      return (2);
    }
    return (1);
  }

  public static int varint_encode (long val, byte [] ba, int idx) {
    if (val >= 0x7fffffffffffffffL) {
      hu_uti.loge ("Too big");
      return (1);
    }
    long left = val;
    for (int idx2 = 0; idx2 < 9; idx2 ++) {
      ba [idx+idx2] = (byte) (0x7f & left);
      left = left >> 7;
      if (left == 0) {
        return (idx2 + 1);
      }
      else if (idx2 < 9 - 1) {
        ba [idx+idx2] |= 0x80;
      }
    }
    return (9);
  }

/*
  private long varint_decode (byte [] ba, int len) {
    int idx = 0;
    long val = 0, new7 = 0;
    while (idx < len) {
      new7 = 
      val |= (0x7f & ba [idx]);
      val = val
    }
  }

  private int varint_encode (long val, byte [] ba, int idx) {
    long left = val;
    while (left > 0) {
      if (left < 127)
    }
  }
*/

  public static int file_write (Context context, String filename, byte [] buf) {
    try {                                                               // File /data/data/ca.yyx.hu/hu.log contains a path separator
      FileOutputStream fos = context.openFileOutput (filename, Context.MODE_PRIVATE); // | MODE_WORLD_WRITEABLE      // NullPointerException here unless permissions 755
                                                                        // Create/open file for writing
      fos.write (buf);                                                  // Copy to file
      fos.close ();                                                     // Close file
    }
    catch (Throwable t) {
      //hu_uti.loge ("Throwable t: " + t);
      Log.e ("hucomuti", "Throwable t: " + t);
      t.printStackTrace ();
      return (-1);
    }
    return (0);
  }

  public static byte [] str_to_ba (String s) {                          // String to byte array
    //s += "�";     // RDS test ?
    char [] buffer = s.toCharArray ();
    byte [] content = new byte [buffer.length];
    for (int i = 0; i < content.length; i ++) {
      content [i] = (byte) buffer [i];
      //if (content [i] == -3) {            // ??
      //  loge ("s: " + s);//content [i]);
      //  content [i] = '~';
      //}
    }
    return (content);
  }


    // Strings:

  public static String str_usb_perm     = "ca.yyx.hu.ACTION_USB_DEVICE_PERMISSION";

  public static String str_MAN = "Android";//"Mike";                    // Manufacturer
  public static String str_MOD = "Android Auto";//"Android Open Automotive Protocol"  // Model
  public static String str_DES = "Head Unit";                           // Description
  public static String str_VER = "1.0";                                 // Version
  public static String str_URI = "http://www.android.com/";             // URI
  public static String str_SER = "0";//000000012345678";                // Serial #

  public static boolean su_installed_get () {
    boolean ret = false;
    if (hu_uti.file_get ("/system/bin/su"))
      ret = true;
    else if (hu_uti.file_get ("/system/xbin/su"))
      ret = true;
    hu_uti.logd ("ret: " + ret);
    return (ret);
  }

  public static int sys_run (String cmd, boolean su) {
    //main_thread_get ("sys_run cmd: " + cmd);

    String [] cmds = {("")};
    cmds [0] = cmd;
    return (arr_sys_run (cmds, su));
  }

  private static int arr_sys_run (String [] cmds, boolean su) {         // !! Crash if any output to stderr !!
    //hu_uti.logd ("sys_run: " + cmds);

    try {
      Process p;
      if (su)
        p = Runtime.getRuntime ().exec ("su");
      else
        p = Runtime.getRuntime ().exec ("sh");
      DataOutputStream os = new DataOutputStream (p.getOutputStream ());
      for (String line : cmds) {
        hu_uti.logd ("su: " + su + "  line: " + line);
        os.writeBytes (line + "\n");
      }           
      os.writeBytes ("exit\n");  
      os.flush ();

      int exit_val = p.waitFor ();                                      // This could hang forever ?
      if (exit_val != 0)
        hu_uti.logw ("cmds [0]: " + cmds [0] + "  exit_val: " + exit_val);
      else
        hu_uti.logd ("cmds [0]: " + cmds [0] + "  exit_val: " + exit_val);

      //os.flush ();
      return (exit_val);
    }
    catch (Exception e) {
      //e.printStackTrace ();
      hu_uti.loge ("Exception e: " + e);
    };
    return (-1);
  }

  public static boolean file_get (String filename, boolean log) {
    //main_thread_get ("file_get filename: " + filename);
    File ppFile = null;
    boolean exists = false;    
    try {
      ppFile = new File (filename);
      if (ppFile.exists ())
        exists = true;
    }
    catch (Exception e) {
      //e.printStackTrace ();
      hu_uti.loge ("Exception: " + e );
      exists = false;                                                   // Exception means no file or no permission for file
    } 
    if (log)
      hu_uti.logd ("exists: " + exists + "  \'" + filename + "\'");
    return (exists);
  }
  public static boolean quiet_file_get (String filename) {
    return (file_get (filename, false));
  }
  public static boolean file_get (String filename) {
    return (file_get (filename, true));
  }


  public static long file_size_get (String filename) {
    main_thread_get ("file_size_get filename: " + filename);
    File ppFile = null;
    long ret = -1;
    try {
      ppFile = new File (filename);
      if (ppFile.exists ())
        ret = ppFile.length ();
    }
    catch (Exception e) {
      //e.printStackTrace ();
    } 
    logd ("ret: " + ret + "  \'" + filename + "\'");
    return (ret);
  }

  public static boolean file_delete (final String filename) {
    main_thread_get ("file_delete filename: " + filename);
    java.io.File f = null;
    boolean ret = false;
    try {
      f = new File (filename);
      f.delete ();
      ret = true;
    }
    catch (Throwable e) {
      hu_uti.logd ("Throwable e: " + e);
      e.printStackTrace();
    }
    hu_uti.logd ("ret: " + ret);
    return (ret);
  }

  public static boolean file_create (final String filename) {
    main_thread_get ("file_create filename: " + filename);
    java.io.File f = null;
    boolean ret = false;
    try {
      f = new File (filename);
      ret = f.createNewFile ();
      hu_uti.logd ("ret: " + ret);
    }
    catch (Throwable e) {
      hu_uti.logd ("Throwable e: " + e);
      e.printStackTrace();
    }
    return (ret);
  }

  public static String res_file_create (Context context, int id, String filename) {
    //main_thread_get ("res_file_create filename: " + filename);

    if (context == null)
      return ("");

    String full_filename = context.getFilesDir () + "/" + filename;
    try {
      InputStream ins = context.getResources().openRawResource (id);          // Open raw resource file as input
      int size = ins.available ();                                      // Get input file size (actually bytes that can be read without indefinite wait)

      if (size > 0 && file_size_get (full_filename) == size) {          // If file already exists and size is unchanged... (assumes size will change on update !!)
        hu_uti.logd ("file exists size unchanged");                            // !! Have to deal with updates !! Could check filesize, assuming filesize always changes.
                                                                        // Could use indicator file w/ version in file name... SSD running problem for update ??
                                                                            // Hypothetically, permissions may not be set for ssd due to sh failure

//!! Disable to re-write all non-EXE w/ same permissions and all EXE w/ permissions 755 !!!!        return (full_filename);                                         // Done

      }

      byte [] buffer = new byte [size];                                 // Allocate a buffer
      ins.read (buffer);                                                // Read entire file into buffer. (Largest file is s.wav = 480,044 bytes)
      ins.close ();                                                     // Close input file

      FileOutputStream fos = context.openFileOutput (filename, Context.MODE_PRIVATE); // | MODE_WORLD_WRITEABLE      // NullPointerException here unless permissions 755
                                                                        // Create/open output file for writing
      fos.write (buffer);                                               // Copy input to output file
      fos.close ();                                                     // Close output file

      //hu_uti.sys_run ("chmod 755 " + full_filename + " 1>/dev/null 2>/dev/null" , false);              // Set execute permission; otherwise rw-rw----
      //perms_all (full_filename);

    }
    catch (Exception e) {
      //e.printStackTrace ();
      hu_uti.loge ("Exception e: " + e);
      return (null);
    }

    return (full_filename);
  }

  public static byte [] file_read_16m (String filename) {               // Read file up to 16 megabytes into byte array
    //main_thread_get ("file_read_16m filename: " + filename);
    byte [] content = new byte [0];
    int bufSize = 16384 * 1024;
    byte [] content1 = new byte [bufSize];
    try {
      FileInputStream in = new FileInputStream (filename);
      int n = in.read (content1, 0, bufSize);
      in.close ();
      content = new byte [n];
      for (int ctr = 0; ctr < n; ctr ++)
        content [ctr] = content1 [ctr];
    }
    catch (Exception e) {
      hu_uti.logd ("Exception: " + e);
      //e.printStackTrace ();
    }
    return (content);
  }


/*
  void device_id_get () {

    if (! BuildConfig.DEBUG)
      return;

    // ANDROID_ID and TelephonyManager.getDeviceId()
    String android_id = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
    hu_uti.logd ("android_id: " + android_id);                         // android_id: c5da68763daf5900

    //UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
    //String deviceId = deviceUuid.toString();

    android.bluetooth.BluetoothAdapter m_BluetoothAdapter =  	android.bluetooth.BluetoothAdapter.getDefaultAdapter(); 
    String bluetooth_add = m_BluetoothAdapter.getAddress();
    hu_uti.logd ("bluetooth_add: " + bluetooth_add);                   // bluetooth_add: B4:CE:F6:34:49:CD

    android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) this.getSystemService (Context.WIFI_SERVICE);
    String wifi_mac_address = "";
    if (wm == null)
      hu_uti.loge ("wm: " + wm);
    else
      wifi_mac_address = wm.getConnectionInfo ().getMacAddress ();
    hu_uti.logd ("wifi_mac_address: " + wifi_mac_address);             // On some devices, it's not available when Wi-Fi is turned off

    android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) this.getSystemService (Context.TELEPHONY_SERVICE);
    String tmdi = tm.getDeviceId ();
    hu_uti.logd ("tmdi: " + tmdi);                                     // tmdi: null
    String sim_serial = tm.getSimSerialNumber ();
    hu_uti.logd ("sim_serial: " + sim_serial);                         // sim_serial: null

    String serial = "";
    try {
      serial = android.os.Build.class.getField ("SERIAL").get (null).toString ();
      hu_uti.logd ("serial: " + serial);                               // serial: HT4A1JT00958
    }
    catch (Throwable t) {
      hu_uti.loge ("Throwable t: " + t);
    }
    try {
      Class<?> c = Class.forName ("android.os.SystemProperties");
      java.lang.reflect.Method get = c.getMethod ("get", String.class);
      serial = (String) get.invoke (c, "ro.serialno");
      hu_uti.logd ("serial: " + serial);                               // serial: HT4A1JT00958
    }
    catch (Throwable t) {
      hu_uti.loge ("Throwable t: " + t);
    }
  }
*/


}

