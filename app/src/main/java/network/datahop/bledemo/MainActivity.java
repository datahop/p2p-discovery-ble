package network.datahop.bledemo;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import java.util.Random;
import java.util.UUID;

import datahop.AdvertisementNotifier;
import datahop.DiscoveryNotifier;


import network.datahop.blediscovery.BLEAdvertising;
import network.datahop.blediscovery.BLEServiceDiscovery;

public class MainActivity extends AppCompatActivity implements AdvertisementNotifier, DiscoveryNotifier {


    private Button startButton,stopButton,refreshButton;

    private TextView status, discovery;
    private BLEAdvertising advertisingDriver;
    private BLEServiceDiscovery discoveryDriver;

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int PERMISSION_WIFI_STATE = 2;

    private static final String TAG = "BleDemo";

    private String stat;

    private int counter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        counter=0;
        advertisingDriver = BLEAdvertising.getInstance(getApplicationContext());
        discoveryDriver = BLEServiceDiscovery.getInstance(getApplicationContext());
        advertisingDriver.setNotifier(this);
        discoveryDriver.setNotifier(this);

        startButton = (Button) findViewById(R.id.startbutton);
        stopButton = (Button) findViewById(R.id.stopbutton);
        refreshButton = (Button) findViewById(R.id.refreshbutton);

        status = (TextView) findViewById(R.id.textview_status);
        discovery = (TextView) findViewById(R.id.textview_discovery);

        discovery.setText("Users discovered: "+counter);
        requestForPermissions();
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stat = randomString();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status.setText("Status: "+stat);
                    }
                });
                advertisingDriver.addAdvertisingInfo("bledemo",stat);
                discoveryDriver.addAdvertisingInfo("bledemo",stat);
                advertisingDriver.start(TAG);
                discoveryDriver.start(TAG,2000,30000);
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                advertisingDriver.stop();
                discoveryDriver.stop();
            }
        });

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //advertisingDriver.addAdvertisingInfo();

                stat = randomString();
                status.setText("Status: "+stat);

                advertisingDriver.addAdvertisingInfo("bledemo",stat);
                discoveryDriver.addAdvertisingInfo("bledemo",stat);
                advertisingDriver.stop();
                advertisingDriver.start(TAG);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        advertisingDriver.stop();
        discoveryDriver.stop();
        super.onDestroy();
    }


    private void requestForPermissions() {
        if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                }
            });
            builder.show();
        }
        if (this.checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.CHANGE_WIFI_STATE}, PERMISSION_WIFI_STATE);
                }
            });
            builder.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        Log.d(TAG, "Permissions " + requestCode + " " + permissions + " " + grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.d(TAG, "Location accepted");
                    //timers.setLocationPermission(true);
                    //if(timers.getStoragePermission())startService();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.d(TAG, "Location not accepted");

                }
                break;
            }

        }

        // other 'case' lines to check for other
        // permissions this app might request.

    }

    @Override
    public void advertiserPeerDifferentStatus(String topic, byte[] bytes) {
        Log.d(TAG,"differentStatusDiscovered");
        advertisingDriver.notifyNetworkInformation(stat,stat,stat);

    }

    @Override
    public void advertiserPeerSameStatus() {
        Log.d(TAG,"sameStatusDiscovered");
        advertisingDriver.notifyEmptyValue();

    }

    @Override
    public void discoveryPeerDifferentStatus(String device, String topic, String network, String pass, String info) {
        Log.d(TAG,"peerDifferentStatusDiscovered "+device+" "+topic+" "+network+" "+pass+" "+info);
        stat = network;
        counter++;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                discovery.setText("Users discovered: "+counter);
                status.setText("Status: "+stat);
            }
        });

        advertisingDriver.addAdvertisingInfo("bledemo",stat);
        discoveryDriver.addAdvertisingInfo("bledemo",stat);
        advertisingDriver.stop();
        advertisingDriver.start(TAG);
    }

    /*@Override
    public void peerDiscovered(String s) {
        Log.d(TAG,"peerDiscovered "+s);
    }*/

    @Override
    public void discoveryPeerSameStatus(String device, String topic) {
        Log.d(TAG,"peerSameStatusDiscovered "+device+" "+topic);

    }


    public String randomString() {

        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(targetStringLength);
        for (int i = 0; i < targetStringLength; i++) {
            int randomLimitedInt = leftLimit + (int)
                    (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();

    }

}