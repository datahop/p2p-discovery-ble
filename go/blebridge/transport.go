package blebridge

import (
	"context"
	host "github.com/libp2p/go-libp2p-core/host"
//	"github.com/libp2p/go-libp2p-core/peer"
	tptu "github.com/libp2p/go-libp2p-transport-upgrader"
	"go.uber.org/zap"
)


type Transport interface {
	HandleFoundPeer(remotePID string) bool
	HandleLostPeer(remotePID string)
	ReceiveFromPeer(remotePID string, payload []byte)
}

type transport struct{
	host  host.Host
	logger   *zap.Logger
	driver NativeDriver
	ctx      context.Context
}

func NewTransport(ctx context.Context, l *zap.Logger, driver NativeDriver) func(h host.Host, u *tptu.Upgrader) (*transport, error) {
	l = l.Named("ProximityTransport")

	if driver == nil {
		l.Error("error: NewTransport: driver is nil")
		driver = &NoopNativeDriver{}
	}

	return func(h host.Host, u *tptu.Upgrader) (*transport, error) {
		l.Debug("NewTransport called", zap.String("driver", driver.ProtocolName()))
		transport := &transport{
			host:     h,
			//upgrader: u,
			//cache:    NewRingBufferMap(l, 128),
			driver:   driver,
			logger:   l,
			ctx:      ctx,
		}

		return transport, nil
	}
}

// HandleFoundPeer is called by the native driver when a new peer is found.
// Adds the peer in the PeerStore and initiates a connection with it
func (t *transport) HandleFoundPeer(sRemotePID string) bool {
	t.logger.Debug("HandleFoundPeer", zap.String("remotePID", sRemotePID))
	//remotePID, err := peer.Decode(sRemotePID)
	if err != nil {
		t.logger.Error("HandleFoundPeer: wrong remote peerID")
		return false
	}
}

// HandleLostPeer is called by the native driver when the connection with the peer is lost.
// Closes connections with the peer.
func (t *transport) HandleLostPeer(sRemotePID string) {
	t.logger.Debug("HandleLostPeer", zap.String("remotePID", sRemotePID))
	//remotePID, err := peer.Decode(sRemotePID)
	if err != nil {
		t.logger.Error("HandleLostPeer: wrong remote peerID")
		return
	}

}

func (t *transport) ReceiveFromPeer(remotePID string, payload []byte) {
	t.logger.Debug("ReceiveFromPeer()", zap.String("remotePID", remotePID), zap.Binary("payload", payload))
}