package com.neonide.studio.app.gradle

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal ELF inspector.
 *
 * We use this to distinguish Android/Termux-compatible binaries (bionic linker or static)
 * from host GNU/Linux binaries (glibc interpreter like /lib64/ld-linux-*.so.*).
 */
object ElfInspector {

    private const val EI_NIDENT = 16
    private const val ELFCLASS32 = 1
    private const val ELFCLASS64 = 2

    private const val PT_INTERP = 3

    // e_machine values
    private const val EM_386 = 3
    private const val EM_ARM = 40
    private const val EM_X86_64 = 62
    private const val EM_AARCH64 = 183

    data class Info(
        val isElf: Boolean,
        val is64Bit: Boolean,
        val littleEndian: Boolean,
        val machine: Int,
        val interpreter: String?,
    )

    fun readInfo(file: File): Info? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(64)
                val n = input.read(header)
                if (n < EI_NIDENT) return@use null

                // Magic
                if (header[0].toInt() != 0x7f || header[1].toInt() != 'E'.code || header[2].toInt() != 'L'.code || header[3].toInt() != 'F'.code) {
                    return@use Info(false, false, false, 0, null)
                }

                val clazz = header[4].toInt() and 0xff
                val data = header[5].toInt() and 0xff
                val is64 = clazz == ELFCLASS64
                val little = data == 1
                val order = if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

                val bb = ByteBuffer.wrap(header)
                bb.order(order)

                // e_machine offset is 18 for both 32 and 64
                val machine = bb.getShort(18).toInt() and 0xffff

                // Need full ELF header + program headers to read PT_INTERP.
                // Re-open and parse with a single buffer for simplicity.
                val allBytes = file.readBytes()
                val b2 = ByteBuffer.wrap(allBytes)
                b2.order(order)

                val e_phoff: Long
                val e_phentsize: Int
                val e_phnum: Int

                if (is64) {
                    e_phoff = b2.getLong(32)
                    e_phentsize = b2.getShort(54).toInt() and 0xffff
                    e_phnum = b2.getShort(56).toInt() and 0xffff
                } else {
                    e_phoff = (b2.getInt(28).toLong() and 0xffffffffL)
                    e_phentsize = b2.getShort(42).toInt() and 0xffff
                    e_phnum = b2.getShort(44).toInt() and 0xffff
                }

                var interp: String? = null
                if (e_phoff > 0 && e_phnum > 0 && e_phentsize > 0) {
                    for (i in 0 until e_phnum) {
                        val off = e_phoff + i.toLong() * e_phentsize.toLong()
                        if (off < 0 || off + e_phentsize > allBytes.size) continue

                        val pType: Int
                        val pOffset: Long
                        val pFilesz: Long

                        if (is64) {
                            pType = b2.getInt(off.toInt())
                            pOffset = b2.getLong(off.toInt() + 8)
                            pFilesz = b2.getLong(off.toInt() + 32)
                        } else {
                            pType = b2.getInt(off.toInt())
                            pOffset = (b2.getInt(off.toInt() + 4).toLong() and 0xffffffffL)
                            pFilesz = (b2.getInt(off.toInt() + 16).toLong() and 0xffffffffL)
                        }

                        if (pType == PT_INTERP) {
                            val start = pOffset.toInt()
                            val end = (pOffset + pFilesz).toInt().coerceAtMost(allBytes.size)
                            if (start in 0..<end) {
                                val raw = allBytes.copyOfRange(start, end)
                                interp = raw.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
                            }
                            break
                        }
                    }
                }

                Info(true, is64, little, machine, interp)
            }
        }.getOrNull()
    }

    /**
     * Return true if the ELF is likely runnable on Android/Termux on the current device.
     *
     * Rules:
     * - Must be ELF and machine must match one of supported ABIs
     * - Interpreter must be null (static) OR look like Android bionic linker (contains "linker")
     * - Reject common glibc interpreters (contains "ld-linux")
     */
    fun isAndroidRunnable(file: File, supportedAbis: List<String>): Boolean {
        val info = readInfo(file) ?: return false
        if (!info.isElf) return false

        val allowedMachines = supportedAbis.mapNotNull { abiToMachine(it) }.toSet()
        if (allowedMachines.isNotEmpty() && info.machine !in allowedMachines) return false

        val interp = info.interpreter
        if (interp == null || interp.isBlank()) return true

        val lower = interp.lowercase()
        if (lower.contains("ld-linux")) return false
        if (lower.contains("linker")) return true

        // Unknown interpreter -> reject (safer)
        return false
    }

    private fun abiToMachine(abi: String): Int? {
        return when (abi) {
            "arm64-v8a" -> EM_AARCH64
            "armeabi-v7a" -> EM_ARM
            "x86_64" -> EM_X86_64
            "x86" -> EM_386
            else -> null
        }
    }
}
