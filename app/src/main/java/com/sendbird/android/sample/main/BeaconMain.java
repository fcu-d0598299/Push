package com.sendbird.android.sample.main;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.THLight.USBeacon.App.Lib.BatteryPowerData;
import com.THLight.USBeacon.App.Lib.USBeaconConnection;
import com.THLight.USBeacon.App.Lib.USBeaconData;
import com.THLight.USBeacon.App.Lib.USBeaconList;
import com.THLight.USBeacon.App.Lib.USBeaconServerInfo;
import com.THLight.USBeacon.App.Lib.iBeaconData;
import com.THLight.USBeacon.App.Lib.iBeaconScanManager;
import com.sendbird.android.sample.R;
import com.THLight.Util.THLLog;

/** ============================================================== */
public class BeaconMain extends Activity implements iBeaconScanManager.OniBeaconScan, USBeaconConnection.OnResponse
{
    /** this UUID is generate by Server while register a new account. */
    final UUID QUERY_UUID		= UUID.fromString("BB746F72-282F-4378-9416-89178C1019FC");
    /** server http api url. */
    final String HTTP_API		= "http://www.usbeacon.com.tw/api/func";

    static String STORE_PATH	= Environment.getExternalStorageDirectory().toString()+ "/USBeaconSample/";

    final int REQ_ENABLE_BT		= 2000;
    final int REQ_ENABLE_WIFI	= 2001;

    final int MSG_SCAN_IBEACON			= 1000;
    final int MSG_UPDATE_BEACON_LIST	= 1001;
    final int MSG_SERVER_RESPONSE		= 3000;

    final int TIME_BEACON_TIMEOUT		= 30000;

    public String condition = "safe";

    static String distance;

    BaseApplication App		= null;
    THLConfig Config= null;

    BluetoothAdapter mBLEAdapter= BluetoothAdapter.getDefaultAdapter();

    /** scanner for scanning iBeacon around. */
    iBeaconScanManager miScaner	= null;

    /** USBeacon server. Connect to the Server.*/
    USBeaconConnection mBServer	= new USBeaconConnection();

    USBeaconList mUSBList		= null;

    ListView mLVBLE= null;

    BLEListAdapter mListAdapter		= null;

    List<ScanediBeacon> miBeacons	= new ArrayList<ScanediBeacon>();    // a beacon list
    /** ================================================ */
    public boolean onCreatOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.action_settings: //點了settings
                Log.d("item","click settings");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    /** ================================================ */
    Handler mHandler= new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch(msg.what)
            {
                case MSG_SCAN_IBEACON:
                {
                    int timeForScaning		= msg.arg1;
                    int nextTimeStartScan	= msg.arg2;

                    miScaner.startScaniBeacon(timeForScaning);   //Start scan iBeacon
                    this.sendMessageDelayed(Message.obtain(msg), nextTimeStartScan);
                }
                break;

                // Update the beacon list on the phone screen.
                case MSG_UPDATE_BEACON_LIST:
                    synchronized(mListAdapter)
                    {
                        verifyiBeacons();
                        mListAdapter.notifyDataSetChanged();
                        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_BEACON_LIST, 500);
                    }
                    break;

