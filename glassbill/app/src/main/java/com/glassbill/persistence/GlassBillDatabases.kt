package com.glassbill.persistence

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.glassbill.data.BusinessProfileRepository
import com.glassbill.data.CatalogItemRepository
import com.glassbill.data.CompanyOpener
import com.glassbill.data.CompanyRegistry
import com.glassbill.data.CompanySession
import com.glassbill.data.CompanySummary
import com.glassbill.data.PartyRepository
import com.glassbill.data.PreferenceRepository
import com.glassbill.data.TaxRateRepository
import com.glassbill.data.UnitRepository
import com.glassbill.domain.BusinessProfile
import com.glassbill.domain.CatalogItem
import com.glassbill.domain.CompanyId
import com.glassbill.domain.ItemKind
import com.glassbill.domain.MeasurementUnit
import com.glassbill.domain.Party
import com.glassbill.domain.PartyRole
import com.glassbill.domain.TaxRate
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first

@Entity(tableName = "company_registry")
data class CompanyRecordEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val databaseFileName: String,
    val createdAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long?,
)

@Dao
interface CompanyRegistryDao {
    @Query("SELECT * FROM company_registry ORDER BY lastOpenedAtEpochMillis DESC, displayName ASC")
    suspend fun list(): List<CompanyRecordEntity>

    @Query("SELECT * FROM company_registry WHERE id = :id")
    suspend fun get(id: String): CompanyRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: CompanyRecordEntity)

    @Update
    suspend fun update(record: CompanyRecordEntity)

    @Delete
    suspend fun delete(record: CompanyRecordEntity)
}

@Database(entities = [CompanyRecordEntity::class], version = 1, exportSchema = false)
abstract class RegistryDatabase : RoomDatabase() {
    abstract fun companyRegistryDao(): CompanyRegistryDao
}

@Entity(tableName = "business_profile")
data class BusinessProfileEntity(
    @PrimaryKey val id: String = "profile",
    val displayName: String,
    val phone: String,
    val email: String,
    val address: String,
    val taxRegistration: String,
)

@Dao
interface BusinessProfileDao {
    @Query("SELECT * FROM business_profile WHERE id = 'profile'")
    suspend fun get(): BusinessProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BusinessProfileEntity)
}

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val openingBalanceMinor: Long,
    val paymentTermDays: Int?,
)

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY name ASC")
    suspend fun list(): List<PartyEntity>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun get(id: String): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartyEntity)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun delete(id: String)
}

@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val symbol: String,
)

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY name ASC")
    suspend fun list(): List<UnitEntity>

    @Query("SELECT * FROM units WHERE id = :id")
    suspend fun get(id: String): UnitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UnitEntity)

    @Query("DELETE FROM units WHERE id = :id")
    suspend fun delete(id: String)
}

@Entity(tableName = "unit_conversions")
data class UnitConversionEntity(
    @PrimaryKey val id: String,
    val primaryUnitId: String,
    val secondaryUnitId: String,
    val secondaryUnitsPerPrimary: String,
)

@Entity(tableName = "tax_rates")
data class TaxRateEntity(
    @PrimaryKey val label: String,
    val percent: String,
    val active: Boolean = true,
)

@Dao
interface TaxRateDao {
    @Query("SELECT * FROM tax_rates ORDER BY label ASC")
    suspend fun list(): List<TaxRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaxRateEntity)

    @Query("DELETE FROM tax_rates WHERE label = :label")
    suspend fun delete(label: String)
}

@Entity(tableName = "catalog_items")
data class CatalogItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val itemKind: String,
    val unitId: String?,
    val taxRateId: String?,
    val openingQuantity: String,
    val salePriceMinor: Long?,
    val purchasePriceMinor: Long?,
)

