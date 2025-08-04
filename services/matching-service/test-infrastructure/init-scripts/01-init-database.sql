-- Test Database Initialization Script
-- Creates schema and test data for matching service

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "h3" CASCADE;

-- Create enum types
CREATE TYPE ride_status AS ENUM (
    'REQUESTED',
    'MATCHED',
    'DRIVER_ASSIGNED',
    'EN_ROUTE_TO_PICKUP',
    'ARRIVED_AT_PICKUP',
    'ON_TRIP',
    'COMPLETED',
    'CANCELLED',
    'FAILED'
);

CREATE TYPE matching_request_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'MATCHED',
    'FAILED',
    'EXPIRED',
    'CANCELLED'
);

CREATE TYPE driver_call_status AS ENUM (
    'PENDING',
    'CALLING',
    'ACCEPTED',
    'REJECTED',
    'NO_RESPONSE',
    'EXPIRED'
);

-- Create tables
CREATE TABLE IF NOT EXISTS rides (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    passenger_id UUID NOT NULL,
    driver_id UUID,
    status ride_status NOT NULL DEFAULT 'REQUESTED',
    pickup_location JSONB NOT NULL,
    dropoff_location JSONB NOT NULL,
    pickup_location_h3 VARCHAR(15) NOT NULL,
    dropoff_location_h3 VARCHAR(15) NOT NULL,
    distance_km NUMERIC(10, 2),
    duration_minutes INTEGER,
    base_fare NUMERIC(10, 2),
    surge_multiplier NUMERIC(3, 2) DEFAULT 1.0,
    total_fare NUMERIC(10, 2),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    matched_at TIMESTAMP,
    pickup_at TIMESTAMP,
    dropoff_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancelled_by VARCHAR(20),
    cancellation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS matching_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ride_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    pickup_h3_index VARCHAR(15) NOT NULL,
    dropoff_h3_index VARCHAR(15) NOT NULL,
    status matching_request_status NOT NULL DEFAULT 'PENDING',
    batch_id UUID,
    surge_multiplier NUMERIC(3, 2) DEFAULT 1.0,
    matched_driver_id UUID,
    matching_score NUMERIC(5, 2),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    last_no_driver_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS driver_calls (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ride_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    status driver_call_status NOT NULL DEFAULT 'PENDING',
    sequence_number INTEGER NOT NULL,
    called_at TIMESTAMP,
    responded_at TIMESTAMP,
    response_time_seconds INTEGER,
    estimated_arrival_seconds INTEGER,
    rejection_reason VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ride_state_transitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ride_id UUID NOT NULL,
    from_state VARCHAR(50),
    to_state VARCHAR(50) NOT NULL,
    transition_reason VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_rides_passenger_id ON rides(passenger_id);
CREATE INDEX idx_rides_driver_id ON rides(driver_id);
CREATE INDEX idx_rides_status ON rides(status);
CREATE INDEX idx_rides_pickup_h3 ON rides(pickup_location_h3);
CREATE INDEX idx_rides_dropoff_h3 ON rides(dropoff_location_h3);
CREATE INDEX idx_rides_requested_at ON rides(requested_at);
CREATE INDEX idx_rides_status_requested_at ON rides(status, requested_at);

CREATE INDEX idx_matching_requests_ride_id ON matching_requests(ride_id);
CREATE INDEX idx_matching_requests_status ON matching_requests(status);
CREATE INDEX idx_matching_requests_batch_id ON matching_requests(batch_id);
CREATE INDEX idx_matching_requests_pickup_h3 ON matching_requests(pickup_h3_index);
CREATE INDEX idx_matching_requests_status_requested_at ON matching_requests(status, requested_at);

CREATE INDEX idx_driver_calls_ride_id ON driver_calls(ride_id);
CREATE INDEX idx_driver_calls_driver_id ON driver_calls(driver_id);
CREATE INDEX idx_driver_calls_status ON driver_calls(status);

CREATE INDEX idx_state_transitions_ride_id ON ride_state_transitions(ride_id);
CREATE INDEX idx_state_transitions_created_at ON ride_state_transitions(created_at);

-- Create foreign key constraints
ALTER TABLE matching_requests 
    ADD CONSTRAINT fk_matching_requests_ride 
    FOREIGN KEY (ride_id) REFERENCES rides(id) ON DELETE CASCADE;

ALTER TABLE driver_calls 
    ADD CONSTRAINT fk_driver_calls_ride 
    FOREIGN KEY (ride_id) REFERENCES rides(id) ON DELETE CASCADE;

ALTER TABLE ride_state_transitions 
    ADD CONSTRAINT fk_state_transitions_ride 
    FOREIGN KEY (ride_id) REFERENCES rides(id) ON DELETE CASCADE;

-- Create update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_rides_updated_at BEFORE UPDATE ON rides
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_matching_requests_updated_at BEFORE UPDATE ON matching_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_driver_calls_updated_at BEFORE UPDATE ON driver_calls
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();