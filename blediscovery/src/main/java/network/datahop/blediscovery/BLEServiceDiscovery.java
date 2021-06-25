package network.datahop.blediscovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import datahop.DiscoveryNotifier;
import datahop.DiscoveryDriver;

import static android.content.Context.BLUETOOTH_SERVICE;
import static java.lang.Thread.sleep;

/**
 * BLEServiceDiscovery class is used for service discovery using Bluetooth Low Energy (BLE) beacons.
 * BLEServiceDiscovery is responsible of scanning for BLE Beacons and starts a connection to the GATT server
 * when found BLE Beacons with the same service id.
 * Characteristics are compared in the GATT Server when accepting connections to compare status for each "topic".
 * When detected different values of the "topics" it receives network information from the server.
 */
public class BLEServiceDiscovery implements DiscoveryDriver{

	private static final String TAG = "BLEServiceDiscovery";

	private static Context context;

	private static volatile BLEServiceDiscovery mBleDiscovery;

	/* Bluetooth API */
	private static BluetoothManager mBluetoothManager;
	private static BluetoothAdapter mBluetoothAdapter;
	private static BluetoothGatt mBluetoothGatt;
	private int mConnectionState = STATE_DISCONNECTED;

	private static final int STATE_DISCONNECTED = 0;
	private static final int STATE_CONNECTING = 1;
	private static final int STATE_CONNECTED = 2;

	private HashMap<UUID,byte[]> advertisingInfo;
	private HashMap<UUID,String> convertedCharacteristics;
	private Set<BluetoothDevice> results;

	private boolean started=false;
	private static BluetoothLeScanner mLEScanner;

	private boolean mInitialized = false;

	private BluetoothDevice device=null;
	private ParcelUuid mServiceUUID;
	private int pendingWrite;
	private boolean sending;

	private Handler mHandler;

	private static DiscoveryNotifier notifier;
	private  boolean exit;

	/**
	 * BLEServiceDiscovery class constructor
	 * @param Android context
	 */
	private BLEServiceDiscovery(Context context)
	{
		this.context = context;
		this.mHandler = new Handler(Looper.getMainLooper());
		this.advertisingInfo = new HashMap<>();
		this.convertedCharacteristics = new HashMap<>();
		this.results = new HashSet<BluetoothDevice>();
    }

	/* Method used to initialize Bluetooth LE Adapter
	 */
	private static synchronized void initDriver(){
		mBluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if(mBluetoothAdapter!=null)mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
	}

	/* Singleton method that creates and returns a BLEServiceDiscovery instance
	 * @return BLEServiceDiscovery instance
	 */
	public static synchronized BLEServiceDiscovery getInstance(Context appContext) {
		if (mBleDiscovery == null) {
			mBleDiscovery = new BLEServiceDiscovery(appContext);
			initDriver();
		}
		return mBleDiscovery;
	}

	/**
	 * Set the notifier that receives the events advertised
	 * when creating or destroying the group or when receiving users connections
	 * @param notifier instance
	 */
	public void setNotifier(DiscoveryNotifier notifier){
		Log.d(TAG,"Trying to start");
		this.notifier = notifier;
	}

