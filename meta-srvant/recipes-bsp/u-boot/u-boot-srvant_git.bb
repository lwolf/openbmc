require recipes-bsp/u-boot/u-boot.inc

SRC_URI = "git://github.com/lwolf/u-boot;branch=v2020.10"
SRCREV = "050acee119b3757fee3bd128f55d720fdd9bb890"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://Licenses/gpl-2.0.txt;md5=b234ee4d69f5fce4486a80fdaf4a4263"
S = "${WORKDIR}/git"

PROVIDES += "u-boot"
