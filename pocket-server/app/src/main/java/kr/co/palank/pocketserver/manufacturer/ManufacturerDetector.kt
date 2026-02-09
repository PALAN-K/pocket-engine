package kr.co.palank.pocketserver.manufacturer

import android.os.Build
import java.util.Locale

enum class Manufacturer(val label: String) {
    SAMSUNG("Samsung"),
    XIAOMI("Xiaomi"),
    HUAWEI("Huawei"),
    OPPO("Oppo"),
    OTHER("기타")
}

object ManufacturerDetector {
    fun detect(): Manufacturer {
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        return when {
            brand.contains("samsung") -> Manufacturer.SAMSUNG
            brand.contains("xiaomi") -> Manufacturer.XIAOMI
            brand.contains("huawei") || brand.contains("honor") -> Manufacturer.HUAWEI
            brand.contains("oppo") || brand.contains("realme") -> Manufacturer.OPPO
            else -> Manufacturer.OTHER
        }
    }
}
