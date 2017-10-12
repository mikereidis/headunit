package gb.xxy.hr;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.ImageButton;
import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

import static android.os.Environment.DIRECTORY_DOCUMENTS;
import static android.os.Environment.DIRECTORY_DOWNLOADS;


public class hu_act extends Activity {

    private static hu_act myact=null;
    private ImageButton button1;
    private ImageButton button2;
    private ImageButton button3;
    private ImageButton button4;
    private ImageButton button5;
    private ImageButton button6;
    private UsbDevice       m_usb_device;
    private UsbManager      m_usb_mgr;
    private usb_receiver    m_usb_receiver;
    private UsbDeviceConnection m_usb_dev_conn  = null;
    private UsbInterface m_usb_iface     = null;
    private UsbEndpoint m_usb_ep_in     = null;
    private UsbEndpoint m_usb_ep_out    = null;
    private int m_ep_in_addr   = -1;
    private int m_ep_out_addr  = -1;
    private boolean m_usb_connected;
    private static final int USB_PID_ACC         = 0x2D00;      // Accessory                  100
    private static final int USB_PID_ACC_ADB     = 0x2D01;      // Accessory + ADB            110
    private static final int USB_VID_GOO           = 0x18D1;
    private static final int ACC_REQ_GET_PROTOCOL = 51;
    private static final int ACC_REQ_SEND_STRING  = 52;
    private static final int ACC_REQ_START        = 53;

    private boolean doubleBackToExitPressedOnce;
    public static boolean showplayer=false;
    public static boolean showselfplayer=false;
    public static boolean hasPermission=false;
    public static boolean debugging=false;
    public static FileOutputStream outputStream;

    private SharedPreferences SP;
    @Override
    protected  void onDestroy()
    {
        super.onDestroy();
        try {
            mylog.d("HU-ACT","On Destory");
            unregisterReceiver(m_usb_receiver);
        }
        catch (Exception e)
        {
            mylog.d("USB-SERVICE","Nothing to unregister...");
        }

    }

