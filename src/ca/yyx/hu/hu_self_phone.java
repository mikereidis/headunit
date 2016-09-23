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
	  
	IntentFilter filter = new IntentFilter();
    filter.addAction("android.intent.action.PHONE_STATE");
	filter.addAction("android.intent.action.DOCK_EVENT");
  receiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
		Log.d(TAG,"receiver fired");
	if (intent.getAction().equals("android.intent.action.DOCK_EVENT"))
	{
		int dockState = intent.getIntExtra(intent.EXTRA_DOCK_STATE, -1);
		Log.d(TAG,"Dock change detected, current value is: "+ dockState);
	
	}
    else if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
		 if (m_uim_mgr.getCurrentModeType()==3) {
		Log.d(TAG, "Outgoing call detected");
		try {
		Thread.sleep(3000);
		}
		catch (Throwable t) {
				  Log.e (TAG,"Throwable: " + t);
				}
		Log.d(TAG, "Sending home key");
		
		Log.d(TAG,"Current mode is: " + m_uim_mgr.getCurrentModeType());		
	
        m_uim_mgr.enableCarMode(UiModeManager.ENABLE_CAR_MODE_GO_CAR_HOME);
		 }
		else {		 
		LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(hu_self_phone.this);
        localBroadcastManager.sendBroadcast(new Intent("kill_switch"));
		}
    } else {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);

        switch (tm.getCallState()) {
       
        case TelephonyManager.CALL_STATE_OFFHOOK:

		
		
           				Log.d(TAG, "Off the hook state detected");
						Log.d(TAG,"Current mode is: " + m_uim_mgr.getCurrentModeType());
						if (m_uim_mgr.getCurrentModeType()==3)
						{
						try {
				Thread.sleep(3000);
						}
		catch (Throwable t) {
				 Log.e (TAG,"Throwable: " + t);
				}
				Log.d(TAG, "Sending home key");
                m_uim_mgr.enableCarMode(UiModeManager.ENABLE_CAR_MODE_GO_CAR_HOME); 
						}
						else
						{
					LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(hu_self_phone.this);
					localBroadcastManager.sendBroadcast(new Intent("kill_switch"));
						}
            break;
					
				}
				}
			}

			  };
		registerReceiver(receiver, filter);	
		
		
	
	
	
	}
	
	
	@Override
	public void onStart(Intent intent, int startId) {
		// TODO Auto-generated method stub
		super.onStart(intent, startId);
		Log.d(TAG, "FirstService started");
		
	}
 
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		Log.d(TAG, "Phone listener service destroyed");
		unregisterReceiver(receiver);
	}
 
}