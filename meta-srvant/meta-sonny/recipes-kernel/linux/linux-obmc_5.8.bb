KBRANCH ?= "playground-5.8.17"
LINUX_VERSION ?= "5.8.17"

SRCREV="184486c15e07d55df3dac132358fbd8818d38726"

require linux-obmc.inc

# OpenBMC loads in kernel features via other mechanisms so this check
# in the kernel-yocto.bbclass is not required
KERNEL_DANGLING_FEATURES_WARN_ONLY="1"

# causes warning during the build. Some other layers remove this feature
# WARNING: do_kernel_metadata: Feature 'phosphor-gpio-keys' not found, but KERNEL_DANGLING_FEATURES_WARN_ONLY is set
KERNEL_FEATURES_remove = "phosphor-gpio-keys"