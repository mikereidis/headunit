
  // Head Unit Activity

/* http://stackoverflow.com/questions/16851638/android-kill-process

public void onBackPressed() {
    android.os.Process.killProcess(android.os.Process.myPid());
    super.onBackPressed();
}

*/

/* How to implement Android Open Accessory mode as a service?

Copy the intent that you received when starting your activity that you use to launch the service, because the intent contains the details of the accessory that the ADK implementation needs.
Then, in the service proceed to implement the rest of ADK exactly as before.

if (intent.getAction().equals(USB_OAP_ATTACHED)) {
    Intent i = new Intent(this, YourServiceName.class);
    i.putExtras(intent);
    startService(i);
}


*/
package ca.yyx.hu;

import android.util.Log;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;

import android.hardware.Camera;
import android.media.CamcorderProfile;

public class hu_act extends Activity implements SurfaceHolder.Callback {

  //public static final boolean m_disable_alleged_google_dda_4_4_violation = true;  // Google Play version: disable Android Auto (tm) functionality
  public static final boolean m_disable_alleged_google_dda_4_4_violation = false;   // Open Source version: enable  Android Auto (tm) functionality


  private hu_tra        m_hu_tra;        // Transport API

  private TextView      m_tv_log;
  private LinearLayout  m_ll_tv_log;

  private TextView      m_tv_ext;
  private LinearLayout  m_ll_tv_ext;

  private FrameLayout   m_fl_sv_vid;

  //private TextView      m_tv_fps;

  // These fields are guarded by m_surf_codec_lock. This is to ensure that the surface lifecycle is respected.
  // Although decoding happens on the transport thread, we are not allowed to access the surface after it is destroyed by the UI thread so we need to stop the codec immediately.
  private final Object  m_surf_codec_lock = new Object ();
  private Surface       m_surface;
  private boolean       m_surface_attached;
  private SurfaceView   m_sv_vid;
  private SurfaceView   m_sv_vid2;

  private MediaCodec    m_codec;
  private ByteBuffer [] m_codec_input_bufs;
  private BufferInfo    m_codec_buf_info;

  private int           m_sur_wid  = 0;
  private int           m_sur_hei  = 0;

  private double        m_scr_wid  = 0;
  private double        m_scr_hei  = 0;

  private double        m_virt_vid_wid = 800f;
  private double        m_virt_vid_hei = 480f;

  private boolean       m_scr_land     = true;


///*
  public void ld (final String str) {
    screen_logd (str);
  }
  public void le (final String str) {
    screen_loge (str);
  }

  private void tv_log_append (final String str) {
    class log_task implements Runnable {
      String str;
      log_task (String s) { str = s; }
        public void run() {
          m_tv_log.append (str);
        }
    }
    //Thread t = new Thread(new log_task (s));
    //t.start();
    runOnUiThread (new log_task (str));
  }
  
  public boolean ls_debug = true;
  private boolean lim_scrn_debug () {   // Limited Screen debug
    //return (BuildConfig.DEBUG);
    return (ls_debug);
  }
  private void screen_logd (final String str) {
    /*if (BuildConfig.DEBUG)*/ hu_uti.logd (str);
    //m_tv_log.append (str + "\n");       // android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
    tv_log_append (str + "\n");           // at ca.yyx.hu.hu_act.video_decode(hu_act.java:564)
  }
  private void screen_loge (final String str) {
    /*if (BuildConfig.DEBUG)*/ hu_uti.loge (str);
    //m_tv_log.append ("ERROR: " + str + "\n");
    tv_log_append ("ERROR: " + str + "\n");
  }
//*/

  private android.os.PowerManager           m_pwr_mgr   = null;
  private android.os.PowerManager.WakeLock  m_wakelock  = null;
  private View                              m_ll_main   = null;         // Main view of activity

