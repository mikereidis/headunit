package gb.xxy.hr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Created by Emil on 21/05/2017.
 */

public class WifiReceiver extends BroadcastReceiver {
    static boolean isrunning=false;
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("HU-SERVICE","Wifi receiver fired");
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
        {
            boolean autorun = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("start_on_boot", false);
            if (autorun) {
                Intent starts = new Intent(context, gb.xxy.hr.new_hu_tra.class);
                starts.putExtra("mode", 5);
                context.startService(starts);
            }
        }
        else {
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

            boolean autorun = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("wifi_autorun", false);
            String SSID = PreferenceManager.getDefaultSharedPreferences(context).getString("ssid", "");
            if (info != null)
                Log.d("HU-SERVICE", "Net state:  " + info.getState().toString());
            if (info != null && autorun && info.getState().equals(NetworkInfo.State.CONNECTED)) {
                Log.d("HU-SERVICE", "Pre-conditions met.  " + info.getState().toString());


                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                String my_ssid = wifiManager.getConnectionInfo().getSSID();
                if (my_ssid.startsWith("\"") && my_ssid.endsWith("\""))
                    my_ssid = my_ssid.substring(1, my_ssid.length() - 1);
                if (SSID.equals("") || SSID.equalsIgnoreCase(my_ssid)) {
                    if (!isrunning) {
                        Log.d("HU-SERVICE", "App is not running so starting a new instance...");
                            /*
                            Intent i = new Intent(context.getPackageManager().getLaunchIntentForPackage("gb.xxy.hr"));
                            i.putExtra("wifi_autostart", "yes");
                            context.startActivity(i);
                            */
                        Intent starts = new Intent(context, gb.xxy.hr.new_hu_tra.class);
                        starts.putExtra("mode", 3);
                        context.startService(starts);
                        //Add a bit of delay sometimes Wifi Connection needs 1-2 secs to be established.
                        try {
                            Thread.sleep(500);
                        } catch (Exception E) {
                            Log.d("HU-RECEIVER", "Thread wasn't able to sleep...");
                        }
                            /*starts=new Intent(context, gb.xxy.hr.player.class);
                            starts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(starts);*/
                        isrunning = true;
                        //We need to start the app
                    }
                }

            } else if (info.getState().equals(NetworkInfo.State.DISCONNECTED) || info.getState().equals(NetworkInfo.State.DISCONNECTING))
                isrunning = false;
        }

    }
}
