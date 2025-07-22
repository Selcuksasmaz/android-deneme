package com.example.snapfilterdemo.facerecognition

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Yüz vektörlerini ve kişi bilgilerini saklayan veritabanı varlığı
 */
@Entity(tableName = "saved_faces")
data class SavedFace(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val personName: String,
    val faceVector: FloatArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SavedFace

        if (id != other.id) return false
        if (personName != other.personName) return false
        if (!faceVector.contentEquals(other.faceVector)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + personName.hashCode()
        result = 31 * result + faceVector.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Float dizisini veritabanına dönüştürücü
 */
class FloatArrayConverter {
    @TypeConverter
    fun fromFloatArray(value: FloatArray?): String? {
        return value?.joinToString(",")
    }

    @TypeConverter
    fun toFloatArray(value: String?): FloatArray? {
        return value?.split(",")?.map { it.toFloat() }?.toFloatArray()
    }
}

/**
 * Yüz veri erişim nesnesi (DAO)
 */
@Dao
interface FaceDao {
    @Insert
    suspend fun insertFace(face: SavedFace): Long

    // Bu satırı değiştireceğiz
    @Query("SELECT * FROM saved_faces")
    suspend fun getAllFaces(): List<SavedFace>

    @Query("SELECT * FROM saved_faces")
    fun getAllFacesFlow(): Flow<List<SavedFace>>

    @Query("SELECT * FROM saved_faces WHERE personName = :name LIMIT 1")
    suspend fun getFaceByName(name: String): SavedFace?

    @Delete
    suspend fun deleteFace(face: SavedFace)

    @Query("DELETE FROM saved_faces WHERE personName = :name")
    suspend fun deleteFacesByName(name: String)
}

/**
 * Yüz veritabanı
 */
@Database(entities = [SavedFace::class], version = 1)
@TypeConverters(FloatArrayConverter::class)
abstract class FaceDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao

    companion object {
        @Volatile
        private var INSTANCE: FaceDatabase? = null

        fun getDatabase(context: Context): FaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java,
                    "face_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Yüz veritabanı üzerinde işlemleri yöneten repository
 */
class FaceRepository(context: Context) {
    private val faceDao: FaceDao = FaceDatabase.getDatabase(context).faceDao()

    suspend fun addFace(face: SavedFace): Long {
        return faceDao.insertFace(face)
    }

    suspend fun getAllFaces(): List<SavedFace> {
        return faceDao.getAllFaces()
    }

    fun getAllFacesFlow(): Flow<List<SavedFace>> {
        return faceDao.getAllFacesFlow()
    }

    suspend fun getFaceByName(name: String): SavedFace? {
        return faceDao.getFaceByName(name)
    }

    suspend fun deleteFace(face: SavedFace) {
        faceDao.deleteFace(face)
    }

    suspend fun deleteFacesByName(name: String) {
        faceDao.deleteFacesByName(name)
    }
}