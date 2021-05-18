/*******************************************************
 * Copyright (C) 2020 DataHop Labs Ltd <sergi@datahop.network>
 *
 * This file is part of DataHop Network project.
 *
 * All rights reserved
 *******************************************************/

package network.datahop.blediscovery;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static network.datahop.datahopdemo.net.ble.Constants.CHARACTERISTIC_DATAHOP_DIRECT;
import static network.datahop.datahopdemo.net.ble.Constants.CHARACTERISTIC_DATAHOP_STRING;
import static network.datahop.datahopdemo.net.ble.Constants.CLIENT_CONFIGURATION_DESCRIPTOR_SHORT_ID;


public class BluetoothUtils {

    // Characteristics
    private static final String TAG="BluetoothUtils";

    public static List<BluetoothGattCharacteristic> findCharacteristics(BluetoothGatt bluetoothGatt, UUID SERVICE_UUID,List<UUID> groups) {
        List<BluetoothGattCharacteristic> matchingCharacteristics = new ArrayList<>();

        List<BluetoothGattService> serviceList = bluetoothGatt.getServices();
        BluetoothGattService service = BluetoothUtils.findService(serviceList,SERVICE_UUID);
        if (service == null) {
            return matchingCharacteristics;
        }
        List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();

        for (UUID group : groups)
        {
            String CHARACTERISTIC_UUID = group.toString();
            for (BluetoothGattCharacteristic characteristic : characteristicList) {
                Log.d(TAG,"Characteristic "+characteristic.getUuid().toString()+" "+CHARACTERISTIC_UUID);
                if(uuidMatches(characteristic.getUuid().toString(), CHARACTERISTIC_UUID))
                {
                //if (isMatchingCharacteristic(characteristic)) {
                    matchingCharacteristics.add(characteristic);
                }
            }
        }

        return matchingCharacteristics;
    }

    public static boolean matchAnyCharacteristic(UUID SERVICE_UUID,List<UUID> groups) {

        for (UUID group : groups)
        {
            String CHARACTERISTIC_UUID = group.toString();
            if(uuidMatches(SERVICE_UUID.toString(), CHARACTERISTIC_UUID))
            {
                //if (isMatchingCharacteristic(characteristic)) {
                return true;
            }

        }

        return false;
    }

    public static boolean matchDirectConnectionCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        return characteristicMatches(characteristic, CHARACTERISTIC_DATAHOP_DIRECT);
    }

    public static List<BluetoothGattCharacteristic> findCharacteristics(BluetoothGatt bluetoothGatt, UUID SERVICE_UUID) {
        List<BluetoothGattCharacteristic> matchingCharacteristics = new ArrayList<>();

        List<BluetoothGattService> serviceList = bluetoothGatt.getServices();
        BluetoothGattService service = BluetoothUtils.findService(serviceList,SERVICE_UUID);
        if (service == null) {
            return matchingCharacteristics;
        }

        List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
        for (BluetoothGattCharacteristic characteristic : characteristicList) {
            Log.d(TAG,"Characteristic "+characteristic.getUuid().toString());

            if (isMatchingCharacteristic(characteristic)) {
                matchingCharacteristics.add(characteristic);
            }
        }

        return matchingCharacteristics;
    }

    @Nullable
    public static BluetoothGattCharacteristic findDataHopCharacteristic(BluetoothGatt bluetoothGatt, UUID SERVICE_UUID) {
        return findCharacteristic(bluetoothGatt, CHARACTERISTIC_DATAHOP_STRING, SERVICE_UUID);
    }

    @Nullable
    public static BluetoothGattCharacteristic findDirectCharacteristic(BluetoothGatt bluetoothGatt, UUID SERVICE_UUID) {
        return findCharacteristic(bluetoothGatt, CHARACTERISTIC_DATAHOP_DIRECT, SERVICE_UUID);
    }

    @Nullable
    private static BluetoothGattCharacteristic findCharacteristic(BluetoothGatt bluetoothGatt, String uuidString, UUID SERVICE_UUID) {
        List<BluetoothGattService> serviceList = bluetoothGatt.getServices();
        BluetoothGattService service = BluetoothUtils.findService(serviceList,SERVICE_UUID);
        if (service == null) {
            return null;
        }

        List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
        for (BluetoothGattCharacteristic characteristic : characteristicList) {
            if (characteristicMatches(characteristic, uuidString)) {
                return characteristic;
            }
        }

        return null;
    }

    public static boolean isDataHopCharacteristic(BluetoothGattCharacteristic characteristic) {
        return characteristicMatches(characteristic, CHARACTERISTIC_DATAHOP_STRING);
    }

    private static boolean characteristicMatches(BluetoothGattCharacteristic characteristic, String uuidString) {
        if (characteristic == null) {
            return false;
        }
        UUID uuid = characteristic.getUuid();
        return uuidMatches(uuid.toString(), uuidString);
    }

    private static boolean isMatchingCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return false;
        }
        UUID uuid = characteristic.getUuid();
        return matchesCharacteristicUuidString(uuid.toString());
    }

    private static boolean matchesCharacteristicUuidString(String characteristicIdString) {
        return uuidMatches(characteristicIdString, CHARACTERISTIC_DATAHOP_STRING);
    }

    public static boolean requiresResponse(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
                != BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
    }

    public static boolean requiresConfirmation(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE)
                == BluetoothGattCharacteristic.PROPERTY_INDICATE;
    }

    // Descriptor

    @Nullable
    public static BluetoothGattDescriptor findClientConfigurationDescriptor(List<BluetoothGattDescriptor> descriptorList) {
        for(BluetoothGattDescriptor descriptor : descriptorList) {
            if (isClientConfigurationDescriptor(descriptor)) {
                return descriptor;
            }
        }

        return null;
    }

    private static boolean isClientConfigurationDescriptor(BluetoothGattDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        UUID uuid = descriptor.getUuid();
        String uuidSubstring = uuid.toString().substring(4, 8);
        return uuidMatches(uuidSubstring, CLIENT_CONFIGURATION_DESCRIPTOR_SHORT_ID);
    }

    // Service

    private static boolean matchesServiceUuidString(String serviceIdString, UUID SERVICE_UUID) {
        return uuidMatches(serviceIdString, SERVICE_UUID.toString());
    }

    @Nullable
    private static BluetoothGattService findService(List<BluetoothGattService> serviceList, UUID SERVICE_UUID) {
        for (BluetoothGattService service : serviceList) {
            String serviceIdString = service.getUuid()
                    .toString();
            if (matchesServiceUuidString(serviceIdString,SERVICE_UUID)) {
                return service;
            }
        }
        return null;
    }

    // String matching

    // If manually filtering, substring to match:
    // 0000XXXX-0000-0000-0000-000000000000
    private static boolean uuidMatches(String uuidString, String... matches) {
        for (String match : matches) {
            if (uuidString.equalsIgnoreCase(match)) {
                return true;
            }
        }

        return false;
    }
}