  @Override
  protected void onCreate (Bundle savedInstanceState) {
    if (BuildConfig.DEBUG) hu_uti.logd ("savedInstanceState: " + savedInstanceState);
    super.onCreate (savedInstanceState);

    Log.d ("hu", "Headunit for Android Auto (tm) - Copyright 2011-2015 Michael A. Reid. All Rights Reserved...");

    m_scr_hei = getWindowManager ().getDefaultDisplay ().getHeight ();
    m_scr_wid  = getWindowManager ().getDefaultDisplay ().getWidth ();
    if (BuildConfig.DEBUG) hu_uti.logd ("m_scr_wid: "  + m_scr_wid + "  m_scr_hei: " + m_scr_hei);

    android.graphics.Point size = new android.graphics.Point ();
    getWindowManager ().getDefaultDisplay ().getSize (size);
    m_scr_wid = size.x;
    m_scr_hei = size.y;

    if (m_scr_wid > m_scr_hei)
      m_scr_land = true;
    else
      m_scr_land = false;

    if (BuildConfig.DEBUG) hu_uti.logd ("m_scr_wid: "  + m_scr_wid  + "  m_scr_hei: " + m_scr_hei  + "  m_scr_land: " + m_scr_land);

//N9 portrait:
//06-08 00:44:24.390 D/                onCreate( 6082): m_scr_wid: 1536.0  m_scr_hei: 1952.0
//06-08 00:44:24.391 D/                onCreate( 6082): m_scr_wid: 1536.0  m_scr_hei: 1952.0  m_scr_land: false


    //device_id_get ();

    setContentView (R.layout.layout);

    getWindow ().addFlags (android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // !! Keep Screen on !!


    // Set the IMMERSIVE flag. Set the content to appear under the system bars so that the content doesn't resize when the system bars hide and show.
    m_ll_main = findViewById (R.id.ll_main);
    if (m_ll_main != null)
      m_ll_main.setSystemUiVisibility (
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
            | View.SYSTEM_UI_FLAG_IMMERSIVE);


    m_tv_log = (TextView) findViewById (R.id.tv_log);
    m_tv_log.setMovementMethod (android.text.method.ScrollingMovementMethod.getInstance ());
    //m_tv_log.setMovementMethod (android.text.method.ArrowKeyMovementMethod.getInstance ());
    //m_tv_log.setMovementMethod (android.text.method.LinkMovementMethod.getInstance ());
    //m_tv_log.setMovementMethod (android.text.method.BaseMovementMethod.getInstance ());


    m_tv_log.setOnClickListener (short_click_lstnr);
    m_tv_log.setId (R.id.tv_log);


    m_ll_tv_log = (LinearLayout) findViewById (R.id.ll_tv_log);
    m_ll_tv_ext = (LinearLayout) findViewById (R.id.ll_tv_ext);
    m_fl_sv_vid = (FrameLayout)  findViewById (R.id.fl_sv_vid);

    m_tv_ext = (TextView) findViewById (R.id.tv_ext);
    //m_tv_ext.setText ("Extra...........................................................\n..............................................\n.................................\r...");
//*
    //m_ll_tv_ext.setVisibility (View.GONE);
    if (m_scr_land && m_scr_wid / m_scr_hei < 1.5)       // 16:9 = 1.777     //  4:3 = 1.333
      m_ll_tv_ext.setVisibility (View.VISIBLE);
//*/
/*
    m_tv_fps = (TextView) findViewById (R.id.tv_fps);
    if (m_tv_fps != null)
      m_tv_fps.setText ("!!!!");
*/

    m_sv_vid = (SurfaceView) findViewById (R.id.sv_vid);
    m_sv_vid.setOnTouchListener (new View.OnTouchListener () {
      @Override
      public boolean onTouch (View v, MotionEvent event) {
        touch_send (event);
        return (true);
      }
    });


    try {
      m_pwr_mgr = (android.os.PowerManager) getSystemService (POWER_SERVICE);
      if (m_pwr_mgr != null)
        m_wakelock = m_pwr_mgr.newWakeLock (android.os.PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
      if (m_wakelock != null)
        m_wakelock.acquire ();                                          // Android M exception for WAKE_LOCK
    }
    catch (Throwable t) {
      if (BuildConfig.DEBUG) hu_uti.loge ("Throwable: " + t);
    }


//"This is an early experimental version released so I can get early feedback from you... :)\n" +
//"Specific USB system settings may be required\n";
    String intro1_str = "HEADUNIT for Android Auto (tm)\n" +
"You accept all liability for any reason by using, distributing, doing, or not doing anything else in respect of this software.\n" +
"This software is experimental and may be distracting. Use it only for testing at this time. Do NOT use this software while operating a vehicle of any sort.\n";
    screen_logd (intro1_str);

String intro2_str = "";
    if (this.getPackageManager ().hasSystemFeature (android.content.pm.PackageManager.FEATURE_USB_HOST))
      intro2_str = "This device/ROM must support USB Host Mode\n";
    else
      intro2_str = "This device/ROM must support USB Host Mode AND IT DOES NOT APPEAR TO BE SUPPORTED !!!!!!!!!!\n";
    screen_logd (intro2_str);

String intro3_str = "";

if (m_disable_alleged_google_dda_4_4_violation)
intro3_str += "" +
"!!!!!!!! NOTE !!!!!!!!\n" +
"This version has Android Auto functionality disabled. It is only useful for USB tests.\n" +
"Google removed Headunit from Play alleging violation of DDA section 4.4.\n" +
"I appealed and have NO reply in 12 days, despite a 3 day response claim.\n" +
"Please email mikereidis@gmail.com, see XDA or ask Google for refunds or more info.\n";

intro3_str += "" +
"See XDA thread for latest info and ask any question\n" +
"\n" +
"Connect this device to USB OTG cable\n" +
"Connect USB OTG cable to regular phone USB cable\n" +
"Connect regular USB cable to Android 5+ device running Google Android Auto app\n" +
"Start this Headunit app\n" +
"Respond OK to prompts\n" +
"First Time with Android Auto check phone screen to see if apps need to be updated or prompts accepted\n" +
"If success, this device screen will show Android Auto User Interface and other device screen should go dark\n" +
"\n" +
"A LOT of USB and OTG cables will not work\n" +
"It can be tricky to get working the first time\n" +
"\n";
    screen_logd (intro3_str);



    if (hu_uti.file_get ("/sdcard/husuj"))
      //hu_uti.sys_run ("setenforce 0 1>/dev/null 2>/dev/null ; chmod -R 777 /dev/bus 1>/dev/null 2>/dev/null", true);
      hu_uti.sys_run ("chmod -R 777 /dev/bus 1>/dev/null 2>/dev/null", true);

    m_hu_tra = new hu_tra (this);                                       // Start USB/SSL/AAP Transport
    if (m_hu_tra != null) {
      Intent intent = getIntent ();                                     // Get launch Intent
      m_hu_tra.transport_start (intent);
    }
  }

// pm grant org.jtb.alogcat.donate android.permission.READ_LOGS
// chmod 04755 /system/bin/logcat
// logcat k9:V *:S AndroidRuntime:E

  private int click_ctr = 0;
  private long click_start_ms = 0;

  private View.OnClickListener short_click_lstnr = new View.OnClickListener () {
    public void onClick (View v) {

      int log_clicks = 7;

      if (BuildConfig.DEBUG) hu_uti.logd ("view: " + v);
      //ani (v);                                                          // Animate button
      if (v == null) {
        if (BuildConfig.DEBUG) hu_uti.loge ("view: " + v);
      }

      else if (v == m_tv_log) {
        //m_gui_act.showDialog (GUI_MENU_DIALOG);
        if (click_ctr <= 0) {
          click_ctr = 1;
          click_start_ms = hu_uti.tmr_ms_get ();
          return;
        }

        if (click_ctr <= log_clicks - 1) {    // If still counting clicks, and/or may trigger...
          if (click_start_ms + 3000 < hu_uti.tmr_ms_get ()) { // If took longer than 3 seconds...
            click_ctr = 1;
            click_start_ms = hu_uti.tmr_ms_get ();
            return;
          }
        }
        if (click_ctr == log_clicks - 1) {
          click_ctr ++;

          logs_email ();

          //video_sample_start (false);

          //click_ctr = 0;    // Zero'd when logs processed
        }
        else if (click_ctr < log_clicks - 1) {   // Protect against buffering more clicks
          click_ctr ++;
        }
      }
    }
  };



  private void video_sample_start (final boolean started) {
/*
    class vs_task implements Runnable {
      boolean started;
      vs_task (boolean s) { started = s; }
        public void run() {
          video_sample (started);
        }
    }
    runOnUiThread (new vs_task (started));
*/

ui_video_started_set (true);

    Thread thread_vs = new Thread (run_vs, "run_vs");
    //com_uti.logd ("thread_vs: " + thread_vs);
    if (thread_vs == null)
      ;//com_uti.loge ("thread_vs == null");
    else {
      //thread_vs_active = true;
      java.lang.Thread.State thread_state = thread_vs.getState ();
      if (thread_state == java.lang.Thread.State.NEW || thread_state == java.lang.Thread.State.TERMINATED) {
        ////com_uti.logd ("thread priority: " + thread_vs.getPriority ());   // Get 5
        thread_vs.start ();
      }
      //else
      //  com_uti.loge ("thread_vs thread_state: " + thread_state);
    }

  }

  private final Runnable run_vs = new Runnable () {
    public void run () {
      //com_uti.logd ("run_vs");
      boolean started = true;
      video_sample (started);
    }
  };



  int h264_after_get (byte [] ba, int idx) {
    idx += 4; // Pass 0, 0, 0, 1
    for (; idx < ba.length - 4; idx ++) {
      if (idx > 24)   // !!!! HACK !!!! else 0,0,0,1 indicates first size 21, instead of 25
        if (ba [idx] == 0 && ba [idx+1] == 0 && ba [idx+2] == 0 && ba [idx+3] == 1)
          return (idx);
    }
    return (-1);
  }

  String h264_full_filename = null;
  private void video_sample (final boolean started) {
//!!    if (h264_full_filename == null)
//!!      h264_full_filename = hu_uti.res_file_create (m_context, R.raw.husam_h264, "husam.h264");
    if (h264_full_filename == null)
      h264_full_filename = "/data/data/ca.yyx.hu/files/husam.h264";

    //ui_video_started_set (true);

    byte [] ba = hu_uti.file_read_16m (h264_full_filename);             // Read entire file, up to 16 MB to byte array ba
    ByteBuffer bb;// = ByteBuffer.wrap (ba);

    int size = (int) hu_uti.file_size_get (h264_full_filename);
    int left = size;
    int idx = 0;
    int max_chunk_size = 65536 * 4;//16384;


    int chunk_size = max_chunk_size;
    int after = 0;
    for (idx = 0; idx < size && left > 0; idx = after) {

      after = h264_after_get (ba, idx);                               // Get index of next packet that starts with 0, 0, 0, 1
      if (after == -1 && left <= max_chunk_size) {
        after = size;
        //if (lim_scrn_debug ()) screen_logd ("Last chunk  chunk_size: " + chunk_size + "  idx: " + idx + "  after: " + after + "  size: " + size + "  left: " + left);
      }
      else if (after <= 0 || after > size) {
        if (lim_scrn_debug ()) screen_loge ("Error chunk_size: " + chunk_size + "  idx: " + idx + "  after: " + after + "  size: " + size + "  left: " + left);
        return;
      }

      chunk_size = after - idx;

      byte [] bc = new byte [chunk_size];                               // Create byte array bc to hold chunk
      int ctr = 0;
      for (ctr = 0; ctr < chunk_size; ctr ++)
        bc [ctr] = ba [idx + ctr];                                      // Copy chunk_size bytes from byte array ba at idx to byte array bc

      //if (lim_scrn_debug ()) screen_logd ("chunk_size: " + chunk_size + "  idx: " + idx + "  after: " + after + "  size: " + size + "  left: " + left);

      idx += chunk_size;
      left -= chunk_size;

      bb = ByteBuffer.wrap (bc);                                        // Wrap chunk byte array bc to create byte buffer bb
      //bb.limit (chunk_size);
      //bb.position (0);

      video_decode (bb);                                                // Decode video
      hu_uti.ms_sleep (20);                                             // Wait a frame
    }

  }

  private boolean video_started = false;
  public void ui_video_started_set (boolean started) {                  // Called directly from hu_tra because runOnUiThread() won't work
    if (video_started == started)
      return;
    try {
      if (started) {
        m_ll_tv_log.setVisibility (View.GONE);
        //m_fl_sv_vid.setVisibility (View.VISIBLE);
/*
        //m_ll_tv_ext.setVisibility (View.GONE);
        if (m_scr_land && m_scr_wid / m_scr_hei < 1.5)       // 16:9 = 1.777     //  4:3 = 1.333
          m_ll_tv_ext.setVisibility (View.VISIBLE);
*/
      }
      else {
        //m_ll_tv_log.setVisibility (View.VISIBLE);
        //m_fl_sv_vid.setVisibility (View.GONE);
        //m_ll_tv_ext.setVisibility (View.GONE);
      }
    }
    catch (Throwable t) {
      if (lim_scrn_debug ()) screen_loge ("Throwable: " + t);     //
    }

    video_started = started;
  }

  private boolean disable_video_started_set = true;
  private void video_started_set (final boolean started) {              // Not used because runOnUiThread() won't work
    if (disable_video_started_set)
      return;

    class vs_task implements Runnable {
      boolean started;
      vs_task (boolean s) { started = s; }
        public void run() {
          ui_video_started_set (started);
        }
    }
    runOnUiThread (new vs_task (started));
  }
  


  @Override
  protected void onDestroy () {
    if (BuildConfig.DEBUG) hu_uti.logd ("");

    super.onDestroy ();

    video_started_set (false);

    if (m_hu_tra != null)
      m_hu_tra.transport_stop ();
    video_record_stop ();

    try {
      if (m_wakelock != null)
        m_wakelock.release ();
    }
    catch (Throwable t) {
      if (BuildConfig.DEBUG) hu_uti.loge ("Throwable: " + t);
    }

    android.os.Process.killProcess (android.os.Process.myPid ());

  }


  //private double        m_vid_wid  = 0;
  //private double        m_vid_hei  = 0;

  private double vid_wid_get () {
    double vid_wid  = 0;
    vid_wid = m_sv_vid.getWidth ();
    /*if (BuildConfig.DEBUG) hu_uti.logd ("vid_wid: "  + vid_wid);
    vid_wid = m_sv_vid.getMeasuredWidth ();                             // Same value
    if (BuildConfig.DEBUG) hu_uti.logd ("vid_wid: "  + vid_wid);*/
    return (vid_wid);
  }
  private double vid_hei_get () {
    double vid_hei  = 0;
    vid_hei = m_sv_vid.getHeight ();
    /*if (BuildConfig.DEBUG) hu_uti.logd ("vid_hei: "  + vid_hei);
    vid_hei = m_sv_vid.getMeasuredHeight ();                            // Same value
    if (BuildConfig.DEBUG) hu_uti.logd ("vid_hei: "  + vid_hei);*/
    return (vid_hei);
  }
/*
    m_vid_wid = m_sv_vid.getWidth ();
    m_vid_hei = m_sv_vid.getHeight ();
    if (BuildConfig.DEBUG) hu_uti.logd ("m_vid_wid: "  + m_vid_wid + "  m_vid_hei: " + m_vid_hei);

    m_vid_wid = m_sv_vid.getMeasuredWidth ();
    m_vid_hei = m_sv_vid.getMeasuredHeight ();
    if (BuildConfig.DEBUG) hu_uti.logd ("m_vid_wid: "  + m_vid_wid + "  m_vid_hei: " + m_vid_hei);

//06-08 00:44:24.390 D/                onCreate( 6082): m_scr_wid: 1536.0  m_scr_hei: 1952.0
    double max_vid_wid = 1536;      //512;    
    double max_vid_hei = 1952 / 2;  //512;
    if (m_vid_wid < max_vid_wid)
      m_vid_wid = max_vid_wid;
    if (m_vid_hei < max_vid_hei)
      m_vid_hei = max_vid_hei;
*/


  private void touch_send (MotionEvent event) {
    //if (BuildConfig.DEBUG) hu_uti.logd ("event: " + event);

    int x = (int) (event.getX (0) / (vid_wid_get () / m_virt_vid_wid));
    int y = (int) (event.getY (0) / (vid_hei_get () / m_virt_vid_hei));

    if (x < 0 || y < 0 || x >= 65535 || y >= 65535) {   // Infinity if vid_wid_get() or vid_hei_get() return 0
      if (BuildConfig.DEBUG) hu_uti.loge ("Invalid x: " + x + "  y: " + y);
      return;
    }

    byte aa_action = 0;
    int me_action = event.getActionMasked ();
    switch (me_action) {
      case MotionEvent.ACTION_DOWN:
        if (BuildConfig.DEBUG) hu_uti.logd ("event: " + event + " (ACTION_DOWN)    x: " + x + "  y: " + y);
        aa_action = 0;
        break;
      case MotionEvent.ACTION_MOVE:
        if (BuildConfig.DEBUG) hu_uti.logd ("event: " + event + " (ACTION_MOVE)    x: " + x + "  y: " + y);
        aa_action = 2;
        break;
      case MotionEvent.ACTION_CANCEL:
        if (BuildConfig.DEBUG) hu_uti.loge ("event: " + event + " (ACTION_CANCEL)  x: " + x + "  y: " + y);
        aa_action = 1;
        break;
      case MotionEvent.ACTION_UP:
        if (BuildConfig.DEBUG) hu_uti.logd ("event: " + event + " (ACTION_UP)      x: " + x + "  y: " + y);
        aa_action = 1;
        break;
      default:
        if (BuildConfig.DEBUG) hu_uti.loge ("event: " + event + " (Unknown: " + me_action + ")  x: " + x + "  y: " + y);
        return;
    }
    if (m_hu_tra != null)
      m_hu_tra.aa_touch (aa_action, x, y);
  }

  @Override
  public void onAttachedToWindow () {
    if (BuildConfig.DEBUG) hu_uti.logd ("");
    super.onAttachedToWindow ();
    m_surface_attached = true;
    sv_vid_set (m_sv_vid);
  }

  @Override
  public void onDetachedFromWindow () {
    if (BuildConfig.DEBUG) hu_uti.logd ("");
    super.onDetachedFromWindow ();
    m_surface_attached = false;
    sv_vid_set (null);
  }


  private void sv_vid_set (final SurfaceView sv_vid) {
    if (BuildConfig.DEBUG) hu_uti.logd ("sv_vid: " + sv_vid);
    if (m_sv_vid2 == sv_vid)                                // If NOT a new surface view...
      return;

    final SurfaceView old_sv_vid = m_sv_vid2;
    m_sv_vid2 = sv_vid;

    if (old_sv_vid != null) {                                     // If we had a previous surface view...
      old_sv_vid.post (new Runnable () {
        @Override
        public void run () {
          final SurfaceHolder holder = old_sv_vid.getHolder ();
          holder.removeCallback (hu_act.this);                         // Remove our callbacks for 3 SurfaceHolder.Callback functions
          surface_update (null);
        }
      });
    }

    if (sv_vid != null) {                                         // If a new surface non-null view passed...
      sv_vid.post (new Runnable () {
        @Override
        public void run () {
          final SurfaceHolder holder = sv_vid.getHolder ();
          holder.addCallback (hu_act.this);                            // Add our callbacks for 3 SurfaceHolder.Callback functions
          surface_update (holder);
        }
      });
    }
  }


    // 3 SurfaceHolder.Callback functions:

  @Override
  public void surfaceCreated (SurfaceHolder holder) {                   // Ignore.  Wait for surface changed event that follows.
    if (BuildConfig.DEBUG) hu_uti.logd ("holder: " + holder);
  }

  @Override
  public void surfaceChanged (SurfaceHolder holder, int format, int width, int height) {
    if (BuildConfig.DEBUG) hu_uti.logd ("holder: " + holder + "  format: " + format + "  width: " + width + "  height: " + height);
    surface_update (holder);
  }

  @Override
  public void surfaceDestroyed (SurfaceHolder holder) {
    if (BuildConfig.DEBUG) hu_uti.logd ("holder: " + holder);

// !! disable ??
surface_update (null);

  }

  private void surface_update (SurfaceHolder holder) {
    if (BuildConfig.DEBUG) hu_uti.logd ("holder: " + holder);

    Surface surface = null;
    int width = 0, height = 0;

    if (holder != null && ! holder.isCreating ()) {
      surface = holder.getSurface ();
      if (BuildConfig.DEBUG) hu_uti.logd ("surface: " + surface);

      if (surface.isValid ()) {
        final Rect frame = holder.getSurfaceFrame ();
        width = frame.width ();
        height = frame.height ();
      }
      else {
        surface = null;
      }
    }

    if (BuildConfig.DEBUG) hu_uti.logd ("surface: " + surface + "  width: " + width + "  height: " + height);

    synchronized (m_surf_codec_lock) {
      if (m_surface == surface &&  m_sur_wid == width && m_sur_hei == height) {  // If no surface, width or height change...
        if (BuildConfig.DEBUG) hu_uti.logd ("No change for surface, width or height; done");
        return;
      }

      if (BuildConfig.DEBUG) hu_uti.logd ("Change for surface, width or height");

      m_surface = surface;
      m_sur_wid = width;
      m_sur_hei = height;

// width: 1920  height: 1104    For ?
      if (m_sur_hei > 1080)
        m_sur_hei = 1080;

// Doesn't work for M8:width: 1794  height: 1080
/*
      if (m_sur_wid >= 1600) {//== 1794) {
        m_sur_wid = 1920;//1280;
        //m_sur_hei = 720;
      }
*/

      if (BuildConfig.DEBUG) hu_uti.logd ("m_sur_wid: " + m_sur_wid + "  m_sur_hei: " + m_sur_hei);


/* GS3 doesn't work:
      if (m_sur_wid == 1280)
        m_sur_wid = 1920;//800;
      if (m_sur_hei == 720)
        m_sur_hei = 1080;//360;
//*/


      int cam_prof = camcorder_profile_get ();

      if (m_codec != null) {
        if (BuildConfig.DEBUG) hu_uti.logd ("Cleanup previous codec");
        m_codec.stop ();
        m_codec = null;
        m_codec_input_bufs = null;
        m_codec_buf_info = null;
      }

      if (m_surface != null) {
        MediaFormat format = MediaFormat.createVideoFormat ("video/avc", m_sur_wid, m_sur_hei);
        try {
          m_codec = MediaCodec.createDecoderByType ("video/avc");       // Create video codec: ITU-T H.264 / ISO/IEC MPEG-4 Part 10, Advanced Video Coding (MPEG-4 AVC)
        }
        catch (Throwable t) {
          if (lim_scrn_debug ()) screen_loge ("Throwable creating video/avc decoder: " + t);
        }
        try {
          m_codec_buf_info = new BufferInfo ();
          m_codec.configure (format, m_surface, null, 0);
          m_codec.start ();
        }
        catch (Throwable t) {
          if (lim_scrn_debug ()) screen_loge ("Throwable: " + t);
        }
      }
    }
  }

  private int camcorder_profile_get () { // Get the recording profile.
    //CamcorderProfile profile = null;
    int prof = 0;

    if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
      if (prof < 1080)
        prof = 1080;
      if (BuildConfig.DEBUG) hu_uti.logd ("1080p");//profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
    }
    if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
      if (prof < 720)
        prof = 720;
      if (BuildConfig.DEBUG) hu_uti.logd (" 720p");//profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
    }
    if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
      if (prof < 480)
        prof = 480;
      if (BuildConfig.DEBUG) hu_uti.logd (" 480p");//profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
    }
    if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_HIGH)) {
      if (prof < 240)
        prof = 240; // !!!!!!!! ??
      if (BuildConfig.DEBUG) hu_uti.logd ("HIGH");//profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
    }
    //if (BuildConfig.DEBUG) hu_uti.loge ("Unknown");//

    //if (BuildConfig.DEBUG) hu_uti.loge ("profile: " + profile);
    return (prof);
  }

  private boolean codec_input_provide (ByteBuffer content) {
    if (hu_uti.ena_log_verbo)
      if (BuildConfig.DEBUG) hu_uti.logv ("content: " + content);
    //if (BuildConfig.DEBUG) hu_uti.logd ("content.position (): " + content.position () + "  content.limit (): " + content.limit ());

    try {
      final int index = m_codec.dequeueInputBuffer (0);                 // java.lang.IllegalStateException
      if (index < 0) {
        return (false);                                                 // Error: No buffers
      }
      if (m_codec_input_bufs == null) {
        m_codec_input_bufs = m_codec.getInputBuffers ();
      }

      final ByteBuffer buffer = m_codec_input_bufs [index];
      final int capacity = buffer.capacity ();
      buffer.clear ();
      if (content.remaining () <= capacity) {                           // If we can just put() the content...
        buffer.put (content);
      }
      else {
        if (lim_scrn_debug ()) screen_loge ("content.hasRemaining (): " + content.hasRemaining () + "  capacity: " + capacity);   // ?? Should not happen ??

        final int limit = content.limit ();
        content.limit (content.position () + capacity);                 // Temp set limit constrained
        buffer.put (content);
        content.limit (limit);                                          // Restore original limit
      }
      buffer.flip ();
      //if (BuildConfig.DEBUG) hu_uti.logd ("buffer.position (): " + buffer.position () + "  buffer.limit (): " + buffer.limit ());
      m_codec.queueInputBuffer (index, 0, buffer.limit (), 0, 0);
      return (true);                                                    // Processed
    }
    catch (Throwable t) {
      if (lim_scrn_debug ()) screen_loge ("Throwable: " + t);
      //return;
    }
    return (false);                                                     // Error: exception
  }

  private void codec_output_consume () {
    //if (BuildConfig.DEBUG) hu_uti.logd ("");
    for (;;) {
      final int index = m_codec.dequeueOutputBuffer (m_codec_buf_info, 0);
      if (index >= 0) {
        m_codec.releaseOutputBuffer (index, true /*render*/);
      }
      else if (index != MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED && index != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        break;
      }
    }
  }

