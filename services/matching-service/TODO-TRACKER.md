# TODO Tracker - Matching Service

## Executive Summary

**Total TODOs Found: 24**  
**Emergency Fixes: 22** (91.7%)  
**Pre-existing TODOs: 2** (8.3%)  
**Status: CRITICAL** - Significant technical debt accumulated during emergency compilation fixes

## Critical TODOs (Immediate Action Required)

### CRITICAL - Repository Method Implementations
**Priority: P0** | **Estimated: 2-3 days** | **Impact: HIGH**

| File | Line | Issue | Status |
|------|------|-------|--------|
| `SurgePriceServiceImpl.kt` | 55 | Missing `findActiveByH3Index` method | CRITICAL |
| `SurgePriceServiceImpl.kt` | 75 | Missing `deactivatePreviousPrices` method | CRITICAL |
| `SurgePriceServiceImpl.kt` | 150 | Missing `countRecentRequestsByH3Index` method | CRITICAL |
| `SurgePriceServiceImpl.kt` | 190 | Missing repository query methods | CRITICAL |
| `RideServiceImpl.kt` | 304 | Missing `findByDriverIdOrderByRequestedAtDesc` method | HIGH |

**Dependencies:** Requires repository interface extensions and query implementations.

### CRITICAL - Entity Business Logic
**Priority: P0** | **Estimated: 1-2 days** | **Impact: HIGH**

| File | Line | Issue | Status |
|------|------|-------|--------|
| `MatchingRequest.kt` | 96 | Temporary `markAsMatched()` method implementation | CRITICAL |
| `RideServiceImpl.kt` | 208 | Missing `isCancellable()` method in Ride entity | HIGH |
| `RideServiceImpl.kt` | 337 | Rating properties access issues | MEDIUM |

**Dependencies:** Entity design improvements and proper business method implementations.

### HIGH - Service Integration Issues
**Priority: P1** | **Estimated: 1-2 days** | **Impact: MEDIUM**

| File | Line | Issue | Status |
|------|------|-------|--------|
| `MatchingServiceImpl.kt` | 21 | Import conflict resolution with `DistanceCalculator` | HIGH |
| `MatchingServiceImpl.kt` | 93 | Using `findPendingRequests` instead of `findActiveRequests` | HIGH |
| `MatchingServiceImpl.kt` | 288 | Location type mismatch in event publishing | HIGH |
| `FareCalculationServiceImpl.kt` | 143 | Missing `matchingProperties.fare` configuration | HIGH |

## Technical Debt Inventory

### Entity Layer Issues
- **Missing Properties**: `vehicleType`, `paymentMethodId`, `pickedUpAt` in Ride entity
- **Business Logic**: Incomplete method implementations for state transitions
- **Rating System**: Property name mismatches between expected and actual

### Repository Layer Issues
- **Missing Query Methods**: 6 critical repository methods not implemented
- **H3 Geo-indexing**: Surge pricing queries not functional
- **Pagination**: Ride history queries using fallback implementations

### Service Layer Issues
- **Configuration**: Missing fare calculation properties
- **Event Publishing**: Type mismatches in Kafka event producers
- **State Management**: Temporary parameter fixes in state machine context

### DTO Layer Issues
- **AvailableDriver**: Added temporary properties for compilation
- **Location Types**: Inconsistencies between `LocationInfo` and `LocationDto`

## Pre-existing TODOs

### Infrastructure Improvements
**Priority: P2** | **Estimated: 1 week** | **Impact: LOW**

| File | Line | Issue | Status |
|------|------|-------|--------|
| `RideEventProducer.kt` | 155 | Implement dead letter queue or retry mechanism | MEDIUM |
| `MatchingEventProducer.kt` | 146 | Implement dead letter queue or retry mechanism | MEDIUM |

## Refactoring Roadmap

### Phase 1: Critical Repository Methods (Week 1)
1. **SurgePriceRepository** implementation
   - `findActiveByH3Index(String, LocalDateTime): SurgePrice?`
   - `deactivatePreviousPrices(String, LocalDateTime): Int`
   - `countRecentRequestsByH3Index(String, LocalDateTime): Long`
   - `countRequestsBetween(String, LocalDateTime, LocalDateTime): Long`

2. **RideRepository** extensions
   - `findByDriverIdOrderByRequestedAtDesc(UUID, Pageable): Page<Ride>`
   - `findByPassengerIdOrderByRequestedAtDesc(UUID, Pageable): Page<Ride>`

### Phase 2: Entity Business Logic (Week 2)
1. **Ride Entity** improvements
   - Implement `isCancellable(): Boolean` method
   - Fix rating property names (`ratingByPassenger`, `ratingByDriver`)
   - Add proper `complete()` method with distance/duration parameters

2. **MatchingRequest Entity** cleanup
   - Replace temporary `markAsMatched()` with proper state machine integration
   - Implement proper completion workflow

### Phase 3: Service Layer Cleanup (Week 3)
1. **Configuration** setup
   - Add `matchingProperties.fare` configuration
   - Implement proper fare calculation rates

2. **Event Publishing** fixes
   - Resolve Location type mismatches
   - Implement proper driver location tracking

3. **Import Resolution**
   - Clean up `DistanceCalculator` import conflicts
   - Consolidate utility classes

### Phase 4: Integration & Testing (Week 4)
1. **Test Suite** restoration
   - Fix integration test compilation issues
   - Implement missing unit tests
   - Add test coverage for new business methods

2. **Documentation** updates
   - API documentation for new repository methods
   - Business logic documentation
   - Configuration guide updates

## Estimation & Timeline

| Phase | Duration | Effort | Risk Level |
|-------|----------|--------|------------|
| Critical Repository Methods | 1 week | 2 developers | HIGH |
| Entity Business Logic | 3-4 days | 1 developer | MEDIUM |
| Service Layer Cleanup | 3-4 days | 1 developer | LOW |
| Integration & Testing | 1 week | 1 developer | MEDIUM |
| **Total** | **3-4 weeks** | **2-3 developers** | **HIGH** |

## Risk Assessment

### High Risk Items
- **Repository Methods**: Core functionality depends on these implementations
- **Entity State Management**: Business logic integrity at risk
- **Integration Tests**: No test coverage currently available

### Medium Risk Items
- **Event Publishing**: May cause runtime errors in production
- **Configuration**: Default values may not match production requirements

### Low Risk Items
- **Import Conflicts**: Cosmetic issues that don't affect functionality
- **Dead Letter Queues**: Infrastructure improvements for resilience

## Next Steps

1. **Immediate (This Week)**:
   - Implement critical repository methods
   - Fix entity business logic methods
   - Restore basic test functionality

2. **Short Term (2-3 weeks)**:
   - Complete service layer cleanup
   - Add comprehensive test coverage
   - Update configuration management

3. **Long Term (1-2 months)**:
   - Implement dead letter queue mechanism
   - Performance optimization
   - Documentation improvements

---

**Generated on:** 2025-08-04  
**Status:** ACTIVE  
**Next Review:** Weekly  
**Owner:** Development Team