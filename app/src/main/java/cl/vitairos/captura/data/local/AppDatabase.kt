package cl.vitairos.captura.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local (Room/SQLite) en el almacenamiento privado de la app.
 * Justificación: datos relacionales (reporte ↔ adjuntos ↔ eventos), consultas por estado,
 * transacciones y Flows reactivos para la UI. Preferencias simples van en DataStore.
 */
@Database(
    entities = [ReportEntity::class, AttachmentEntity::class, ReportEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun eventDao(): EventDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "vitairos_captura.db").build()
    }
}
