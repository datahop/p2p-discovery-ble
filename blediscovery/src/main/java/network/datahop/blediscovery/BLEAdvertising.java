package network.datahop.blediscovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import datahop.AdvertisingDriver;
import datahop.AdvertisementNotifier;

import static android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_BALANCED;
import static android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
import static android.content.Context.BLUETOOTH_SERVICE;

import javax.crypto.SecretKey;

/**
 * BLEAdvertising class is used for service discovery using Bluetooth Low Energy beacons.
 * BLEAdvertising is responsible of advertising service discovery data and exchange service status
 * using GATT server and GATT characteristics. Advertised data for each service is structured in "topics"
 * and each topic is configured as a BLE characteristic in the GATT server.
 * Characteristics are compared in the GATT Server when accepting connections to compare status for each "topic.
 * When detected different values of the "topics" means different service status and it can reply with network information.
 */
public class BLEAdvertising  implements AdvertisingDriver{

    private static final String TAG = BLEAdvertising.class.getSimpleName();
    private BluetoothLeAdvertiser adv;
    private AdvertiseCallback advertiseCallback;
    private BluetoothManager manager;
    private BluetoothAdapter btAdapter;
    private GattServerCallback serverCallback;
    private BluetoothGattServer mBluetoothGattServer;
    private HashMap<UUID,String> advertisingInfo;
    private HashMap<UUID,String> convertedCharacteristics;

    private static volatile BLEAdvertising mBleAdvertising;
    private Context context;

    private static AdvertisementNotifier notifier;

    private List<UUID> pendingNotifications;


    private String serviceId;

    private boolean started;

    private String peerInfo;

    //private SecretKey key;
    //private byte[] salt;
    private String password;
    /**
     * BLEAdvertising class constructor
     * @param context Android context
     */
    private BLEAdvertising(Context context){
        Log.d(TAG,"New bleadvertising");
        this.manager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
        this.btAdapter = manager.getAdapter();
        this.context = context;
        this.advertisingInfo = new HashMap<>();
        this.convertedCharacteristics = new HashMap<>();
        this.pendingNotifications = new ArrayList<>();
    }

    /* Singleton method that creates and returns a BLEAdvertising instance
     * @return BLEAdvertising instance
     */
    public static synchronized BLEAdvertising getInstance(Context appContext) {
        if (mBleAdvertising == null) {
            mBleAdvertising = new BLEAdvertising(appContext);
        }
        return mBleAdvertising;
    }

    /**
     * Set the notifier that receives the events advertised
     * when creating or destroying the group or when receiving users connections
     * @param notifier instance
     */
    public void setNotifier(AdvertisementNotifier notifier){
        //Log.d(TAG,"Trying to start");
        this.notifier = notifier;
    }

    /**
     * Set the notifier that receives the events advertised
     * when creating or destroying the group or when receiving users connections
     * @param key private encryption key

    public void setKey(SecretKey key,byte[] salt){
        Log.d(TAG,"Trying to start");
        this.key = key;
        this.salt = salt;
    }*/

    /**
     * Set the encryption passphrase
     * @param password passphrase used for encrypting
     */
    public void setPassword(String password){
        this.password = password;
    }

    /**
     * This method configures AdvertiseSettings, starts advertising via BluetoothLeAdvertiser
     * and starts the GATT server
     * @param serviceId service id
     * @param peerInfo peer identifier
     */
    public void start(String serviceId, String peerInfo) {
        this.serviceId = serviceId;
        this.peerInfo = peerInfo;
        Log.d(TAG, "Starting ADV, Tx power " + this.serviceId.toString());

        if (notifier == null || this.serviceId == null) {
            Log.e(TAG, "notifier not found");
            return ;
        }
        if (btAdapter != null) {
            if (btAdapter.isMultipleAdvertisementSupported()) {
                Log.d(TAG, "Starting ADV2, Tx power " + this.serviceId.toString());
                adv = btAdapter.getBluetoothLeAdvertiser();
                advertiseCallback = createAdvertiseCallback();
                ParcelUuid mServiceUUID = new ParcelUuid(UUID.nameUUIDFromBytes(this.serviceId.getBytes()));

                AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                        .setAdvertiseMode(ADVERTISE_MODE_BALANCED)
                        .setTxPowerLevel(ADVERTISE_TX_POWER_MEDIUM)
                        .setConnectable(true)
                        .build();

                AdvertiseData advertiseData = new AdvertiseData.Builder()
                        //          .addManufacturerData(0, stats.getUserName().getBytes())
                        .addServiceUuid(mServiceUUID)
                        .setIncludeTxPowerLevel(false)
                        .setIncludeDeviceName(false)
                        .build();
                adv.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
            }
        }
        startGATTServer(this.serviceId);
        started=true;
    }

