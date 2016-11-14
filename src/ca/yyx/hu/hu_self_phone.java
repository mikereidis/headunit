package gb.xxy.hr;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.app.UiModeManager;
import android.os.BatteryManager;
import android.support.v4.content.LocalBroadcastManager;


public class hu_self_phone extends Service{
 
	private static String TAG = "HU-SERVICE";
	private BroadcastReceiver receiver;
	private BroadcastReceiver dock_receiver;
    private static UiModeManager          m_uim_mgr   = null;
	
	//private boolean is_car_mode = true;
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
 
	@Override
	public void onCreate(){
	super.onCreate();
	 m_uim_mgr = (UiModeManager) getSystemService (UI_MODE_SERVICE);
	 
	/*Run a check each 5 seconds to see if we are still in car mode, if not unregister everything */
		new Thread(new Runnable() {
		  public void run() {
			  boolean keep_running=true;
			  while (keep_running) {
				  try {
				Thread.sleep(5000);
				  }
					catch (Throwable e) {
					Log.e ("HU-SERVICE", e.getMessage ());
					keep_running=false;
					}
					if (m_uim_mgr.getCurrentModeType()!=3)
						keep_running=false;
			  }
			 if (m_uim_mgr.getCurrentModeType()!=3) {
			 LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(hu_self_phone.this);
			 localBroadcastManager.sendBroadcast(new Intent("kill_switch")); 
			  }
		  }
	  }).start();

		
		
	
	
	
	}
	
	
	@Override
	public void onStart(Intent intent, int startId) {
		// TODO Auto-generated method stub
		super.onStart(intent, startId);
		Log.d(TAG, "FirstService started");
		Intent my = getPackageManager().getLaunchIntentForPackage("gb.xxy.hr");
		startActivity(my);
	}
 
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		Log.d(TAG, "Phone listener service destroyed");
		unregisterReceiver(receiver);
	}
 
}