package com.audibleclone.data.local

import androidx.room.TypeConverter
import com.audibleclone.model.BookModels.BookSource
import com.audibleclone.model.BookModels.BookStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun stringListToJson(list: List<String>?): String? {
        return if (list == null) null else gson.toJson(list)
    }
    
    @TypeConverter
    fun jsonToStringList(json: String?): List<String>? {
        return if (json == null) null else gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
    }
    
    @TypeConverter
    fun bookSourceToString(source: BookSource?): String? {
        return source?.name
    }
    
    @TypeConverter
    fun stringToBookSource(name: String?): BookSource? {
        return if (name == null) null else BookSource.valueOf(name)
    }
    
    @TypeConverter
    fun bookStatusToString(status: BookStatus?): String? {
        return status?.name
    }
    
    @TypeConverter
    fun stringToBookStatus(name: String?): BookStatus? {
        return if (name == null) null else BookStatus.valueOf(name)
    }
}