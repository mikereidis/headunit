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
import android.support.v4.content.LocalBroadcastManager;

 public class hu_wifi_service extends BroadcastReceiver {  
	private static String TAG = "HU-SERVICE";
	private static int is_app_running=0;
	private static int is_wifi_connected=0;
         // USB Broadcast Receiver enabled by transport_start() & disabled by transport_stop()
     @Override
     public void onReceive (Context context, Intent intent) {
		// Log.d(TAG,"onReceiver fired");
		// // Let's open a database to store the allowed/dissalowed devices
		
		if (intent.getAction().equals("toggle_app_running_state"))
		{
			
		is_app_running=intent.getIntExtra("curr",0);
		Log.d(TAG,"App toggle state" + is_app_running);		
		}
		else {
	
			if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("wifi_autorun",false)) 
			{
				
				 
				 
				 NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				 Log.d(TAG,"Info:" + info.getState() + " is_app_running: " + is_app_running);
				  if(info != null && info.getState().equals(NetworkInfo.State.CONNECTED) && is_app_running==0) {
					WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
					WifiInfo wifiInfo = wifiManager.getConnectionInfo();
					String my_ssid = wifiInfo.getSSID();
					if (my_ssid.startsWith("\"") && my_ssid.endsWith("\"")){
						 my_ssid = my_ssid.substring(1, my_ssid.length()-1);
					}
					Log.d(TAG,my_ssid + " = " + PreferenceManager.getDefaultSharedPreferences(context).getString("ssid","0") + " means " + PreferenceManager.getDefaultSharedPreferences(context).getString("ssid","0").equalsIgnoreCase(my_ssid));
					if (PreferenceManager.getDefaultSharedPreferences(context).getString("ssid","0").equals("") || PreferenceManager.getDefaultSharedPreferences(context).getString("ssid","0").equalsIgnoreCase(my_ssid))
						 {
							 is_app_running=1;
							 is_wifi_connected=1;
							 Intent ii=context.getPackageManager().getLaunchIntentForPackage("gb.xxy.hr");
							 context.startActivity(ii);
						 }
				  }
				  else if ((info.getState().equals(NetworkInfo.State.DISCONNECTING) || info.getState().equals(NetworkInfo.State.DISCONNECTED)) && is_app_running==1){
					  Log.d(TAG,"Wifi disconecting...");
					
							if (is_wifi_connected==1)
						  {
								Log.d(TAG,"trying to kill...");
							is_app_running=0;
							is_wifi_connected=0;
							 LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
							 localBroadcastManager.sendBroadcast(new Intent("kill_switch"));
							
						 }
					  
				  }
				 

			}
		}
		  
	  }
	}
