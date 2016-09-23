package gb.xxy.hr;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.support.v4.content.LocalBroadcastManager;
import java.util.Locale;
import java.io.File;
import android.content.Intent;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;

// public class hu_usb_service extends BroadcastReceiver {  
public class hu_usb_service extends Activity {  
 
	private static String TAG = "HU-SERVICE";
	public static final String ACTION_USB_DEVICE_ATTACHED = "gb.xxy.hr.ACTION_USB_DEVICE_ATTACHED";
	@Override
	protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
    }
	
	  @Override
    protected void onResume()
    {
		 Log.d(TAG,"Invisible app started...");
        super.onResume();

	final File newFile = new File("/data/data/gb.xxy.hr/databases");
	try {newFile.mkdir();
	}
	catch (Exception e) {
		Log.d(TAG,"Error creating folder");
	}
	SQLiteDatabase mydatabase = SQLiteDatabase.openDatabase("/data/data/gb.xxy.hr/databases/devices.db",null,SQLiteDatabase.CREATE_IF_NECESSARY);
	mydatabase.execSQL("CREATE TABLE IF NOT EXISTS my_devices(device_name VARCHAR,status VARCHAR);"); //shouldn't use varchar for yes/no but cannot be bothered...
    // Done. we will do queries later on... 
	 Intent intent = getIntent();
        if (intent != null)
        {
			   if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED))
            {
     UsbDevice device = intent.<UsbDevice>getParcelableExtra (UsbManager.EXTRA_DEVICE);
	 
      hu_uti.logd ("USB BCR intent: " + intent);
     


      if (device != null) {
		String dev_name="";
		if (android.os.Build.VERSION.SDK_INT>=21)
			try {
			dev_name=device.getSerialNumber();
			}
			catch (Throwable t) {
      Log.e(TAG,"Throwable t: " + t);
    }
		else
			dev_name=Integer.toString(device.getVendorId()) + Integer.toString(device.getProductId());
		if (dev_name==null)
			finish();
		else if (dev_name.equals(""))
			finish();
		
		String action = intent.getAction ();
        if (action.equals (UsbManager.ACTION_USB_DEVICE_ATTACHED)) {    // If detach...


		  
		  Cursor resultSet = mydatabase.rawQuery("SELECT * FROM my_devices where device_name=?", new String[]{dev_name});
		  if (resultSet.getCount()==0)
		  {Log.d(TAG,"This is a new device we need to add it to the list");
		  
		  Intent ii=getPackageManager().getLaunchIntentForPackage("gb.xxy.hr");
          ii.putExtra("name", dev_name);
          startActivity(ii);
		  }
		  else 
		  {
			resultSet.moveToFirst();
			if(resultSet.getString(1).equals("Y"))
			{	
				Intent ii=getPackageManager().getLaunchIntentForPackage("gb.xxy.hr");
				startActivity(ii);
			}
		  }
		  Log.d(TAG,"Usb device attached" + dev_name);
        }

      }
      }

	}
		 finish();
	}	
	}
 
 
 
 
 
