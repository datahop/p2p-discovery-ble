package blebridge

/*import (
	proximity "berty.tech/berty/v2/go/internal/proximitytransport"
)*/

type NativeNBDriver interface {
	NativeDriver
}


func GetProximityTransport(protocolName string) Transport {

	print(protocolName)
	return nil
}
