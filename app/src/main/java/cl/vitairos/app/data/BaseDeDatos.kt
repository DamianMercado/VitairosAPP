package cl.vitairos.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Tabla «reportes» de la base SQLite local (Room).
 * Archivo en el celular: /data/data/cl.vitairos.app/databases/vitairos.db
 */
@Entity(tableName = "reportes")
data class Reporte(
    @PrimaryKey val id: String,          // UUID
    val folio: String,                   // VTR-AAMMDD-XXXX
    val tipo: String,
    val proceso: String,
    val observaciones: String,
    val fotoPath: String?,               // foto en almacenamiento privado de la app
    val usuario: String,
    val usuarioNombre: String,
    val creadoEn: Long,
    val estado: Estado,                  // se guarda como texto: BORRADOR, EN_COLA, SINCRONIZADO, ERROR
    val intentos: Int = 0,
    val mensaje: String? = null,
    val sincronizadoEn: Long? = null,
    val comprobante: String? = null,
)

@Dao
interface ReporteDao {
    @Query("SELECT * FROM reportes WHERE usuario = :usuario ORDER BY creadoEn DESC")
    fun observarDe(usuario: String): Flow<List<Reporte>>

    @Query("SELECT * FROM reportes WHERE id = :id")
    fun observar(id: String): Flow<Reporte?>

    @Query("SELECT * FROM reportes WHERE id = :id")
    suspend fun obtener(id: String): Reporte?

    @Query("SELECT * FROM reportes WHERE usuario = :usuario AND estado = 'EN_COLA' ORDER BY creadoEn ASC")
    suspend fun enCola(usuario: String): List<Reporte>

    @Upsert
    suspend fun guardar(reporte: Reporte)

    /** Solo se elimina si aún no se envió (Borrador o En cola). */
    @Query("DELETE FROM reportes WHERE id = :id AND estado IN ('BORRADOR', 'EN_COLA')")
    suspend fun eliminar(id: String): Int
}

@Database(entities = [Reporte::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reporteDao(): ReporteDao

    companion object {
        const val NOMBRE = "vitairos.db"
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NOMBRE).build()
    }
}
