DESCRIPTION = "Linux kernel for srvant-sonny board"
SECTION = "kernel"
LICENSE = "GPLv2"

PROVIDES += "virtual/kernel"

KCONFIG_MODE="--alldefconfig"
KBUILD_DEFCONFIG_sonny ?= "sunxi_defconfig"

KSRC ?= "git://github.com/lwolf/linux;protocol=git;branch=${KBRANCH}"
SRC_URI = "${KSRC}"
# SRC_URI += " file://defconfig"

FILESEXTRAPATHS_prepend_linux-obmc := "${THISDIR}/linux-obmc:"

SRC_URI += "file://kernel-debug.cfg \
            file://i2c-ds3232-rtc.cfg \
            file://rtl-network.cfg"

LINUX_VERSION_EXTENSION ?= "-${SRCREV}"

PV = "${LINUX_VERSION}+git${SRCPV}"

inherit kernel
require recipes-kernel/linux/linux-yocto.inc

# From 5.6+ the COPYING file changed
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"
