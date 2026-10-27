package com.glassbill.data

import com.glassbill.domain.BusinessProfile
import com.glassbill.domain.CatalogItem
import com.glassbill.domain.CompanyId
import com.glassbill.domain.MeasurementUnit
import com.glassbill.domain.Party
import com.glassbill.domain.PartyRole
import com.glassbill.domain.TaxRate
import java.time.Instant

data class CompanySummary(
    val id: CompanyId,
    val displayName: String,
    val createdAt: Instant,
    val lastOpenedAt: Instant?,
)

data class PartySummary(
    val id: String,
    val name: String,
    val role: PartyRole,
    val outstandingAmountMinor: Long,
)

interface CompanyRegistry {
    suspend fun listCompanies(): List<CompanySummary>
    suspend fun createCompany(displayName: String): CompanySummary
    suspend fun markOpened(companyId: CompanyId, openedAt: Instant)
    suspend fun renameCompany(companyId: CompanyId, displayName: String)
    suspend fun deleteCompany(companyId: CompanyId)
}

interface PartyRepository {
    suspend fun list(): List<Party>
    suspend fun get(id: String): Party?
    suspend fun save(party: Party)
    suspend fun delete(id: String)
}

interface UnitRepository {
    suspend fun list(): List<MeasurementUnit>
    suspend fun get(id: String): MeasurementUnit?
    suspend fun save(unit: MeasurementUnit)
    suspend fun delete(id: String)
}

interface TaxRateRepository {
    suspend fun list(): List<TaxRate>
    suspend fun save(taxRate: TaxRate)
    suspend fun delete(label: String)
}

interface CatalogItemRepository {
    suspend fun list(): List<CatalogItem>
    suspend fun get(id: String): CatalogItem?
    suspend fun save(item: CatalogItem)
    suspend fun delete(id: String)
}

interface BusinessProfileRepository {
    suspend fun get(): BusinessProfile
    suspend fun save(profile: BusinessProfile)
}

interface PreferenceRepository {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun all(): Map<String, String>
}

interface CompanySession {
    val companyId: CompanyId
    val parties: PartyRepository
    val units: UnitRepository
    val taxRates: TaxRateRepository
    val items: CatalogItemRepository
    val businessProfile: BusinessProfileRepository
    val preferences: PreferenceRepository
}

interface CompanyOpener {
    suspend fun open(companyId: CompanyId): CompanySession
    suspend fun create(displayName: String): CompanySession
}