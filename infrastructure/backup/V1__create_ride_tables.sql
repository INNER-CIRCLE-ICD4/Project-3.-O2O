SET search_path TO matching;

-- Extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Ride table
CREATE TABLE rides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passenger_id UUID NOT NULL,
    driver_id UUID,
    status VARCHAR(50) NOT NULL,
    pickup_latitude DOUBLE PRECISION NOT NULL,
    pickup_longitude DOUBLE PRECISION NOT NULL,
    pickup_address TEXT,
    pickup_h3 VARCHAR(15) NOT NULL,
    dropoff_latitude DOUBLE PRECISION NOT NULL,
    dropoff_longitude DOUBLE PRECISION NOT NULL,
    dropoff_address TEXT,
    dropoff_h3 VARCHAR(15) NOT NULL,
    distance_meters INTEGER,
    duration_seconds INTEGER,
    base_fare DECIMAL(10,2),
    surge_multiplier DECIMAL(3,2) DEFAULT 1.0,
    total_fare DECIMAL(10,2),
    currency VARCHAR(3) DEFAULT 'KRW',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    matched_at TIMESTAMP,
    pickup_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(100),
    cancelled_by VARCHAR(20), -- PASSENGER, DRIVER, SYSTEM
    rating_by_passenger INTEGER CHECK (rating_by_passenger BETWEEN 1 AND 5),
    rating_by_driver INTEGER CHECK (rating_by_driver BETWEEN 1 AND 5),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Indexes for rides
CREATE INDEX idx_rides_passenger_id ON rides(passenger_id);
CREATE INDEX idx_rides_driver_id ON rides(driver_id);
CREATE INDEX idx_rides_status ON rides(status);
CREATE INDEX idx_rides_pickup_h3 ON rides(pickup_h3);
CREATE INDEX idx_rides_requested_at ON rides(requested_at);
CREATE INDEX idx_rides_status_requested_at ON rides(status, requested_at);

-- Partial indexes for active rides
CREATE INDEX idx_rides_active_by_passenger ON rides(passenger_id, status)
    WHERE status IN ('REQUESTED', 'MATCHED', 'DRIVER_ASSIGNED', 'EN_ROUTE_TO_PICKUP', 'ARRIVED_AT_PICKUP', 'ON_TRIP');

CREATE INDEX idx_rides_active_by_driver ON rides(driver_id, status)
    WHERE status IN ('DRIVER_ASSIGNED', 'EN_ROUTE_TO_PICKUP', 'ARRIVED_AT_PICKUP', 'ON_TRIP');

-- Ride state transitions table
CREATE TABLE ride_state_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
    from_status VARCHAR(50) NOT NULL,
    to_status VARCHAR(50) NOT NULL,
    event VARCHAR(50) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ride_state_transitions_ride_id ON ride_state_transitions(ride_id);
CREATE INDEX idx_ride_state_transitions_created_at ON ride_state_transitions(created_at);
