# ASRock Rack ROMED8-2T

AST2500 BMC, single-socket SP3 (EPYC 7002/7003). Layer derived from
`meta-romed8hm3`.

## Build

Tested so far: `bitbake virtual/kernel` (kernel 6.18.63 + DTB) builds clean.
The full image has not been built yet.

### Host requirements

- Linux x86_64. Ubuntu 22.04/24.04 or Debian 12 work. Native, a VM, or a
  container with a bind-mounted build dir all work.
- Disk: about 100 GB free for `build/`, downloads and sstate (rm_work keeps
  the peak lower). It ran out at about 30 GB.
- RAM: 16 GB minimum, 32 GB+ is better (Rust/LLVM links are heavy).
- Time: about 3-5 h on 16 cores from cold, 10+ h on 4 cores.
- Run as a normal user. bitbake refuses to run as root.

### Host packages (Ubuntu/Debian)

```sh
sudo apt-get install -y gawk wget git diffstat unzip texinfo gcc \
  build-essential chrpath socat cpio python3 python3-pip python3-pexpect \
  xz-utils debianutils iputils-ping python3-git python3-jinja2 \
  python3-subunit zstd liblz4-tool file locales libacl1
sudo locale-gen en_US.UTF-8
```

### Build

```sh
git clone -b claude/project-thread-ia37kk https://github.com/lwolf/openbmc
cd openbmc
export LANG=en_US.UTF-8
. setup romed8-2t build/romed8-2t

# optional, recommended: lower peak disk, keep caches outside build/
cat >> conf/local.conf <<'CONF'
INHERIT += "rm_work"
DL_DIR = "${TOPDIR}/../../downloads"
SSTATE_DIR = "${TOPDIR}/../../sstate"
CONF

bitbake obmc-phosphor-image
```

To resume after an interruption, re-run the same `bitbake` line. Finished
tasks are reused.

### Output

`build/romed8-2t/tmp/deploy/images/romed8-2t/`:

| File | Use |
|---|---|
| `image-bmc` | full 64 MiB flash image (u-boot + kernel + rootfs + rwfs) |
| `image-u-boot`, `image-kernel`, `image-rofs` | individual partitions |
| `obmc-phosphor-image-romed8-2t.static.mtd.tar` | update tarball for a running OpenBMC |

Check that `image-bmc` is exactly 67108864 bytes before flashing.

### Flashing (do it together, after the vendor flash dump)

The AMI web UI will not take an OpenBMC image. Write `image-bmc` to the
64 MiB BMC SPI chip either with an external programmer (SOIC-8 clip +
flashrom) or with ASRock's `socflash` from the host. Keep the vendor dump
to roll back.

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
