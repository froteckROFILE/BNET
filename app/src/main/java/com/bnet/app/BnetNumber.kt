package com.bnet.app

import android.content.Context
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object BnetNumber {
    fun getOrCreate(context: Context): String {
        val prefs=context.getSharedPreferences("bnet",Context.MODE_PRIVATE)
        prefs.getString("number",null)?.let{return it}
        val date=LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyMMdd"))
        val suffix=SecureRandom().nextInt(100_000_000).toString().padStart(8,'0')
        return "+$date-$suffix".also{prefs.edit().putString("number",it).apply()}
    }
}
