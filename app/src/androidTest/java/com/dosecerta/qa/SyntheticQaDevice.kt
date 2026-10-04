package com.dosecerta.qa

import androidx.test.uiautomator.UiDevice

/** API 26 places emulator boot properties under ro.kernel; newer images use ro.boot. */
object SyntheticQaDevice {
    fun isDedicated(device: UiDevice): Boolean =
        listOf("ro.boot.qemu.avd_name", "ro.kernel.qemu.avd_name").any {
            device.executeShellCommand("getprop $it").trim() == "DoseCerta_QA"
        }
}
