package androidblediscovery

import (
	"github.com/srene/p2p-service-ble/go/discovery"
)

type NativeNBDriver interface {
	NativeDriver
}

func GetProximityDiscovery(protocolName string) bleDiscoveryService {
	/*t, ok := DiscoveryMap.Load(protocolName)
	if ok {
		return t.(bleDiscoveryService)
	}
	return nil*/
	t, _ := DiscoveryMap.Load(protocolName)
	return t.(bleDiscoveryService)
}