	/**
	 * This method starts the service and periodically scans for users and tries to connect to them
	 * @param service_uuid service id
	 * @param scanTime duration of the scanning phase
	 * @param idleTime idle time before starting another scan cycle
	 */
	@Override
	public void start(String service_uuid,long scanTime, long idleTime) {
		if (notifier == null) {
			Log.e(TAG, "notifier not found");
			return ;
		}
		exit=false;
		startScanning(service_uuid);
		Handler handler = new Handler(Looper.getMainLooper());
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				Log.d(TAG, "Stop scan");
				stopScanning();
				tryConnection();
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						if(!exit){
							Log.d(TAG,"Start service");
							start(service_uuid,scanTime,idleTime);
						}
					}
				}, idleTime);
			}
		}, scanTime);
	}


	/**
	 * Stops the discovery service
	 */
	@Override
	public void stop()
	{
		Log.d(TAG,"Stop");
		exit=true;
		stopScanning();
	}

	/**
	 * This method adds advertising information value for the specified "topic". In case "topic"
	 * already exists information is updated
	 * @param topic topic id
	 * @param info value advertised
	 */
	@Override
	public void addAdvertisingInfo(String characteristic, byte[] info){
		String inf = new String(info);
		Log.d(TAG,"addAdvertisingInfo "+characteristic+" "+inf);
		advertisingInfo.put(UUID.nameUUIDFromBytes(characteristic.getBytes()),info);
		convertedCharacteristics.put(UUID.nameUUIDFromBytes(characteristic.getBytes()),characteristic);
	}

	private void startScanning(String service_uuid)
	{
		Log.d(TAG,"startScanning Service uuid:"+service_uuid+" "+started);
		if(mBluetoothAdapter!=null&&!started) {
			pendingWrite = 0;
			sending = false;
			started = true;
			mServiceUUID =new ParcelUuid(UUID.nameUUIDFromBytes(service_uuid.getBytes()));
			results.clear();
			mConnectionState = STATE_DISCONNECTED;

			if (!mBluetoothAdapter.isEnabled()) {
				Log.d(TAG, "Bluetooth is currently disabled...enabling ");
				mBluetoothAdapter.enable();
			} else {
				Log.d(TAG, "Bluetooth enabled...starting services");

			}

			scanLeDevice();
		}
    }

	private void stopScanning()
	{
		try {
			mLEScanner.stopScan(mScanCallback);
			mLEScanner.flushPendingScanResults(mScanCallback);
		}catch (Exception e){Log.d(TAG,"Failed when stopping ble scanner "+e);}
	}

	private void scanLeDevice() {

		Log.d(TAG, "Start scan "+mConnectionState+" "+mServiceUUID);

		results.clear();
        mConnectionState=STATE_DISCONNECTED;
		ScanSettings settings = new ScanSettings.Builder()
				.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
				.build();
		List<ScanFilter> filters = new ArrayList<ScanFilter>();
			// Stops scanning after a pre-defined scan period.
		ScanFilter scanFilter = new ScanFilter.Builder()
				.setServiceUuid(mServiceUUID)
		//		.setDeviceName("BLEDataHop")
				.build();
/*		ScanFilter scanFilter2 = new ScanFilter.Builder()
				.setServiceUuid(mServiceUUID)
				//.setDeviceName("BLEDataHop")
				.build();*/
		filters.add(scanFilter);
	//	filters.add(scanFilter2);


		if(mConnectionState==STATE_DISCONNECTED)
		{
		    try {
				Log.d(TAG, "Scanning");
                mLEScanner.startScan(filters, settings, mScanCallback);
            }catch (IllegalStateException e){Log.d(TAG,"Exception "+e);}
		}

	}

	private ScanCallback mScanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {

			Log.d(TAG,"Scan result "+result.getScanRecord().getDeviceName());//+" "+result.getScanRecord().getServiceUuids()+" "+new String(result.getScanRecord().getManufacturerSpecificData(0)));

			results.add(result.getDevice());
		}

		@Override
		public void onBatchScanResults(List<ScanResult> results) {
			for (ScanResult sr : results) {
				Log.d(TAG,"ScanResult - Results " +sr.toString());
			}
		}

		@Override
		public void onScanFailed(int errorCode) {
			Log.d(TAG,"Scan Failed Error Code: " + errorCode);
			if(errorCode==2){
				mBluetoothAdapter.disable();
				mBluetoothAdapter.enable();
			}
			try{ sleep(2000);}catch (Exception e){}
			if(!started){
				Log.d(TAG,"Not started cancelling");
				return;
			}
			scanLeDevice();
		}
	};

	private void tryConnection(){
		Log.d(TAG,"TryConnection "+results.size()+" "+started+" "+mConnectionState);
		if (mConnectionState == STATE_DISCONNECTED) {

			for (BluetoothDevice res : results) {
				//Log.d(TAG,"Connections "+res.getAddress()+" "+res.getAddress().hashCode()+" "+res.getUuids());
				if (connect(res.getAddress())) {
					Log.d(TAG,"Connect to "+res.getAddress());
					results.remove(res);
					break;
				}
			}
		}
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 *
	 * @param address The device address of the destination device.
	 *
	 * @return Return true if the connection is initiated successfully. The connection result
	 *         is reported asynchronously through the
	 *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 *         callback.
	 */
	private boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			Log.d(TAG, "BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

		List<BluetoothDevice> devices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
		for (BluetoothDevice d : devices) {
			if (d.getAddress().equals(address)) {
				Log.d(TAG, "Device Connected");
				return true;
			}
		}

		device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Log.d(TAG, "Device not found.  Unable to connect.");
			return false;
		}
		// We want to directly connect to the device, so we are setting the autoConnect
		// parameter to false.
		mBluetoothGatt = device.connectGatt(context, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection to "+address);
		mConnectionState = STATE_CONNECTING;
		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The disconnection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	private void disconnect() {
		Log.d(TAG,"Disconnect");
		mInitialized = false;
		started=false;
		mConnectionState = STATE_DISCONNECTED;
		if (mBluetoothAdapter == null ) {
			Log.d(TAG, "BluetoothAdapter not initialized");
			return;
		}
		if (mBluetoothGatt == null ) {
			Log.d(TAG, "mBluetoothGatt not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
		mBluetoothGatt.close();
		mBluetoothGatt = null;
		//try{LocalBroadcastManager.getInstance(context).unregisterReceiver(mBroadcastReceiver);}catch (IllegalArgumentException e){Log.d(TAG,"Unregister failed "+e);}
	}



	private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
		boolean characteristicWriteSuccess = gatt.setCharacteristicNotification(characteristic, true);
		if (characteristicWriteSuccess) {
			//if (BluetoothUtils.isDataHopCharacteristic(characteristic)) {
				if(!mInitialized){
					Log.d(TAG, "Requesting MTU CHANGE");
					mInitialized = true;
					gatt.requestMtu(512);
				}
			//}
		} else {
			Log.d(TAG,"Characteristic notification set failure for " + characteristic.getUuid().toString());
		}
	}


	private void readCharacteristic(BluetoothGattCharacteristic characteristic) {

        byte[] messageBytes = characteristic.getValue();
        String message = new String(messageBytes);
		Log.d(TAG, "Message from remote: " + message +" pending:"+pendingWrite);
		sending=false;
        if (message == null) {
            Log.d(TAG, "Unable to convert bytes to string");
            return;
        }
		pendingWrite--;
		if (Arrays.equals(new byte[]{0x00}, messageBytes)){
            notifier.peerSameStatusDiscovered(device.getName(),convertedCharacteristics.get(characteristic));
            if (pendingWrite<=0)
				disconnect();
            tryConnection();
        }else {
        	String msg = new String(messageBytes);
			Log.d(TAG, "Attempting to connect :"+msg);
			String[] split = msg.split(":", 3);
        	if(split.length==3)
				notifier.peerDifferentStatusDiscovered(device.getName(),convertedCharacteristics.get(characteristic),split[0],split[1],split[2]);
        	else
        		notifier.peerSameStatusDiscovered(device.getName(),convertedCharacteristics.get(characteristic));
			disconnect();
			//started=false;
			//close();
			//results.clear();
		}

	}

	private void sendMessage() {
		if (mConnectionState != STATE_CONNECTED || !mInitialized) {
			Log.d(TAG,"Not initialized.");
			return;
		}
     	List<UUID> groups = new ArrayList();
		groups.addAll(advertisingInfo.keySet());

		List<BluetoothGattCharacteristic> characteristics = BluetoothUtils.findCharacteristics(mBluetoothGatt,mServiceUUID.getUuid(),groups);
		pendingWrite+=characteristics.size();
		Log.d(TAG,"Found "+pendingWrite+" characteristics. TryWriting");
		Runnable r = new TryWriting(characteristics);
		new Thread(r).start();

	}

	// Implements callback methods for GATT events that the app cares about.  For example,
	// connection change and services discovered.
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			if (newState == BluetoothProfile.STATE_CONNECTED&&mConnectionState!=BluetoothProfile.STATE_CONNECTED) {
				mConnectionState = STATE_CONNECTED;
				Log.d(TAG, "Connected to GATT server: "+gatt.getDevice().getAddress());
//				int con = stats.getBtConnections();
//				stats.setBtConnections(++con);
				if(mBluetoothGatt!=null)mBluetoothGatt.discoverServices();

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				mConnectionState = STATE_DISCONNECTED;
				Log.d(TAG, "Disconnected from GATT server.");
				if(mBluetoothGatt!=null)mBluetoothGatt.close();
				//if(started)tryConnection();
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {

			super.onServicesDiscovered(gatt, status);

			if (status != BluetoothGatt.GATT_SUCCESS) {
				Log.d(TAG,"Device service discovery unsuccessful, status " + status);
				return;
			}

			Log.d(TAG,"Gatt "+gatt.getServices().size());

			List<UUID> groups = new ArrayList<>();
			groups.addAll(advertisingInfo.keySet());
			List<BluetoothGattCharacteristic> matchingCharacteristics = BluetoothUtils.findCharacteristics(gatt,mServiceUUID.getUuid(),groups);
			if (matchingCharacteristics.isEmpty()) {
				Log.d(TAG,"Unable to find characteristics.");
				return;
			}

			for (BluetoothGattCharacteristic characteristic : matchingCharacteristics) {
				Log.d(TAG, "characteristic: " + characteristic.getUuid().toString());
				characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
				//subscribe(characteristic);
				enableCharacteristicNotification(gatt, characteristic);

			}
		}

		@Override
		public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
			super.onMtuChanged(gatt, mtu, status);
			Log.d(TAG, "ON MTU CHANGED");
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if(mInitialized)sendMessage();
			}
		}


		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
											BluetoothGattCharacteristic characteristic) {
			Log.d(TAG, "onCharacteristicChanged received: "+characteristic.getUuid().toString());
			readCharacteristic(characteristic);

		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			super.onDescriptorWrite(gatt, descriptor, status);

			Log.d(TAG, "On Descriptor Write");
			if(status == BluetoothGatt.GATT_SUCCESS){
				Log.d(TAG, "On Descriptor Write - GATT SUCCESS");
				//sendMessage(gatt);
			}
		}


		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
										 BluetoothGattCharacteristic characteristic,
										 int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d(TAG, "Characteristic Read");
				//printIncoming(characteristic);
			}
		}
	};


	private class TryWriting implements Runnable {

		List<BluetoothGattCharacteristic> characteristics;

		public TryWriting (List<BluetoothGattCharacteristic> characteristics)
		{
			this.characteristics = characteristics;
		}

		@Override
		public void run() {

			Log.d(TAG,"Trywriting "+characteristics.size()+" "+started+" "+advertisingInfo.size());
			for (BluetoothGattCharacteristic characteristic : characteristics) {
				//if (characteristic == null||!started) {
				if (characteristic == null){
					Log.d(TAG, "Unable to find echo characteristic.");
					disconnect();
					return;
				}

				if(!started)return;

				byte[] messageBytes = null;

				for(UUID uuid : advertisingInfo.keySet()){
					Log.d(TAG,"Advertising info uuid "+uuid+" "+characteristic.getUuid());
					if(characteristic.getUuid().equals(uuid))messageBytes=advertisingInfo.get(uuid);
					break;
				}

				if(messageBytes!=null) {

					Log.d(TAG, "Sending message: " + new String(messageBytes) + " " + messageBytes.length + " " + characteristic.getUuid().toString());

					if (messageBytes.length == 0) {
						Log.d(TAG, "Unable to convert message to bytes");
						return;
					}

					characteristic.setValue(messageBytes);
					mInitialized = false;
					sending = true;

					boolean success = mBluetoothGatt.writeCharacteristic(characteristic);
					int tries = 0;
					while (started && !success && tries < 5) {
						try {
							sleep(1000);
							Log.d(TAG, "Failed retry " + tries);
							success = mBluetoothGatt.writeCharacteristic(characteristic);
							tries++;
						} catch (InterruptedException e) {
						}
					}
					if (started && !success) Log.d(TAG, "Failed to write data");
					while (sending) try {
						sleep(1000);
					} catch (InterruptedException e) {
					}
				}
			}
		}
	}


	//endregion
} // BtServiceDiscovery

