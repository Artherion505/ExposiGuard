package com.exposiguard.app.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: String = "default",
    val name: String = "",
    val weight: Double = 70.0, // kg
    val height: Double = 170.0, // cm
    val age: Int = 25,
    val gender: Gender = Gender.OTHER,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable {

    val bmi: Double
        get() = if (height > 0) weight / ((height / 100) * (height / 100)) else 0.0

    val bmiCategory: BMICategory
        get() = when {
            bmi < 18.5 -> BMICategory.UNDERWEIGHT
            bmi < 25.0 -> BMICategory.NORMAL
            bmi < 30.0 -> BMICategory.OVERWEIGHT
            else -> BMICategory.OBESE
        }
}

enum class Gender {
    MALE, FEMALE, OTHER
}

enum class BMICategory {
    UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE;

    val displayName: String
        get() = when (this) {
            UNDERWEIGHT -> "Bajo peso"
            NORMAL -> "Normal"
            OVERWEIGHT -> "Sobrepeso"
            OBESE -> "Obeso"
        }
}