                case MSG_SERVER_RESPONSE:
                    switch(msg.arg1)
                    {
                        case USBeaconConnection.MSG_NETWORK_NOT_AVAILABLE:
                            break;

                        // Get the data from Server by the "QUERY_UUID"
                        case USBeaconConnection.MSG_HAS_UPDATE:
                            //Download beacon data to a zip file, and send MSG_DATA_UPDATE_FINISHED
                            mBServer.downloadBeaconListFile();
                            Toast.makeText(BeaconMain.this, "HAS_UPDATE.", Toast.LENGTH_SHORT).show();
                            break;

                        case USBeaconConnection.MSG_HAS_NO_UPDATE:
                            Toast.makeText(BeaconMain.this, "No new BeaconList.", Toast.LENGTH_SHORT).show();
                            break;

                        case USBeaconConnection.MSG_DOWNLOAD_FINISHED:
                            break;

                        case USBeaconConnection.MSG_DOWNLOAD_FAILED:
                            Toast.makeText(BeaconMain.this, "Download file failed!", Toast.LENGTH_SHORT).show();
                            break;

                        case USBeaconConnection.MSG_DATA_UPDATE_FINISHED:
                        {
                            USBeaconList BList= mBServer.getUSBeaconList();  //Get the beacon list that was from Server

                            if(null == BList)
                            {
                                Toast.makeText(BeaconMain.this, "Data Updated failed.", Toast.LENGTH_SHORT).show();
                                THLLog.d("debug", "update failed.");
                            }
                            else if(BList.getList().isEmpty())
                            {
                                Toast.makeText(BeaconMain.this, "Data Updated but empty.", Toast.LENGTH_SHORT).show();
                                THLLog.d("debug", "this account doesn't contain any devices.");
                            }
                            else
                            {
                                String BeaconData = "";
                                Toast.makeText(BeaconMain.this, "Data Updated("+ BList.getList().size()+ ")", Toast.LENGTH_SHORT).show();

                                for(USBeaconData data : BList.getList())
                                {
                                    BeaconData = BeaconData + "Name("+ data.name+ "), Ver("+ data.major+ "."+ data.minor+ ")\n";
                                    THLLog.d("debug", "Name("+ data.name+ "), Ver("+ data.major+ "."+ data.minor+ ")");
                                }

                                showBeaconFromServerOnDialog(BeaconData);
                            }
                        }
                        break;

                        case USBeaconConnection.MSG_DATA_UPDATE_FAILED:
                            Toast.makeText(BeaconMain.this, "UPDATE_FAILED!", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
            }
        }
    };

    /** ================================================ */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ui_main);

        App		= BaseApplication.getApp();
        Config	= BaseApplication.Config;

        /** create instance of iBeaconScanManager. */
        miScaner		= new iBeaconScanManager(this, this);

        mListAdapter	= new BLEListAdapter(this);

        mLVBLE			= (ListView)findViewById(R.id.beacon_list);
        mLVBLE.setAdapter(mListAdapter);

        //Check the BT is on or off on the phone.
        if(!mBLEAdapter.isEnabled())
        {
            Intent intent= new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQ_ENABLE_BT);   // A request for open Bluetooth
        }
        else
        {
            // Start scan if BT is opened
            Message msg= Message.obtain(mHandler, MSG_SCAN_IBEACON, 1000, 1100);
            msg.sendToTarget();
        }

        /** create store folder. */
        File file= new File(STORE_PATH);
        if(!file.exists())
        {
            if(!file.mkdirs())
            {
                Toast.makeText(this, "Create folder("+ STORE_PATH+ ") failed.", Toast.LENGTH_SHORT).show();//////////////////////////////////////////////////////////////////////////////////
            }
        }

        /** check network is available or not. */
        ConnectivityManager cm	= (ConnectivityManager)getSystemService(BeaconMain.CONNECTIVITY_SERVICE);
        if(null != cm)
        {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if(null == ni || (!ni.isConnected()))
            {
                dlgNetworkNotAvailable();     //Show a dialog to inform users to enable  the network.
            }
            else
            {
                THLLog.d("debug", "NI not null");

                NetworkInfo niMobile= cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                if(null != niMobile)
                {
                    boolean isMobileInt	= niMobile.isConnectedOrConnecting();

                    if(isMobileInt)
                    {
                        dlgNetworkMobile();  //Show a dialog to make sure to use the Mobile Internet
                    }
                    else
                    {
                        USBeaconServerInfo info= new USBeaconServerInfo();

                        info.serverUrl		= HTTP_API;
                        info.queryUuid		= QUERY_UUID;
                        info.downloadPath	= STORE_PATH;

                        mBServer.setServerInfo(info, this);
                        //Check is there is data to download from Server or not(By QUERY_UUID).
                        // If yes, send MSG_HAS_UPDATE.
                        // If no, send MSG_HAS_NO_UPDATE.
                        mBServer.checkForUpdates();
                    }
                }
            }
        }
        else
        {
            THLLog.d("debug", "CM null");
        }







        /**=傳值*/


        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_BEACON_LIST, 500);
    }

    /** ================================================ */
    @Override
    public void onResume()
    {
        super.onResume();
    }

    /** ================================================ */
    @Override
    public void onPause()
    {
        super.onPause();
    }

    /** ================================================ */
    @Override
    public void onBackPressed()
    {
        super.onBackPressed();
    }

    /** ================================================ */
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        THLLog.d("DEBUG", "onActivityResult()");

        switch(requestCode)
        {
            case REQ_ENABLE_BT:
                if(RESULT_OK == resultCode)
                {
                }
                break;

            case REQ_ENABLE_WIFI:
                if(RESULT_OK == resultCode)
                {
                }
                break;
        }
    }

    /** ================================================ */
    @Override
    public void onScaned(iBeaconData iBeacon)
    {
        synchronized(mListAdapter)
        {
            addOrUpdateiBeacon(iBeacon);
        }

        Toast.makeText(BeaconMain.this,"掃描中...",Toast.LENGTH_SHORT).show();


    }
    /** ================================================ */
    @Override
    public void onBatteryPowerScaned(BatteryPowerData batteryPowerData) {
        // TODO Auto-generated method stub
        Log.d("debug", batteryPowerData.batteryPower+"");
        for(int i = 0 ; i < miBeacons.size() ; i++)
        {
            if(miBeacons.get(i).macAddress.equals(batteryPowerData.macAddress))
            {
                ScanediBeacon ib = miBeacons.get(i);
                ib.batteryPower = batteryPowerData.batteryPower;
                miBeacons.set(i, ib);
            }
        }
    }

    /** ========================================================== */
    public void onResponse(int msg)
    {
        THLLog.d("debug", "Response("+ msg+ ")");
        mHandler.obtainMessage(MSG_SERVER_RESPONSE, msg, 0).sendToTarget();
    }

    public void showBeaconFromServerOnDialog(String sBeaconDate)//後台伺服器的連線
    {
        final AlertDialog dlg = new AlertDialog.Builder(BeaconMain.this).create();

        dlg.setTitle("Beacon from Server. " + QUERY_UUID);
        dlg.setMessage(sBeaconDate);

        dlg.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                dlg.dismiss();
            }
        });

        //dlg.show();
    }
    /** ========================================================== */

    public void dlgNetworkNotAvailable()//檢查是否有網路
    {
        final AlertDialog dlg = new AlertDialog.Builder(BeaconMain.this).create();

        dlg.setTitle("Network");
        dlg.setMessage("Please enable your network for updating beacon list.");

        dlg.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                dlg.dismiss();
            }
        });

        //dlg.show();
    }
    /** ========================================================== */
    public void dlgNetworkMobile()
    {
        final AlertDialog dlg = new AlertDialog.Builder(BeaconMain.this).create();

        dlg.setTitle("3G");
        dlg.setMessage("App will send/recv data via Mobile Internet, this may result in significant data charges.");

        // To check yes or no of using mobile Internet.
        dlg.setButton(AlertDialog.BUTTON_POSITIVE, "Allow", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                Config.allow3G= true;
                dlg.dismiss();
                USBeaconServerInfo info= new USBeaconServerInfo();

                info.serverUrl		= HTTP_API;
                info.queryUuid		= QUERY_UUID;
                info.downloadPath	= STORE_PATH;

                mBServer.setServerInfo(info, BeaconMain.this);
                mBServer.checkForUpdates();
            }
        });

        dlg.setButton(AlertDialog.BUTTON_NEGATIVE, "Reject", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                Config.allow3G= false;
                dlg.dismiss();
            }
        });

        dlg.show();
    }

    /** ========================================================== */
    public void addOrUpdateiBeacon(iBeaconData iBeacon)
    {
        long currTime= System.currentTimeMillis();

        ScanediBeacon beacon= null;

        for(ScanediBeacon b : miBeacons)
        {
            if(b.equals(iBeacon, false))
            {
                beacon= b;
                break;
            }
        }

        if(null == beacon)
        {
            beacon= ScanediBeacon.copyOf(iBeacon);
            miBeacons.add(beacon);
        }
        else
        {
            beacon.rssi= iBeacon.rssi;
        }

        beacon.lastUpdate= currTime;
    }

    /** ========================================================== */
    public void verifyiBeacons()
    {
        {
            long currTime	= System.currentTimeMillis();

            int len= miBeacons.size();
            ScanediBeacon beacon= null;

            for(int i= len- 1; 0 <= i; i--)
            {
                beacon= miBeacons.get(i);

                if(null != beacon && TIME_BEACON_TIMEOUT < (currTime- beacon.lastUpdate))
                {
                    miBeacons.remove(i);
                }
            }
        }

        {
            mListAdapter.clear();

            //Add beacon to the list that it could show on the screen.
            for(final ScanediBeacon beacon : miBeacons)
            {
                if(beacon.rssi >= -50){
                    condition = "dangerous";
                }
                else
                    condition = "safe";
                mListAdapter.addItem(new ListItem(beacon.beaconUuid.toString().toUpperCase(), ""+ beacon.major, ""+ beacon.minor, ""+ beacon.rssi,""+beacon.beaconUuid, ""+beacon.batteryPower, ""+beacon.macAddress,""+beacon.calDistance((double)beacon.oneMeterRssi,beacon.rssi), ""+condition));
                final EditText macaddress = new EditText(this);
                final EditText name = new EditText(this);
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder
                        .setTitle("請輸入欲註冊之Beacon Address ")
                        .setView(macaddress)
                        .setPositiveButton("確定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                                Editable strAddress;
                                strAddress = macaddress.getText();
                                String Address = strAddress.toString();
                                if(Address.equals(beacon.macAddress)){
                                    Toast.makeText(getApplicationContext(), "正確", Toast.LENGTH_SHORT).show();
									/*AlertDialog.Builder builder1 = new AlertDialog.Builder();
									builder1
											.setTitle("請輸入欲註冊之名稱")
											.setView(name)
											.setPositiveButton("確定", new DialogInterface.OnClickListener(){
												public void onClick(DialogInterface dialogInterface, int i){
													Editable strName;
													strName = name.getText();
													String Name = strName.toString();
													beacon.beaconUuid = new String(Name);
												}
											});*/
                                }
                                else{
                                    Toast.makeText(getApplicationContext(), "錯誤", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });

                final AlertDialog alert = builder.create();
            }
        }
    }


    /** ========================================================== */
    public void cleariBeacons()
    {
        mListAdapter.clear();
    }

}

