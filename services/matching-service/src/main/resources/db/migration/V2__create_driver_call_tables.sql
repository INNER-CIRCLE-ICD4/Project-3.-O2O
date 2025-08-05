-- Driver calls table
CREATE TABLE driver_calls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL, -- Call order (1, 2, 3)
    status VARCHAR(30) NOT NULL, -- PENDING, ACCEPTED, REJECTED, EXPIRED, CANCELLED
    offered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    estimated_arrival_seconds INTEGER,
    estimated_fare DECIMAL(10,2),
    driver_latitude DOUBLE PRECISION,
    driver_longitude DOUBLE PRECISION,
    driver_h3 VARCHAR(15),
    distance_to_pickup_meters INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for driver calls
CREATE INDEX idx_driver_calls_ride_id ON driver_calls(ride_id);
CREATE INDEX idx_driver_calls_driver_id ON driver_calls(driver_id);
CREATE INDEX idx_driver_calls_status ON driver_calls(status);
CREATE INDEX idx_driver_calls_expires_at ON driver_calls(expires_at);
CREATE UNIQUE INDEX idx_driver_calls_ride_driver ON driver_calls(ride_id, driver_id);

-- Partial index for pending calls
CREATE INDEX idx_driver_calls_pending ON driver_calls(driver_id, status, expires_at)
    WHERE status = 'PENDING';