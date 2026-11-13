package com.superplumber.odyssey

import android.content.Context

class SaveManager(context: Context) {
    private val prefs = context.getSharedPreferences("super_plumber_odyssey_v1", Context.MODE_PRIVATE)

    var sfxEnabled: Boolean
        get() = prefs.getBoolean("sfx_enabled", true)
        set(value) = prefs.edit().putBoolean("sfx_enabled", value).apply()

    var bgmEnabled: Boolean
        get() = prefs.getBoolean("bgm_enabled", true)
        set(value) = prefs.edit().putBoolean("bgm_enabled", value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics_enabled", true)
        set(value) = prefs.edit().putBoolean("haptics_enabled", value).apply()

    var autoRunEnabled: Boolean
        get() = prefs.getBoolean("auto_run_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_run_enabled", value).apply()

    var unlockAllEnabled: Boolean
        get() = prefs.getBoolean("unlock_all_enabled", false)
        set(value) = prefs.edit().putBoolean("unlock_all_enabled", value).apply()

    var controlScale: Float
        get() = prefs.getFloat("control_scale", 1.0f)
        set(value) = prefs.edit().putFloat("control_scale", value).apply()

    var controlOpacity: Float
        get() = prefs.getFloat("control_opacity", 0.85f)
        set(value) = prefs.edit().putFloat("control_opacity", value).apply()

    fun getMissionSave(missionId: Int): MissionSave {
        val unlocked = if (missionId == 1 || unlockAllEnabled) true
        else prefs.getBoolean("m_${missionId}_unlocked", false)
        val stars = prefs.getInt("m_${missionId}_stars", 0)
        val bestTime = prefs.getInt("m_${missionId}_best_time", 0)
        val highScore = prefs.getInt("m_${missionId}_high_score", 0)
        return MissionSave(unlocked, stars, bestTime, highScore)
    }

    fun saveMissionResult(missionId: Int, stars: Int, timeRemaining: Int, score: Int, totalMissions: Int) {
        val prev = getMissionSave(missionId)
        val editor = prefs.edit()
        editor.putBoolean("m_${missionId}_unlocked", true)
        if (stars > prev.stars) {
            editor.putInt("m_${missionId}_stars", stars)
        }
        if (timeRemaining > prev.bestTimeSec) {
            editor.putInt("m_${missionId}_best_time", timeRemaining)
        }
        if (score > prev.highScore) {
            editor.putInt("m_${missionId}_high_score", score)
        }
        if (missionId < totalMissions) {
            editor.putBoolean("m_${missionId + 1}_unlocked", true)
        }
        editor.apply()
    }

    fun getTotalStars(totalMissions: Int): Int {
        var sum = 0
        for (i in 1..totalMissions) {
            sum += getMissionSave(i).stars
        }
        return sum
    }
}
