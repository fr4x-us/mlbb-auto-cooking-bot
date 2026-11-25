package us.fr4x.mlbbbot

/** Deskriptor dan format laporan HID mouse relatif (3 tombol + X + Y + roda), tanpa Report ID. */
object MouseReport {
    const val BTN_LEFT = 1

    private val DESC_INT = intArrayOf(
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x02,       // Usage (Mouse)
        0xA1, 0x01,       // Collection (Application)
        0x09, 0x01,       //   Usage (Pointer)
        0xA1, 0x00,       //   Collection (Physical)
        0x05, 0x09,       //     Usage Page (Button)
        0x19, 0x01,       //     Usage Min (1)
        0x29, 0x03,       //     Usage Max (3)
        0x15, 0x00,       //     Logical Min (0)
        0x25, 0x01,       //     Logical Max (1)
        0x95, 0x03,       //     Report Count (3)
        0x75, 0x01,       //     Report Size (1)
        0x81, 0x02,       //     Input (Data,Var,Abs)
        0x95, 0x01,       //     Report Count (1)
        0x75, 0x05,       //     Report Size (5)
        0x81, 0x03,       //     Input (Const) padding
        0x05, 0x01,       //     Usage Page (Generic Desktop)
        0x09, 0x30,       //     Usage (X)
        0x09, 0x31,       //     Usage (Y)
        0x09, 0x38,       //     Usage (Wheel)
        0x15, 0x81,       //     Logical Min (-127)
        0x25, 0x7F,       //     Logical Max (127)
        0x75, 0x08,       //     Report Size (8)
        0x95, 0x03,       //     Report Count (3)
        0x81, 0x06,       //     Input (Data,Var,Rel)
        0xC0,             //   End Collection
        0xC0              // End Collection
    )

    val DESCRIPTOR: ByteArray = ByteArray(DESC_INT.size) { DESC_INT[it].toByte() }

    /** Laporan 4 byte: [tombol, dx, dy, roda]. */
    fun build(buttons: Int, dx: Int, dy: Int, wheel: Int = 0): ByteArray = byteArrayOf(
        (buttons and 0x07).toByte(),
        dx.coerceIn(-127, 127).toByte(),
        dy.coerceIn(-127, 127).toByte(),
        wheel.coerceIn(-127, 127).toByte()
    )

    /** Pecah gerakan (dx, dy) menjadi langkah-langkah kecil; jumlah langkah selalu sama dengan total. */
    fun splitMove(dx: Int, dy: Int, maxStep: Int = 127): List<Pair<Int, Int>> {
        require(maxStep in 1..127)
        val n = maxOf(ceilDiv(Math.abs(dx), maxStep), ceilDiv(Math.abs(dy), maxStep))
        if (n == 0) return emptyList()
        val out = ArrayList<Pair<Int, Int>>(n)
        var px = 0
        var py = 0
        for (i in 1..n) {
            val cx = dx * i / n
            val cy = dy * i / n
            out.add(Pair(cx - px, cy - py))
            px = cx
            py = cy
        }
        return out
    }

    private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
}
