package network.datahop.blediscovery;

import java.util.UUID;

/**
 * BLE characteristics constant values
 *
 */
public class Constants {


    public static String CLIENT_CONFIGURATION_DESCRIPTOR_STRING = "00002902-0000-1000-8000-00805f9b34fb";
    public static UUID CLIENT_CONFIGURATION_DESCRIPTOR_UUID = UUID.fromString(CLIENT_CONFIGURATION_DESCRIPTOR_STRING);

    public static final String CLIENT_CONFIGURATION_DESCRIPTOR_SHORT_ID = "2902";

    /* DataHop service Laptop */
    public static String CHARACTERISTIC_DATAHOP_STRING = "ffffffff-ffff-ffff-ffff-fffffffffff1";
    public static String CHARACTERISTIC_DATAHOP_DIRECT = "ffffffff-ffff-ffff-ffff-fffffffffff2";

    public static UUID CHARACTERISTIC_DATAHOP_UUID = UUID.fromString(CHARACTERISTIC_DATAHOP_STRING);
    public static UUID CHARACTERISTIC_DIRECT_UUID = UUID.fromString(CHARACTERISTIC_DATAHOP_DIRECT);


}
