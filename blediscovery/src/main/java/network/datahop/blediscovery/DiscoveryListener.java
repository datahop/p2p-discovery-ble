package network.datahop.blediscovery;

import java.util.UUID;

/**
 * Interface used to report events in the GATT server.
 */
public interface DiscoveryListener {

    void sameStatusDiscovered(UUID characteristic);
    void differentStatusDiscovered(byte[] value, UUID characteristic,String peerId);
}