package com.glassbill

import android.content.Context
import com.glassbill.data.CompanySummary
import com.glassbill.data.CompanyOpener
import com.glassbill.data.CompanyRegistry
import com.glassbill.data.CompanySession
import com.glassbill.domain.BusinessProfile
import com.glassbill.domain.CompanyId
import com.glassbill.domain.DocumentKind
import com.glassbill.domain.Party
import com.glassbill.domain.Voucher
import com.glassbill.domain.VoucherLine
import com.glassbill.domain.VoucherStatus
import com.glassbill.persistence.CompanyDatabaseFactory
import com.glassbill.persistence.RoomCompanyOpener
import com.glassbill.persistence.RoomCompanyRegistry
import com.glassbill.persistence.RegistryDatabase
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GlassBillController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val registryDb: RegistryDatabase =
        Room.databaseBuilder(appContext, RegistryDatabase::class.java, "registry.db").build()
    private val registry: CompanyRegistry = RoomCompanyRegistry(registryDb)
    private val factory = CompanyDatabaseFactory(appContext)
    private val opener: CompanyOpener = RoomCompanyOpener(appContext, factory, registry)

    private val _companies = MutableStateFlow<List<CompanySummary>>(emptyList())
    val companies: StateFlow<List<CompanySummary>> = _companies.asStateFlow()

    private val _currentCompanyId = MutableStateFlow<CompanyId?>(null)
    val currentCompanyId: StateFlow<CompanyId?> = _currentCompanyId.asStateFlow()

    private val _session = MutableStateFlow<CompanySession?>(null)
    val session: StateFlow<CompanySession?> = _session.asStateFlow()

    private val _parties = MutableStateFlow<List<Party>>(emptyList())
    val parties: StateFlow<List<Party>> = _parties.asStateFlow()

    private val _profile = MutableStateFlow<BusinessProfile?>(null)
    val profile: StateFlow<BusinessProfile?> = _profile.asStateFlow()

    private val _vouchers = MutableStateFlow<List<Voucher>>(emptyList())
    val vouchers: StateFlow<List<Voucher>> = _vouchers.asStateFlow()

    private val _items = MutableStateFlow<List<com.glassbill.domain.CatalogItem>>(emptyList())
    val items: StateFlow<List<com.glassbill.domain.CatalogItem>> = _items.asStateFlow()

    private val _units = MutableStateFlow<List<com.glassbill.domain.MeasurementUnit>>(emptyList())
    val units: StateFlow<List<com.glassbill.domain.MeasurementUnit>> = _units.asStateFlow()

    private val _taxRates = MutableStateFlow<List<com.glassbill.domain.TaxRate>>(emptyList())
    val taxRates: StateFlow<List<com.glassbill.domain.TaxRate>> = _taxRates.asStateFlow()

    init {
        scope.launch {
            refreshCompanies()
            val list = _companies.value
            if (list.isNotEmpty()) {
                openCompany(list.first().id)
            } else {
                val session = opener.create("My Business")
                _currentCompanyId.value = session.companyId
                _session.value = session
                refreshCompanies()
                loadData()
            }
        }
    }

    private suspend fun refreshCompanies() {
        _companies.value = registry.listCompanies()
    }

    private suspend fun loadData() {
        val s = _session.value ?: return
        _parties.value = s.parties.list()
        _profile.value = s.businessProfile.get()
        _vouchers.value = s.vouchers.list()
        _items.value = s.items.list()
        _units.value = s.units.list()
        _taxRates.value = s.taxRates.list()
    }

    fun openCompany(id: CompanyId) {
        scope.launch {
            val session = opener.open(id)
            registry.markOpened(id, java.time.Instant.now())
            _currentCompanyId.value = id
            _session.value = session
            loadData()
            refreshCompanies()
        }
    }

    fun createCompany(name: String) {
        scope.launch {
            val session = opener.create(name)
            registry.markOpened(session.companyId, java.time.Instant.now())
            _currentCompanyId.value = session.companyId
            _session.value = session
            loadData()
            refreshCompanies()
        }
    }

    fun addParty(party: Party) {
        scope.launch {
            _session.value?.parties?.save(party)
            loadData()
        }
    }

    fun deleteParty(id: String) {
        scope.launch {
            _session.value?.parties?.delete(id)
            loadData()
        }
    }

    fun updateProfile(profile: BusinessProfile) {
        scope.launch {
            _session.value?.businessProfile?.save(profile)
            loadData()
        }
    }

    fun createSale(party: Party?, lines: List<VoucherLine>, paidMinor: Long = 0) {
        scope.launch {
            val s = _session.value ?: return@launch
            val number = s.numberSeries.next(DocumentKind.Sale)
            val voucher = Voucher(
                kind = DocumentKind.Sale,
                number = number,
                partyId = party?.id,
                partyName = party?.name ?: "",
                lines = lines,
                paidAmountMinor = paidMinor,
            )
            s.vouchers.save(voucher)
            lines.mapNotNull { it.itemId }.distinct().forEach { itemId ->
                val line = lines.first { it.itemId == itemId }
                s.stock.save(
                    com.glassbill.domain.StockMovement(
                        itemId = itemId,
                        voucherId = voucher.id,
                        quantityDelta = line.quantity.negate(),
                    ),
                )
            }
            loadData()
        }
    }

    fun deleteVoucher(id: String) {
        scope.launch {
            _session.value?.vouchers?.delete(id)
            loadData()
        }
    }

    fun addItem(item: com.glassbill.domain.CatalogItem) {
        scope.launch {
            _session.value?.items?.save(item)
            loadData()
        }
    }
}