//*
  private boolean video_recording = false;

  private void video_record_stop () {
    try {
      if (fos != null)
        fos.close ();                                                     // Close output file
      }
      catch (Throwable t) {
        if (BuildConfig.DEBUG) hu_uti.loge ("Throwable: " + t);
        //return;
      }

    fos = null;
    video_recording = false;
  }

  private FileOutputStream fos = null;

  private void video_record_write (ByteBuffer content) {    // ffmpeg -i 2015-04-29-00_38_16.mp4 -vcodec copy -an -bsf:v h264_mp4toannexb  aa.h264
    if (! video_recording) {
      try {
        fos = this.openFileOutput ("/sdcard/hurec.h264", Context.MODE_WORLD_READABLE);//, Context.MODE_PRIVATE); // | MODE_WORLD_WRITEABLE      // NullPointerException here unless permissions 755
      }
      catch (Throwable t) {
        //if (BuildConfig.DEBUG) hu_uti.loge ("Throwable: " + t);
        if (lim_scrn_debug ()) screen_loge ("Throwable: " + t);
        //return;
      }
      try {
        if (fos == null)
          fos = this.openFileOutput ("hurec.h264", Context.MODE_WORLD_READABLE);//, Context.MODE_PRIVATE); // | MODE_WORLD_WRITEABLE      // NullPointerException here unless permissions 755
      }
      catch (Throwable t) {
        //if (BuildConfig.DEBUG) hu_uti.loge ("Throwable: " + t);
        if (lim_scrn_debug ()) screen_loge ("Throwable: " + t);
        return;
      }

      video_recording = true;
    }

    int pos = content.position ();
    int siz = content.remaining ();
    int last = pos + siz - 1;
    byte [] ba = content.array ();
    if (ba == null) {
      if (BuildConfig.DEBUG) hu_uti.loge ("ba == null...   pos: " + pos + "  siz: " + siz + " (" + hu_uti.hex_get (siz) + ")  last: " + last);
      return;
    }
    byte b1 = ba [pos + 3];
    byte bl = ba [last];
    if (hu_uti.ena_log_verbo)
      if (BuildConfig.DEBUG) hu_uti.logv ("pos: " + pos + "  siz: " + siz + "  last: " + last + " (" + hu_uti.hex_get (b1) + ")  b1: " + b1 + "  bl: " + bl + " (" + hu_uti.hex_get (bl) + ")");
    
    try {
      fos.write (ba, pos, siz);                                               // Copy input to output file
    }
    catch (Throwable t) {
      if (BuildConfig.DEBUG) hu_uti.loge ("Throwable: " + t);
      return;
    }
  }
