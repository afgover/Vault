package com.afgover.vault.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Telefonu bilgisayara Bluetooth klavye (HID cihazı) olarak tanıtır ve
 * verilen metni tuş basımları halinde gönderir.
 *
 * Metin ağdan veya panodan geçmez; Bluetooth bağlantısının kendi şifrelemesi
 * içinde tuş tuş iletilir. Bilgisayara hiçbir yazılım kurulması gerekmez.
 *
 * İzinler çağıran taraf (BtTypeDialog) tarafından garanti edilir; yine de tüm
 * BT çağrıları SecurityException'a karşı korunur.
 */
@SuppressLint("MissingPermission")
object BtHidManager {

    sealed interface State {
        data object Idle : State
        data object Unsupported : State
        data object Registering : State
        /** HID uygulaması kayıtlı; cihaz seçilebilir. */
        data object Ready : State
        data class Connecting(val name: String) : State
        data class Connected(val name: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var hid: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var adapter: BluetoothAdapter? = null

    /** Standart 8 baytlık boot klavye rapor tanımı. */
    private val KEYBOARD_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01, // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06, // Usage (Keyboard)
        0xA1.toByte(), 0x01, // Collection (Application)
        0x05.toByte(), 0x07, //   Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(), //   Usage Min (224)
        0x29.toByte(), 0xE7.toByte(), //   Usage Max (231)
        0x15.toByte(), 0x00, //   Logical Min (0)
        0x25.toByte(), 0x01, //   Logical Max (1)
        0x75.toByte(), 0x01, //   Report Size (1)
        0x95.toByte(), 0x08, //   Report Count (8)
        0x81.toByte(), 0x02, //   Input (Data, Var, Abs) — değiştirici tuşlar
        0x95.toByte(), 0x01, //   Report Count (1)
        0x75.toByte(), 0x08, //   Report Size (8)
        0x81.toByte(), 0x01, //   Input (Const) — ayrılmış bayt
        0x95.toByte(), 0x06, //   Report Count (6)
        0x75.toByte(), 0x08, //   Report Size (8)
        0x15.toByte(), 0x00, //   Logical Min (0)
        0x25.toByte(), 0x65, //   Logical Max (101)
        0x05.toByte(), 0x07, //   Usage Page (Key Codes)
        0x19.toByte(), 0x00, //   Usage Min (0)
        0x29.toByte(), 0x65, //   Usage Max (101)
        0x81.toByte(), 0x00, //   Input (Data, Array) — 6 tuş
        0xC0.toByte()        // End Collection
    )

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 28

    @RequiresApi(28)
    fun start(context: Context) {
        if (_state.value != State.Idle && _state.value != State.Unsupported) return
        _lastError.value = null
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val btAdapter = manager.adapter
        if (btAdapter == null || safe { btAdapter.isEnabled } != true) {
            _lastError.value = "Bluetooth kapalı. Önce Bluetooth'u aç."
            return
        }
        adapter = btAdapter
        _state.value = State.Registering

        val ok = safe {
            btAdapter.getProfileProxy(
                context.applicationContext,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        if (profile != BluetoothProfile.HID_DEVICE) return
                        val hidDevice = proxy as BluetoothHidDevice
                        hid = hidDevice
                        registerApp(context.applicationContext, hidDevice)
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        hid = null
                        connectedDevice = null
                        _state.value = State.Idle
                    }
                },
                BluetoothProfile.HID_DEVICE
            )
        }
        if (ok != true) {
            _state.value = State.Unsupported
            _lastError.value = "Bu telefon Bluetooth klavye (HID) profilini desteklemiyor."
        }
    }

    @RequiresApi(28)
    private fun registerApp(context: Context, hidDevice: BluetoothHidDevice) {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Vault Klavye",
            "Vault güvenli tuş aktarımı",
            "Vault",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            KEYBOARD_DESCRIPTOR
        )
        val ok = safe {
            hidDevice.registerApp(
                sdp, null, null, context.mainExecutor,
                object : BluetoothHidDevice.Callback() {
                    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                        _state.value = if (registered) State.Ready else State.Idle
                    }

                    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                        when (state) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                connectedDevice = device
                                _state.value = State.Connected(deviceName(device))
                            }
                            BluetoothProfile.STATE_CONNECTING ->
                                _state.value = State.Connecting(deviceName(device))
                            BluetoothProfile.STATE_DISCONNECTED,
                            BluetoothProfile.STATE_DISCONNECTING -> {
                                if (connectedDevice == device) connectedDevice = null
                                if (_state.value !is State.Idle) _state.value = State.Ready
                            }
                        }
                    }
                }
            )
        }
        if (ok != true) {
            _state.value = State.Unsupported
            _lastError.value = "HID kaydı başarısız — telefon desteklemiyor olabilir."
        }
    }

    /** Eşleştirilmiş cihazlar (bilgisayarını buradan seçersin). */
    fun bondedDevices(): List<Pair<String, BluetoothDevice>> =
        safe {
            adapter?.bondedDevices?.map { deviceName(it) to it }?.sortedBy { it.first }
        } ?: emptyList()

    @RequiresApi(28)
    fun connect(device: BluetoothDevice) {
        _lastError.value = null
        _state.value = State.Connecting(deviceName(device))
        val ok = safe { hid?.connect(device) }
        if (ok != true) {
            _state.value = State.Ready
            _lastError.value = "Bağlantı başlatılamadı. Cihazın Bluetooth'u açık mı?"
        }
    }

    /**
     * Metni tuş basımları olarak gönderir; haritada olmayan karakterleri
     * atlayıp geri döndürür. Arka plan iş parçacığında çağrılmalı.
     */
    @RequiresApi(28)
    fun typeText(text: String, layout: HidLayouts.Layout): List<Char> {
        val hidDevice = hid ?: return text.toList()
        val device = connectedDevice ?: return text.toList()
        val map = HidLayouts.map(layout)
        val untyped = mutableListOf<Char>()

        for (ch in text) {
            val stroke = map[ch]
            if (stroke == null) {
                untyped.add(ch)
                continue
            }
            val press = byteArrayOf(
                stroke.modifier.toByte(), 0,
                stroke.usage.toByte(), 0, 0, 0, 0, 0
            )
            val release = ByteArray(8)
            safe { hidDevice.sendReport(device, 0, press) }
            Thread.sleep(12)
            safe { hidDevice.sendReport(device, 0, release) }
            Thread.sleep(12)
        }
        return untyped
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= 28) {
            safe {
                hid?.unregisterApp()
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
            }
        }
        hid = null
        connectedDevice = null
        _state.value = State.Idle
    }

    private fun deviceName(device: BluetoothDevice): String =
        safe { device.name } ?: device.address ?: "Bilinmeyen cihaz"

    /** BT çağrılarını izin/durum hatalarına karşı sarar. */
    private fun <T> safe(block: () -> T): T? =
        try {
            block()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
}