/** ============================================================== */
class ListItem
{
    public String it3_text1= "";
    public String text5= "";
    String tV_mac = null;
    public String text7= "";
    public String tV_rssi = "";
    public String tV_condition = "";


    public ListItem(String toUpperCase, String text51, String tVMac, String s, String text1, String text5, String tV_mac, String text7,String tV_condition)
    {
        this.it3_text1= text1;
        this.text5= text5;
        this.tV_mac = tV_mac;
        this.tV_rssi = s;
        this.text7= text7;
        this.tV_condition = tV_condition;
    }
}

/** ============================================================== */
class BLEListAdapter extends BaseAdapter
{
    private Context mContext;

    List<ListItem> mListItems= new ArrayList<ListItem>();

    /** ================================================ */
    public BLEListAdapter(Context c) { mContext= c; }

    /** ================================================ */
    public int getCount() { return mListItems.size(); }

    /** ================================================ */
    public Object getItem(int position)
    {
        if((!mListItems.isEmpty()) && mListItems.size() > position)
        {
            return mListItems.toArray()[position];
        }

        return null;
    }

    public String getItemText(int position)
    {
        if((!mListItems.isEmpty()) && mListItems.size() > position)
        {
            return ((ListItem)mListItems.toArray()[position]).it3_text1;
        }

        return null;
    }

