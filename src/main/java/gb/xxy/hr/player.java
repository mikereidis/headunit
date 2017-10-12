package gb.xxy.hr;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.nio.ByteBuffer;


import gb.xxy.hr.new_hu_tra.*;


/**
 * Created by Emil on 26/12/2016.
 */

public class player extends Activity  implements SurfaceHolder.Callback{

    private SurfaceView mSurfaceView;
    private double m_virt_vid_wid=800f;
    private double m_virt_vid_hei=480f;
    private PowerManager m_pwr_mgr;
    private PowerManager.WakeLock m_wakelock;
    MediaCodec m_codec;
    private MediaCodec.BufferInfo m_codec_buf_info;
    private int h264_wait=0;
    private ByteBuffer [] m_codec_input_bufs;

    private long last_touch;

    private boolean connection_ok;
    public volatile boolean codec_ready=false;
    private final static Object sLock = new Object();
    private SurfaceHolder mHolder;
    private new_hu_tra mService;
    private player this_player;
    volatile boolean mBound = false;
    private boolean usb_mode;
    private String wifi_direct="";
    private int ep_in;
    private int ep_out;
    private boolean doubleBackToExitPressedOnce;
    private int last_possition;

    private message_receiver m_message_receiver;

    private boolean swdec;
    private long lastframe;



