KBRANCH ?= "playground-5.10.14"
LINUX_VERSION ?= "5.10.14"

SRCREV="e01c549e4e82f1e7382bb529d4aaebfcbb635b89"

require linux-obmc.inc

# OpenBMC loads in kernel features via other mechanisms so this check
# in the kernel-yocto.bbclass is not required
KERNEL_DANGLING_FEATURES_WARN_ONLY="1"

# causes warning during the build. Some other layers remove this feature
# WARNING: do_kernel_metadata: Feature 'phosphor-gpio-keys' not found, but KERNEL_DANGLING_FEATURES_WARN_ONLY is set
KERNEL_FEATURES_remove = "phosphor-gpio-keys"