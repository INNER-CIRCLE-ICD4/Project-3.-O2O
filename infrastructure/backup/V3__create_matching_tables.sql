SET search_path TO matching;

-- Matching requests queue table
CREATE TABLE matching_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
    pickup_h3 VARCHAR(15) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMP,
    processed_at TIMESTAMP,
    status VARCHAR(30) NOT NULL, -- PENDING, PROCESSING, COMPLETED, FAILED
    batch_id UUID,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for matching requests
CREATE INDEX idx_matching_requests_status ON matching_requests(status);
CREATE INDEX idx_matching_requests_batch_id ON matching_requests(batch_id);
CREATE INDEX idx_matching_requests_created_at ON matching_requests(created_at);
CREATE INDEX idx_matching_requests_ride_id ON matching_requests(ride_id);

-- Partial index for pending requests
CREATE INDEX idx_matching_requests_pending ON matching_requests(status, created_at)
    WHERE status = 'PENDING';

-- Surge pricing table
CREATE TABLE surge_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    h3_index VARCHAR(15) NOT NULL,
    surge_multiplier DECIMAL(3,2) NOT NULL CHECK (surge_multiplier >= 1.0 AND surge_multiplier <= 5.0),
    demand_count INTEGER NOT NULL DEFAULT 0,
    supply_count INTEGER NOT NULL DEFAULT 0,
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for surge prices
CREATE INDEX idx_surge_prices_h3_index ON surge_prices(h3_index);
CREATE INDEX idx_surge_prices_effective ON surge_prices(effective_from, effective_to);

-- Partial index for active surge prices
CREATE INDEX idx_surge_prices_active ON surge_prices(h3_index, effective_from)
    WHERE effective_to IS NULL;
--     WHERE effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP;