    /**
     * This method starts the GATT server
     * @param serviceid service id
     */
    private void startGATTServer(String serviceid){
        Log.d(TAG, "startGATTServer");

        serverCallback = new GattServerCallback(context, serviceid, advertisingInfo, new DiscoveryListener() {
            @Override
            public void sameStatusDiscovered(UUID characteristic) {
                pendingNotifications.add(characteristic);
                notifier.advertiserPeerSameStatus();
            }

            @Override
            public void differentStatusDiscovered(byte[] value,UUID characteristic,String peerId) {
                pendingNotifications.add(characteristic);
                notifier.advertiserPeerDifferentStatus(convertedCharacteristics.get(characteristic),value,peerId);
            }
        });
        mBluetoothGattServer = manager.openGattServer(context, serverCallback);
        serverCallback.setServer(mBluetoothGattServer);

        if (mBluetoothGattServer == null) {
            Log.d(TAG, "Unable to create GATT server");
            return;
        }


        ParcelUuid SERVICE_UUID = new ParcelUuid(UUID.nameUUIDFromBytes(serviceid.getBytes()));
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID.getUuid(), BluetoothGattService.SERVICE_TYPE_PRIMARY);

        for(UUID uuid:advertisingInfo.keySet()){
            Log.d(TAG, "Advertising characteristic " + uuid.toString());
            BluetoothGattCharacteristic writeCharacteristic = new BluetoothGattCharacteristic(
                    uuid,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
            service.addCharacteristic(writeCharacteristic);
        }
        mBluetoothGattServer.addService(service);

    }

    /**
     * This method stops the advertising service
     */
    @Override
    public void stop() {
        started=false;
        Log.d(TAG, "Stopping ADV");
        if(adv!=null)adv.stopAdvertising(advertiseCallback);
        if(serverCallback!=null)serverCallback.stop();
    }

    /**
     * This method adds advertising information value for the specified "topic". In case "topic"
     * already exists information is updated
     * @param topic topic id
     * @param info value advertised
     */
    @Override
    public void addAdvertisingInfo(String topic, String info){
        //String inf = new String(info);
        Log.d(TAG,"Advertising info "+topic+" "+info);

        if(advertisingInfo.get(UUID.nameUUIDFromBytes(topic.getBytes()))!=null) {
            Log.d(TAG,"Advertising info not null "+advertisingInfo.get(topic)+" "+info);
            if (advertisingInfo.get(UUID.nameUUIDFromBytes(topic.getBytes())).equals(info)) {
                Log.d(TAG,"Advertising info equal");
                return;
            }
        }
        advertisingInfo.put(UUID.nameUUIDFromBytes(topic.getBytes()), info);
        convertedCharacteristics.put(UUID.nameUUIDFromBytes(topic.getBytes()), topic);
        if(started)restart();



    }

    /**
     * This method can be used to notify network information (SSID, password, node info) when detected different "topic" status
     * @param network SSID
     * @param password
     */
    @Override
    public void notifyNetworkInformation(String network, String pass){

        String msg = network+":"+pass+":"+peerInfo;
        if(password!=null) {
            try {
                String encMsg = Encryption.encrypt(msg,password);
                for(UUID characteristic : pendingNotifications)
                    serverCallback.notifyCharacteristic(encMsg.getBytes(), characteristic);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            for(UUID characteristic : pendingNotifications)
                serverCallback.notifyCharacteristic(msg.getBytes(), characteristic);
        }

    }

    /**
     * This method can be used to notify no different "topic" status is detected.
     */
    @Override
    public void notifyEmptyValue(){
        for(UUID characteristic : pendingNotifications)
            serverCallback.notifyCharacteristic(new byte[]{0x00}, characteristic);
    }


    private void restart()  {
        //try {
            stop();
            //sleep(3);
            start(this.serviceId,this.peerInfo);
        /*} catch (InterruptedException e) {
            e.printStackTrace();
        }*/

    }


    private AdvertiseCallback createAdvertiseCallback() {
        return new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {

                switch (errorCode) {
                    case ADVERTISE_FAILED_DATA_TOO_LARGE:
                        Log.d(TAG,"ADVERTISE_FAILED_DATA_TOO_LARGE");
                        break;
                    case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                        Log.d(TAG,"ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
                        break;
                    case ADVERTISE_FAILED_ALREADY_STARTED:
                        Log.d(TAG, "ADVERTISE_FAILED_ALREADY_STARTED");
                        break;
                    case ADVERTISE_FAILED_INTERNAL_ERROR:
                        Log.d(TAG, "ADVERTISE_FAILED_INTERNAL_ERROR");
                        break;
                    case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                        Log.d(TAG, "ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
                        break;
                    default:
                        Log.d(TAG, "startAdvertising failed with unknown error " + errorCode);
                        break;
                }
            }
        };
    }

}