    @Override
    public void onBackPressed() {
        mylog.d("HU-SERVICE","Back arrow button was pressed main activity...");

        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            stopService(new Intent(getBaseContext(), gb.xxy.hr.new_hu_tra.class));
            showplayer=false;
            showselfplayer=false;
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
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
        mylog.d("HU-SERVICE","OnResume..." + showplayer);

        if (showplayer) {
            Intent i = new Intent(getBaseContext(), gb.xxy.hr.player.class);
            startActivity(i);
        }
        else if (showselfplayer)
        {
            Intent i = new Intent(getBaseContext(), gb.xxy.hr.self_player.class);
            startActivity(i);
        }
        else
        {
            Intent starts = new Intent(this, gb.xxy.hr.new_hu_tra.class);
            starts.putExtra("mode",5);
            startService(starts);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myact=this;
        setContentView(R.layout.layout);
        m_usb_mgr = (UsbManager) this.getSystemService (Context.USB_SERVICE);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        addListenerOnButton();
        Intent intent = getIntent ();
        mylog.d("HU-APP","OnCreate"+intent.toString());
        if (intent.getAction().equals (UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            mylog.d("USB-SERVICE","Started on USB ATTACHED ACTION INTENT");
            UsbDevice device = intent.<UsbDevice>getParcelableExtra (UsbManager.EXTRA_DEVICE);
            usb_attach_handler(device);
            }
        IntentFilter filter = new IntentFilter ();
        filter.addAction (UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction (UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction ("gb.xxy.hr.ACTION_USB_DEVICE_PERMISSION");
        filter.addAction("gb.xxy.hr.show_connection_error");
        m_usb_receiver = new usb_receiver ();
        registerReceiver (m_usb_receiver, filter);

        SP = PreferenceManager.getDefaultSharedPreferences(this);
        if (SP.getBoolean("self_autorun", false))
        {
            try {
                Process p;
                p = Runtime.getRuntime ().exec ("su");
                DataOutputStream os = new DataOutputStream (p.getOutputStream ());
                os.writeBytes ("am force-stop com.google.android.projection.gearhead; am start -W -n com.google.android.projection.gearhead/.companion.SplashScreenActivity; am startservice -n com.google.android.projection.gearhead/.companion.DeveloperHeadUnitNetworkService; \n");
                os.writeBytes ("exit\n");
                os.flush ();
                p.waitFor ();
                Thread.sleep(500);
            }
            catch (Exception e) {
                mylog.e ("HU-SERVICE","Exception e: " + e);
            };
            Intent i = new Intent(getBaseContext(), gb.xxy.hr.self_player.class);
            startActivity(i);
        }

        if (SP.getBoolean("enabledebug",false))
        {
            Log.d("HU","Debugging is enabled");
           debugging=true;
            if (((ContextCompat.checkSelfPermission(hu_act.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) || (ContextCompat.checkSelfPermission(hu_act.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)))
            {
                Log.d("HU","No permission, should request permission...");
                AlertDialog.Builder builder = new AlertDialog.Builder(hu_act.this);
                builder.setTitle(getResources().getString(R.string.stor_perm_tit));
                builder.setMessage(getResources().getString(R.string.stor_perm_desc));
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        ActivityCompat.requestPermissions(hu_act.this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE},0);
                    }
                });
                builder.setNegativeButton(getString(R.string.ignore), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
                builder.show();

            }
            else {
                Log.d("HU","Have permission, creating file...");
                String filename = "hur.log";
                File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS), filename);

                try {
                    file.createNewFile();
                    outputStream = new FileOutputStream(file);
                } catch (Exception E) {

                }
            }
        }

         }

     public void addListenerOnButton() {


        button1= (ImageButton) findViewById(R.id.imageButton1);
        button2= (ImageButton) findViewById(R.id.imageButton2);
        button3= (ImageButton) findViewById(R.id.imageButton3);
        button4= (ImageButton) findViewById(R.id.imageButton4);
        button5= (ImageButton) findViewById(R.id.imageButton5);
        button6= (ImageButton) findViewById(R.id.imageButton6);
        button1.setOnClickListener(ibLis);
        button2.setOnClickListener(ibLis);
        button3.setOnClickListener(ibLis);
        button4.setOnClickListener(ibLis);
        button5.setOnClickListener(ibLis);
        button6.setOnClickListener(ibLis);

    }

    private View.OnClickListener ibLis = new                // Tap: Tune to preset
            View.OnClickListener () {
                public void onClick (View v) {



                    if(v == button1){

                        try {
                            Process p;
                            p = Runtime.getRuntime ().exec ("su");
                            DataOutputStream os = new DataOutputStream (p.getOutputStream ());
                            os.writeBytes ("am force-stop com.google.android.projection.gearhead; am start -W -n com.google.android.projection.gearhead/.companion.SplashScreenActivity; am startservice -n com.google.android.projection.gearhead/.companion.DeveloperHeadUnitNetworkService; \n");
                            os.writeBytes ("exit\n");
                            os.flush ();
                            p.waitFor ();
                            Thread.sleep(500);
                        }
                        catch (Exception e) {
                           mylog.e ("HU-SERVICE","Exception e: " + e);
                        };



                        /*Intent serviceIntent = new Intent(getBaseContext(),gb.xxy.hr.new_hu_tra.class);
                        serviceIntent.putExtra("mode", 0);
                        startService(serviceIntent);*/

                        Intent i = new Intent(getBaseContext(), gb.xxy.hr.self_player.class);
                        startActivity(i);

                    } else if(v == button2){
                        Intent i = new Intent(getBaseContext(), gb.xxy.hr.player.class);
                        startActivity(i);
                        showplayer=true;
                    } else if(v == button3){
                        showplayer=true;
                        Intent i = new Intent(getBaseContext(), gb.xxy.hr.Wifip2plaunch.class);
                        startActivity(i);

                    } else if(v == button4){
                       try {
                           Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://forum.xda-developers.com/general/paid-software/android-4-1-headunit-reloaded-android-t3432348"));
                           startActivity(browserIntent);
                       }
                       catch (ActivityNotFoundException e) {
                           Toast.makeText(getBaseContext(), "No application can handle this request."
                                   + " Please install a webbrowser",  Toast.LENGTH_LONG).show();
                           e.printStackTrace();
                       }
                    } else if(v == button5){
                        Intent i = new Intent(getBaseContext(), gb.xxy.hr.hu_pref.class);
                        startActivity(i);
                    } else if(v == button6){
                        stopService(new Intent(getBaseContext(), gb.xxy.hr.new_hu_tra.class));
                        WifiP2pManager mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
                        WifiP2pManager.Channel mChannel = mManager.initialize(getBaseContext(), getMainLooper(), null);
                        try {
                            mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onFailure(int reasonCode) {
                                    mylog.d("HU-WIFIP2P", "Remove group failed. Reason :" + reasonCode);
                                    finish();
                                    android.os.Process.killProcess(android.os.Process.myPid());
                                }

                                @Override
                                public void onSuccess() {
                                    finish();
                                    android.os.Process.killProcess(android.os.Process.myPid());
                                }
                            });
                        }
                        catch (Exception e)
                        {
                            finish();
                            android.os.Process.killProcess(android.os.Process.myPid());
                        }

                    }


                }
            };
