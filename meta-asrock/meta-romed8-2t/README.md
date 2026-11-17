# ASRock Rack ROMED8-2T

AST2500 BMC, single-socket SP3 (EPYC 7002/7003). Layer derived from
`meta-romed8hm3`.

## Build

```sh
. setup romed8-2t
bitbake obmc-phosphor-image
# output: tmp/deploy/images/romed8-2t/image-bmc (64 MiB)
```

## Where the board facts come from

Extracted from the vendor firmware `ROMED8-2T(03.08.00)BMC.zip`
(AMI MegaRAC SP-X, 64 MiB `.ima`): its kernel DTB, `IPMI.conf`,
`libipmipar.so` (sensor/I2C tables) and `libipmipdk.so` (GPIO use).

| Item | Value | Source |
|---|---|---|
| BMC flash | 64 MiB SPI-NOR on FMC CS0 | `.ima` size |
| DRAM | 512 MiB | vendor DTB `memory` |
| Console | UART5, 115200 | vendor DTB |
| eth0 | MAC0, RGMII1 + MDIO1, dedicated PHY | vendor DTB pinctrl |
| eth1 | MAC1, NC-SI to X550-AT2 | `NCSI_DEFAULT_INTERFACE=eth1` |
| MAC addresses | FRU EEPROM 0x3f80 (eth0), 0x3f88 (eth1) | `libasrrcmds.so` |
| FRU EEPROM | i2c7 @ 0x51, 16-bit addressing | `libasrrglobal.so` |
| NCT6779D SIO | i2c1 @ 0x2d (CPU temp via its TSI bank) | `libipmipar.so` |
| Mux | i2c1 PCA9545A @ 0x70: ch1 W83773G @ 0x4c, ch2/ch3 M.2 NVMe-MI @ 0x6a | `libipmipar.so` |
| Mux | i2c7 PCA9545 @ 0x70: ch0/ch1 DIMM temps | `libipmipar.so` |
| PSU PMBus | i2c2 @ 0x58, 0x59 | `libipmipar.so` |
| IPMB | i2c8 | `IPMI.conf` |
| Fans | 7 headers, tach n and n+8 (FANn, FANn_2) | `libipmipar.so` symbols |
| ADC | channels 0-14 in use | `libipmipar.so` symbols |
| Power button out | GPIOD1 (D0 = button in, passthrough) | `PDK_PowerOnChassis` |
| Reset out | GPIOD3 | `PDK_ResetChassis` |
| BMC_READY | GPIOJ0 | `bmcready` |
| BIOS SPI mux | GPIOJ1 | `libbiosfw.so` |
| SMI | GPIOAB3 | `TriggerSMI` |
| PROCHOT | GPIOAA2/AA3 | `ASRR_CPUProcHotTask` |
| Host BIOS | 32 MiB, magic `aa55aa55` at 0x20000 | BIOS 4.30 image |

These GPIO numbers match the ROMED8HM3 line names exactly, so the rest of
the ROMED8HM3 GPIO map was reused.

## Verify on hardware

Before flashing, capture from the vendor BMC (needed for rollback and to
fill gaps):

- [ ] Full SPI dump of the BMC flash (`flashrom` with a clip, or
      `socflash` backup). This is the only way back to the vendor firmware.
- [ ] `ipmitool mc info` (product ID for `dev_id.json`, set to 0 for now)
- [ ] `ipmitool fru print`, `ipmitool sdr elist`

After first boot of OpenBMC:

- [ ] Serial console on UART5 (BMC debug header) works
- [ ] eth0 (dedicated port) gets a link and the right MAC
- [ ] eth1 NC-SI: link via X550 port, package/channel 0
- [ ] `i2cdetect` on buses 1, 2, 7, 8 matches the table above
- [ ] NCT6779D, W83773G hwmon readings are sane
- [ ] Fan PWM channel per header (PWM0-6 assumed), tach pairs
- [ ] ADC channel to rail mapping (3VSB, 5VSB, VCPU, VSOC, VCCM, VPPM, 1.8V...)
- [ ] Power on/off/reset via `obmcutil` / Redfish
- [ ] PowerOk (`PWRGD_OUT`, AB1) and POST complete (B0) polarity
- [ ] Heartbeat LED (H6), fault LED (Z2)
- [ ] Host console / SOL: vuart at 0x2f8 vs the vendor UART route
- [ ] KCS3 at 0xca2 works with the host `ipmi_si` driver
- [ ] POST codes on port 0x80
- [ ] BIOS update via `bios-update` (BMC_PCH_BIOS_CS_N mux)
- [ ] KVM (video engine) and USB virtual media

## Not done yet

- entity-manager JSON for sensors (voltages, temps, fans, PSUs)
- Fan control (phosphor-pid-control)
- Upstreaming the device tree to the kernel
