package blediscovery

import (
	"io"
	"sync"
	"time"

	"github.com/libp2p/go-libp2p-core/host"
	"github.com/libp2p/go-libp2p-core/peer"

	logging "github.com/ipfs/go-log"
)


var log = logging.Logger("mdns")

const ServiceTag = "_ipfs-discovery._udp"

type Service interface {
	io.Closer
	RegisterNotifee(Notifee)
	UnregisterNotifee(Notifee)
}

type Notifee interface {
	HandlePeerFound(peer.AddrInfo)
}
type BleDiscoveryService interface {
	HandlePeerFound(peer.AddrInfo)
	HandleLostPeer(remotePID string)
}

type bleDiscoveryService struct {
	//server  *mdns.Server
	//service *mdns.MDNSService
	host    host.Host
	tag     string

	lk       sync.Mutex
	notifees []Notifee
	interval time.Duration
}


// Initialises the .datahop repo, if required at the given location with the given swarm port as config.
// Default swarm port is 4501
func Init(root string, peerhost host.Host, interval time.Duration, serviceTag string) (Service,error) {

	//n := &Notifier{}

	if serviceTag == "" {
		serviceTag = ServiceTag
	}
	s := &bleDiscoveryService{
		host:     peerhost,
		interval: interval,
		tag:      serviceTag,
	}

	return s, nil
}

func (s *bleDiscoveryService) RegisterNotifee(n Notifee) {
	s.lk.Lock()
	s.notifees = append(s.notifees, n)
	s.lk.Unlock()
}

func (s *bleDiscoveryService) Close() error {
	return nil
}

func (s *bleDiscoveryService) UnregisterNotifee(n Notifee) {
	s.lk.Lock()
	found := -1
	for i, notif := range s.notifees {
		if notif == n {
			found = i
			break
		}
	}
	if found != -1 {
		s.notifees = append(s.notifees[:found], s.notifees[found+1:]...)
	}
	s.lk.Unlock()
}


