package network.datahop.blediscovery;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static network.datahop.blediscovery.Constants.CLIENT_CONFIGURATION_DESCRIPTOR_UUID;


/**
 * GattServerCallback extends BluetoothGattServerCallback and overwrites all functions required to
 * accept GATT connections and read/write characteristics.
 */
public class GattServerCallback extends BluetoothGattServerCallback {

    private BluetoothGattServer mGattServer;
    private List<BluetoothDevice> mDevices;
    private Map<String, byte[]> mClientConfigurations;

    String network, password;
    private Context mContext;
    BluetoothGattCharacteristic mCharacteristic;
    ParcelUuid mServiceUUID;
    private static final String TAG = "GattServerCallback";
    private HashMap<UUID, String> advertisingInfo;

    DiscoveryListener listener;

    public GattServerCallback(Context context, String parcelUuid, HashMap<UUID, String> advertisingInfo, DiscoveryListener listener) {//WifiDirectHotSpot hotspot, HashMap<UUID,ContentAdvertisement> ca, ParcelUuid service_uuid,StatsHandler stats,List<String> groups) {

        mDevices = new ArrayList<>();
        mClientConfigurations = new HashMap<>();
        mContext = context;

        network = null;
        mServiceUUID = new ParcelUuid(UUID.nameUUIDFromBytes(parcelUuid.getBytes()));
        this.advertisingInfo = advertisingInfo;
        Log.d(TAG, "Service uuid:" + mServiceUUID + " " + parcelUuid);
        this.listener = listener;

    }

    public void stop() {

        if(mGattServer!=null)mGattServer.close();
        network = password = null;
        mGattServer = null;
    }

    public void setServer(BluetoothGattServer gattServer) {
        mGattServer = gattServer;
    }


    @Override
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
        super.onConnectionStateChange(device, status, newState);

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            mDevices.add(device);
            //       int con = stats.getBtConnections();
            //       stats.setBtConnections(++con);
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            mDevices.remove(device);
            mClientConfigurations.remove(device.getAddress());
            mCharacteristic = null;
        }
    }

    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device,
                                            int requestId,
                                            int offset,
                                            BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicReadRequest(device, requestId, offset, characteristic);


        if (BluetoothUtils.requiresResponse(characteristic)) {
            // Unknown read characteristic requiring response, send failure
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
        }
    }

    @Override
    public void onCharacteristicWriteRequest(BluetoothDevice device,
                                             int requestId,
                                             BluetoothGattCharacteristic characteristic,
                                             boolean preparedWrite,
                                             boolean responseNeeded,
                                             int offset,
                                             byte[] value) {
        super.onCharacteristicWriteRequest(device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value);


        List<UUID> groups = new ArrayList<>();
        groups.addAll(advertisingInfo.keySet());

        Log.d(TAG, "onCharacteristicWriteRequest");
        if (BluetoothUtils.matchAnyCharacteristic(characteristic.getUuid(), groups)) {
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            String valueString = new String(value).split(":")[0];
            String peerId = new String(value).split(":")[1];
            String valueString2 = new String(advertisingInfo.get(characteristic.getUuid()));
            Log.d(TAG, "Characteristic check " + characteristic.getUuid().toString() + " " + network + " " + valueString2 + " " + valueString);
            if (!valueString.equals(valueString2)) {
                Log.d(TAG, "Connecting");
                listener.differentStatusDiscovered(value,characteristic.getUuid(),peerId);

            } else {
                Log.d(TAG, "Not Connecting");
                listener.sameStatusDiscovered(characteristic.getUuid());
            }

        }

    }


    @Override
    public void onDescriptorReadRequest(BluetoothDevice device,
                                        int requestId,
                                        int offset,
                                        BluetoothGattDescriptor descriptor) {
        super.onDescriptorReadRequest(device, requestId, offset, descriptor);
    }

    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device,
                                         int requestId,
                                         BluetoothGattDescriptor descriptor,
                                         boolean preparedWrite,
                                         boolean responseNeeded,
                                         int offset,
                                         byte[] value) {
        super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
        Log.d(TAG,"onDescriptorWriteRequest");
        if (CLIENT_CONFIGURATION_DESCRIPTOR_UUID.equals(descriptor.getUuid())) {
            Log.d(TAG,"onDescriptorWriteRequest");
            mClientConfigurations.put(device.getAddress(), value);
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
        }
    }

    @Override
    public void onNotificationSent(BluetoothDevice device, int status) {
        super.onNotificationSent(device, status);
    }

    public void notifyCharacteristic(byte[] value, UUID uuid) {
        BluetoothGattService service = mGattServer.getService(mServiceUUID.getUuid());
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);

        characteristic.setValue(value);
        boolean confirm = BluetoothUtils.requiresConfirmation(characteristic);
        for (BluetoothDevice device : mDevices) {
            if (clientEnabledNotifications(device, characteristic)) {
                mGattServer.notifyCharacteristicChanged(device, characteristic, confirm);
            }
        }
    }

    private boolean clientEnabledNotifications(BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
        List<BluetoothGattDescriptor> descriptorList = characteristic.getDescriptors();
        BluetoothGattDescriptor descriptor = BluetoothUtils.findClientConfigurationDescriptor(descriptorList);
        if (descriptor == null) {
            return true;
        }
        String deviceAddress = device.getAddress();
        byte[] clientConfiguration = mClientConfigurations.get(deviceAddress);
        if (clientConfiguration == null) {
            return false;
        }

        byte[] notificationEnabled = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        return clientConfiguration.length == notificationEnabled.length
                && (clientConfiguration[0] & notificationEnabled[0]) == notificationEnabled[0]
                && (clientConfiguration[1] & notificationEnabled[1]) == notificationEnabled[1];
    }

}
