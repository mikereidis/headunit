
// Headunit app Transport: USB / Wifi

package gb.xxy.hr;

import gb.xxy.hr.MediaServices.*;
import gb.xxy.hr.NavStatServices.*;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import android.os.Binder;

import android.os.Build;
import android.os.Bundle;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import android.widget.Toast;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class new_hu_tra  extends Service {

  static UsbDeviceConnection usbconn;
  private final IBinder mBinder = new LocalBinder();
  public int trans_aud = 0;
  private int hi_res;
  private long ip_result;
  private boolean is_stopped;
  private player m_player = null;
  private self_player self_m_player = null;
  protected WifiReceiver wifi_receiver;
  private int connection_mode=0;
  private boolean selfmode=false;
  public LocationManager locationManager;

  private Intent notificationIntent=null;
  private static final int AA_CH_CTR = 0;
  private static final int AA_CH_TOU = 1;
  private static final int AA_CH_MIC = 7;
  private Notification mynotification;
  private int last_speed=0;
  private int nightmode=0;
  private double sunset=0;
  private double sunrise=0;
  private double lastcalculated=0;
  private boolean sharegps;
  private int luxval=0;
  private float lastlux=0;
  private boolean autovol=true;
  public boolean isnightset=false;
  private boolean videorunning=false;
  private int luxsens=0;
  SensorManager mySensorManager;
  int mode=9;
  private NotificationManager mNotificationManager;
  private byte[] fanart;
  private String song;
  private String artist;
  private int duration;
  private boolean slowcpu=false;
  private String album;
  private boolean transport_audio;
  static UsbEndpoint m_usb_ep_in;
  static UsbEndpoint m_usb_ep_out;
  private boolean newtrunevent;
  private Integer totalturndistance;
  private NotificationCompat.Builder navnotification;
  private String manuver;
  private boolean useimperial=false;



  //private NsdManager.RegistrationListener mReglist;

  public class LocalBinder extends Binder {
    new_hu_tra getService() {
      // Return this instance of LocalService so clients can call public methods
      return new_hu_tra.this;
    }
  }


  public IBinder onBind(Intent intent) {
    Log.d("HU-SERVICE", "On Binder");
    return mBinder;
  }


  // Native API:

  {
    System.loadLibrary("hu_jni");
  }


  private native int native_aa_cmd(int cmd_len, byte[] cmd_buf, int res_len, byte[] res_buf, long myip_string, int transport_audio, int hires);








  //we need a stream where we can keep filling in the commands....
  public ByteArrayOutputStream send_que = new ByteArrayOutputStream();
  public ByteArrayOutputStream micdata = new ByteArrayOutputStream();
  public ByteArrayOutputStream contruct_media_message = new ByteArrayOutputStream();


  ExecutorService videoexecutor = Executors.newSingleThreadExecutor();
  ExecutorService audio0executor = Executors.newSingleThreadExecutor();
  ExecutorService audio1executor = Executors.newSingleThreadExecutor();
  ExecutorService audio2executor = Executors.newSingleThreadExecutor();


   volatile boolean m_mic_active = false;
   public volatile boolean m_stopping = false;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d("HU-SERVICE", "Thread Service Created");

    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    notificationIntent = new Intent(this, player.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);



    mynotification = new Notification.Builder(this)
            .setContentTitle("Headunit Reloaded...")
            .setContentText("Running....")
            .setSmallIcon(R.drawable.hu_icon_256)
            .setContentIntent(pendingIntent)
            .setTicker("")
            .build();

    startForeground(1, mynotification);

  /*  NsdServiceInfo serviceInfo = new NsdServiceInfo();
    serviceInfo.setServiceName("AndroidAuto");
    serviceInfo.setServiceType("_androidauto._tcp");

    serviceInfo.setPort(6666);
    NsdManager mNsdman = (NsdManager) this.getSystemService(this.NSD_SERVICE);
    mNsdman.registerService(serviceInfo,NsdManager.PROTOCOL_DNS_SD,registrationListener);
    */
    String locale;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        locale = getBaseContext().getResources().getConfiguration().getLocales().get(0).getCountry();
    else
      locale = getBaseContext().getResources().getConfiguration().locale.getCountry();

    if (locale.equalsIgnoreCase("US") || locale.equalsIgnoreCase("GB") || locale.equalsIgnoreCase("LR") || locale.equalsIgnoreCase("MM"))
      useimperial=true;
  }
  /*  //NSD FOR LATER
  private final NsdManager.RegistrationListener registrationListener = new NsdManager.RegistrationListener() {
    @Override
    public void onRegistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
      Log.v("NSD", "register failed :" + nsdServiceInfo.toString());
    }

    @Override
    public void onUnregistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
      Log.v("NSD", "unRegister failed :" + nsdServiceInfo.toString());
    }

    @Override
    public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
      Log.v("NSD", "Service Registered: " + nsdServiceInfo.toString());
    }

    @Override
    public void onServiceUnregistered(NsdServiceInfo nsdServiceInfo) {
      Log.v("NSD", "Service unRegistered: " + nsdServiceInfo.toString());
    }
  };*/

    @Override
  public int onStartCommand(Intent intent, int flags, int startId) {

    super.onStartCommand(intent, flags, startId);
    try {
      mode = intent.getExtras().getInt("mode", 0);
    }
      catch (Exception e)
      {
        mode=5;
      }
      Log.d("HU-SERVICE","On start command mode:"+mode);
    /*Mode description
    Mode 0 - Self Mode
    Mode 1 - Started AA from Main activity with Wifi
    Mode 2 - Started AA from Main activity with USB
    Mode 3 - Started AA from Wifi received onConnect
    Mode 4 - Started AA from Wifi receiver onDisconnect
    Mode 5 - Just create the Service, don't run anything.
    */

    IntentFilter filter = new IntentFilter ();
    filter.addAction ("android.net.conn.CONNECTIVITY_CHANGE");
    filter.addAction ("gb.xxy.hr.playpause");
    filter.addAction ("gb.xxy.hr.next");
    filter.addAction ("gb.xxy.hr.prev");
    wifi_receiver = new WifiReceiver ();
    IntentFilter filter2 = new IntentFilter("gb.xxy.hr.mediaupdateintent");
    registerReceiver (wifi_receiver, filter);
    registerReceiver (wifi_receiver, filter2);

    if (mode == 0) {
      notificationIntent = new Intent(this, self_player.class);
      if (!aap_running)
        jni_aap_start("127.0.0.1", 0, 0);
      selfmode = true;
      PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
      mynotification = new Notification.Builder(this)
              .setContentTitle("Headunit Reloaded...")
              .setContentText("Running....")
              .setSmallIcon(R.drawable.hu_icon_256)
              .setContentIntent(pendingIntent)
              .setTicker("")
              .build();
      startForeground(1, mynotification);
    }

    else if (mode == 4)
      stopSelf();


    if (!aap_running) {
      Log.d("HU-SERVICE", "Service Start called");
     /* Log.d("HU-SERVICE", "Should start: " + notificationIntent.toString());
      notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      notificationIntent.putExtra("key", 1);
      startActivity(notificationIntent);*/

      if (isLocationEnabled(this))
                start_gps_track();
      else
      {
        recheck_gps();
      }

      SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      nightmode = Integer.parseInt(SP.getString("lightsens", "1"));
      sharegps = SP.getBoolean("share_GPS", true);
      autovol = SP.getBoolean("auto_adjust_audio", false);
      luxval = Integer.parseInt(SP.getString("luxval", "0"));
      luxsens = Integer.parseInt(SP.getString("luxsensibility", "0"));
      if (SP.getBoolean("h264",false))
        slowcpu=SP.getBoolean("slowcpu",false);

      if (nightmode == 0) {
        mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor LightSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (LightSensor != null) {
          mySensorManager.registerListener(
                  LightSensorListener,
                  LightSensor,
                  SensorManager.SENSOR_DELAY_NORMAL);
        }
      }

    }
      if (mode==3)
      {
        Intent starts = new Intent(this, player.class);
        starts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(starts);
      }
    return START_STICKY;
  }

  private void recheck_gps()
  {
    final Handler handler = new Handler();
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        if (isLocationEnabled(getBaseContext()))
          start_gps_track();
        else
        {
          recheck_gps();
        }
      }
    },5000);

  }


  public boolean isLocationEnabled(Context context) {
    int locationMode = 0;
    String locationProviders;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
      try {
        locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);

      } catch (Settings.SettingNotFoundException e) {
        e.printStackTrace();
        return false;
      }

      return locationMode != Settings.Secure.LOCATION_MODE_OFF;

    }else{
      locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
      return !TextUtils.isEmpty(locationProviders);
    }


  }

  private void start_gps_track () {
    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    if ( locationManager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, listener);
      Location lastlocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
      if (lastlocation == null)
        lastlocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
      if (lastlocation != null)
        Calculate_Sunset_Sunrise(lastlocation);
    }
    else
    {
      Log.d("HU-GPS","No GPS provider");
    }
  }

  public class WifiReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equalsIgnoreCase("gb.xxy.hr.playpause") && aap_running)
      {
        long ts = android.os.SystemClock.elapsedRealtime () * 1000000L;
        key_send(0x55, 1, ts);
        key_send(0x55, 0, ts + 200);
      }
      else if (intent.getAction().equalsIgnoreCase("gb.xxy.hr.prev") && aap_running)
      {
        long ts = android.os.SystemClock.elapsedRealtime () * 1000000L;
        key_send(0x58, 1, ts);
        key_send(0x58, 0, ts + 200);
      }
      else if (intent.getAction().equalsIgnoreCase("gb.xxy.hr.next") && aap_running)
      {
        long ts = android.os.SystemClock.elapsedRealtime () * 1000000L;
        key_send(0x57, 1, ts);
        key_send(0x57, 0, ts + 200);
      }
      else {
        ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conMan.getActiveNetworkInfo();
        if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI)
          Log.d("WifiReceiver", "Have Wifi Connection");
        else {
          Log.d("WifiReceiver", "No Wifi Connection." + aap_running + "mode: " + connection_mode);
          if (aap_running && connection_mode == 1) {
            if (m_player != null) {
              Message msg = m_player.handler.obtainMessage();
              if (mode != 3 && mode != 4)
                msg.arg2 = 0;
              else
                msg.arg2 = 1;
              msg.arg1 = 3;
              m_player.handler.sendMessage(msg);
            }
            if (mode == 3 || mode == 4)
              stopSelf();
          }
        }
      }
    }
  };




  //private int m_out_bufsize0 = 16*32768;            // 186 ms at 44.1K stereo 16 bit        //5120 * 16;//65536;
  private int m_out_bufsize0 =  32768*4;
  private int m_out_bufsize1 = 2048*4;
  private int m_out_bufsize2 = 2048*4;
  private int m_out_audio_stream = AudioManager.STREAM_MUSIC;
  private int m_out_samplerate0 = 48000;//44100;            // Default that all devices can handle =  44.1Khz CD samplerate
  private int m_out_samplerate1 = 16000;
  private int m_out_samplerate2 = 16000;
  private AudioTrack m_out_audiotrack0 = null;
  private AudioTrack m_out_audiotrack1 = null;
  private AudioTrack m_out_audiotrack2 = null;
  private int                   m_mic_src           = 0;  // Default
  private AudioRecord m_mic_audiorecord   = null;
  private int m_out_aud0_pos=0;
  private int m_out_aud1_pos=0;
  private int m_out_aud2_pos=0;


  private void out_audio_start(int chan) {
    try {
      if (chan == 0)
      {
        m_out_audiotrack0 = new AudioTrack(m_out_audio_stream, m_out_samplerate0, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, m_out_bufsize0, AudioTrack.MODE_STREAM);
        m_out_aud0_pos=0;
      }
      else if (chan == 1)
      { m_out_audiotrack1 = new AudioTrack(m_out_audio_stream, m_out_samplerate1, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, m_out_bufsize1, AudioTrack.MODE_STREAM);
        m_out_aud1_pos=0;}
      else if (chan == 2) {
        m_out_audiotrack2 = new AudioTrack(m_out_audio_stream, m_out_samplerate2, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, m_out_bufsize2, AudioTrack.MODE_STREAM);
        m_out_aud2_pos=0;
      }
     /* if (chan == 0)
        m_out_audiotrack0.play();                                         // Start output
     /* else if (chan == 1)
        m_out_audiotrack1.play();                                         // Start output
      else if (chan == 2)
        m_out_audiotrack2.play();                                         // Start output*/
    } catch (Throwable e) {
      //hu_uti.loge("Throwable: " + e);
      e.printStackTrace();
    }
  }




  private Runnable pollthread = new Runnable() {

    public void run() {
      try {
        Looper.prepare();
      }
      catch (Exception e)
      {
        Log.e("HU-SERVICE",e.getMessage());
      }
           while (!m_stopping) {
             int ret=0;

        if (send_que.size() > 0) { //If we have nothing in the que there is nothing to do :)
          byte c[] = send_que.toByteArray();
          send_que.reset(); // reset the que
          //hu_uti.hex_dump ("HEX_DUMP: ", c, c.length);

          int sendsize = c.length;
          int sendpos = 0;
          while (sendpos < sendsize) {
            int fragment_size = ByteBuffer.wrap(c, sendpos, 4).getInt();
            sendpos = sendpos + 4;
            byte [] senddata=new byte[fragment_size];
            System.arraycopy(c,sendpos,senddata,0,fragment_size);
            aa_cmd_send(fragment_size,senddata,0,null,"");
            sendpos = sendpos + fragment_size;
          }

        }

        if (micdata.size()>128) // If there is mic data....
        {
          byte [] tmp_mic=micdata.toByteArray();
          micdata.reset();
          byte[] ba_mic = new byte[14 + tmp_mic.length];//0x02, 0x0b, 0x03, 0x00,
          ba_mic[0] = AA_CH_MIC;// Mic channel
          ba_mic[1] = 0x0b;  // Flag filled here
          ba_mic[2] = 0x00;  // 2 bytes Length filled here
          ba_mic[3] = 0x00;
          ba_mic[4] = 0x00;  // Message Type = 0 for data, OR 32774 for Stop w/mandatory 0x08 int and optional 0x10 int (senderprotocol/aq -> com.google.android.e.b.ca)
          ba_mic[5] = 0x00;

          long ts = android.os.SystemClock.elapsedRealtime();        // ts = Timestamp (uptime) in microseconds
          int ctr = 0;
          for (ctr = 7; ctr >= 0; ctr--) {                           // Fill 8 bytes backwards
            ba_mic[6 + ctr] = (byte) (ts & 0xFF);
            ts = ts >> 8;
          }
            System.arraycopy(tmp_mic,0,ba_mic,14,tmp_mic.length);
            aa_cmd_send(14 + tmp_mic.length, ba_mic, 0, null, "");


        }

        else
         ret = aa_cmd_send(0, null, 0, null, "");
             if (ret==0)
               try {
                 Thread.sleep(5);
               } catch (InterruptedException e) {
                 e.printStackTrace();
               }


           }
      isnightset=false;
      videorunning=false;
      Log.d("HU-SERVICE","Thread finished the loop.");
      aap_running = false;

    }
  };



  public int jni_aap_start(String myip_string, int m_ep_out_addr, int m_ep_in_addr) {                                         // Start JNI Android Auto Protocol and Main Thread. Called only by usb_attach_handler(), usb_force() & hu_act.wifi_long_start()
    Log.d("HU-SERVICE","JNI_AAP_START: "+ myip_string+" ep_out:" +m_ep_out_addr+" ep in: "+m_ep_in_addr);

    videorunning=false;
    SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    boolean hires = SP.getBoolean("hires", false);

    transport_audio = SP.getBoolean("phoneaudio", false);
    trans_aud = 0;
    if (transport_audio) {
      trans_aud = 1;
    }

    if (slowcpu)
      trans_aud=0;        //Disable audio sink for slow CPU, use Bluetooth instead!

    hi_res = 0;
    if (hires) {
      hi_res = 1;
    }
    if (myip_string == "127.0.0.1") {
      trans_aud = 0;
      transport_audio = false;
    }
    else
      m_stopping=false;

    ip_result = 0;
    String unitname=SP.getString("displayname","Headunit");
    Log.d("HU-SETUP","Unit name:" + unitname);
    byte[] cmd_buf = new byte[4+unitname.length()];
    cmd_buf[0]=121;
    cmd_buf[1]=(byte)255;
    cmd_buf[2]=(byte)255;
    cmd_buf[3]=(byte)255;
    for (int mi = 0; mi < unitname.length(); mi ++) {
      cmd_buf[mi+4] = (byte) (int) unitname.charAt(mi);
    }
   // byte[] cmd_buf = {121, (byte) 255, (byte) 255};                                   // Start Request w/ m_ep_in_addr, m_ep_out_addr

    if (!myip_string.isEmpty()) {
      String[] ipAddressInArray = myip_string.split("\\.");
      for (int i = 0; i < ipAddressInArray.length; i++) {
        int power = 3 - i;
        int ip = Integer.parseInt(ipAddressInArray[power]);
        ip_result += ip * Math.pow(256, power);
      }
    }
    else {
      cmd_buf[1] = (byte) m_ep_in_addr;
      cmd_buf[2] = (byte) m_ep_out_addr;
      if (SP.getBoolean("oldusb",false))
        cmd_buf[3]=1;

      mode=2;
    }


  /*
	Bluetooth MAC setup....
    byte[] cmd_buf = new byte[20];
    cmd_buf[0]=(byte)121;
    cmd_buf[1]=(byte)-127;
    cmd_buf[2]=(byte)2;

    String hf_mac=SP.getString("hf_mac","");

    for (int mi = 0; mi < hf_mac.length(); mi ++) {
      cmd_buf[mi+3] = (byte) (int) hf_mac.charAt(mi);
    }
	//This is needed for the Bluetooth Mac address to be passed over to the C function, to be used for (Joying and others)
*/






    Log.d("HU-SERVICE","Running Start");
    int ret = aa_cmd_send(cmd_buf.length, cmd_buf, 0, null, myip_string);           // Send: Start USB & AA

    if (ret == 0) {                                                     // If started OK...
      Log.d("HU-SERVICE","aa_cmd_send ret: " + ret);
      aap_running = true;

      if (myip_string != "")
        connection_mode=1;
      else if (myip_string.equalsIgnoreCase("127.0.0.1"))
        connection_mode=3;
      else
        connection_mode=2;

      Thread polling = new Thread(pollthread, "Playback audio");
      polling.start();


    }

      return (ret);
  }



  public int night_toggle(int state_to) {
    byte[] data = {  0x02, (byte) 0x80, 0x03, 0x52, 0x02, 0x08, (byte) state_to};
    byte [] tosend=new byte[11];
    byte [] nextchunk=ByteBuffer.allocate(4).putInt(7).array();
    System.arraycopy(nextchunk,0,tosend,0,4);
    System.arraycopy(data,0,tosend,4,7);
   // Log.d("HU-SERVICE","Sendq night toggle");
    send_que.write(tosend,0,tosend.length);
   // send_que.write(data, 0, 9);
    return (0);
  }

  public int byebye_send() {                                          // Send Byebye request. Called only by transport_stop (), tra_thread:run()
    //hu_uti.logd ("");
    send_que.reset();
    m_stopping=true;
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    byte[] cmd_buf = {AA_CH_CTR, 0x0b, 0, 0, 0, 0x0f, 0x08, 0};          // Byebye Request:  000b0004000f0800  00 0b 00 04 00 0f 08 00
    int ret = aa_cmd_send(cmd_buf.length, cmd_buf, 0, null, "");           // Send

    return (ret);
  }

  private byte[] fixed_cmd_buf = new byte[256];
  private byte[] fixed_res_buf = new byte[65536 * 4];

  // Send AA packet/HU command/mic audio AND/OR receive video/output audio/audio notifications
  public int aa_cmd_send(int cmd_len, byte[] cmd_buf, int res_len, byte[] res_buf, String myip_string) {
    // synchronized (this) {

    if (cmd_buf == null || cmd_len <= 0) {
      cmd_buf = fixed_cmd_buf;//new byte [256];// {0};                                  // Allocate fake buffer to avoid problems
      cmd_len = 0;//cmd_buf.length;
    }
    if (res_buf == null || res_len <= 0) {
      res_buf = fixed_res_buf;//new byte [65536 * 16];  // Seen up to 151K so far; leave at 1 megabyte
      res_len = res_buf.length;
    }


    int ret = native_aa_cmd(cmd_len, cmd_buf, res_len, res_buf, ip_result, trans_aud, hi_res);       // Send a command (or null command)
       if (ret == -9)
    {
      Log.d("HU-SERVICE","Not able to connect the Headunit Server, is it running????");
      if (m_player!=null)
      {
        Message msg = m_player.handler.obtainMessage();
        msg.arg1 = 1;
        m_player.handler.sendMessage(msg);
      }
      else if (self_m_player!=null)
      {
        Message msg = self_m_player.handler.obtainMessage();
        msg.arg1 = 1;
        self_m_player.handler.sendMessage(msg);
      }
      else
      {
        m_stopping=true;
        aap_running=false;
        //this.stopSelf();
      }
    }
    else if (ret < 0) {
      Log.d("HU-SERVICE","We are going to stop, current mode is: "+mode);
      if (!is_stopped) {
        byte[] cmd_buf_finisf = {AA_CH_CTR, 0x0b, 0, 0, 0, 0x0f, 0x08, 0};
        ret = aa_cmd_send(cmd_buf_finisf.length, cmd_buf_finisf, 0, null, "");
      }
        is_stopped = true;
         m_stopping=true;
         aap_running=false;

      //Send activity close signal
         if (mode==0)
         {
           UiModeManager uiManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
           uiManager.disableCarMode(0);
         }
                  if (m_player!=null)
                  {
                    Message msg = m_player.handler.obtainMessage();
                    msg.arg1 = 3;
                     if( mode==3 || mode==2)
                       msg.arg2=1;
                    m_player.handler.sendMessage(msg);
                  }
                  else if (self_m_player!=null)
                  {
                    Message msg = self_m_player.handler.obtainMessage();
                    msg.arg1 = 3;
                    self_m_player.handler.sendMessage(msg);
                  }
                  else if (connection_mode != 1)
                  {
                    Log.e("HU-SERVICE","No player and no self-Player defined...");
                    Intent i = new Intent("gb.xxy.hr.show_connection_error");
                    sendBroadcast(i);
                  }

    }
       else if (ret==0)
       {
          return(0);
       }
    else if (ret == 1) {                                                     // If mic stop...
      Log.d("HU-SERVICE", "Microphone Stop");
      m_mic_active = false;
      /*if (m_player!=null)
          m_player.mic_audio_stop();
      else if (self_m_player != null)
        self_m_player.mic_audio_stop();
        */
      return (0);
    } else if (ret == 2) {                                                // Else if mic start...
         if (!m_mic_active) {
           Log.d("HU-SERVICE", "Microphone Start");
           m_mic_active = true;
             mic_audio_start();
         }
      return (0);
      }
       else if (ret == 3) {                                                // Else if audio stop...
         Log.d("HU-SERVICE", "Audio Stop");
         stop_audio(m_out_audiotrack1,audio0executor);
         return (0);
        }
      else if (ret == 4)
       {                                                // Else if audio1 stop...
      Log.d("HU-SERVICE", "Audio1 Stop");
      stop_audio(m_out_audiotrack1,audio1executor);
      return (0);
      }
       else if (ret == 5) {                                                // Else if audio2 stop...
        Log.d("HU-SERVICE", "Audio2 Stop");
         stop_audio(m_out_audiotrack2,audio2executor);
       return (0);
    }
       else if (ret > 0) {                                                 // Else if audio or video returned...

         final byte[] dataslice = Arrays.copyOfRange(res_buf,1,ret);
         ret--;
         final int myret=ret;
            if (res_buf[0] == 3) {

        if (m_player!=null)
        {
          if (!videorunning)
          {
            videorunning=true;
            if (nightmode==3)
              night_toggle(1);
            else if (nightmode==2)
              night_toggle(0);
         }
          videoexecutor.submit(new Runnable() {
            @Override
            public void run() {
              m_player.media_decode(dataslice, myret);
            }
          });

        }
        else if (self_m_player !=null)
          self_m_player.media_decode(dataslice,ret);
      }
      else if (res_buf[0] == 4)
              play_audio(myret,dataslice,1,audio1executor,m_out_audiotrack1);
      else if (res_buf[0] == 5)
              play_audio(myret,dataslice,2,audio2executor,m_out_audiotrack2);
      else if (res_buf[0]==6)
              play_audio(myret,dataslice,0,audio0executor,m_out_audiotrack0);
      else if (res_buf[0]==9)
        {
          int message_type = (int) dataslice[1];
          message_type = message_type + (Math.abs((int) dataslice[0]) * 256);
          if (message_type==32769) {
            try {
              MediaMetaData metadata = MediaMetaData.parseFrom(contruct_media_message.toByteArray());
              PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

              Intent intent = new Intent("gb.xxy.hr.mediaupdateintent");
              intent.setAction("gb.xxy.hr.playpause");
              PendingIntent playpause =PendingIntent.getBroadcast (this,1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
              intent.setAction("gb.xxy.hr.next");
              PendingIntent next =PendingIntent.getBroadcast (this,1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
              intent.setAction("gb.xxy.hr.prev");
              PendingIntent prev =PendingIntent.getBroadcast (this,1, intent, PendingIntent.FLAG_UPDATE_CURRENT);


              if (metadata.toString().length()>2) {

                fanart=metadata.getAlbumart().toByteArray();
                song=metadata.getSong();
                artist=metadata.getArtist();
                album=metadata.getAlbum();
                duration=metadata.getDuration();
                Bitmap b = BitmapFactory.decodeByteArray(fanart, 0, fanart.length);
                Bitmap resized = null;
                if (b!=null) {
                  resized=Bitmap.createBitmap(b.getWidth()+160,b.getHeight()+160,Bitmap.Config.ARGB_8888);
                  resized.eraseColor(Color.argb(255,255,255,255));
                  Canvas canvas = new Canvas(resized);

                  Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.hu160);
                  canvas.drawBitmap(logo, 0, 0, new Paint());
                  canvas.drawBitmap(b, 80, 160, new Paint());



                }
                NotificationCompat.MediaStyle notiStyle = new NotificationCompat.MediaStyle();



                mynotification = new NotificationCompat.Builder(this)
                        .setContentTitle(song)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setContentText(album + " - " + artist)
                        .setSubText("Remaing: "+ String.format("%02d:%02d", duration / 60, duration % 60))
                        .setSmallIcon(R.drawable.hu_icon_256)
                        .setLargeIcon(resized)
                        .setContentIntent(pendingIntent)
                        .setStyle(notiStyle)
                        .addAction (R.drawable.ic_skip_previous,"", prev)
                        .addAction (R.drawable.ic_play_pause,"", playpause)
                        .addAction (R.drawable.ic_skip_next,"", next)

                .build();
                mNotificationManager.notify(1, mynotification);
              }

              
            } catch (InvalidProtocolBufferException e) {
              e.printStackTrace();
            }
            contruct_media_message.reset();
          }
          else if (message_type==32771)
          {
            contruct_media_message.write(dataslice,2,ret-2);
          }
          else {
            contruct_media_message.write(dataslice,0,ret);

          }
        }
            else if (res_buf[0]==10)
            {
              int message_type = (int) dataslice[1];
              message_type = message_type + (Math.abs((int) dataslice[0]) * 256);
              byte [] message_data =  Arrays.copyOfRange(dataslice,2,ret);
              if (message_type==32772) {
                try {
                  NextTurnDetail nextturn=NextTurnDetail.parseFrom(message_data);
                  newtrunevent=true;
                  fanart=nextturn.getTurngraph().toByteArray();
                  String road=nextturn.getRoad();
                  manuver=eventtostring(nextturn.getNextturn().getNumber(),nextturn.getSide().toString());
                  Bitmap b = BitmapFactory.decodeByteArray(fanart, 0, fanart.length);
                  PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
                   navnotification = new NotificationCompat.Builder(this);
                   navnotification.setContentTitle(manuver)
                          .setContentText(road)
                          .setLargeIcon(b)
                          .setSmallIcon(R.drawable.hu_icon_256)
                         /* .setVibrate(new long[0])
                          .setPriority(Notification.PRIORITY_HIGH)*/
                          .setContentIntent(pendingIntent)
                          .setProgress(100, 0, false);

                  mNotificationManager.notify(2, navnotification.build());
                //  Log.d("HU-NAVSTAT",bytesToHex(message_data));

                } catch (InvalidProtocolBufferException e) {
                  e.printStackTrace();
                }
              }
              else
              {
                try {
                  Nextturndistnaceevent nextturndist=Nextturndistnaceevent.parseFrom(message_data);
                  Integer nextturntime=nextturndist.getTime();
                  Integer nextturndistance=nextturndist.getDistance();

                  if (newtrunevent)
                  {
                    totalturndistance=nextturndistance;
                    newtrunevent=false;
                  }
                  else
                  if (useimperial)
                    if (nextturndistance>300)
                      navnotification.setProgress(totalturndistance,nextturndistance,false).setContentTitle("IN "+ String.format("%.2f", (nextturndistance / 1609.34)) +" miles "+manuver);
                  else
                      navnotification.setProgress(totalturndistance,nextturndistance,false).setContentTitle("IN "+Math.round(nextturndistance * 3.28084)+" ft "+manuver);
                  else
                    navnotification.setProgress(totalturndistance,nextturndistance,false).setContentTitle("IN "+nextturndistance+" meters "+manuver);

                  if (m_player==null && self_m_player == null && ((nextturndistance<300) || (nextturntime>0 && nextturntime<30)))
                    navnotification.setVibrate(new long[0])
                          .setPriority(Notification.PRIORITY_HIGH);

                  mNotificationManager.notify(2,navnotification.build());


                  //Log.d("HU-NAVSTAT",Nextturndistnaceevent.parseFrom(message_data).toString());
                } catch (InvalidProtocolBufferException e) {
                  e.printStackTrace();
                }
              }
            }
      }




    return (0);

    // }
  }

  private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for ( int j = 0; j < bytes.length; j++ ) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = hexArray[v >>> 4];
      hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
  }

  private byte[] ba_touch = null;

  public void rotary(int direction,long ts) {
    if (!aap_running)
      return;


    byte[] ba_key = null;
    ba_key = new byte[50];
    ba_key[0] = AA_CH_TOU;
    ba_key[1] = (byte) 0x0b;
    ba_key[2] = (byte) 0x00;
    ba_key[3] = (byte) 0x00;
    ba_key[4] = (byte) -128;
    ba_key[5] = (byte) 0x01;
    int idx = varint_encode(ts, ba_key, 7);
    ba_key[6] = (byte) 0x08;
    idx = idx + 7;
    ba_key[idx++] = (byte) 0x32;
   if (direction==1)
   {
     ba_key[idx++] = (byte) 0x08;
     ba_key[idx++] = (byte) 0x0a;
     ba_key[idx++] = (byte) 0x06;
     ba_key[idx++] = (byte) 0x08;
     ba_key[idx++] = (byte) 0x80;
     ba_key[idx++] = (byte) 0x80;
     ba_key[idx++] = (byte) 0x04;
     ba_key[idx++] = (byte) 0x10;
     ba_key[idx++] = (byte) 0x01;
   }
   else if (direction==2)
   {
     ba_key[idx++] = (byte) 0x11;
     ba_key[idx++] = (byte) 0x0a;
     ba_key[idx++] = (byte) 0x0f;
     ba_key[idx++] = (byte) 0x08;
     ba_key[idx++] = (byte) 0x80;
     ba_key[idx++] = (byte) 0x80;
     ba_key[idx++] = (byte) 0x04;
     ba_key[idx++] = (byte) 0x10;
     ba_key[idx++] = (byte) 0xff;
     ba_key[idx++] = (byte) 0xff;
     ba_key[idx++] = (byte) 0xff;
     ba_key[idx++] = (byte) 0xff;
     ba_key[idx++] = (byte) 0xff;
     ba_key[idx++] = (byte) 0xff;
     ba_key[idx++] = (byte) 0xff;
     ba_key[idx++] = (byte) 0xff;
     ba_key[idx++] = (byte) 0xff;
     ba_key[idx++] = (byte) 0x01;
   }
    else if (direction==3)
    {
      ba_key[idx++] = (byte) 0x08;
      ba_key[idx++] = (byte) 0x0a;
      ba_key[idx++] = (byte) 0x06;
      ba_key[idx++] = (byte) 0x08;
      ba_key[idx++] = (byte) 0x80;
      ba_key[idx++] = (byte) 0x80;
      ba_key[idx++] = (byte) 0x04;
      ba_key[idx++] = (byte) 0x10;
      ba_key[idx++] = (byte) 0x05;
    }
    else if (direction==4)
    {
      ba_key[idx++] = (byte) 0x11;
      ba_key[idx++] = (byte) 0x0a;
      ba_key[idx++] = (byte) 0x0f;
      ba_key[idx++] = (byte) 0x08;
      ba_key[idx++] = (byte) 0x80;
      ba_key[idx++] = (byte) 0x80;
      ba_key[idx++] = (byte) 0x04;
      ba_key[idx++] = (byte) 0x10;
      ba_key[idx++] = (byte) 0xfb;
      ba_key[idx++] = (byte) 0xff;
      ba_key[idx++] = (byte) 0xff;
      ba_key[idx++] = (byte) 0xff;
      ba_key[idx++] = (byte) 0xff;
      ba_key[idx++] = (byte) 0xff;
      ba_key[idx++] = (byte) 0xff;
      ba_key[idx++] = (byte) 0xff;
      ba_key[idx++] = (byte) 0xff;
      ba_key[idx++] = (byte) 0x01;
    }

    byte [] tosend=new byte[idx+4];
    byte [] nextchunk=ByteBuffer.allocate(4).putInt(idx).array();
    System.arraycopy(nextchunk,0,tosend,0,4);
    System.arraycopy(ba_key,0,tosend,4,idx);

    send_que.write(tosend,0,tosend.length);


  }

  public void key_send(int key, int action, long ts) {

    if (!aap_running)
      return;


    byte[] ba_key = null;
    ba_key = new byte[50];
    ba_key[0] = AA_CH_TOU;
    ba_key[1] = (byte) 0x0b;
    ba_key[2] = (byte) 0x00;
    ba_key[3] = (byte) 0x00;
    ba_key[4] = (byte) -128;
    ba_key[5] = (byte) 0x01;
    int idx = varint_encode(ts, ba_key, 7);
    ba_key[6] = (byte) 0x08;
    idx = idx + 7;
    ba_key[idx++] = (byte) 0x22;
    ba_key[idx++] = (byte) 0x0a;
    ba_key[idx++] = (byte) 0x0a;
    ba_key[idx++] = (byte) 0x08;
    ba_key[idx++] = (byte) 0x08;
    ba_key[idx++] = (byte) key;
    ba_key[idx++] = (byte) 0x10;
    ba_key[idx++] = (byte) action;
    ba_key[idx++] = (byte) 0x18;
    ba_key[idx++] = (byte) 0x00;
    ba_key[idx++] = (byte) 0x20;
    ba_key[idx++] = (byte) 0x00;


    byte [] tosend=new byte[idx+4];
    byte [] nextchunk=ByteBuffer.allocate(4).putInt(idx).array();
    System.arraycopy(nextchunk,0,tosend,0,4);
    System.arraycopy(ba_key,0,tosend,4,idx);

    send_que.write(tosend,0,tosend.length);



  }

  public void touch_send(byte action, int x, int y, long ts) {                  // Touch event send. Called only by hu_act:touch_send()


    ba_touch = new byte[]{AA_CH_TOU, 0x0b, 0x00, 0x00, -128, 0x01, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0, 0, 0, 0x1a, 0x0e, 0x0a, 0x08, 0x08, 0x2e, 0, 0x10, 0x2b, 0, 0x18, 0x00, 0x10, 0x00, 0x18, 0x00};

    int siz_arr = 0;

    int idx = 1 + 6 + varint_encode(ts, ba_touch, 1 + 6);          // Encode timestamp

    ba_touch[idx++] = 0x1a;                                           // Value 3 array
    int size1_idx = idx;                                                // Save size1_idx
    ba_touch[idx++] = 0x0a;                                           // Default size 10
//
    ba_touch[idx++] = 0x0a;                                           // Contents = 1 array
    int size2_idx = idx;                                                // Save size2_idx
    ba_touch[idx++] = 0x04;                                           // Default size 4
    //
    ba_touch[idx++] = 0x08;                                             // Value 1
    siz_arr = varint_encode(x, ba_touch, idx);                 // Encode X
    idx += siz_arr;
    ba_touch[size1_idx] += siz_arr;                                    // Adjust array sizes for X
    ba_touch[size2_idx] += siz_arr;

    ba_touch[idx++] = 0x10;                                             // Value 2
    siz_arr = varint_encode(y, ba_touch, idx);                 // Encode Y
    idx += siz_arr;
    ba_touch[size1_idx] += siz_arr;                                    // Adjust array sizes for Y
    ba_touch[size2_idx] += siz_arr;

    ba_touch[idx++] = 0x18;                                             // Value 3
    ba_touch[idx++] = 0x00;                                           // Encode Z ?
    //
    ba_touch[idx++] = 0x10;
    ba_touch[idx++] = 0x00;

    ba_touch[idx++] = 0x18;
    ba_touch[idx++] = action;



    byte [] tosend=new byte[idx+4];
    byte [] nextchunk=ByteBuffer.allocate(4).putInt(idx).array();
    System.arraycopy(nextchunk,0,tosend,0,4);
    System.arraycopy(ba_touch,0,tosend,4,idx);

    send_que.write(tosend,0,tosend.length);


  }


  public volatile boolean aap_running = false;



  public void quit() {
    m_stopping = true;
  }

  public void update_mplayer(player player){
    m_player=player;
    notificationIntent = new Intent(this, player.class);
  }

  public void update_self_mplayer(self_player player){
    self_m_player=player;
    notificationIntent = new Intent(this, self_player.class);
  }


  public void onDestroy() {
    super.onDestroy();
    try {
      unregisterReceiver(wifi_receiver);
      unregisterReceiver(wifi_receiver);
    }
    catch (Exception e)
    {
      Log.d("WIFI-SERVICE","Nothing to unregister...");
    }
    try {

      locationManager.removeUpdates(listener);
    }
    catch (Exception e)
    {
      Log.w("HU-SERVICE","Cannot remove loaction listener");
    }
    try {
      mySensorManager.unregisterListener(LightSensorListener);
    }
    catch (Exception e)
    {
      Log.w("HU-SERVICE","Cannot remove lightsensor listener");
    }
    //byebye_send();
    m_stopping=true;
   //android.os.Process.killProcess(android.os.Process.myPid());
  }

  int mic_audio_start() {
    try {
      int bufferSize = AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
      m_mic_audiorecord = new AudioRecord (m_mic_src, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
      int rec_state = m_mic_audiorecord.getState ();
      Log.d ("HU-SERVICE","rec_state: " + rec_state);
      if (rec_state == AudioRecord.STATE_INITIALIZED) {                 // If Init OK...
        Log.d ("HU-SERVICE","Success with m_mic_src: " + m_mic_src);
        stop_audio_onmic(m_out_audiotrack0,audio0executor);
        stop_audio_onmic(m_out_audiotrack1,audio1executor);
        stop_audio_onmic(m_out_audiotrack2,audio2executor);
        m_mic_audiorecord.startRecording ();                            // Start input

        Thread thread_mic_audio = new Thread(run_mic_audio, "mic_audio");
        Log.d ("HU-SERVICE","thread_mic_audio: " + thread_mic_audio);
        if (thread_mic_audio == null)
          Log.e ("HU-SERVICE","thread_mic_audio == null");
        else {

          java.lang.Thread.State thread_state = thread_mic_audio.getState ();
          if (thread_state == java.lang.Thread.State.NEW || thread_state == java.lang.Thread.State.TERMINATED) {
            //Log.d ("thread priority: " + thread_mic_audio.getPriority ());   // Get 5
            thread_mic_audio.start ();
          }
          else
            Log.e ("HU-SERVICE","thread_mic_audio thread_state: " + thread_state);
        }

        return (0);
      }
    }
    catch (Exception e) {
      Log.e ("HU-SERVICE","Exception: " + e );  // "java.lang.IllegalArgumentException: Invalid audio source."
      m_mic_audiorecord = null;
      return (-2);
    }
    m_mic_audiorecord = null;
    return (-1);
  }

  private final Runnable run_mic_audio = new Runnable () {
    public void run () {
      Log.d ("HU-SERVICE","run_mic_audio");
      micdata.reset();
      while (m_mic_active) {                                 // While Thread should be active...
        byte[] ba=new byte[128];
        m_mic_audiorecord.read(ba,0,128);
        micdata.write(ba,0,128);

        try {
          Thread.sleep(5);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

      }
      m_mic_audiorecord.release();
      micdata.reset();
    }
  };



  public int varint_encode(int val, byte[] ba, int idx) {
    if (val >= 1 << 14) {
      //hu_uti.loge ("Too big");
      return (1);
    }
    ba[idx + 0] = (byte) (0x7f & (val >> 0));
    ba[idx + 1] = (byte) (0x7f & (val >> 7));
    if (ba[idx + 1] != 0) {
      ba[idx + 0] |= 0x80;
      return (2);
    }
    return (1);
  }

  public int varint_encode(long val, byte[] ba, int idx) {
    if (val >= 0x7fffffffffffffffL) {
      //hu_uti.loge ("Too big");
      return (1);
    }
    long left = val;
    for (int idx2 = 0; idx2 < 9; idx2++) {
      ba[idx + idx2] = (byte) (0x7f & left);
      left = left >> 7;
      if (left == 0) {
        return (idx2 + 1);
      } else if (idx2 < 9 - 1) {
        ba[idx + idx2] |= 0x80;
      }
    }
    return (9);
  }

//Function to calculate the sunset and sunreise based on Lat and Lng

  public void Calculate_Sunset_Sunrise(Location loc) {
    Calendar rightNow = Calendar.getInstance();
   if ((sharegps && !selfmode && !m_stopping)) {
    LocationProtos.LocationData.Builder mylocdata = LocationProtos.LocationData.newBuilder();
    mylocdata.setTimestamp(loc.getTime());
    mylocdata.setLatitude((int) (loc.getLatitude() * 10000000));
    mylocdata.setLongitude((int) (loc.getLongitude() * 10000000));
    mylocdata.setAccuracy((int) loc.getAccuracy()*1000);
    mylocdata.setAltitude((int) loc.getAltitude() * 100);
    mylocdata.setSpeed(1*1000);
    mylocdata.setBearing((int) loc.getBearing()*1000000);
    byte[] data = mylocdata.build().toByteArray();


     byte [] tosend=new byte[data.length+9];
     byte [] nextchunk=ByteBuffer.allocate(4).putInt(data.length+5).array();
     System.arraycopy(nextchunk,0,tosend,0,4);
     tosend[4]=2;
     tosend[5]=(byte)0x80;
     tosend[6]=0x03;
     tosend[7]=0x0a;
     tosend[8]=(byte) data.length;
     System.arraycopy(data,0,tosend,9,data.length);
     //Log.d("HU-SERVICE","Sendq gps data");
     send_que.write(tosend,0,tosend.length);




     /* send_que.write(cmd_len_byte,0,2);
      send_que.write(2);
      send_que.write(0x80);
      send_que.write(0x03);
      send_que.write(0x0a);
      send_que.write(data.length);
      send_que.write(data, 0, data.length);*/
    }
    else if ((selfmode || !sharegps) && (!m_stopping)) { //If in self mode or GPS is not shared just send a null speed for unlimited usage.
     LocationProtos.LocationData.Builder mylocdata = LocationProtos.LocationData.newBuilder();
     mylocdata.setTimestamp(loc.getTime());

     mylocdata.setSpeed(1*1000);

     byte[] data = mylocdata.build().toByteArray();


     byte [] tosend=new byte[data.length+9];
     byte [] nextchunk=ByteBuffer.allocate(4).putInt(data.length+5).array();
     System.arraycopy(nextchunk,0,tosend,0,4);
     tosend[4]=2;
     tosend[5]=(byte)0x80;
     tosend[6]=0x03;
     tosend[7]=0x0a;
     tosend[8]=(byte) data.length;
     System.arraycopy(data,0,tosend,9,data.length);
     //Log.d("HU-SERVICE","Sendq gps data");
     send_que.write(tosend,0,tosend.length);
    // Log.d("HU-GPS","Sending null speed");
   }


   byte[] data2 = {0x02, (byte) 0x80, 0x03, 0x6a, 0x02, 0x08, 0};
   byte[] tosend2 = new byte[11];
   byte[] nextchunk2 = ByteBuffer.allocate(4).putInt(7).array();
   System.arraycopy(nextchunk2, 0, tosend2, 0, 4);
   System.arraycopy(data2, 0, tosend2, 4, 7);
   // Log.d("HU-SERVICE","Sendq night toggle");
   send_que.write(tosend2, 0, tosend2.length);



//Auto volume adjust
    if (autovol && !m_stopping)
    {
      int curr_speed=0;
      if (loc.getSpeed()<8.3333)
        curr_speed=0;
      else if (loc.getSpeed()>=8.3333 && loc.getSpeed()<13.8889)
        curr_speed=1;
      else if (loc.getSpeed()>=13.8889 && loc.getSpeed()<22.222)
        curr_speed=2;
      else if (loc.getSpeed()>=22.222 && loc.getSpeed()<30.5556)
        curr_speed=3;
      else if (loc.getSpeed()>=30.5556 && loc.getSpeed()<36.1111)
        curr_speed=4;
      else if (loc.getSpeed()>=36.111)
        curr_speed=5;

      int vol_action=0;
      if ((curr_speed-last_speed)>0)
        vol_action=0x18;
      else if ((curr_speed-last_speed)<0)
        vol_action=0x19;
      int x = Math.abs(curr_speed-last_speed);
      long ts = android.os.SystemClock.elapsedRealtime () * 1000000L;
      if (x>0)
        if (trans_aud != 1)
          for (int i=0;i<x;i++)
          {
            key_send(vol_action,1,ts+200*i);
            key_send(vol_action,0,ts+200*i+150);
          }
        else
        {
          AudioManager audio;
          audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
          if (vol_action==0x18)
            vol_action=1;
          else
            vol_action=-1;
          for (int i=0;i<x;i++)
          {
            audio.adjustVolume(vol_action, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
          }
        }
      last_speed=curr_speed;
    }


    if ((((SystemClock.uptimeMillis()-lastcalculated)/1000>10*60) && nightmode==1) || (sunrise==0 || sunset==0)) {
      double latitude = loc.getLatitude();
      double longitude = loc.getLongitude();
      double zenith = 90.833333333;
      double D2R = Math.PI / 180;
      double R2D = 180 / Math.PI;
      int day;
      double t1;
      double t2;
      double M1;
      double M2;
      double L1;
      double L2;
      double UT1;
      double UT2;
      double RA1;
      double RA2;

      double cosH1;
      double cosH2;



      day = rightNow.get(Calendar.DAY_OF_YEAR);


      t1 = day + ((6 - (longitude / 15)) / 24);
      t2 = day + ((18 - (longitude / 15)) / 24);

      M1 = (0.9856 * t1) - 3.289;
      M2 = (0.9856 * t2) - 3.289;

      L1 = M1 + (1.916 * Math.sin(M1 * D2R)) + (0.020 * Math.sin(2 * M1 * D2R)) + 282.634;
      L2 = M2 + (1.916 * Math.sin(M2 * D2R)) + (0.020 * Math.sin(2 * M2 * D2R)) + 282.634;

      if (L1 > 360)
        L1 = L1 - 360;
      else if (L1 < 0)
        L1 = L1 + 360;

      if (L2 > 360)
        L2 = L2 - 360;
      else if (L2 < 0)
        L2 = L2 + 360;

      RA1 = R2D * Math.atan(0.91764 * Math.tan(L1 * D2R));
      RA2 = R2D * Math.atan(0.91764 * Math.tan(L2 * D2R));
      if (RA1 > 360)
        RA1 = RA1 - 360;
      else if (RA1 < 0)
        RA1 = RA1 + 360;

      if (RA2 > 360)
        RA2 = RA2 - 360;
      else if (RA2 < 0)
        RA2 = RA2 + 360;


      RA1 = (RA1 + ((Math.floor(L1 / (90))) * 90 - (Math.floor(RA1 / 90)) * 90)) / 15;
      RA2 = (RA2 + ((Math.floor(L2 / (90))) * 90 - (Math.floor(RA2 / 90)) * 90)) / 15;


      cosH1 = (Math.cos(zenith * D2R) - (0.39782 * Math.sin(L1 * D2R) * Math.sin(latitude * D2R))) / (Math.cos(Math.asin(0.39782 * Math.sin(L1 * D2R))) * Math.cos(latitude * D2R));
      cosH2 = (Math.cos(zenith * D2R) - (0.39782 * Math.sin(L2 * D2R) * Math.sin(latitude * D2R))) / (Math.cos(Math.asin(0.39782 * Math.sin(L2 * D2R))) * Math.cos(latitude * D2R));

      sunrise = (360 - R2D * Math.acos(cosH1)) / 15;
      sunset = R2D * Math.acos(cosH2) / 15;

      sunrise = sunrise + RA1 - (0.06571 * t1) - 6.622;
      sunset = sunset + RA2 - (0.06571 * t2) - 6.622;

      UT1 = sunrise - (longitude / 15);
      UT2 = sunset - (longitude / 15);
      if (UT1 > 24) {
        UT1 = UT1 - 24;
      } else if (UT1 < 0) {
        UT1 = UT1 + 24;
      }

      if (UT2 > 24) {
        UT2 = UT2 - 24;
      } else if (UT2 < 0) {
        UT2 = UT2 + 24;
      }


      int offsetFromUtc = rightNow.get(Calendar.ZONE_OFFSET) / 1000;
      int winterSummerOffset = rightNow.get(Calendar.DST_OFFSET) / 1000;

      sunrise = UT1 * 3600 + offsetFromUtc + winterSummerOffset;
      sunset = UT2 * 3600 + offsetFromUtc + winterSummerOffset;
    }
    int now_sec=(rightNow.get(Calendar.HOUR_OF_DAY)*3600+rightNow.get(Calendar.MINUTE)*60+rightNow.get(Calendar.SECOND));
    //Log.d("HU-SERVICE","Night mode:"+nightmode+", isnightset="+isnightset+", now_sec:"+now_sec+",sunset: "+sunset+",sunrise: "+sunrise + "m_stopping: "+m_stopping);
    if (nightmode==1 && !isnightset && (now_sec<sunrise || now_sec>sunset) && videorunning)
    { night_toggle(1); isnightset=true;}
    else if (nightmode==1 && isnightset && now_sec>sunrise && now_sec<sunset && videorunning)
    {night_toggle(0); isnightset=false;}

  }


  LocationListener listener=new LocationListener() {


    public void onLocationChanged(Location loc)
    {
      Calculate_Sunset_Sunrise(loc);

    }

    public void onProviderDisabled(String provider)
    {
      Toast.makeText( getApplicationContext(), "Gps Disabled", Toast.LENGTH_SHORT ).show();
      Log.d("HU-GPS","No GPS provider");
    }


    public void onProviderEnabled(String provider)
    {
      Toast.makeText( getApplicationContext(), "Gps Enabled", Toast.LENGTH_SHORT).show();
    }


    public void onStatusChanged(String provider, int status, Bundle extras)
    {

    }

  };
  final SensorEventListener LightSensorListener
          = new SensorEventListener() {
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
      // TODO Auto-generated method stub

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
        if (Math.abs(event.values[0]-lastlux)>luxsens) {
          if (event.values[0] < luxval && !m_stopping && !isnightset) {
            isnightset = true;
            night_toggle(1);
          } else if (event.values[0] > luxval && !m_stopping && isnightset) {
            isnightset = false;
            night_toggle(0);
          }
          lastlux=event.values[0];
        }
      }
    }
  };

  private void play_audio ( final int size, final byte[] data, final int tracknumber, ExecutorService whichservice,  final AudioTrack whichtrack)
  {

    whichservice.submit(new Runnable() {
      @Override
      public void run() {
        if (!m_mic_active) {
          if (whichtrack == null && transport_audio)
            out_audio_start(tracknumber);
          whichtrack.write(data, 0, size);
          if (tracknumber==0)
          {
            if (m_out_aud0_pos>=4096*4 && whichtrack.getState()==AudioTrack.PLAYSTATE_STOPPED)
              m_out_audiotrack0.play();
            else
              m_out_aud0_pos = m_out_aud0_pos + size;
          }
          else if (tracknumber==1) {
            if (m_out_aud1_pos>=4096 && whichtrack.getState()==AudioTrack.PLAYSTATE_STOPPED)
              m_out_audiotrack1.play();
            else
              m_out_aud1_pos = m_out_aud1_pos + size;
          }
          else if (tracknumber==2) {

            if (m_out_aud2_pos>=4096 && whichtrack.getState()==AudioTrack.PLAYSTATE_STOPPED)
              m_out_audiotrack1.play();
            else
              m_out_aud2_pos = m_out_aud2_pos + size;
          }
        }
      }
    });
  }


  private void stop_audio (final AudioTrack whichtrack, ExecutorService whichservice) //Convenince function to stop the audio
  {
  whichservice.submit(new Runnable() {


      @Override
      public void run() {
        int last_frames = 0;
        int curr_frames = 1;

        if (whichtrack!=null)
          whichtrack.flush();

        while (last_frames != curr_frames) {
          try {
            Thread.sleep (150);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          last_frames = curr_frames;
          if (whichtrack != null)
            try {
              curr_frames = whichtrack.getPlaybackHeadPosition();
            }
            catch (Exception e)
            {

            }
        }
        try {
          if (whichtrack!=null) {
            whichtrack.stop();
            whichtrack.release();
          }
        }
        catch (Exception e)
        {
          Log.e("HU-SERVICE","Channel 1 audio isn't running.");
        }
        if (whichtrack==m_out_audiotrack0)
               m_out_audiotrack0 = null;
        else if (whichtrack==m_out_audiotrack1)
          m_out_audiotrack1 = null;
        else
          m_out_audiotrack2 = null;

      }
    });

  }
  private void stop_audio_onmic (final AudioTrack whichtrack, ExecutorService whichservice) //Convenince function to stop the audio
  {
  //  whichservice.submit(new Runnable() {

    Thread stopaudi = new Thread(new Runnable() {
      @Override
      public void run() {
        int last_frames = 0;
        int curr_frames = 1;

        if (whichtrack!=null)
          whichtrack.flush();

        while (last_frames != curr_frames) {
          try {
            Thread.sleep (150);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          last_frames = curr_frames;
          if (whichtrack != null)
            try {
              curr_frames = whichtrack.getPlaybackHeadPosition();
            }
            catch (Exception e)
            {

            }
        }
        try {
          if (whichtrack!=null) {
            whichtrack.stop();
            whichtrack.release();
          }
        }
        catch (Exception e)
        {
          Log.e("HU-SERVICE","Channel 1 audio isn't running.");
        }
        if (whichtrack==m_out_audiotrack0)
               m_out_audiotrack0 = null;
        else if (whichtrack==m_out_audiotrack1)
          m_out_audiotrack1 = null;
        else
          m_out_audiotrack2 = null;

      }
    });
  stopaudi.start();
  }

  private int usbsend(byte[] buf,int len, int tmo)
  {
   // Log.d("HU-USBSEND","Made it this far: "+len);
    try {
      return usbconn.bulkTransfer(m_usb_ep_out, buf, len, -1);
    }
    catch (Exception e)
    {
      if (m_player!=null)
      {
        Message msg = m_player.handler.obtainMessage();
        msg.arg1 = 3;
        if( mode==3 || mode==2)
          msg.arg2=1;
        m_player.handler.sendMessage(msg);
      }
      return 0;
    }
    //return 10;
  }

 private int usbrecv(byte[] buf,int len, int tmo)
  {
   // Log.d("HU-USBRECV","Made it this far: "+len);
    int bytestransfered=0;
    try {
      bytestransfered = usbconn.bulkTransfer(m_usb_ep_in, buf, len, 2000);
    }
    catch (Exception e)
    {
      if (m_player!=null)
      {
        Message msg = m_player.handler.obtainMessage();
        msg.arg1 = 3;
        if( mode==3 || mode==2)
          msg.arg2=1;
        m_player.handler.sendMessage(msg);
      }
      return 0;
    }
    if (bytestransfered<0)
      return (0);
    else
      return bytestransfered;
    //return 10;
  }

  private String eventtostring(int value, String side) {
    switch (value) {
      case 0: return "UNKNOWN";
      case 1: return "DEPARTE";
      case 2: return "NAME CHANGE";
      case 3: return "SLIGHT " + side +" TURN";
      case 4: return "TURN "+side;
      case 5: return "SHARP "+side+" TURN";
      case 6: return "MAKE A U-TURN";
      case 7: return "USE THE ON-RAMP ON THE "+side;
      case 8: return "USE THE OFF-RAMP ON THE "+side;
      case 9: return "FORM";
      case 10: return "MERGE";
      case 11: return "ENTER ROUNDABOUT";
      case 12: return "EXIT ROUNDABOUT ON THE "+side;
      case 13: return "CROSS THE ROUNDABOUT";
      case 14: return "STRAIGHT";
      case 16: return "USE FERRY BOAT";
      case 17: return "USE FERRY TRAIN";
      case 18: return "DESTINATION";
      default: return null;
    }
  }

}
