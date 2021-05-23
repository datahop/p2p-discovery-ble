/*******************************************************
 * Copyright (C) 2020 DataHop Labs Ltd <sergi@datahop.network>
 *
 * This file is part of DataHop Network project.
 *
 * All rights reserved
 *******************************************************/

package network.datahop.blediscovery;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
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

import datahop.BleDiscNotifier;
import datahop.BleDiscoveryDriver;

import static android.content.Context.BLUETOOTH_SERVICE;
import static java.lang.Thread.sleep;

public class BLEServiceDiscovery implements BleDiscoveryDriver{

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

	private long btIdleFgTime = Config.bleAdvertiseForegroundDuration;
	private long scanTime = Config.bleScanDuration;
	//public static final String USER_DISCOVERED = "user_discovered";

	private HashMap<UUID,byte[]> advertisingInfo;
    private boolean mScanning;
	private Set<BluetoothDevice> results;

	private boolean started=false;
	//SettingsPreferences mTimers;
	private static BluetoothLeScanner mLEScanner;

	private boolean mInitialized = false;

	private BluetoothDevice device=null;
	private ParcelUuid mServiceUUID;
	private int pendingWrite;
	private boolean sending;

	private Handler mHandler;

	private static BleDiscNotifier notifier;
	private  boolean exit;
	//public BLEServiceDiscovery(LinkListener lListener, DiscoveryListener dListener, Context context/*, SettingsPreferences timers*/, StatsHandler stats)

	private BLEServiceDiscovery(Context context)
	{

		this.context = context;

		mHandler = new Handler(Looper.getMainLooper());
		advertisingInfo = new HashMap();
		this.results = new HashSet<BluetoothDevice>();

    }

	private static synchronized void initDriver(){
		mBluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if(mBluetoothAdapter!=null)mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
	}

	// Singleton method
	public static synchronized BLEServiceDiscovery getInstance(Context appContext) {
		if (mBleDiscovery == null) {
			mBleDiscovery = new BLEServiceDiscovery(appContext);
			initDriver();
		}
		return mBleDiscovery;
	}


	public void setNotifier(BleDiscNotifier notifier){
		Log.d(TAG,"Trying to start");
		this.notifier = notifier;
	}

	@Override
	public void start(String service_uuid) {
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
						//bleScan.tryConnection();
						if(!exit){
							Log.d(TAG,"Start service");
							start(service_uuid);
						}
					}
				}, btIdleFgTime);
			}
		}, scanTime);
	}



	@Override
	public void stop()
	{
		Log.d(TAG,"Stop");
		exit=true;
		stopScanning();
	}

	@Override
	public void addAdvertisingInfo(String characteristic, byte[] info){
		String inf = new String(info);
		Log.d(TAG,"addAdvertisingInfo "+characteristic+" "+inf);
		advertisingInfo.put(UUID.nameUUIDFromBytes(characteristic.getBytes()),info);
	}

	public void startScanning(String service_uuid)
	{

		Log.d(TAG,"startScanning Service uuid:"+service_uuid+" "+started);
		if(mBluetoothAdapter!=null&&!started) {
			pendingWrite = 0;
			sending = false;
			started = true;
			mServiceUUID =new ParcelUuid(UUID.nameUUIDFromBytes(service_uuid.getBytes()));
			results.clear();
			//serverstate = BluetoothProfile.STATE_DISCONNECTED;
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

	public void closeConnWithPeer(String peer){

	}


	public void scanLeDevice() {

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
			try{ sleep(Config.bleScanDuration);}catch (Exception e){}
			if(!started){
				Log.d(TAG,"Not started cancelling");
				return;
			}
			scanLeDevice();
		}
	};

	public void tryConnection(){
		//String address = android.provider.Settings.Secure.getString(context.getContentResolver(), "bluetooth_address");
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
	public void disconnect() {
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

	/**
	 * After using a given BLE device, the app must call this method to ensure resources are
	 * released properly.
	 */
	public void close() {
		Log.d(TAG,"Close");
		if (mBluetoothGatt == null) {
			return;
		}
		started=false;
		mBluetoothGatt.close();
		//mBluetoothGatt = null;
		//context.unregisterReceiver(mBroadcastReceiver);

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
        String message = StringUtils.stringFromBytes(messageBytes);
		Log.d(TAG, "Message from remote: " + message +" pending:"+pendingWrite);
		sending=false;
        if (message == null) {
            Log.d(TAG, "Unable to convert bytes to string");
            return;
        }
		pendingWrite--;
		if (Arrays.equals(new byte[]{0x00}, messageBytes)){
            notifier.peerSameStatusDiscovered(device.getName(),"");
            if (pendingWrite<=0)
				disconnect();
            tryConnection();
        }else {
        	Log.d(TAG, "Attempting to connect");
        	String msg = new String(messageBytes);
        	String[] split = msg.split(":");
        	if(split.length==3)
				notifier.peerDifferentStatusDiscovered(device.getName(),"",split[0],split[1],split[2]);
        	else
        		notifier.peerSameStatusDiscovered(device.getName(),"");
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
				subscribe(characteristic);
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
				printIncoming(characteristic);
			}
		}
	};


	private void printIncoming(final BluetoothGattCharacteristic characteristic) {
		Log.d(TAG, "\n\nBroadcast Update Printing Services!!!\n\n");
		for(BluetoothGattService b : characteristic.getService().getIncludedServices()) {
			Log.d(TAG, b.toString());
		}

		if(Constants.CHARACTERISTIC_DATAHOP_UUID.equals(characteristic.getUuid())) {
			// For all other profiles, writes the data formatted in HEX.
			Log.d(TAG, "Received data from Bleno...\n");
			final byte[] data = characteristic.getValue();
			if (data != null && data.length > 0) {
				final StringBuilder stringBuilder = new StringBuilder(data.length);
				for(byte byteChar : data)
					stringBuilder.append(String.format("%02X ", byteChar));
				Log.d(TAG,"Received Data OutPut\n");
				Log.d(TAG,stringBuilder.toString());
				Log.d(TAG, "Data received: " + new String(data));
			}
		}
	}

	private void subscribe(BluetoothGattCharacteristic characteristic) {
		Log.d(TAG, "Subscribing on characteristic");

		if (characteristic == null) {
			Log.d(TAG,"Characteristic does not exist");
			return;
		}

		BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Constants.CLIENT_CONFIGURATION_DESCRIPTOR_UUID);
		if(descriptor!=null){
			descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		}
	}

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

