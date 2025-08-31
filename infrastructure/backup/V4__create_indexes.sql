SET search_path TO matching;

-- Additional composite indexes for performance optimization

-- Rides table composite indexes
CREATE INDEX idx_rides_matching_batch ON rides(pickup_h3, status, requested_at)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_rides_driver_location ON rides(driver_id, status, pickup_h3)
    WHERE status IN ('DRIVER_ASSIGNED', 'EN_ROUTE_TO_PICKUP', 'ARRIVED_AT_PICKUP');

-- Driver calls optimization
CREATE INDEX idx_driver_calls_batch_processing ON driver_calls(ride_id, status, sequence_number)
    WHERE status = 'PENDING';

-- Matching requests optimization
CREATE INDEX idx_matching_requests_batch_processing ON matching_requests(status, pickup_h3, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');

-- Surge price lookup optimization
CREATE INDEX idx_surge_prices_lookup ON surge_prices(h3_index, surge_multiplier, effective_from DESC)
    WHERE effective_to IS NULL;

-- Functions for updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply updated_at trigger to rides table
CREATE TRIGGER update_rides_updated_at BEFORE UPDATE ON rides
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