//*/

    // 1 or 2 APIs provided to hu_tra:
/*
  public void do_sv_vid_set () {
    if (m_surface_attached)                                             // If attached to window...
      sv_vid_set (m_sv_vid);                                // Set Surface view for H264 decoding
  }
*/


  public void video_decode (ByteBuffer content) {                       // decode H264 video content

    video_started_set (true);

    //screen_logd ("content: " + content);

    if (hu_uti.ena_log_verbo)
      if (BuildConfig.DEBUG) hu_uti.logv ("content: " + content);

    if (content == null) {
      return;
    }

    int pos = content.position ();
    int siz = content.remaining ();
    int last = pos + siz - 1;
    byte [] ba = content.array ();
    if (ba == null) {
      screen_loge ("ba == null...   pos: " + pos + "  siz: " + siz + " (" + hu_uti.hex_get (siz) + ")  last: " + last);
      return;
    }
    byte b1 = ba [pos + 3];
    byte bl = ba [last];
    //screen_logd ("pos: " + pos + "  siz: " + siz + "  last: " + last + " (" + hu_uti.hex_get (b1) + ")  b1: " + b1 + "  bl: " + bl + " (" + hu_uti.hex_get (bl) + ")");



    if (hu_uti.file_get ("/sdcard/hurec"))
      video_record_write (content);

    else if (video_recording)
      video_record_stop ();

    synchronized (m_surf_codec_lock) {
      if (m_codec == null) {
        return;
      }

      while (content.hasRemaining ()) {
//screen_logd ("content.hasRemaining (): " + content.hasRemaining ());    // content.hasRemaining (): true

        if (! codec_input_provide (content)) {                      // Process buffer
          if (lim_scrn_debug ()) screen_loge ("Dropping content because there are no available buffers.");
          return;
        }
        if (content.hasRemaining ())
          if (lim_scrn_debug ()) screen_loge ("content.hasRemaining ()");

        codec_output_consume ();                                    // Send result to video codec
      }
    }
  }