    /** ================================================ */
    public long getItemId(int position) { return 0; }

    /** ================================================ */
    // create a new ImageView for each item referenced by the Adapter
    public View getView(int position, View convertView, ViewGroup parent)
    {
        View view= (View)convertView;

        if(null == view)
            view= View.inflate(mContext, R.layout.item_text_3, null);

        // view.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

        if((!mListItems.isEmpty()) && mListItems.size() > position)
        {
            TextView text1	= (TextView)view.findViewById(R.id.it3_text1);
            TextView text5	= (TextView)view.findViewById(R.id.it3_text5);
            TextView tV_Mac	= (TextView)view.findViewById(R.id.tV_mac);
            TextView text7	= (TextView)view.findViewById(R.id.it3_text7);
            TextView tV_Rssi = (TextView)view.findViewById(R.id.tV_rssi);
            TextView tV_condition = (TextView)view.findViewById(R.id.tV_condition);

            ListItem item= (ListItem)mListItems.toArray()[position];

            text1.setText(item.it3_text1);
            text5.setText(item.text5+ " V");
            tV_Mac.setText(item.tV_mac);
            tV_Rssi.setText(item.tV_rssi);
            text7.setText(item.text7+ "m");
            tV_condition.setText(item.tV_condition);

        }
        else
        {
            view.setVisibility(View.GONE);
        }

        return view;
    }

    /** ================================================ */
    @Override
    public boolean isEnabled(int position)
    {
        if(mListItems.size() <= position)
            return false;

        return true;
    }

    /** ================================================ */
    public boolean addItem(ListItem item)
    {
        mListItems.add(item);
        return true;
    }

    /** ================================================ */
    public void clear()
    {
        mListItems.clear();
    }
}

/** ============================================================== */