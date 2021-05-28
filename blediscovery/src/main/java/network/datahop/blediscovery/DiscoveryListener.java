package network.datahop.blediscovery;

import java.util.UUID;

public interface DiscoveryListener {
    //void onNewContent(String msg, String group, int size, int latency);
    //void onUserDiscovered(String user, String address);
    void sameStatusDiscovered(UUID characteristic);
    void differentStatusDiscovered(byte[] value, UUID characteristic);
}