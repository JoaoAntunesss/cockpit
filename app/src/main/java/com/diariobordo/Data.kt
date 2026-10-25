package com.diariobordo

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ---------- Entidades (equivalem às coleções planeadas para o Firestore) ----------

@Entity
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String,      // chave da Brand, define o tema
    val model: String,
    val year: Int,
    val engine: String,
    val fuelType: String,
    val tankL: Int,
    val plate: String,
    val km: Int,
    @ColumnInfo(defaultValue = "0") val purchasePrice: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val purchaseKm: Int = 0,
    @ColumnInfo(defaultValue = "0") val currentValue: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val monthlyBudget: Double = 0.0,
)

@Entity(indices = [Index("vehicleId")])
data class Tire(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val brand: String,
    val size: String,        // ex.: 205/55 R16
    val kind: String,        // Verão, Inverno, Todas as estações
    val mountDate: Long,
    val mountKm: Int,
    val rotationKm: Int,     // km da última rotação (0 = nunca)
    val active: Boolean,     // montados agora
    val removedKm: Int,      // km do carro quando saíram (0 se ainda montados)
    val notes: String,
)

@Entity(indices = [Index("vehicleId")])
data class Fueling(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val date: Long,         // LocalDate.toEpochDay()
    val km: Int,
    val liters: Double,
    val total: Double,
    val full: Boolean,
    val station: String,
    @ColumnInfo(defaultValue = "0") val electric: Boolean = false,   // carga elétrica de um plug-in
)

@Entity(indices = [Index("vehicleId")])
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val km: Int,
    val title: String,
    val category: String,
    val byMe: Boolean,
    val shop: String,
    val parts: Double,
    val labor: Double,
    val notes: String,
    @ColumnInfo(defaultValue = "''") val image: String = "",   // foto ou fatura
)

@Entity(indices = [Index("vehicleId")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val type: String,       // Seguro, IUC, Inspeção, Portagem...
    val amount: Double,
    @ColumnInfo(defaultValue = "''") val image: String = "",   // foto ou fatura
)

@Entity(indices = [Index("vehicleId")])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val name: String,
    val category: String,   // se registares uma intervenção desta categoria, o aviso recomeça
    val everyKm: Int,       // 0 = não usa km
    val everyMonths: Int,   // 0 = não usa meses
    val lastKm: Int,
    val lastDate: Long,
)

@Entity
data class Doc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val type: String,       // Inspeção, Seguro, IUC, ...
    val title: String,      // seguradora ou entidade
    val number: String,     // apólice ou referência
    val expiry: Long,       // dia em que acaba (epochDay)
    val cost: Double,
    val notes: String,
    val image: String,      // caminho do print/foto guardado na app (vazio = sem imagem)
    val warnDays: Int,      // avisar com tantos dias de antecedência
)

@Entity
data class FuelCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,      // chave de STATION_BRANDS
    val name: String,
    val number: String,
    val format: String,     // formato ZXing: CODE_128, EAN_13, QR_CODE...
    val notes: String,
)

// ---------- DAO ----------

@Dao
abstract class AppDao {
    @Query("SELECT * FROM Vehicle ORDER BY id") abstract fun vehicles(): Flow<List<Vehicle>>
    @Query("SELECT * FROM Fueling WHERE vehicleId = :v ORDER BY date DESC, km DESC") abstract fun fuelings(v: Long): Flow<List<Fueling>>
    @Query("SELECT * FROM Service WHERE vehicleId = :v ORDER BY date DESC, km DESC") abstract fun services(v: Long): Flow<List<Service>>
    @Query("SELECT * FROM Expense WHERE vehicleId = :v ORDER BY date DESC") abstract fun expenses(v: Long): Flow<List<Expense>>
    @Query("SELECT * FROM Reminder WHERE vehicleId = :v ORDER BY id") abstract fun reminders(v: Long): Flow<List<Reminder>>
    @Query("SELECT * FROM Doc WHERE vehicleId = :v ORDER BY expiry") abstract fun docs(v: Long): Flow<List<Doc>>

    @Query("SELECT * FROM Tire WHERE vehicleId = :v ORDER BY id") abstract fun tires(v: Long): Flow<List<Tire>>
    @Query("SELECT * FROM Tire") abstract suspend fun allTires(): List<Tire>
    @Upsert abstract suspend fun saveTire(x: Tire): Long
    @Delete abstract suspend fun delete(x: Tire)
    @Query("DELETE FROM Tire WHERE vehicleId = :v") abstract suspend fun clearTires(v: Long)