@Dao
interface CatalogItemDao {
    @Query("SELECT * FROM catalog_items ORDER BY name ASC")
    suspend fun list(): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE id = :id")
    suspend fun get(id: String): CatalogItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogItemEntity)

    @Query("DELETE FROM catalog_items WHERE id = :id")
    suspend fun delete(id: String)
}

@Entity(tableName = "typed_preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface PreferenceDao {
    @Query("SELECT value FROM typed_preferences WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: PreferenceEntity)

    @Query("SELECT * FROM typed_preferences")
    suspend fun all(): List<PreferenceEntity>
}

@Database(
    entities = [
        BusinessProfileEntity::class,
        PartyEntity::class,
        UnitEntity::class,
        UnitConversionEntity::class,
        TaxRateEntity::class,
        CatalogItemEntity::class,
        PreferenceEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CompanyDatabase : RoomDatabase() {
    abstract fun businessProfileDao(): BusinessProfileDao
    abstract fun partyDao(): PartyDao
    abstract fun unitDao(): UnitDao
    abstract fun taxRateDao(): TaxRateDao
    abstract fun catalogItemDao(): CatalogItemDao
    abstract fun preferenceDao(): PreferenceDao
}

class CompanyDatabaseFactory(private val context: Context) {
    fun open(databaseFileName: String): CompanyDatabase =
        Room.databaseBuilder(context, CompanyDatabase::class.java, databaseFileName).build()
}

class RoomCompanyRegistry(private val db: RegistryDatabase) : CompanyRegistry {
    override suspend fun listCompanies(): List<CompanySummary> =
        db.companyRegistryDao().list().map { it.toSummary() }

    override suspend fun createCompany(displayName: String): CompanySummary {
        val id = UUID.randomUUID().toString()
        val fileName = "company_$id.vyp"
        val now = System.currentTimeMillis()
        val record = CompanyRecordEntity(id, displayName, fileName, now, now)
        db.companyRegistryDao().insert(record)
        return record.toSummary()
    }

    override suspend fun markOpened(companyId: CompanyId, openedAt: Instant) {
        val existing = db.companyRegistryDao().get(companyId.value) ?: return
        db.companyRegistryDao().update(existing.copy(lastOpenedAtEpochMillis = openedAt.toEpochMilli()))
    }

    override suspend fun renameCompany(companyId: CompanyId, displayName: String) {
        val existing = db.companyRegistryDao().get(companyId.value) ?: return
        db.companyRegistryDao().update(existing.copy(displayName = displayName))
    }

    override suspend fun deleteCompany(companyId: CompanyId) {
        val existing = db.companyRegistryDao().get(companyId.value) ?: return
        db.companyRegistryDao().delete(existing)
    }
}

private fun CompanyRecordEntity.toSummary() = CompanySummary(
    id = CompanyId(id),
    displayName = displayName,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    lastOpenedAt = lastOpenedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
)

class RoomCompanySession(
    override val companyId: CompanyId,
    private val db: CompanyDatabase,
) : CompanySession {
    override val parties: PartyRepository = object : PartyRepository {
        override suspend fun list(): List<Party> = db.partyDao().list().map { it.toDomain() }
        override suspend fun get(id: String): Party? = db.partyDao().get(id)?.toDomain()
        override suspend fun save(party: Party) { db.partyDao().upsert(party.toEntity()) }
        override suspend fun delete(id: String) { db.partyDao().delete(id) }
    }
    override val units: UnitRepository = object : UnitRepository {
        override suspend fun list(): List<MeasurementUnit> = db.unitDao().list().map { it.toDomain() }
        override suspend fun get(id: String): MeasurementUnit? = db.unitDao().get(id)?.toDomain()
        override suspend fun save(unit: MeasurementUnit) { db.unitDao().upsert(unit.toEntity()) }
        override suspend fun delete(id: String) { db.unitDao().delete(id) }
    }
    override val taxRates: TaxRateRepository = object : TaxRateRepository {
        override suspend fun list(): List<TaxRate> = db.taxRateDao().list().map { it.toDomain() }
        override suspend fun save(taxRate: TaxRate) { db.taxRateDao().upsert(taxRate.toEntity()) }
        override suspend fun delete(label: String) { db.taxRateDao().delete(label) }
    }
    override val items: CatalogItemRepository = object : CatalogItemRepository {
        override suspend fun list(): List<CatalogItem> = db.catalogItemDao().list().map { it.toDomain() }
        override suspend fun get(id: String): CatalogItem? = db.catalogItemDao().get(id)?.toDomain()
        override suspend fun save(item: CatalogItem) { db.catalogItemDao().upsert(item.toEntity()) }
        override suspend fun delete(id: String) { db.catalogItemDao().delete(id) }
    }
    override val businessProfile: BusinessProfileRepository = object : BusinessProfileRepository {
        override suspend fun get(): BusinessProfile =
            db.businessProfileDao().get()?.toDomain() ?: BusinessProfile("My Business")
        override suspend fun save(profile: BusinessProfile) {
            db.businessProfileDao().upsert(profile.toEntity())
        }
    }
    override val preferences: PreferenceRepository = object : PreferenceRepository {
        override suspend fun get(key: String): String? = db.preferenceDao().get(key)
        override suspend fun set(key: String, value: String) {
            db.preferenceDao().set(PreferenceEntity(key, value))
        }
        override suspend fun all(): Map<String, String> =
            db.preferenceDao().all().associate { it.key to it.value }
    }
}

class RoomCompanyOpener(
    private val context: Context,
    private val factory: CompanyDatabaseFactory,
    private val registry: CompanyRegistry,
) : CompanyOpener {
    private val openDbs = mutableMapOf<String, CompanyDatabase>()

    override suspend fun open(companyId: CompanyId): CompanySession {
        val fileName = "company_${companyId.value}.vyp"
        val db = openDbs.getOrPut(fileName) { factory.open(fileName) }
        return RoomCompanySession(companyId, db)
    }

    override suspend fun create(displayName: String): CompanySession {
        val summary = registry.createCompany(displayName)
        val fileName = "company_${summary.id.value}.vyp"
        val db = openDbs.getOrPut(fileName) { factory.open(fileName) }
        return RoomCompanySession(summary.id, db)
    }
}

// Mapping helpers
private fun PartyEntity.toDomain() = Party(id, name, PartyRole.valueOf(role), openingBalanceMinor, paymentTermDays)
private fun Party.toEntity() = PartyEntity(id, name, role.name, openingBalanceMinor, paymentTermDays)
private fun UnitEntity.toDomain() = MeasurementUnit(id, name, symbol)
private fun MeasurementUnit.toEntity() = UnitEntity(id, name, symbol)
private fun TaxRateEntity.toDomain() = TaxRate(label, java.math.BigDecimal(percent))
private fun TaxRate.toEntity() = TaxRateEntity(label, percent.toPlainString())
private fun CatalogItemEntity.toDomain() = CatalogItem(
    id = id,
    name = name,
    itemKind = ItemKind.valueOf(itemKind),
    unitId = unitId,
    taxRateId = taxRateId,
    openingQuantity = java.math.BigDecimal(openingQuantity),
    salePriceMinor = salePriceMinor,
    purchasePriceMinor = purchasePriceMinor,
)
private fun CatalogItem.toEntity() = CatalogItemEntity(
    id = id,
    name = name,
    itemKind = itemKind.name,
    unitId = unitId,
    taxRateId = taxRateId,
    openingQuantity = openingQuantity.toPlainString(),
    salePriceMinor = salePriceMinor,
    purchasePriceMinor = purchasePriceMinor,
)
private fun BusinessProfileEntity.toDomain() = BusinessProfile(displayName, phone, email, address, taxRegistration)
private fun BusinessProfile.toEntity() = BusinessProfileEntity(displayName = displayName, phone = phone, email = email, address = address, taxRegistration = taxRegistration)