# DataHop discovery service library for Android using Bluetooth Low Energy

This library implements an energy-aware wireless discovery service for Android, using  Bluetooth Low Energy (BLE) technology, to discover other Android devices in the vicinity using the same service and compare service status, used by the DataHop Network platform.
[DataHop](https://datahop.network) is a new platform aimed at exploring the potential of a user-operated, smartphone-centric content distribution model for user applications in contrast with the traditional client-server model, that is used by the majority of mobile data transfers today.
In particular, we propose that some nodes, either fetching the content via Internet from the content provider (e.g., BBC, CNN), or via sharing user-generated content, could directly update and share content with other nodes in the vicinity in a D2D manner without requiring Internet connectivity. We leverage on sophisticated information-aware and application-centric connectivity techniques to distribute content between mobile devices in densely-populated urban environments or rural areas where Internet connectivity is poor.

### Information-Centric and Application-Aware Wireless Discovery

Bluetooth Low Energy (BLE) beacons technology provides the features required for the necessary service discovery to provide a smart connectivity between users and transfer content without infrastructure participation. 
With this library, Android apps are able to discover other users in the vicinity and compare the status of the service before deciding whether to connect using Device-to-Device (D2D) communications (e.g., Wifi-DirecT) and establish a connection and proceed with a data transfer. 

This library implements the native library for Android of the BLE service discovery specification to exchange service discovery information between peers before any connection.
This library is compatible with Go and the IPFS-lite library, compatible with Android devices, created in [https://github.com/datahop/ipfs-lite](https://github.com/datahop/ipfs-lite).


## Objectives

* [x] User devices must automatically discover users in the vicinity without requiring user participation when required
* [X] Devices must be able to exchange information about the status (e.g., list of files available to share) of the service after discovering the other device and before starting any connection. 
* [x] Connectivity should be transparent (and run as a background process) to the user and work when the device is in standby mode.
* [x] Connectivity must take into account power consumption and must implement mechanisms to avoid battery depletion.

# Installation

The library can be built manually using the following command:

```
$ ./gradlew blediscovery:assembleRelease
```

and copy the generated .aar library into the `app/libs` folder of your app.

To add the library in your Android project first add the libs folder in your repositories list of the project `build.gradle` file,

```
allprojects {
    repositories {
    ....
      flatDir {
          dirs 'libs'
      }
    }
}
```

and then add the library as a depencency of your project in the app `build.gradle` file.

```
dependencies {
    ....
    implementation(name: 'blediscovery-release', ext: 'aar')

}
```
The library can be also automatically imported via gradle: TBC


# Usage

The library is basically implementing two functionalities. The `BLEAdvertising` service is responsible of advertising service discovery data and exchange service status using BLE GATT server and GATT characteristics. Advertised data for each service is structured in "topics" and each topic is configured as a BLE characteristic in the GATT server. Characteristics are compared in the GATT Server when accepting connections to compare status for each "topic.
When detected different values of the "topics" means different service status and it can reply with network information.
The  `BLEServiceDiscovery` service is responsible of scanning for BLE Beacons and starts a connection to the GATT server provided by the `BLEAdvertising` service 
when found BLE Beacons with the same service id.

* From Android:

```
  //Both instances must be created
  BLEServiceDiscovery bleDiscoveryDriver = BLEServiceDiscovery.getInstance(getApplicationContext());
  BLEAdvertising bleAdvertisingDriver = BLEAdvertising.getInstance(getApplicationContext());
  
  //A notifier should be passed into the instance. These notifiers can be implemented in Go and must implement BleDiscoveryDriver and BleAdvertisingDriver interfaces defined in github.com/datahop/mobile
  bleDiscoveryDriver.setNotifier(bdnotifier);
  bleAdvertisingDriver.setNotifier(banotifier);
  
```

`BLEServiceDiscovery` and `BLEAdvertising` can be direclty handled from Go passing it to the Go app like the following

```
Datahop.init(getApplicationContext().getCacheDir() + "/" + root, this, bleDiscoveryDriver, bleAdvertisingDriver, hotspot,connection);
```


* From Go:

Generate the BleDiscoveryService, passing the `BLEServiceDiscovery` and `BLEAdvertising` native driver instance and starting the service afterwards.

```
service, err := NewBleDiscoveryService(hop.peer.Host, hop.discDriver, hop.advDriver, 1000, 20000, hop.wifiHS, hop.wifiCon, ipfslite.ServiceTag)
if err != nil {
       log.Error("ble discovery setup failed : ", err.Error())
       wg.Done()
       return
}
if res, ok := service.(*bleDiscoveryService); ok {
       hop.discService = res
}
hop.discService.RegisterNotifee(hop.notifier)
hop.discService.AddAdvertisingInfo("topic", "value")
hop.discService.Start()
```

# Docs

[Code documentation](https://datahop.github.io/p2p-discovery-ble)


# Demo  application

[https://github.com/datahop/datahop-android-demo](https://github.com/datahop/datahop-android-demo)

# How to make contributions
Please read and follow the steps in [CONTRIBUTING.md](/CONTRIBUTING.md)

# License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

# Acknowledgment

This software is part of the NGI Pointer project "Incentivised Content Dissemination at the Network Edge" that has received funding from the European Union’s Horizon 2020 research and innovation programme under grant agreement No 871528

<p align="center"><img  alt="ngi logo" src="./Logo_Pointer.png" width=40%> <img  alt="eu logo" src="./eu.png" width=25%></p>