    @Query("SELECT * FROM FuelCard ORDER BY name") abstract fun cards(): Flow<List<FuelCard>>
    @Query("SELECT * FROM FuelCard") abstract suspend fun allCards(): List<FuelCard>
    @Upsert abstract suspend fun saveCard(x: FuelCard): Long
    @Delete abstract suspend fun delete(x: FuelCard)

    @Query("SELECT * FROM Vehicle") abstract suspend fun allVehicles(): List<Vehicle>
    @Query("SELECT * FROM Fueling") abstract suspend fun allFuel(): List<Fueling>
    @Query("SELECT * FROM Service") abstract suspend fun allServices(): List<Service>
    @Query("SELECT * FROM Expense") abstract suspend fun allExpenses(): List<Expense>
    @Query("SELECT * FROM Reminder") abstract suspend fun allReminders(): List<Reminder>
    @Query("SELECT * FROM Doc") abstract suspend fun allDocs(): List<Doc>

    @Upsert abstract suspend fun saveVehicle(x: Vehicle): Long
    @Upsert abstract suspend fun saveFueling(x: Fueling): Long
    @Upsert abstract suspend fun saveService(x: Service): Long
    @Upsert abstract suspend fun saveExpense(x: Expense): Long
    @Upsert abstract suspend fun saveReminder(x: Reminder): Long
    @Upsert abstract suspend fun saveDoc(x: Doc): Long

    @Delete abstract suspend fun delete(x: Fueling)
    @Delete abstract suspend fun delete(x: Service)
    @Delete abstract suspend fun delete(x: Expense)
    @Delete abstract suspend fun delete(x: Reminder)
    @Delete abstract suspend fun delete(x: Doc)

    @Query("UPDATE Vehicle SET km = MAX(km, :km) WHERE id = :id") abstract suspend fun bumpKm(id: Long, km: Int)
    @Query("UPDATE Reminder SET lastKm = :km, lastDate = :date WHERE vehicleId = :v AND category = :cat AND lastDate <= :date")
    abstract suspend fun resetReminders(v: Long, cat: String, km: Int, date: Long)

    @Query("DELETE FROM Fueling WHERE vehicleId = :v") abstract suspend fun clearFuel(v: Long)
    @Query("DELETE FROM Service WHERE vehicleId = :v") abstract suspend fun clearServ(v: Long)
    @Query("DELETE FROM Expense WHERE vehicleId = :v") abstract suspend fun clearExp(v: Long)
    @Query("DELETE FROM Reminder WHERE vehicleId = :v") abstract suspend fun clearRem(v: Long)
    @Query("DELETE FROM Doc WHERE vehicleId = :v") abstract suspend fun clearDocs(v: Long)
    @Query("DELETE FROM Vehicle WHERE id = :v") abstract suspend fun clearVehicle(v: Long)

    @Transaction
    open suspend fun deleteVehicle(v: Long) {
        clearFuel(v); clearTires(v); clearServ(v); clearExp(v); clearRem(v); clearDocs(v); clearVehicle(v)
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Doc` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, `title` TEXT NOT NULL, `number` TEXT NOT NULL, `expiry` INTEGER NOT NULL, " +
                "`cost` REAL NOT NULL, `notes` TEXT NOT NULL, `image` TEXT NOT NULL, `warnDays` INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Service` ADD COLUMN `image` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `Expense` ADD COLUMN `image` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Fueling` ADD COLUMN `electric` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Vehicle` ADD COLUMN `purchasePrice` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `Vehicle` ADD COLUMN `purchaseKm` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `Vehicle` ADD COLUMN `currentValue` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `Vehicle` ADD COLUMN `monthlyBudget` REAL NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Tire` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, " +
                "`brand` TEXT NOT NULL, `size` TEXT NOT NULL, `kind` TEXT NOT NULL, `mountDate` INTEGER NOT NULL, `mountKm` INTEGER NOT NULL, " +
                "`rotationKm` INTEGER NOT NULL, `active` INTEGER NOT NULL, `removedKm` INTEGER NOT NULL, `notes` TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Tire_vehicleId` ON `Tire` (`vehicleId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `FuelCard` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `brand` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `number` TEXT NOT NULL, `format` TEXT NOT NULL, `notes` TEXT NOT NULL)"
        )
    }
}

@Database(
    entities = [Vehicle::class, Fueling::class, Service::class, Expense::class, Reminder::class, Doc::class, FuelCard::class, Tire::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "diario.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { inst = it }
        }
    }
}
