package id.co.alphanusa.perisaitab.domain.model

import com.google.gson.annotations.SerializedName

data class PocData(
    @SerializedName("pitch") val pitch: Double,
    @SerializedName("roll") val roll: Double,
    @SerializedName("yaw") val yaw: Double,
    @SerializedName("battery") val battery: BatteryData?,
    @SerializedName("aircraft_latitude") val aircraftLatitude: Double,
    @SerializedName("aircraft_longitude") val aircraftLongitude: Double,
    @SerializedName("aircraft_altitude") val aircraftAltitude: Double,
    @SerializedName("home_latitude") val homeLatitude: Double,
    @SerializedName("home_longitude") val homeLongitude: Double,
    @SerializedName("gps_satellite_count") val gpsSatelliteCount: Int,
    @SerializedName("gps_signal_level") val gpsSignalLevel: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

sealed class BatteryData {
    data class SingleBatteryState(
        @SerializedName("battery_percentage") val percentageRemaining: Int,
        @SerializedName("battery_voltage") val voltageLevel: Float,
        @SerializedName("battery_status") val batteryStatus: BatteryStatus
    ) : BatteryData()

    data class DualBatteryState(
        @SerializedName("battery_percentage1") val percentageRemaining1: Int,
        @SerializedName("battery_voltage1") val voltageLevel1: Float,
        @SerializedName("battery_status1") val batteryStatus1: BatteryStatus,
        @SerializedName("battery_percentage2") val percentageRemaining2: Int,
        @SerializedName("battery_voltage2") val voltageLevel2: Float,
        @SerializedName("battery_status2") val batteryStatus2: BatteryStatus
    ) : BatteryData()
}

enum class WebSocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}


enum class BatteryStatus constructor(val index: Int) {
    /**
     * Battery is operating without issue
     */
    NORMAL(0),

    /**
     * Battery charge is starting to get low, to the point that the aircraft should return home
     */
    WARNING_LEVEL_1(1),

    /**
     * Battery charge is starting to get very low, to the point that the aircraft should
     * land immediately.
     */
    WARNING_LEVEL_2(2),

    /**
     * Battery has an error that is preventing a proper reading
     */
    ERROR(3),

    /**
     * Battery temperature is too high
     */
    OVERHEATING(4),

    /**
     * The state of the battery is unknown or the system is initializing
     */
    UNKNOWN(5);

}


fun getBatteryStatus(level: Int): BatteryStatus {
    return when {
        level >= 50 -> BatteryStatus.NORMAL
        level in 20..49 -> BatteryStatus.WARNING_LEVEL_1
        level in 1..19 -> BatteryStatus.WARNING_LEVEL_2
        else -> BatteryStatus.ERROR
    }
}
