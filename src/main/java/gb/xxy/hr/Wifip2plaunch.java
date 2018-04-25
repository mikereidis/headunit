package gb.xxy.hr;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;


/**
 * Created by Emil on 03/07/2017.
 */

public class Wifip2plaunch extends Activity implements   android.content.DialogInterface.OnClickListener, WifiP2pManager.ConnectionInfoListener {

private WifiP2pManager mManager;
private WifiP2pManager.Channel mChannel;
private BroadcastReceiver mReceiver;
private IntentFilter mIntentFilter;

private Button mDiscover;

public ArrayAdapter mAdapter;
private ArrayList<WifiP2pDevice> mDeviceList = new ArrayList<WifiP2pDevice>();
protected static final int CHOOSE_FILE_RESULT_CODE = 20;
        int flag=0;
private boolean server_running=false;
    private boolean doubleBackToExitPressedOnce;
    private AlertDialog alertDialog=null;
    private AlertDialog.Builder builder;
    private boolean isconnected=false;


    @Override
    public void onBackPressed() {
        Log.d("HU-SERVICE","Back arrow button was pressed...");
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            cancelDisconnect();
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
protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifip2p);




        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WiFiDirectReceiver(mManager, mChannel, this);
        setDeviceName("Headunit Reloaded");


        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        onDiscover();

        }

@Override
protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
        }

@Override
protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
        }
@Override
protected void onDestroy() {
        super.onDestroy();
        }

public void setDeviceName(String devName) {
        try {
        Class[] paramTypes = new Class[3];
        paramTypes[0] = WifiP2pManager.Channel.class;
paramTypes[1] = String.class;
paramTypes[2] = WifiP2pManager.ActionListener.class;
Method setDeviceName = mManager.getClass().getMethod(
        "setDeviceName", paramTypes);
        setDeviceName.setAccessible(true);

        Object arglist[] = new Object[3];
        arglist[0] = mChannel;
        arglist[1] = devName;
        arglist[2] = new WifiP2pManager.ActionListener() {

@Override
public void onSuccess() {
        Log.d("WIFIP2P","setDeviceName succeeded");
        }

@Override
public void onFailure(int reason) {
        Log.d("WIFIP2P","setDeviceName failed");
        }
        };

        setDeviceName.invoke(mManager, arglist);

        } catch (NoSuchMethodException e) {
        e.printStackTrace();
        } catch (IllegalAccessException e) {
        e.printStackTrace();
        } catch (IllegalArgumentException e) {
        e.printStackTrace();
        } catch (InvocationTargetException e) {
        e.printStackTrace();
        }

        }


private class WiFiDirectReceiver extends BroadcastReceiver {

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private Wifip2plaunch mActivity;

    public WiFiDirectReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, Wifip2plaunch activity) {
        super();
        mManager = manager;
        mChannel = channel;
        mActivity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {

                Toast.makeText(mActivity, "Wi-Fi Direct is enabled.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mActivity, "Wi-Fi Direct is disabled.", Toast.LENGTH_SHORT).show();
                finish();
            }

        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                Log.d("HU-WIFIP2P","Peers changed...");
            if (mManager != null) {

                mManager.requestPeers(mChannel, new WifiP2pManager.PeerListListener() {

                    @Override
                    public void onPeersAvailable(WifiP2pDeviceList peers) {
                        Log.d("HU-WIFIP2P","Peers list received..." + peers.toString());
                      if (alertDialog != null)
                                alertDialog.cancel();
                        if (peers != null) {
                            mDeviceList = new ArrayList<WifiP2pDevice>();
                            mDeviceList.addAll(peers.getDeviceList());
                            ArrayList<String> deviceNames = new ArrayList<String>();
                            int i=0;
                            for (WifiP2pDevice device : mDeviceList) {
                                if (device.deviceName.equalsIgnoreCase("Android Auto Server"))
                                {
                                    if (!isconnected)
                                        onDeviceSelected(i);
                                    return;
                                }
                                deviceNames.add(device.deviceName);
                                i++;

                            }
                            if (deviceNames.size() > 0) {
                                mAdapter = new ArrayAdapter<String>(mActivity, android.R.layout.simple_list_item_1, deviceNames);
                                if(flag==0)
                                {
                                   // flag=1;
                                    showDeviceListDialog();
                                }
                            } else {
                                Toast.makeText(mActivity, "Device list is empty.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            if (mManager == null) {
                return;
            }
            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);


            if (networkInfo.isConnected()) {

                mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {

                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {

                        InetAddress groupOwnerAddress = info.groupOwnerAddress;
                        String s=groupOwnerAddress.getHostAddress();
                        Intent i = new Intent(getBaseContext(), gb.xxy.hr.player.class);
                        i.putExtra("wifi_direct", true);
                        i.putExtra("wifi_direct_ip", s);
                        startActivity(i);
                        finish();

                    }
                });
            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

        }
    }

}



    private void onDiscover() {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifi.disconnect();
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                //Toast.makeText(Wifip2plaunch.this, "Discover peers successfully.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
               // Toast.makeText(Wifip2plaunch.this, "Discover peers failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeviceListDialog() {
       /* DeviceListDialog deviceListDialog = new DeviceListDialog();
        deviceListDialog.show(getFragmentManager(), "devices");*/
        Log.d("WIFIP2P","We should create a new dialog..." + mAdapter.toString());
        builder = new AlertDialog.Builder(this, R.style.MyDialogTheme);
        builder.setTitle("Select a device")
                .setSingleChoiceItems(mAdapter, 0, Wifip2plaunch.this)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        alertDialog=null;
                        dialog.dismiss();
                    }

                });

         alertDialog = builder.show();

    }

/*private class DeviceListDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.MyDialogTheme);
        builder.setTitle("Select a device")
                .setSingleChoiceItems(mAdapter, 0, Wifip2plaunch.this)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                    }

                });

        return builder.create();
    }

}*/

    @Override
    public void onClick(DialogInterface dialog, int which) {
        onDeviceSelected(which);
        alertDialog=null;
        flag=1;
        dialog.dismiss();
    }

    private void onDeviceSelected(int which) {
        WifiP2pDevice device = mDeviceList.get(which);
        if (device == null) {
            return;
        }


        final WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
       // config.wps.setup = WpsInfo.PBC;
        config.groupOwnerIntent = 0;
        Log.d("HU-WIFIP2P","Device selected....");
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d("HU-WIFIP2P","Connected to Wifi-p2p");
                 Toast.makeText(Wifip2plaunch.this, "Connected: " , Toast.LENGTH_SHORT).show();
                isconnected=true;
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(Wifip2plaunch.this, "Failed to connect", Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        // TODO Auto-generated method stub
        Log.d("WIFIP2P","Connection info available");
       // Toast.makeText(getApplicationContext(), "connectioninfoo", Toast.LENGTH_LONG).show();

    }


    public void cancelDisconnect() {

        if (mManager != null) {


                mManager.stopPeerDiscovery(mChannel,new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {

                    }
                    @Override
                    public void onFailure(int reasonCode) {

                    }
                });
                mManager.cancelConnect(mChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(Wifip2plaunch.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(Wifip2plaunch.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            disconnect();
            }
        }

    public void disconnect() {

        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onFailure(int reasonCode) {
                Log.d("HU-WIFIP2P", "Remove group failed. Reason :" + reasonCode);
            }
            @Override
            public void onSuccess() {

            }
        });
    }

}