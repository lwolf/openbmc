
PREFERRED_PROVIDER_virtual/kernel ?= "linux-arm"
PREFERRED_PROVIDER_virtual/bootloader ?= "u-boot-arm"

DEFAULTTUNE ?= "armv7"

SERIAL_CONSOLES = "115200;ttyS0"


require conf/machine/include/arm/arch-armv7.inc
