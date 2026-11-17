FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI += " \
    file://romed8-2t.cfg \
    file://aspeed-bmc-asrock-romed8-2t.dts \
    "

# The ROMED8-2T device tree is not upstream yet; drop it into the kernel tree
# so KERNEL_DEVICETREE can build it.
do_patch:append() {
    install -m 0644 ${UNPACKDIR}/aspeed-bmc-asrock-romed8-2t.dts \
        ${S}/arch/arm/boot/dts/aspeed/
}
