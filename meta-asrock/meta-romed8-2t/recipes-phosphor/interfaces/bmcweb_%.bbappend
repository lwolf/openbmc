# The 64 MiB flash layout produces a ~31 MiB static.mtd.tar, which is over
# bmcweb's default 30 MiB request body limit (HTTP 413 on Redfish update).
EXTRA_OEMESON:append = " \
    -Dhttp-body-limit=65 \
"
