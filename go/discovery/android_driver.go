package discovery

/*import (
	proximity "berty.tech/berty/v2/go/internal/proximitytransport"
)*/

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


