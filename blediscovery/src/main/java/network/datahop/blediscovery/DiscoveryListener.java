package network.datahop.blediscovery;

public interface DiscoveryListener {
    //void onNewContent(String msg, String group, int size, int latency);
    //void onUserDiscovered(String user, String address);
    void sameStatusDiscovered();
    void differentStatusDiscovered(byte[] value);
}