    //LifeCycle Function
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mylog.d("HU-SERVICE","Prevent onDestroy being called due to orientation and other changes....");
    }

    @Override
    public void onBackPressed() {
        mylog.d("HU-SERVICE","Back arrow button was pressed...");
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            hu_act.showplayer=false;
            connection_ok=false;
            mService.byebye_send();
            mService.m_stopping=true;

            codec_ready=false;
            try {
                unregisterReceiver(mService.wifi_receiver);
            }
            catch (Exception e){

            }
            try {
                    mService.mySensorManager.unregisterListener(mService.LightSensorListener);
            }
            catch (Exception e){

            }
             try {
                 mService.locationManager.removeUpdates(mService.listener);
            }
            catch (Exception e){

            }
            if (mService.mode==3) {
                mService.stopSelf();
                finish();
                android.os.Process.killProcess(android.os.Process.myPid());
            }

             return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce=false;
            }
        }, 2000);
    }
    @Override
    protected  void onResume(){
        super.onResume();
        mylog.d("HU-SERVICE","Player on Resume");
    }
    @Override
    protected  void onRestart(){
        super.onRestart();
        mylog.d("HU-SERVICE","Player on Restart");
    }
    @Override
    protected  void onStop(){
        super.onStop();
        mylog.d("HU-SERVICE","Player on Stop");
    }
    @Override
    protected  void onDestroy(){
        super.onDestroy ();
        unregisterReceiver(m_message_receiver);
        mylog.d("HU-SERVICE","Player on Destory");
        WifiReceiver.isrunning=false;
    }
    @Override
    protected  void onPause(){
        super.onPause ();

            mylog.d("HU-SERVICE", "Player on Pause");
        try {
            mService.update_mplayer(null);
            unbindService(mConnection);
        }
        catch (Exception E)
        {

        }
      //finish();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mylog.d("HU-SERVICE", "Player on Create");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player);
        this_player=(this);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //Check the required Video size
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hires = SP.getBoolean("hires", false);
        swdec= SP.getBoolean("h264",false);

        if (hires) {
            m_virt_vid_wid = 1280f;
            m_virt_vid_hei = 720f;
        }
        usb_mode=getIntent().hasExtra("USB");
        if (usb_mode)
        {
            ep_in=getIntent().getIntExtra("ep_in",0);
            ep_out=getIntent().getIntExtra("ep_out",0);

        }
        if (getIntent().hasExtra("wifi_direct"))
            wifi_direct=getIntent().getStringExtra("wifi_direct_ip");

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceView.setVisibility(View.VISIBLE);


        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("stretch_full", true)) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int width = displayMetrics.widthPixels;
            int original_height=displayMetrics.heightPixels;
            mylog.d("HU-SERVICE","Surface size is: "+width);
            float aspect_ratio = (float) 1.66;
            if (m_virt_vid_wid != 800f)
                aspect_ratio = (float) 1.77;
            int height = (int) (width / aspect_ratio);
            mylog.d("HU-SERVICE","Original height: " + original_height + "needed height:" + height);
                if (height>original_height)  // we have some weired screen ratio, need to letter box it other way around...
                {
                    height=original_height;
                    width= (int) (height*aspect_ratio);
                }
            mylog.d("HU-SERVICE", "Height: " + height + "width:" + width);
           mSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(width, height, 17));
        }
        mSurfaceView.getHolder().addCallback(this);
        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                touch_send(event);
                return (true);
            }
        });
        Intent intent = new Intent(this, new_hu_tra.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT | Context.BIND_ADJUST_WITH_ACTIVITY);


        Thread thread = new Thread() {
            @Override
            public void run() {
                mylog.d("HU-SERVICE", "player thread started....");
                while (!mBound || !codec_ready) {
                    try
                    {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }

                mylog.d("HU-SERVICE", "Bond to service....");
                if (!mService.aap_running) {
                    if (!usb_mode && wifi_direct.isEmpty()) {
                        ConnectivityManager cm = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

                        if (!isConnected) {
                            mylog.d("HU-SERVICE", "Player considers itself not connected....");
                            Message msg = handler.obtainMessage();
                            msg.arg1 = 2;
                            msg.arg2 = 0;
                            handler.sendMessage(msg);
                            return;
                        }

                        WifiManager wifii = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                        DhcpInfo d = wifii.getDhcpInfo();
                        mylog.d("HU-Service","GateWay is: "+intToIp(d.gateway) +" IP setting is:" + PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString("ip",""));
                        if (PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString("ip","").length()==0)
                            mService.jni_aap_start(intToIp(d.gateway),0,0);
                        else
                            mService.jni_aap_start(PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString("ip",""),0,0);

                    }
                    else if (!usb_mode && !wifi_direct.isEmpty())
                        mService.jni_aap_start(wifi_direct,0,0);
                    else
                    {

                        mService.jni_aap_start("",ep_out,ep_in);
                    }


                } else {

                    byte[] data = {  0x03, (byte) 0x80, 0x08, 0x08, 0x01, 0x10, 0x01};
                    byte [] tosend=new byte[11];
                    byte [] nextchunk=ByteBuffer.allocate(4).putInt(7).array();
                    System.arraycopy(nextchunk,0,tosend,0,4);
                    System.arraycopy(data,0,tosend,4,7);
                    //mylog.d("HU-SERVICE","video start");
                    mService.send_que.write(tosend,0,tosend.length);



                }

            }
        };

        thread.start();


        WifiReceiver.isrunning=true;
        IntentFilter filter = new IntentFilter ();
        filter.addAction("gb.xxy.hr.sendmessage");
        m_message_receiver = new message_receiver ();
        registerReceiver (m_message_receiver, filter);

    }



    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mylog.d("HU-SERVICE","Service connected");
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mService.update_mplayer(this_player);
            mBound = true;
            mylog.d("HU-SERVICE","mBound is:"+mBound);
          }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;

        }
    };

    //SurfaceView
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (sLock) {
            if (m_codec != null) {
                mylog.d("HU-SERVICE","Codec is running");
                return;
            }
        }
        mHolder=holder;
        codec_init();
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format_2, int width, int height) {


    }


    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        byte[] data = {  0x03, (byte) 0x80, 0x08, 0x08, 0x02, 0x10, 0x01};
        byte [] tosend=new byte[11];
        byte [] nextchunk=ByteBuffer.allocate(4).putInt(7).array();
        System.arraycopy(nextchunk,0,tosend,0,4);
        System.arraycopy(data,0,tosend,4,7);
        //mylog.d("HU-SERVICE","Sendq surface destroy");
        mService.send_que.write(tosend,0,tosend.length);


        codec_ready=false;
        if (m_codec!=null)
            try {
                m_codec.flush();
                m_codec.stop();
                m_codec = null;
            }
            catch (Exception E)
            {
                throw E;
            }
        try {
            holder.removeCallback(this);
            holder.getSurface().release();
        }
        catch (Exception E)
        {
            throw E;
        }

        }


    //Here we start with our own functions
    public void codec_init(){
        synchronized (sLock) {
            mylog.d("HU-SERVICE", "surfaceChanged called: ");
            DisplayMetrics displaymetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            int height = displaymetrics.heightPixels;
            int width = displaymetrics.widthPixels;

            if (height > 1080) {                                              // Limit surface height to 1080 or N7 2013 won't work: width: 1920  height: 1104    Screen 1200x1920, nav = 96 pixels
                mylog.e("HU-SERVICE","height: " + height);
                height = 1080;
            }


            mylog.d("HU-SERVICE", "Setting up media player");


            try {
                if (!swdec)
                    m_codec = MediaCodec.createDecoderByType("video/avc");       // Create video codec: ITU-T H.264 / ISO/IEC MPEG-4 Part 10, Advanced Video Coding (MPEG-4 AVC)
                else {
                    m_codec = MediaCodec.createByCodecName("OMX.google.h264.decoder");
                    h264_wait=10000;
                    mylog.d("HU-VIDEO","Using Software decoding.... might be slow...");
                    }
                m_codec_buf_info = new MediaCodec.BufferInfo();                         // Create Buffer Info
                MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,655360);
                m_codec.configure(format, mHolder.getSurface(), null, 0);               // Configure codec for H.264 with given width and height, no crypto and no flag (ie decode)
                m_codec.start();                                             // Start codec
                 codec_ready=true;
                //Thread videothread = new Thread(videoplayback, "Playback_audio 0");
                //videothread.start();
            } catch (Throwable t) {
                mylog.e("HU-SERVICE","Throwable: " + t);
            }

        }
    }



    public void sys_ui_hide(){
        View m_ll_main = findViewById(R.id.ll_main);
        if (m_ll_main != null)
            m_ll_main.setSystemUiVisibility (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE);//_STICKY);
    }
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mylog.d("HU-SERVICE","Focus changed!");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            if (hasFocus) {
                sys_ui_hide();

            }
        }
    }

    private void touch_send (MotionEvent event) {


        int x = (int) (event.getX (0) / (mSurfaceView.getWidth() / m_virt_vid_wid));
        int y = (int) (event.getY (0) / (mSurfaceView.getHeight() / m_virt_vid_hei));

        if (x < 0 || y < 0 || x >= 65535 || y >= 65535) {   // Infinity if vid_wid_get() or vid_hei_get() return 0
            mylog.e("HU-SERVICE","Invalid x: " + x + "  y: " + y);
            return;
        }

        byte aa_action = 0;
        int me_action = event.getActionMasked ();
        switch (me_action) {
            case MotionEvent.ACTION_DOWN:
                mylog.d("HU-SERVICE","event: " + event + " (ACTION_DOWN)    x: " + x + "  y: " + y);
                aa_action = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                mylog.d("HU-SERVICE","event: " + event + " (ACTION_MOVE)    x: " + x + "  y: " + y);
                aa_action = 2;
                break;
            case MotionEvent.ACTION_CANCEL:
                mylog.d("HU-SERVICE","event: " + event + " (ACTION_CANCEL)  x: " + x + "  y: " + y);
                aa_action = 1;
                break;
            case MotionEvent.ACTION_UP:
                mylog.d("HU-SERVICE","event: " + event + " (ACTION_UP)      x: " + x + "  y: " + y);
                aa_action = 1;
                break;
            default:
                mylog.e("HU-SERVICE","event: " + event + " (Unknown: " + me_action + ")  x: " + x + "  y: " + y);
                return;
        }
        if (mBound) {
            mService.touch_send (aa_action, x, y, event.getEventTime()*1000000L);
            last_touch=event.getEventTime();
              //We need to keep track of the last touch event for SelfMode, otherwise it will generate onUserLeaveHint when dialing!
        }

    }





    private boolean codec_input_provide (ByteBuffer content) {            // Called only by media_decode() with new NAL unit in Byte Buffer

        try {
            final int index = m_codec.dequeueInputBuffer (3000000);           // Get input buffer with 3 second timeout
            if (index < 0) {
                mylog.e("HU-VIDEO","No input buffer available...");
                /*if (h264_wait>0)
                {*/
                    m_codec.flush();
                    m_codec_input_bufs = null;
                    m_codec_buf_info=null;
                    m_codec_buf_info = new MediaCodec.BufferInfo();
                    byte[] data = {  0x03, (byte) 0x80, 0x08, 0x08, 0x02, 0x10, 0x01};
                    byte [] tosend=new byte[11];
                    byte [] nextchunk=ByteBuffer.allocate(4).putInt(7).array();
                    System.arraycopy(nextchunk,0,tosend,0,4);
                    System.arraycopy(data,0,tosend,4,7);
                    //mylog.d("HU-SERVICE","Sendq surface destroy");
                    mService.send_que.write(tosend,0,tosend.length);
                    tosend[8]=1;
                    Thread.sleep(200);
                    mService.send_que.write(tosend,0,tosend.length);
                    return (false);                                                 // Done with "No buffer" error
               /* }*/
            }

            if (m_codec_input_bufs == null) {
                m_codec_input_bufs = m_codec.getInputBuffers ();                // Set m_codec_input_bufs if needed
            }

            final ByteBuffer buffer = m_codec_input_bufs [index];
            final int capacity = buffer.capacity ();
            buffer.clear ();


            if (content.limit() <= capacity) {                           // If we can just put() the content...
                buffer.put (content);                                           // Put the content
            }
            else {                                                            // Else... (Should not happen ?)
                int limit = content.limit ();
                content.limit (content.position () + capacity);                 // Temporarily set constrained limit
                buffer.put (content);
                content.limit (limit);                                          // Restore original limit
            }
            buffer.flip ();                                                   // Flip buffer for reading
            m_codec.queueInputBuffer (index, 0, buffer.limit (), 0, 0);       // Queue input buffer for decoding w/ offset=0, size=limit, no microsecond timestamp and no flags (not end of stream)
            return (true);                                                    // Processed
        }
        catch (Throwable t) {
            mylog.e("HU-SERVICE","Throwable: " + t);
        }
        return (false);                                                     // Error: exception
    }

    private void codec_output_consume () {                                // Called only by media_decode() after codec_input_provide()
        //mylog.d("HU-SERVICE","");
        int index = -777;
        for (;;) {                                                          // Until no more buffers...

            index = m_codec.dequeueOutputBuffer (m_codec_buf_info, h264_wait);        // Dequeue an output buffer but do not wait
            if (index >= 0) {
                m_codec.releaseOutputBuffer(index, true /*render*/);           // Return the buffer to the codec

                //mService.lastframe=System.currentTimeMillis();
            }
            else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)         // See this 1st shortly after start. API >= 21: Ignore as getOutputBuffers() deprecated
                mylog.d("HU-SERVICE","INFO_OUTPUT_BUFFERS_CHANGED: ");
            else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)          // See this 2nd shortly after start. Output format changed for subsequent data. See getOutputFormat()
            {
                mylog.d("HU-SERVICE","INFO_OUTPUT_FORMAT_CHANGED:");

            }
            else if (index == MediaCodec.INFO_TRY_AGAIN_LATER)
            {
               // mylog.e("HU-VIDEO","Timed out....");
                break;

            }
            else
                break;
        }
        if (index != MediaCodec.INFO_TRY_AGAIN_LATER)
            mylog.e("HU-SERVICE","index: " + index);
    }





    public void media_decode (byte [] buffer, int size) {                       // Decode audio or H264 video content. Called only by video_test() & hu_tra.aa_cmd_send()


       // synchronized (sLock) {
            ByteBuffer content = ByteBuffer.wrap(buffer, 0, size);

            while (content.hasRemaining ()) {                                 // While there is remaining content...

                if (! codec_input_provide (content)) {                          // Process buffer; if no available buffers...
                    mylog.e("HU-SERVICE","Dropping content because there are no available buffers.");
                    content.clear();
                }
                if (content.hasRemaining ())                                    // Never happens now
                    mylog.e("HU-SERVICE","content.hasRemaining ()");

                codec_output_consume ();                                        // Send result to video codec
            }
       // }

    }




    public final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
           mylog.d("HU-SERVICE","Handler message is..." + msg.arg2);
            if (msg.arg1==1)
                Toast.makeText(getBaseContext(),getResources().getString(R.string.err_noserv),Toast.LENGTH_LONG).show();
            else if (msg.arg1==2)
                Toast.makeText(getBaseContext(),getResources().getString(R.string.err_nowifi),Toast.LENGTH_LONG).show();
            else if (msg.arg1==3)
                Toast.makeText(getBaseContext(), getResources().getString(R.string.err_conterm), Toast.LENGTH_LONG).show();
            else if (msg.arg1==4)
                Toast.makeText(getBaseContext(), getResources().getString(R.string.err_usberr), Toast.LENGTH_LONG).show();

            if (msg.arg2==0) {
                mService.m_stopping = true;
                mService.aap_running = false;
                this_player.finish();
                hu_act.showplayer=false;
            }
            else
            {
                mService.m_stopping = true;
                mService.aap_running = false;
                mService.stopSelf();
                this_player.finish();
                try {
                    hu_act.closemyapp();
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                android.os.Process.killProcess(android.os.Process.myPid());
            }
                /*mService.stopSelf();
                finishAffinity();
                //this_player.finish();
                //android.os.Process.killProcess(android.os.Process.myPid());
                mService.stopSelf();
                this_player.finish();*/
        }
    };


    public String intToIp(int addr) {
        return  ((addr & 0xFF) + "." +
                ((addr >>>= 8) & 0xFF) + "." +
                ((addr >>>= 8) & 0xFF) + "." +
                ((addr >>>= 8) & 0xFF));
    }





    public boolean onKeyUp(int keyCode, KeyEvent event) {

        long ts = android.os.SystemClock.elapsedRealtime () * 1000000L;
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hires=SP.getBoolean("hires",false);
        if (hires && SP.getBoolean("h264",false))
            hires=false;
        if (SP.getBoolean("non_st_k",false)) {
            int playb=Integer.parseInt(SP.getString("cust_key_play","0"));
            int nextb=Integer.parseInt(SP.getString("cust_key_next","0"));
            int prevb=Integer.parseInt(SP.getString("cust_key_prev","0"));
            int micb=Integer.parseInt(SP.getString("cust_key_mic","0"));
            int phoneb=Integer.parseInt(SP.getString("cust_key_phone","0"));


                if (keyCode == nextb){
                    mService.key_send(0x57, 1, ts);
                    mService.key_send(0x57, 0, ts + 200);

                    return true;}
                else if (keyCode == prevb){
                    mService.key_send(0x58, 1, ts);
                    mService.key_send(0x58, 0, ts + 200);

                    return true;}
                else if (keyCode == playb) {
                    mService.key_send(0x55, 1, ts);
                    mService.key_send(0x55, 0, ts + 200);
                    return true;
                }
                else if (keyCode == micb) {
                    mService.key_send(0x54, 1, ts);
                    mService.key_send(0x54, 0, ts + 200);
                    return true;
                }
             else if (keyCode == phoneb) {
                    mService.key_send(0x05, 1, ts);
                    mService.key_send(0x05, 0, ts + 200);
                    return true;
                }


        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                mService.key_send(0x18,1,ts);
                mService.key_send(0x18,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mService.key_send(0x19,1,ts);
                mService.key_send(0x19,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                mService.key_send(0x57,1,ts);
                mService.key_send(0x57,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                mService.key_send(0x58,1,ts);
                mService.key_send(0x58,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                mService.key_send(0x55,1,ts);
                mService.key_send(0x55,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                mService.key_send(0x55,1,ts);
                mService.key_send(0x55,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                mService.key_send(0x55,1,ts);
                mService.key_send(0x55,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                mService.key_send(0x56,1,ts);
                mService.key_send(0x56,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                mService.key_send(0x59,1,ts);
                mService.key_send(0x59,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_CALL:
                mService.key_send(0x05,1,ts);
                mService.key_send(0x05,0,ts+200);

                last_possition=2;
                return true;
            case KeyEvent.KEYCODE_F: // SAME AS CALL CONVENIENCE BUTTON
                mService.key_send(0x05,1,ts);
                mService.key_send(0x05,0,ts+200);

                last_possition=2;
                return true;
            case KeyEvent.KEYCODE_M: //mic
                mService.key_send(0x54,1,ts);
                mService.key_send(0x54,0,ts+200);

                return true;
            case KeyEvent.KEYCODE_H: //home screen (middle)
                mService.key_send(0x03,1,ts);
                mService.key_send(0x03,0,ts+200);

                last_possition=3;
                return true;
            case KeyEvent.KEYCODE_D: //home screen (middle){0x80, 0x03, 0x52, 2, 8, cmd_buf[1]};
                mService.night_toggle(0);
                return true;
            case KeyEvent.KEYCODE_T: //home screen (middle){0x80, 0x03, 0x52, 2, 8, cmd_buf[1]};
                if (!mService.isnightset)
                {
                    mService.night_toggle(1);
                    mService.isnightset=true;
                }
                else
                {
                    mService.night_toggle(0);
                    mService.isnightset=false;
                }
                return true;
            case KeyEvent.KEYCODE_N: //home screen (middle){0x80, 0x03, 0x52, 2, 8, cmd_buf[1]};
                mService.night_toggle(1);
                return true;
            /*
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mService.key_send(21,1,ts);
                mService.key_send(21,0,ts+200);
                return true;
          case KeyEvent.KEYCODE_DPAD_RIGHT:
                mService.key_send(22,1,ts);
                mService.key_send(22,0,ts+200);
                return true;
          case KeyEvent.KEYCODE_DPAD_UP:
                mService.key_send(19,1,ts);
                mService.key_send(19,0,ts+200);
                return true;
          case KeyEvent.KEYCODE_DPAD_DOWN:
                mService.key_send(20,1,ts);
                mService.key_send(20,0,ts+200);
                return true;
          case KeyEvent.KEYCODE_SOFT_LEFT:
                mService.key_send(1,1,ts);
                mService.key_send(1,0,ts+200);
                return true;
            case KeyEvent.KEYCODE_SOFT_RIGHT:
                mService.key_send(2,1,ts);
                mService.key_send(2,0,ts+200);
                return true;
*/

            case KeyEvent.KEYCODE_1:
                   last_possition=1;
                   if (hires) {
                        mService.touch_send ((byte)0, (last_possition-1)*300+50, 700,android.os.SystemClock.elapsedRealtime () * 1000000L);
                        mService.touch_send ((byte)1, (last_possition-1)*300+50, 700,android.os.SystemClock.elapsedRealtime () * 1000000L + 100);
                    }
                    else {
                        mService.touch_send ((byte)0, (last_possition-1)*200+10, 460,android.os.SystemClock.elapsedRealtime () * 1000000L);
                        mService.touch_send ((byte)1, (last_possition-1)*200+10, 460,android.os.SystemClock.elapsedRealtime () * 1000000L + 100);
                    }
                return true;
            case KeyEvent.KEYCODE_4:
                   last_possition=4;
                   if (hires) {
                        mService.touch_send ((byte)0, (last_possition-1)*300+50, 700,android.os.SystemClock.elapsedRealtime () * 1000000L);
                        mService.touch_send ((byte)1, (last_possition-1)*300+50, 700,android.os.SystemClock.elapsedRealtime () * 1000000L + 100);
                    }
                    else {
                        mService.touch_send ((byte)0, (last_possition-1)*200+10, 460,android.os.SystemClock.elapsedRealtime () * 1000000L);
                        mService.touch_send ((byte)1, (last_possition-1)*200+10, 460,android.os.SystemClock.elapsedRealtime () * 1000000L + 100);
                    }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (last_possition==0)
                    last_possition=4;
                if (last_possition>0) {
                    last_possition--;
                    if (hires) {
                        mService.touch_send ((byte)0, (last_possition-1)*300+50, 700,android.os.SystemClock.elapsedRealtime () * 1000000L);
                        mService.touch_send ((byte)1, (last_possition-1)*300+50, 700,android.os.SystemClock.elapsedRealtime () * 1000000L + 100);
                    }
                    else {
                        mService.touch_send ((byte)0, (last_possition-1)*200+10, 460,android.os.SystemClock.elapsedRealtime () * 1000000L);
                        mService.touch_send ((byte)1, (last_possition-1)*200+10, 460,android.os.SystemClock.elapsedRealtime () * 1000000L + 100);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (last_possition==4)
                    last_possition=0;
                if (last_possition<4) {
                    last_possition++;
                    if (hires) {
                        mService.touch_send ((byte)0, (last_possition-1)*300+50, 700,android.os.SystemClock.elapsedRealtime () * 1000000L);
                        mService.touch_send ((byte)1, (last_possition-1)*300+50, 700,android.os.SystemClock.elapsedRealtime () * 1000000L+100);
                    }
                    else {
                        mService.touch_send ((byte)0, (last_possition-1)*200+10, 460,android.os.SystemClock.elapsedRealtime () * 1000000L);
                        mService.touch_send ((byte)1, (last_possition-1)*200+10, 460,android.os.SystemClock.elapsedRealtime () * 1000000L+100);
                    }
                }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (hires)
                { mService.touch_send ((byte)0, 50, 200,android.os.SystemClock.elapsedRealtime () * 1000000L);
                    mService.touch_send ((byte)1, 50, 200,android.os.SystemClock.elapsedRealtime () * 1000000L+100);
                }
                else {
                    mService.touch_send ((byte)0, 50, 120,android.os.SystemClock.elapsedRealtime () * 1000000L);
                    mService.touch_send ((byte)1, 50, 120,android.os.SystemClock.elapsedRealtime () * 1000000L+100);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (hires)
                {mService.touch_send ((byte)0, 50, 550,android.os.SystemClock.elapsedRealtime () * 1000000L);
                    mService.touch_send ((byte)1, 50, 550,android.os.SystemClock.elapsedRealtime () * 1000000L+100);
                }
                else
                {mService.touch_send ((byte)0, 50, 370,android.os.SystemClock.elapsedRealtime () * 1000000L);
                    mService.touch_send ((byte)1, 50, 370,android.os.SystemClock.elapsedRealtime () * 1000000L+100); }
                return true;


            default:
                return super.onKeyUp(keyCode, event);
        }

    }

    private final class message_receiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {

            String message = intent.getStringExtra("message");
            mylog.d("HU-RECEIVER","Message: "+message);
            GenericMessage.GenericMessageNotification.Builder mynot = GenericMessage.GenericMessageNotification.newBuilder();
            mynot.setMessageId("01");
            mynot.setMessageBoddy("Test notification");




            int my_len = message.length();

            byte[] data = new byte[(my_len / 2)];

            for (int mi = 0; mi < my_len; mi += 2) {
                data[(mi / 2)] = (byte) ((Character.digit(message.charAt(mi), 16) << 4)
                        + Character.digit(message.charAt(mi+1), 16));
            }
            mService.aa_cmd_send (data.length, data, 0, null,"");


        }
        }



}