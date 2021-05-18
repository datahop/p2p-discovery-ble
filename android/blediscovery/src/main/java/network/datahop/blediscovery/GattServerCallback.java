/*******************************************************
 * Copyright (C) 2020 DataHop Labs Ltd <sergi@datahop.network>
 *
 * This file is part of DataHop Network project.
 *
 * All rights reserved
 *******************************************************/

package network.datahop.blediscovery;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static network.datahop.datahopdemo.net.ble.Constants.CLIENT_CONFIGURATION_DESCRIPTOR_UUID;

public class GattServerCallback extends BluetoothGattServerCallback {

    private BluetoothGattServer mGattServer;
    private List<BluetoothDevice> mDevices;
    private Map<String, byte[]> mClientConfigurations;

    String network, password;
    //    WifiDirectHotSpot hotspot;
    private Context mContext;
    //   HashMap<UUID,ContentAdvertisement> ca;
    BluetoothGattCharacteristic mCharacteristic;
    //DataHopConnectivityService service;
    ParcelUuid mServiceUUID;
    private static final String TAG = "GattServerCallback";
    //  StatsHandler stats;
    //ContentDatabaseHandler db;
    private HashMap<UUID, byte[]> advertisingInfo;

    BroadcastReceiver mBroadcastReceiver;

    DiscoveryListener listener;

    public GattServerCallback(Context context, String parcelUuid, HashMap<UUID, byte[]> advertisingInfo, DiscoveryListener listener) {//WifiDirectHotSpot hotspot, HashMap<UUID,ContentAdvertisement> ca, ParcelUuid service_uuid,StatsHandler stats,List<String> groups) {

        mDevices = new ArrayList<>();
        mClientConfigurations = new HashMap<>();
        mContext = context;
        //      this.hotspot = hotspot;
        //    this.ca = ca;
        //db = new ContentDatabaseHandler(context);
        network = null;
        mServiceUUID = new ParcelUuid(UUID.nameUUIDFromBytes(parcelUuid.getBytes()));
        this.advertisingInfo = advertisingInfo;
        Log.d(TAG, "Service uuid:" + mServiceUUID + " " + parcelUuid);
        this.listener = listener;

    }

    public void stop() {
        mGattServer.close();
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
            //      if(ca.get(characteristic.getUuid()).connectFilter(value)&&network!=null) {
            String valueString = new String(value);
            String valueString2 = new String(advertisingInfo.get(characteristic.getUuid()));
            Log.d(TAG, "Characteristic check " + characteristic.getUuid().toString() + " " + network + " " + valueString2 + " " + valueString);
            if (!valueString.equals(valueString2)) {
                Log.d(TAG, "Connecting");
                listener.differentStatusDiscovered(value);
                /*hotspot.start(new WifiDirectHotSpot.StartStopListener() {
                    public void onSuccess() {
                        Log.d(TAG, "Hotspot started");
                    }

                    public void onFailure(int reason) {
                        Log.d(TAG, "Hotspot started failed, error code " + reason);
                    }
                });
                hotspot.startConnection();
                mCharacteristic = characteristic;
                Random rn = new Random();
                int range = 254 - 2 + 1;
                int ip =  rn.nextInt(range) + 2;
                byte[] response = (network+":"+password+":"+Config.ip+"."+String.valueOf(ip)).getBytes();
                characteristic.setValue(response);
                notifyCharacteristic(response, characteristic.getUuid());*/
            } else {
                Log.d(TAG, "Not Connecting");
                listener.sameStatusDiscovered();
            }

        }
        /*if(BluetoothUtils.matchDirectConnectionCharacteristic(characteristic))
        {
            Log.d(TAG,"Direct connection request");
            LocalBroadcastManager.getInstance(mContext).registerReceiver(mBroadcastReceiver,getIntentFilter());

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            String action = DIRECT_CONNECTION;
            Intent broadcast = new Intent(action)
                    .putExtra("user", new String(value));
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);

        }*/
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

    /*public static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(DIRECT_CONNECTION_ACCEPTED);
        filter.addAction(DIRECT_CONNECTION_REJECTED);
        return filter;
    }*/
}
