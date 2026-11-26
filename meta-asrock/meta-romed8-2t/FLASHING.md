# ROMED8-2T: back up and flash the BMC from the host

How to dump the stock ASRock (AMI MegaRAC) BMC flash and write OpenBMC to
it from the running host OS, without an external SPI programmer.

Status: not yet tried on this board. Commands were checked against culvert
v0.4.0 source (`amboar/culvert` at `42dd40d`, 2026-08-19) and its `--help`
output.

## How it works

The AST2500 exposes two host-to-BMC debug bridges that give the host full
access to the BMC's internal bus, including the SPI flash controller (FMC):

| Bridge | Host interface | culvert name | Notes |
|---|---|---|---|
| P2A | PCIe, AST2500 VGA device `1a03:2000` | `p2a` | Fast. Needs the onboard VGA enabled. |
| iLPC2AHB | LPC SuperIO at I/O `0x2e`/`0x4e` | `ilpc` | Slow. |
| Debug UART | BMC UART1/UART5 via a USB-UART | `debug-uart` | Needs a cable to the BMC header. |

Both P2A and iLPC2AHB are open at cold boot. BMC firmware can lock them
(CVE-2019-6260). Whether ASRock's firmware does is unknown: `culvert probe`
tells you.

The stock web UI and IPMI update only accept signed ASRock `.ima` images,
so they cannot install OpenBMC.

## What stops working while you do this

| Affected | Effect |
|---|---|
| Host OS | Keeps running. Not rebooted. |
| BMC | Halted during the write (culvert gates the ARM clock), then reset. |
| Fans | May run at the hardware failsafe speed (loud) until OpenBMC fan control runs. |
| Remote access | No IPMI, web UI, KVM or remote power control until OpenBMC is up. |
| Onboard VGA | Output may drop when the BMC resets. Work over SSH, not the BMC console. |
| MAC addresses | OpenBMC may come up with a different MAC (the FRU MAC offsets are unverified), so the DHCP lease can change. |

## Flash layout

64 MiB SPI-NOR on FMC CS0 (`FLASH_SIZE = "65536"` in
`conf/machine/romed8-2t.conf`, `openbmc-flash-layout-64.dtsi` in the DTS).
Offsets from `meta-phosphor/classes/image_types_phosphor.bbclass` for
`flash-65536`:

| Partition | Offset | Size |
|---|---|---|
| u-boot | `0x0000000` | 888 KiB |
| manifest | `0x00de000` | 8 KiB |
| u-boot-env | `0x00e0000` | 128 KiB |
| kernel | `0x0100000` | 9 MiB |
| rofs | `0x0a00000` | 32 MiB |
| rwfs | `0x2a00000` | 22 MiB |

`image-bmc` must be exactly 67108864 bytes. culvert writes the whole chip
from offset 0.

## 0. Before you start

- Record what OpenBMC will not know:
  ```sh
  ipmitool lan print 1 > vendor-lan1.txt   # eth0 MAC, IP config
  ipmitool fru print   > vendor-fru.txt
  ipmitool mc info     > vendor-mc.txt
  ```
- Keep a way back that does not depend on the BMC: an SPI clip
  (SOIC-8 or SOIC-16, check the chip package) and a `flashrom`-capable
  programmer (CH341A or a Raspberry Pi). You only need it if a write fails
  half way.
- Root shell on the host, over SSH.
- Kernel lockdown must be off. With Secure Boot, many distros enable it,
  and it blocks both bridges (`iopl()` and mmap of PCI resources):
  ```sh
  cat /sys/kernel/security/lockdown   # want: [none] integrity confidentiality
  ```
  If it is not `[none]`, disable Secure Boot or boot with `lockdown=none`
  where the kernel allows it.

## 1. Build culvert on the host

```sh
sudo apt-get install -y build-essential flex bison swig meson \
  device-tree-compiler libyaml-dev pkg-config
git clone https://github.com/amboar/culvert.git
cd culvert
meson setup build && meson compile -C build
sudo install -m 755 build/src/culvert /usr/local/sbin/
culvert --version   # expect v0.4.0 or later
```

The README examples show `debug` and `devmem` commands that are not in
v0.4.0. Use `culvert --help` and `culvert <cmd> --help` as the reference.

## 2. Check the bridges

```sh
sudo culvert -l            # lists bridge drivers: l2a ilpc devmem debug-uart p2a
sudo culvert probe
```

Read the `p2a` and `ilpc` sections:

- `p2a: Permissive` and `[0x20000000 - 0x2fffffff] BMC Flash: Writable`:
  you can dump and flash over PCIe. Best case.
- `p2a: Restricted` with BMC Flash read-only, or `Disabled`: P2A is out.
  Check `ilpc`.
