package blebridge

import (
	disc "github.com/srene/p2p-discovery-ble/go"
)

type NativeDriver interface {
	disc.NativeDriver
}


func GetProximityTransport(protocolName string) Transport {

	print(protocolName)
	return nil
}
