package bledriver

import (
	discovery "github.com/srene/p2p-discovery-ble/go/discovery"
)

type NativeBleDriver interface {
	discovery.NativeDriver
}

/*type ProximityTransport interface {
	discovery.ProximityTransport
}

func GetProximityTransport(protocolName string) ProximityTransport {
	t, ok := proximity.TransportMap.Load(protocolName)
	if ok {
		return t.(proximity.ProximityTransport)
	}
	return nil
}*/

