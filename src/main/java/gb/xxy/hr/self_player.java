package gb.xxy.hr;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;

import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.nio.ByteBuffer;
import gb.xxy.hr.new_hu_tra.*;


/**
 * Created by Emil on 26/12/2016.
 */

public class self_player extends Activity  implements  TextureView.SurfaceTextureListener {


    private double m_virt_vid_wid=800f;
    private double m_virt_vid_hei=480f;
    private PowerManager m_pwr_mgr;
    private PowerManager.WakeLock m_wakelock;
    private MediaCodec m_codec;
    private MediaCodec.BufferInfo m_codec_buf_info;
    private int h264_wait;
    private static ByteBuffer [] m_codec_input_bufs;


    private boolean connection_ok=true;
    private volatile boolean codec_ready=false;
    private final  Object sLock = new Object();

    private new_hu_tra mService;
    private self_player this_player;
    private boolean mBound = false;
    private TextureView m_tv_vid;
    private SurfaceTexture m_sur_tex=null;
    boolean doubleBackToExitPressedOnce = false;
    private Sensor senAccelerometer;
    private SensorManager mSensorManager;

    private int lastScale=1;


    @Override
    public void onBackPressed() {
        Log.d("HU-SERVICE","Back arrow button was pressed...");
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            UiModeManager uiManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
            uiManager.disableCarMode(0);
            connection_ok=false;
            mService.m_stopping=true;
            stopService(new Intent(this, gb.xxy.hr.new_hu_tra.class));
            hu_act.showselfplayer=false;
            hu_act.closemyapp();
            android.os.Process.killProcess(android.os.Process.myPid());
            //finish();

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
    protected  void onStop(){
        super.onStop();
        Log.d("HU-SERVICE","Player on Stop");
        if (!connection_ok)
        {
            Log.d("HU-SERVICE","We have no connection, stopping the service!");
            Intent intent = new Intent(getApplicationContext(), hu_act.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }



    }
    @Override
    protected  void onDestroy(){
        super.onDestroy ();
        Log.d("HU-SERVICE","Player on Destory: " + connection_ok);
            unbindService(mConnection);
       // android.os.Process.killProcess (android.os.Process.myPid ());

    }
    @Override
    protected  void onPause(){
        super.onPause ();
        try {

        Log.d("HU-SERVICE", "Player on Pause");



        } catch (Throwable t) {
            Log.e("HU-SERVICE", "Cannot unbind unexisting service!");
        }
        //finish();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hu_act.showselfplayer=true;
            Log.d("HU-SERVICE", "Player Created");

        Intent starts = new Intent(this, gb.xxy.hr.new_hu_tra.class);
        starts.putExtra("mode",0);
        startService(starts);

            setContentView(R.layout.self_player);
            this_player = (this);
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            //Check the required Video size

            mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            try {
                senAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
            catch (Throwable e) {
                Log.e ("HU-SERVICE", e.getMessage ());
            }
            m_tv_vid = (TextureView) findViewById(R.id.surfaceView);
            if (m_tv_vid != null) {
                m_tv_vid.setSurfaceTextureListener(self_player.this);
                m_tv_vid.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        touch_send(event);
                        return (true);
                    }
                });
                if (m_sur_tex != null)
                    try {
                        m_tv_vid.setSurfaceTexture(m_sur_tex);
                    } catch (Throwable t) {
                        Log.e("HU-SERVICE", "Already has surface!");
                    }
            }
            Intent intent = new Intent(this, new_hu_tra.class);
            bindService(intent, mConnection, Context.BIND_ABOVE_CLIENT);
           // final Integer x=this.getIntent().getIntExtra("key",0);
           //  Log.d("HU-SERVICE", "X value is: " + x);


            Log.d("HU-SERVICE","Thread start called...");


    }

