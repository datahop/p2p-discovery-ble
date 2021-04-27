SUPPORT := $(shell dirname $(abspath $(lastword $(MAKEFILE_LIST))))/.release
VERSION_PACKAGE := github.com/srene/p2p-discovery-ble/go/version

build: VERSION := $(shell . $(SUPPORT); getVersion)
build:
	@gomobile bind -o ./discovery/blediscovery.aar -target=android -ldflags "\
	-X '$(VERSION_PACKAGE).Version=$(VERSION)'" \
	github.com/srene/p2p-discovery-ble/go/discovery

