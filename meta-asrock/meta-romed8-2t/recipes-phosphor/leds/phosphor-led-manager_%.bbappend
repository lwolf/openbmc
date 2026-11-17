FILESEXTRAPATHS:prepend:romed8-2t := "${THISDIR}/${PN}:"

SRC_URI:append:romed8-2t = " file://led-group-config.json"

do_install:append:romed8-2t() {
        install -m 0644 ${UNPACKDIR}/led-group-config.json ${D}${datadir}/phosphor-led-manager/
}