public static void closemyapp(){
    if (myact!=null)
        myact.finishAffinity();
}
//USB Related Stuff
private final class usb_receiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {

        UsbDevice device = intent.<UsbDevice>getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
            String action = intent.getAction();
            mylog.d("USB-SERVICE", "We are here" + action);
            if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {    // If detach...
                usb_detach_handler(device);                                  // Handle detached device
            } else if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {// If attach...
                usb_attach_handler(device);                            // Handle New attached device
            } else if (action.equals("gb.xxy.hr.ACTION_USB_DEVICE_PERMISSION")) {                 // If Our App specific Intent for permission request...
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    usb_attach_handler(device);                         // Handle same as attached device except NOT NEW so don't add to USB device list
                }
            }
        }
        else
        {
            String action = intent.getAction();
            if(action.equals("gb.xxy.hr.show_connection_error")){
                Toast.makeText(getBaseContext(),getResources().getString(R.string.err_noserv),Toast.LENGTH_LONG).show();
            }
        }
    }
}

    private boolean usb_attach_handler (UsbDevice device) {     // Handle attached device. Called only by:  transport_start() on autolaunch or device find, and...

        if (! m_usb_connected)
            usb_connect (device);

       if (m_usb_connected) {
            mylog.d("USB-SERVICE","Connected so start JNI");
           Intent starts = new Intent(this, gb.xxy.hr.new_hu_tra.class);
           starts.putExtra("mode",2);
           showplayer=true;
           startService(starts);

           Intent i = new Intent(getBaseContext(), gb.xxy.hr.player.class);
           i.putExtra("USB", true);
           i.putExtra("ep_in", m_ep_in_addr);
           i.putExtra("ep_out", m_ep_out_addr);
           new_hu_tra.usbconn=m_usb_dev_conn;
           new_hu_tra.m_usb_ep_in=m_usb_ep_in;
           new_hu_tra.m_usb_ep_out=m_usb_ep_out;

           //m_usb_dev_conn.releaseInterface (m_usb_iface);
           startActivity(i);
           m_usb_device=device;
        }

        return (true);
    }

    private void usb_detach_handler (UsbDevice device) {                  // Handle detached device.  Called only by usb_receiver() if device detached while app is running (only ?)
        int dev_vend_id = device.getVendorId ();                            // mVendorId=2996               HTC
        int dev_prod_id = device.getProductId ();                           // mProductId=1562              OneM8


        // If in accessory mode...
        if (dev_vend_id == USB_VID_GOO && (dev_prod_id == USB_PID_ACC || dev_prod_id == USB_PID_ACC_ADB))
         {                                         //If it is our connected device, disconnet, otherwise ignore!
            stopService(new Intent(getBaseContext(), gb.xxy.hr.new_hu_tra.class));
            finishAffinity();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
        else
            mylog.d("HU-MAIN","Not our USB DEVICE...");
    }


    private void usb_connect (final UsbDevice device) {

        m_usb_dev_conn = null;
        mylog.d("USB-SERVICE","Device connect");

        if (! m_usb_mgr.hasPermission (device)) {                               // If we DON'T have permission to access the USB device...

            if (SP.getBoolean("oldusb",false))
            {
                Intent intent = new Intent ("gb.xxy.hr.ACTION_USB_DEVICE_PERMISSION");                 // Our App specific Intent for permission request
                intent.setPackage (this.getPackageName ());
                PendingIntent pendingIntent = PendingIntent.getBroadcast (this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
                m_usb_mgr.requestPermission (device, pendingIntent);              // Request permission. BCR called later if we get it.
            }
            else {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        usb_attach_handler(device);
                    }
                }, 3000);
            }

            return;                                                           // Done for now. Wait for permission
        }
        int ret = usb_open (device);                                        // Open USB device & claim interface
        if (ret < 0) {                                                      // If error...
            usb_disconnect ();                                                // Ensure state is disconnected
            return;                                                           // Done
        }

        int dev_vend_id = device.getVendorId ();                            // mVendorId=2996               HTC
        int dev_prod_id = device.getProductId ();                           // mProductId=1562              OneM8


        // If in accessory mode...
        if (dev_vend_id == USB_VID_GOO && (dev_prod_id == USB_PID_ACC || dev_prod_id == USB_PID_ACC_ADB)) {
            ret = acc_mode_endpoints_set ();                                  // Set Accessory mode Endpoints
            if (ret < 0) {                                                    // If error...
                usb_disconnect ();                                              // Ensure state is disconnected
            }
            else {
                m_usb_connected = true;

            }
            return;                                                           // Done
        }
        // Else if not in accessory mode...
        acc_mode_switch (m_usb_dev_conn);                                   // Do accessory negotiation and attempt to switch to accessory mode
        //usb_disconnect ();                                                  // Ensure state is disconnected
                                                                // Done, wait for accessory mode
    }

    private void usb_disconnect () {
       mylog.d("USB-SERVICE","usb_disconnect");// m_usb_device: " + m_usb_device);
        m_usb_connected = false;

        usb_close ();
    }

    private void usb_close () {                                           // Release interface and close USB device connection. Called only by usb_disconnect()
       mylog.d("USB-SERVICE","usb_close");
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
               mylog.d("USB-SERVICE","OK releaseInterface()");
            }
            else {
                mylog.e ("USB-SERVICE","Error releaseInterface()");
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
           mylog.e("USB-SERVICE","Throwable: " + e);                                  // java.lang.IllegalArgumentException: device /dev/bus/usb/001/019 does not exist or is restricted
        }

        if (m_usb_dev_conn == null) {
           Log.w("USB-SERVICE","Could not obtain m_usb_dev_conn for device: " + device);
            return (-1);                                                      // Done error
        }
       mylog.d("USB-SERVICE","Device m_usb_dev_conn: " + m_usb_dev_conn);

        try {
            int iface_cnt = device.getInterfaceCount ();
            if (iface_cnt <= 0) {
               mylog.e("USB-SERVICE","iface_cnt: " + iface_cnt);
                return (-1);                                                    // Done error
            }
           mylog.d("USB-SERVICE","iface_cnt: " + iface_cnt);
            m_usb_iface = device.getInterface (0);                            // java.lang.ArrayIndexOutOfBoundsException: length=0; index=0

            if (! m_usb_dev_conn.claimInterface (m_usb_iface, true)) {        // Claim interface, if error...   true = take from kernel
               mylog.e("USB-SERVICE","Error claiming interface");
                return (-1);
            }
           mylog.d("USB-SERVICE","Success claiming interface");
        }
        catch (Throwable e) {
           mylog.e("USB-SERVICE","Throwable: " + e);           // Nexus 7 2013:    Throwable: java.lang.ArrayIndexOutOfBoundsException: length=0; index=0
            return (-1);                                                      // Done error
        }
        return (0);                                                         // Done success
    }

    private int acc_mode_endpoints_set () {                               // Set Accessory mode Endpoints. Called only by usb_connect()
       mylog.d("USB-SERVICE","In acc so get EPs...");
        m_usb_ep_in   = null;                                               // Setup bulk endpoints.
        m_usb_ep_out  = null;
        m_ep_in_addr  = -1;     // 129
        m_ep_out_addr = -1;     // 2

        for (int i = 0; i < m_usb_iface.getEndpointCount (); i ++) {        // For all USB endpoints...
            UsbEndpoint ep = m_usb_iface.getEndpoint (i);
            if (ep.getDirection () == UsbConstants.USB_DIR_IN) {              // If IN
                if (m_usb_ep_in == null) {                                      // If Bulk In not set yet...
                    m_ep_in_addr = ep.getAddress ();
                    mylog.d ("USB-SERVICE",String.format ("Bulk IN m_ep_in_addr: %d  %d", m_ep_in_addr, i));
                    m_usb_ep_in = ep;                                             // Set Bulk In
                }
            }
            else {                                                            // Else if OUT...
                if (m_usb_ep_out == null) {                                     // If Bulk Out not set yet...
                    m_ep_out_addr = ep.getAddress ();
                    mylog.d ("USB-SERVICE",String.format ("Bulk OUT m_ep_out_addr: %d  %d", m_ep_out_addr, i));
                    m_usb_ep_out = ep;                                            // Set Bulk Out
                }
            }
        }
        if (m_usb_ep_in == null || m_usb_ep_out == null) {
           mylog.e("USB-SERVICE","Unable to find bulk endpoints");
            return (-1);                                                      // Done error
        }

       mylog.d("USB-SERVICE","Connected have EPs");
        return (0);                                                         // Done success
    }

    private void acc_mode_switch (UsbDeviceConnection conn) {             // Do accessory negotiation and attempt to switch to accessory mode. Called only by usb_connect()
       mylog.d("USB-SERVICE","Attempt acc");

        int len;
        byte buffer  [] = new byte  [2];
        len = conn.controlTransfer (UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR, ACC_REQ_GET_PROTOCOL, 0, 0, buffer, 2, 10000);
        if (len != 2) {
           mylog.e("USB-SERVICE","Error controlTransfer len: " + len);
            return;
        }
        int acc_ver = (buffer [1] << 8) | buffer  [0];                      // Get OAP / ACC protocol version
       mylog.d("USB-SERVICE","Success controlTransfer len: " + len + "  acc_ver: " + acc_ver);
        if (acc_ver < 1) {                                                  // If error or version too low...
           mylog.e("USB-SERVICE","No support acc");
            return;
        }
       mylog.d("USB-SERVICE","acc_ver: " + acc_ver);

        // Send all accessory identification strings
        usb_acc_string_send (conn, 0, "Android");            // Manufacturer
        usb_acc_string_send (conn, 1, "Android Auto");            // Model
        usb_acc_string_send (conn, 2, "Android Auto");            // desc
        usb_acc_string_send (conn, 3, "2.0.1");            // ver
        usb_acc_string_send (conn, 4, "https://forum.xda-developers.com/general/paid-software/android-4-1-headunit-reloaded-android-t3432348");            // uri
        usb_acc_string_send (conn, 5, "HU-AAAAAA001");            // serial



       mylog.d("USB-SERVICE","Sending acc start");           // Send accessory start request. Device should re-enumerate as an accessory.
        len = conn.controlTransfer (UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, ACC_REQ_START, 0, 0, null, 0, 10000);
        if (len != 0) {
           mylog.e("USB-SERVICE","Error acc start");
        }
        else {
           mylog.d("USB-SERVICE","OK acc start. Wait to re-enumerate...");
        }
    }

    // Send one accessory identification string.    Called only by acc_mode_switch()
    private void usb_acc_string_send (UsbDeviceConnection conn, int index, String string) {
        byte  [] buffer = (string + "\0").getBytes ();
        int len = conn.controlTransfer (UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, ACC_REQ_SEND_STRING, 0, index, buffer, buffer.length, 10000);
        if (len != buffer.length) {
           mylog.e("USB-SERVICE","Error controlTransfer len: " + len + "  index: " + index + "  string: \"" + string + "\"");
        }
        else {
           mylog.d("USB-SERVICE","Success controlTransfer len: " + len + "  index: " + index + "  string: \"" + string + "\"");
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                   hasPermission=true;
                    String filename = "hur.log";
                    File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), filename);

                    try {
                        file.createNewFile();
                        outputStream = new FileOutputStream(file);
                    }
                    catch (Exception E)
                    {

                    }
                }

    }

}