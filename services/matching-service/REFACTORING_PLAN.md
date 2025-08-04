# Matching Service Refactoring Plan

## Overview
Split multi-class files into individual files following one-class-per-file pattern.

## Files to Refactor (9 files, 50 classes total)

### 1. exception/MatchingException.kt (11 classes)
- **Keep**: MatchingException.kt (base class)
- **Split to new files**:
  - exception/RideNotFoundException.kt
  - exception/NoAvailableDriverException.kt
  - exception/InvalidRideStateException.kt
  - exception/InvalidRideStateTransitionException.kt
  - exception/MatchingTimeoutException.kt
  - exception/DriverCallNotFoundException.kt
  - exception/DriverCallExpiredException.kt
  - exception/InvalidDriverCallStateException.kt
  - exception/RideAlreadyMatchedException.kt
  - exception/DuplicateRideRequestException.kt
  - exception/EventPublishException.kt

### 2. dto/internal/MatchingResult.kt (2 classes)
- **Keep**: MatchingResult.kt
- **Split to new file**:
  - dto/internal/MatchedDriver.kt

### 3. dto/response/RideResponseDto.kt (2 classes)
- **Keep**: RideResponseDto.kt
- **Split to new file**:
  - dto/response/FareDto.kt

### 4. dto/request/RideRequestDto.kt (3 classes)
- **Keep**: RideRequestDto.kt
- **Split to new files**:
  - dto/request/LocationDto.kt (Note: naming conflict!)
  - dto/request/FareEstimateDto.kt

### 5. config/FeignConfig.kt (3 classes)
- **Keep**: FeignConfig.kt
- **Move to exception package**:
  - exception/ServiceUnavailableException.kt
  - exception/ResourceNotFoundException.kt

### 6. config/CacheConfig.kt (2 classes)
- **Keep**: CacheConfig.kt
- **Move to new location**:
  - cache/CustomKeyGenerator.kt

### 7. event/model/MatchingEvent.kt (3 classes)
- **Delete original, split to**:
  - event/model/matching/MatchingRequestCreatedEvent.kt
  - event/model/matching/MatchingSuccessEvent.kt
  - event/model/matching/MatchingRequestExpiredEvent.kt

### 8. event/model/ExternalEvent.kt (15 classes)
- **Delete original, organize by service**:
  - event/model/payment/:
    - PaymentProcessedEvent.kt
    - PaymentFailedEvent.kt
    - PaymentRefundedEvent.kt
  - event/model/driver/:
    - DriverLocationUpdatedEvent.kt
    - DriverStatusChangedEvent.kt
    - DriverAvailabilityChangedEvent.kt
    - DriverRatingUpdatedEvent.kt
  - event/model/notification/:
    - NotificationDeliveredEvent.kt
    - NotificationFailedEvent.kt
  - event/model/user/:
    - UserProfileUpdatedEvent.kt
    - UserPaymentMethodUpdatedEvent.kt
  - event/model/location/:
    - TrafficConditionsUpdatedEvent.kt
    - TrafficIncident.kt
    - LocationInfo.kt
  - event/model/pricing/:
    - SurgePricingUpdatedEvent.kt

### 9. event/model/RideEvent.kt (8 classes)
- **Delete original, split to**:
  - event/model/ride/RideRequestedEvent.kt
  - event/model/ride/RideMatchedEvent.kt
  - event/model/ride/RideStatusChangedEvent.kt
  - event/model/ride/RideCancelledEvent.kt
  - event/model/ride/RideCompletedEvent.kt
  - event/model/ride/DriverCallRequestEvent.kt
  - event/model/ride/DriverCallInfo.kt
  - event/model/ride/LocationDto.kt (Note: naming conflict!)

## Naming Conflicts to Resolve
1. **LocationDto** appears in:
   - dto/request/RideRequestDto.kt → dto/request/RequestLocationDto.kt
   - event/model/RideEvent.kt → event/model/ride/EventLocationDto.kt
2. **LocationInfo** appears in:
   - dto/internal/LocationInfo.kt (keep as is)
   - event/model/ExternalEvent.kt → event/model/location/TrafficLocationInfo.kt

## Import Updates Required
- All files importing from multi-class files need import updates
- Estimate: ~50-70 import statements across the module

## Execution Order
1. Create new directory structure
2. Split exception files first (base class dependency)
3. Split DTO files
4. Split event model files (create subdirectories)
5. Split config-related classes
6. Update all imports
7. Validate compilation