/*
  void device_id_get () {

    if (! BuildConfig.DEBUG)
      return;

    // ANDROID_ID and TelephonyManager.getDeviceId()
    String android_id = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
    if (BuildConfig.DEBUG) hu_uti.logd ("android_id: " + android_id);                         // android_id: c5da68763daf5900

    //UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
    //String deviceId = deviceUuid.toString();

    android.bluetooth.BluetoothAdapter m_BluetoothAdapter =  	android.bluetooth.BluetoothAdapter.getDefaultAdapter(); 
    String bluetooth_add = m_BluetoothAdapter.getAddress();
    if (BuildConfig.DEBUG) hu_uti.logd ("bluetooth_add: " + bluetooth_add);                   // bluetooth_add: B4:CE:F6:34:49:CD

    android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) this.getSystemService (Context.WIFI_SERVICE);
    String wifi_mac_address = "";
    if (wm == null)
      if (BuildConfig.DEBUG) hu_uti.loge ("wm: " + wm);
    else
      wifi_mac_address = wm.getConnectionInfo ().getMacAddress ();
    if (BuildConfig.DEBUG) hu_uti.logd ("wifi_mac_address: " + wifi_mac_address);             // On some devices, it's not available when Wi-Fi is turned off

    android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) this.getSystemService (Context.TELEPHONY_SERVICE);
    String tmdi = tm.getDeviceId ();
    if (BuildConfig.DEBUG) hu_uti.logd ("tmdi: " + tmdi);                                     // tmdi: null
    String sim_serial = tm.getSimSerialNumber ();
    if (BuildConfig.DEBUG) hu_uti.logd ("sim_serial: " + sim_serial);                         // sim_serial: null

    String serial = "";
    try {
      serial = android.os.Build.class.getField ("SERIAL").get (null).toString ();
      if (BuildConfig.DEBUG) hu_uti.logd ("serial: " + serial);                               // serial: HT4A1JT00958
    }
    catch (Throwable t) {
      if (BuildConfig.DEBUG) hu_uti.loge ("Throwable t: " + t);
    }
    try {
      Class<?> c = Class.forName ("android.os.SystemProperties");
      java.lang.reflect.Method get = c.getMethod ("get", String.class);
      serial = (String) get.invoke (c, "ro.serialno");
      if (BuildConfig.DEBUG) hu_uti.logd ("serial: " + serial);                               // serial: HT4A1JT00958
    }
    catch (Throwable t) {
      if (BuildConfig.DEBUG) hu_uti.loge ("Throwable t: " + t);
    }
  }
*/


    // Debug logs Email:

  private boolean new_logs = true;
  String logfile = "/sdcard/hulog.txt";//"/data/data/ca.yyx.hu/hu.txt";

  String cmd_build (String cmd) {
    String cmd_head = " ; ";
    String cmd_tail = " >> " + logfile;
    return (cmd_head + cmd + cmd_tail);
  }

  String new_logs_cmd_get () {
    String cmd  = "rm " + logfile;

    //cmd += cmd_build ("cat /data/data/ca.yyx.hu/shared_prefs/prefs.xml");
    cmd += cmd_build ("id");
    cmd += cmd_build ("uname -a");
    cmd += cmd_build ("getprop");
    cmd += cmd_build ("ps");
    cmd += cmd_build ("lsmod");
                                                                        // !! Wildcard * can lengthen command line unexpectedly !!
    cmd += cmd_build ("modinfo /system/vendor/lib/modules/* /system/lib/modules/*");
    //cmd += cmd_build ("dumpsys");                                     // 11 seconds on MotoG
    //cmd += cmd_build ("dumpsys audio");
    //cmd += cmd_build ("dumpsys media.audio_policy");
    //cmd += cmd_build ("dumpsys media.audio_flinger");
    cmd += cmd_build ("dumpsys usb");

    cmd += cmd_build ("dmesg");

    cmd += cmd_build ("logcat -d -v time");

    cmd += cmd_build ("ls -lR /data/data/ca.yyx.hu/ /data/data/ca.yyx.hu/lib/ /init* /sbin/ /firmware/ /data/anr/ /data/tombstones/ /dev/ /system/ /sys/");

    cmd += cmd_build ("cat " + hu_min_log);

    return (cmd);
  }

  Context m_context = this;

  private boolean file_email (String subject, String filename) {        // See http://stackoverflow.com/questions/2264622/android-multiple-email-attachment-using-intent-question
    Intent i = new Intent (Intent.ACTION_SEND);
    i.setType ("message/rfc822");                                       // Doesn't work well: i.setType ("text/plain");
    i.putExtra (Intent.EXTRA_EMAIL  , new String []{"mikereidis@gmail.com"});
    i.putExtra (Intent.EXTRA_SUBJECT, subject);
    i.putExtra (Intent.EXTRA_TEXT   , "Please write write problem, device/model and ROM/version. Please ensure " + filename + " file is actually attached or send manually. Thanks ! Mike.");
    i.putExtra (Intent.EXTRA_STREAM, Uri.parse ("file://" + filename)); // File -> attachment
    try {
      startActivity (Intent.createChooser (i, "Send email..."));
    }
    catch (android.content.ActivityNotFoundException e) {
      Toast.makeText (m_context, "No email. Manually send " + filename, Toast.LENGTH_LONG).show ();
    }
    //dlg_dismiss (DLG_WAIT);
    return (true);
  }

  private String hu_min_log = "/data/data/ca.yyx.hu/hu.log";
  private int long_logs_email () {
    String cmd = "bugreport > " + logfile;
    if (new_logs) {

      String str = "" + m_tv_log.getText ();
      hu_uti.file_write (this, hu_min_log, hu_uti.str_to_ba (str));

      logfile = "/sdcard/hulog.txt";//"/data/data/ca.yyx.hu/hu.txt";
      //hu_uti.daemon_set ("audio_alsa_log", "1");                       // Log ALSA controls
      cmd = new_logs_cmd_get ();
    }

    int ret = hu_uti.sys_run (cmd, false);//true);                              // Run "bugreport" and output to file
    if (hu_uti.su_installed_get ()) {
      hu_uti.sys_run ("logcat -d -v time >> " + logfile, true);
    }


    String subject = "Headunit " + hu_uti.app_version_get (m_context);
    boolean bret = file_email (subject, logfile);                       // Email debug log file

    return (0);
  }

  private Timer logs_email_tmr = null;
  private class logs_email_tmr_hndlr extends java.util.TimerTask {
    public void run () {
      int ret = long_logs_email ();
      click_ctr = 0;                                                    // Reset click_ctr to re-allow next 7 clicks
      hu_uti.logd ("done ret: " + ret);
    }
  }

  private int logs_email () {
    int ret = 0;
    logs_email_tmr = new Timer ("Logs Email", true);                    // One shot Poll timer for logs email
    if (logs_email_tmr != null) {
      logs_email_tmr.schedule (new logs_email_tmr_hndlr (), 10);        // Once after 0.01 seconds.
      Toast.makeText (m_context, "Please wait while debug log is collected. Will prompt when done...", Toast.LENGTH_LONG).show ();
    }
    return (ret);
  }

}