- Both locked: stop. Use the SPI clip, or the debug UART bridge if
  `debug:` shows `Permissive` and you can reach the BMC UART header.

The iLPC probe writes to I/O port `0x2e` and `0x4e`. The NCT6779D SuperIO
may also sit there. The probe uses the ASPEED unlock sequence (`0xa5 0xa5`),
which the Nuvoton part ignores (it needs `0x87 0x87`), so this should be
harmless.

To force one bridge, add `via p2a` or `via ilpc` to any command below.

## 3. Back up the vendor flash

```sh
sudo culvert read --type=firmware > vendor-1.bin
sudo culvert read --type=firmware > vendor-2.bin
ls -l vendor-*.bin          # both must be 67108864 bytes
sha256sum vendor-*.bin
```

- If `read` fails with `Flash identification failed`, the chip is not in
  culvert's table (`src/flash.c`). 64 MiB parts it knows: Macronix
  MX25L512xx (`c2201a`), Winbond W25Q512JV (`ef4020`), Micron N25Q512A
  (`20ba20`). Nothing has been written at that point. Fall back to the clip.
- `read` does not halt the BMC. AMI may write its config area while you
  read, so the two dumps can differ. Find where:
  ```sh
  cmp -l vendor-1.bin vendor-2.bin | awk '{printf "0x%x\n", $1-1}' | head
  ```
  A few differences in one small region (the AMI config/SEL area) are
  expected. Differences spread over the image mean a bad read: dump again.
- Sanity check: the dump should contain AMI firmware headers:
  ```sh
  grep -ac '\$MODULE\$' vendor-1.bin   # non-zero
  ```
- Copy `vendor-1.bin` and the `vendor-*.txt` files off the box (two places)
  before going further. This dump is the only way back to the vendor
  firmware, because it holds the board's own config and MACs.

## 4. Flash OpenBMC

```sh
test "$(stat -c %s image-bmc)" -eq 67108864 && echo size ok
sudo culvert write --type=firmware < image-bmc
```

Notes:

- Redirect from a file, do not pipe. culvert reads stdin in 64 KiB chunks.
  On a short read it logs `Unexpected ingress value` and stops writing, but
  in v0.4.0 that path does not set an error code, so it still resets the
  BMC onto a partly written flash. A pipe can produce short reads. This is
  also why the size check above matters (64 MiB is a whole number of
  64 KiB blocks).
- What culvert does (from `src/cmd/write.c`): stops the BMC watchdog from
  resetting the SoC, gates the BMC ARM clock, erases and writes each
  64 KiB block, reads it back and retries on mismatch, then resets the BMC
  through watchdog 2.
- Over P2A expect minutes. Over iLPC it is much slower. Neither is measured
  on this board yet. Do not interrupt it.
- If it fails part way, the BMC is not reset and its clock is ungated, but
  the flash is partly erased. Do not power-cycle the board. Re-run the same
  command. If that also fails, write `vendor-1.bin` back the same way, or
  use the clip.

## 5. Check OpenBMC came up

Wait about 2-3 minutes after the write finishes.

```sh
sudo ipmitool mc info        # host KCS: should now report OpenBMC
```

- Find the BMC on the network (DHCP on the dedicated port, eth0). The MAC
  may differ from `vendor-lan1.txt`.
- Log in: `ssh root@<bmc-ip>`, password `0penBMC`. Change it.
- Work through "After first boot of OpenBMC" in `README.md`.
- If the MAC is wrong, set it from the BMC shell and reboot the BMC:
  `fw_setenv ethaddr <mac from vendor-lan1.txt>`.

## 6. Roll back to the vendor firmware

From the host, while OpenBMC runs:

```sh
sudo culvert probe                             # bridges must still be writable
sudo culvert write --type=firmware < vendor-1.bin
```

This layer does not configure the bridges, so they should stay open under
OpenBMC. Confirm with `probe` first. If they are locked, use the clip:

```sh
flashrom -p ch341a_spi -w vendor-1.bin    # board powered off, BMC chip clipped
```

## Alternative: socflash

ASPEED's `socflash` (shipped in some ASRock BMC packages) also flashes
from the host. It uses the same P2A/LPC bridges, so if `culvert probe`
shows them locked, expect `socflash` to fail too. Flags differ between
versions; read its own README. culvert is preferred here because its source
is open and its behaviour above is checked.

## Open items

- [ ] `culvert probe` output on this board with vendor firmware 2.08
- [ ] Flash chip part number (read the package, or the `Flash ID` line from
      `culvert -v`)
- [ ] Time for a full dump and write over P2A
- [ ] Whether the host survives the BMC reset with the onboard VGA in use