   /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance

            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mService.update_self_mplayer(this_player);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
           // mBound = false;

        }
    };




    //Here we start with our own functions
    public void codec_init(SurfaceTexture m_sur_tex, int width, int height){



            Log.d("HU-SERVICE", "surfaceChanged called: ");

            if (height > 1080) {                                              // Limit surface height to 1080 or N7 2013 won't work: width: 1920  height: 1104    Screen 1200x1920, nav = 96 pixels
                Log.e("HU-SERVICE","height: " + height);
                height = 1080;
            }


            Log.d("HU-SERVICE", "Setting up media player");


            try {
                if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("h264", false)) {
                    m_codec = MediaCodec.createDecoderByType("video/avc");       // Create video codec: ITU-T H.264 / ISO/IEC MPEG-4 Part 10, Advanced Video Coding (MPEG-4 AVC)
                    Log.d("HU-SERVICE", "Using Hardware decoding");
                } else {
                    Log.d("HU-SERVICE", "Using Software decoding");
                    m_codec = MediaCodec.createByCodecName("OMX.google.h264.decoder");       // Use Google OMX (software) deocding.
                    h264_wait = 10000;
                }
            } catch (Throwable t) {
                Log.e("HU-SERVICE","Throwable creating video/avc decoder: " + t);
            }
            try {
                m_codec_buf_info = new MediaCodec.BufferInfo();                         // Create Buffer Info
                MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
                m_codec.configure (format, new Surface(m_sur_tex), null, 0);               // Configure codec for H.264 with given width and height, no crypto and no flag (ie decode)
                m_codec.start();                                             // Start codec
                codec_ready=true;
            } catch (Throwable t) {
                Log.e("HU-SERVICE","Throwable: " + t);
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
        Log.d("HU-SERVICE","Focus changed!");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            if (hasFocus) {
                sys_ui_hide();

            }
        }
    }

    private void touch_send (MotionEvent event) {


        int x = (int) (event.getX (0) / (m_tv_vid.getWidth() / m_virt_vid_wid));
        int y = (int) (event.getY (0) / (m_tv_vid.getHeight() / m_virt_vid_hei));

        if (x < 0 || y < 0 || x >= 65535 || y >= 65535) {   // Infinity if vid_wid_get() or vid_hei_get() return 0
            Log.e("HU-SERVICE","Invalid x: " + x + "  y: " + y);
            return;
        }

        byte aa_action = 0;
        int me_action = event.getActionMasked ();
        switch (me_action) {
            case MotionEvent.ACTION_DOWN:
                Log.d("HU-SERVICE","event: " + event + " (ACTION_DOWN)    x: " + x + "  y: " + y);
                aa_action = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                Log.d("HU-SERVICE","event: " + event + " (ACTION_MOVE)    x: " + x + "  y: " + y);
                aa_action = 2;
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.d("HU-SERVICE","event: " + event + " (ACTION_CANCEL)  x: " + x + "  y: " + y);
                aa_action = 1;
                break;
            case MotionEvent.ACTION_UP:
                Log.d("HU-SERVICE","event: " + event + " (ACTION_UP)      x: " + x + "  y: " + y);
                aa_action = 1;
                break;
            default:
                Log.e("HU-SERVICE","event: " + event + " (Unknown: " + me_action + ")  x: " + x + "  y: " + y);
                return;
        }
        if (mBound) {
            mService.touch_send (aa_action, x, y, event.getEventTime()*1000000L);

            //We need to keep track of the last touch event for SelfMode, otherwise it will generate onUserLeaveHint when dialing!
        }

    }


    private boolean codec_input_provide (ByteBuffer content) {            // Called only by media_decode() with new NAL unit in Byte Buffer

        try {
            final int index = m_codec.dequeueInputBuffer (1000000);           // Get input buffer with 1 second timeout
            if (index < 0) {

                if (codec_ready) {
                    m_codec.flush();
                    m_codec_input_bufs = null;
                    m_codec_buf_info = null;
                    m_codec_buf_info = new MediaCodec.BufferInfo();
                    byte[] data = {0x03, (byte) 0x80, 0x08, 0x08, 0x02, 0x10, 0x01};
                    byte[] tosend = new byte[11];
                    byte[] nextchunk = ByteBuffer.allocate(4).putInt(7).array();
                    System.arraycopy(nextchunk, 0, tosend, 0, 4);
                    System.arraycopy(data, 0, tosend, 4, 7);
                    //Log.d("HU-SERVICE","Sendq surface destroy");
                    mService.send_que.write(tosend, 0, tosend.length);
                    tosend[8] = 1;
                    Thread.sleep(200);
                    mService.send_que.write(tosend, 0, tosend.length);
                }
                return (false);                                                                // Done with "No buffer" error

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
            Log.e("HU-SERVICE","Throwable: " + t);
        }
        return (false);                                                     // Error: exception
    }

    private void codec_output_consume () {                                // Called only by media_decode() after codec_input_provide()
        //Log.d("HU-SERVICE","");
        int index = -777;
        for (;;) {                                                          // Until no more buffers...
            try {
                index = m_codec.dequeueOutputBuffer(m_codec_buf_info, h264_wait);        // Dequeue an output buffer but do not wait
                if (index >= 0)
                    m_codec.releaseOutputBuffer(index, true /*render*/);           // Return the buffer to the codec
                else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)         // See this 1st shortly after start. API >= 21: Ignore as getOutputBuffers() deprecated
                    Log.d("HU-SERVICE", "INFO_OUTPUT_BUFFERS_CHANGED: ");
                else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)          // See this 2nd shortly after start. Output format changed for subsequent data. See getOutputFormat()
                    Log.d("HU-SERVICE", "INFO_OUTPUT_FORMAT_CHANGED");
                else if (index == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;
                else
                    break;
            }
            catch (Exception E)
            {
                break;
            }
        }
        if (index != MediaCodec.INFO_TRY_AGAIN_LATER)
            Log.e("HU-SERVICE","index: " + index);
    }



    // For NAL units having nal_unit_type equal to 7 or 8 (indicating
    // a sequence parameter set or a picture parameter set,
    // respectively)
    private static boolean isSps(byte[] ba, int offset)
    {
        return getNalType(ba, offset) == 7;
    }


    private static int getNalType(byte[] ba, int offset)
    {
        // nal_unit_type
        // ba[4] == 0x67
        // +---------------+
        // |0|1|1|0|0|1|1|1|
        // +-+-+-+-+-+-+-+-+
        // |F|NRI|  Type   |
        // +---------------+
        return (ba[offset + 4] & 0x1f);
    }


    public void media_decode (byte [] buffer, int size) {                       // Decode audio or H264 video content. Called only by video_test() & hu_tra.aa_cmd_send()

        // synchronized (sLock) {
        ByteBuffer content = ByteBuffer.wrap(buffer, 0, size);

        while (content.hasRemaining ()) {                                 // While there is remaining content...

            if (! codec_input_provide (content)) {                          // Process buffer; if no available buffers...
                Log.e("HU-SERVICE","Dropping content because there are no available buffers.");

            }
            if (content.hasRemaining ())                                    // Never happens now
                Log.e("HU-SERVICE","content.hasRemaining ()");

            codec_output_consume ();                                        // Send result to video codec
        }
        // }

    }

  /*  public void media_decode (byte [] buffer, int size) {                       // Decode audio or H264 video content. Called only by video_test() & hu_tra.aa_cmd_send()


        int videodatasize = size;
        int videopos = 0;
        while (videopos < videodatasize) {
            int fragment_size = ByteBuffer.wrap(buffer, videopos, 4).getInt();
            videopos = videopos + 4;
            ByteBuffer content = ByteBuffer.wrap(buffer, videopos, fragment_size);
            videopos = videopos + fragment_size;
            synchronized (sLock) {


                while (content.hasRemaining()) {                                 // While there is remaining content...

                    if (!codec_input_provide(content)) {                          // Process buffer; if no available buffers...
                        Log.e("HU-SERVICE", "Dropping content because there are no available buffers.");
                        return;
                    }
                    if (content.hasRemaining())                                    // Never happens now
                        Log.e("HU-SERVICE", "content.hasRemaining ()");

                    codec_output_consume();                                        // Send result to video codec
                }
            }
        }
    }*/



    public final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            //   Log.d("HU-SERVICE","Should display toast...");
            if (msg.arg1==1)
                Toast.makeText(getBaseContext(),"Cannot connect to Headunit Server. Is it running on the phone?",Toast.LENGTH_LONG).show();
            else if (msg.arg1==2)
                Toast.makeText(getBaseContext(),"Your not connected to any Wifi...",Toast.LENGTH_LONG).show();
            else if (msg.arg1==3)
                return;

            connection_ok=false;
            mService.stopSelf();
            this_player.finish();
        }
    };



    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        m_sur_tex=surface;
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hires = SP.getBoolean("hires", false);

        if (hires) {
            m_virt_vid_wid = 1280f;
            m_virt_vid_hei = 720f;
        }

        if (SP.getBoolean("hud",false) && senAccelerometer!=null)
        {
            mSensorManager.registerListener(AccelerometerListener, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }



        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("stretch_full", true)) {

            float aspect_ratio = (float) 1.66;
            if (m_virt_vid_wid != 800f)
                aspect_ratio = (float) 1.77;


            m_tv_vid.setLayoutParams(new FrameLayout.LayoutParams(width, height, 17));
        }
        Log.d("HU-SERVICE", "Height: " + height + "width:" + width);
        codec_init(m_sur_tex, width, height);


        Thread thread = new Thread() {
            @Override
            public void run() {

                while (!mBound || m_codec == null) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }


                if (!mService.aap_running)
                {
                    mService.jni_aap_start("127.0.0.1",0,0);

                }
                else {

                    byte[] data = {  0x03, (byte) 0x80, 0x08, 0x08, 0x01, 0x10, 0x01};
                    byte [] tosend=new byte[11];
                    byte [] nextchunk=ByteBuffer.allocate(4).putInt(7).array();
                    System.arraycopy(nextchunk,0,tosend,0,4);
                    System.arraycopy(data,0,tosend,4,7);
                    //Log.d("HU-SERVICE","video start");
                    mService.send_que.write(tosend,0,tosend.length);

                }


            }
        };


        thread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d("HU-SERVICE","Surface was destroyed");


        mService.update_self_mplayer(null);
        byte[] data = {  0x03, (byte) 0x80, 0x08, 0x08, 0x02, 0x10, 0x01};
        byte [] tosend=new byte[11];
        byte [] nextchunk=ByteBuffer.allocate(4).putInt(7).array();
        System.arraycopy(nextchunk,0,tosend,0,4);
        System.arraycopy(data,0,tosend,4,7);
        Log.d("HU-SERVICE","Sendq surface destroy");
        mService.send_que.write(tosend,0,tosend.length);
        codec_ready=false;
        m_codec.flush();
        m_codec.stop();
        m_codec=null;
        m_sur_tex=null;


        return true;

    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }


    private SensorEventListener AccelerometerListener = new SensorEventListener(){
        public void onAccuracyChanged(Sensor sensor, int accuracy) {


        }

        public void onSensorChanged(SensorEvent event) {

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            {
                float[] g = new float[3];
                g = event.values.clone();

                double norm_Of_g = Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]);


                g[0] = (float) (g[0] / norm_Of_g);
                g[1] = (float) (g[1] / norm_Of_g);
                g[2] = (float) (g[2] / norm_Of_g);
                int inclination = (int) Math.round(Math.toDegrees(Math.acos(g[2])));

                if (inclination < 25 || inclination > 155)
                {
                    if (lastScale!=-1)
                        m_tv_vid.setScaleX(-1);
                        lastScale=-1;
                }
                else
                {
                    if (lastScale!=1)
                        m_tv_vid.setScaleX(1);
                    lastScale=1;
                }
            }
        }
    };

}