package com.endang.scannerobdii.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class Elm327ConnectionManager(
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @SuppressLint("MissingPermission")
    fun connect(macAddress: String): Boolean {
        disconnect()
        val device = bluetoothAdapter?.getRemoteDevice(macAddress) ?: return false
        return try {
            bluetoothAdapter.cancelDiscovery()
            val s = device.createRfcommSocketToServiceRecord(sppUuid)
            s.connect()
            socket = s
            input = s.inputStream
            output = s.outputStream
            initializeElm327v21()
            true
        } catch (_: Exception) {
            disconnect()
            false
        }
    }

    private fun initializeElm327v21() {
        val initCommands = listOf("ATZ", "ATE0", "ATL0", "ATH0", "ATSP0", "ATDP")
        for (cmd in initCommands) {
            sendCommand(cmd, waitMs = if (cmd == "ATZ") 1000L else 250L)
        }
    }

    @Synchronized
    fun sendCommand(command: String, waitMs: Long = 180L): String {
        val out = output ?: return ""
        val inp = input ?: return ""
        return try {
            out.write("$command\r".toByteArray(Charsets.US_ASCII))
            out.flush()
            Thread.sleep(waitMs)

            val sb = StringBuilder()
            val buffer = ByteArray(256)
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 1200L) {
                if (inp.available() > 0) {
                    val read = inp.read(buffer)
                    if (read > 0) {
                        val chunk = String(buffer, 0, read, Charsets.US_ASCII)
                        sb.append(chunk)
                        if (chunk.contains(">")) break
                    }
                } else if (sb.isNotEmpty() && System.currentTimeMillis() - start > waitMs) {
                    break
                }
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    fun disconnect() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
    }
}
