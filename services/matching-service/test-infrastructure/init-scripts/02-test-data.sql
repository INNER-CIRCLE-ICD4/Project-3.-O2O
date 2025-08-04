-- Test Data for Matching Service
-- Simulates 강남 area drivers and passengers

-- Sample H3 indexes for 강남 area (resolution 8, ~460m hexagons)
-- These are example H3 indexes around Gangnam Station area
-- 882a100d9ffffff - 강남역
-- 882a100dbffffff - 역삼역  
-- 882a100ddffffff - 선릉역
-- 882a100c1ffffff - 삼성역
-- 882a100c3ffffff - 신논현역

-- Insert sample historical rides (for testing cache warming and analytics)
INSERT INTO rides (
    id, passenger_id, driver_id, status, 
    pickup_location, dropoff_location,
    pickup_location_h3, dropoff_location_h3,
    distance_km, duration_minutes, base_fare, surge_multiplier, total_fare,
    requested_at, matched_at, pickup_at, dropoff_at
) VALUES
-- Completed rides for analytics
('a1111111-1111-1111-1111-111111111111', 'p1111111-1111-1111-1111-111111111111', 'd1111111-1111-1111-1111-111111111111', 'COMPLETED',
 '{"lat": 37.4979, "lng": 127.0276, "address": "강남역 2번출구"}', '{"lat": 37.5172, "lng": 127.0473, "address": "삼성역 5번출구"}',
 '882a100d9ffffff', '882a100c1ffffff', 4.5, 15, 10000, 1.2, 12000,
 NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour 55 minutes', NOW() - INTERVAL '1 hour 50 minutes', NOW() - INTERVAL '1 hour 35 minutes'),

('a2222222-2222-2222-2222-222222222222', 'p2222222-2222-2222-2222-222222222222', 'd2222222-2222-2222-2222-222222222222', 'COMPLETED',
 '{"lat": 37.5089, "lng": 127.0638, "address": "선릉역 1번출구"}', '{"lat": 37.4979, "lng": 127.0276, "address": "강남역 2번출구"}',
 '882a100ddffffff', '882a100d9ffffff', 3.2, 12, 8000, 1.0, 8000,
 NOW() - INTERVAL '3 hours', NOW() - INTERVAL '2 hours 55 minutes', NOW() - INTERVAL '2 hours 50 minutes', NOW() - INTERVAL '2 hours 38 minutes'),

-- Cancelled ride
('a3333333-3333-3333-3333-333333333333', 'p3333333-3333-3333-3333-333333333333', 'd3333333-3333-3333-3333-333333333333', 'CANCELLED',
 '{"lat": 37.5045, "lng": 127.0498, "address": "역삼역 3번출구"}', '{"lat": 37.5172, "lng": 127.0473, "address": "삼성역 5번출구"}',
 '882a100dbffffff', '882a100c1ffffff', 2.8, 10, 7000, 1.0, 7000,
 NOW() - INTERVAL '1 hour', NOW() - INTERVAL '55 minutes', NULL, NULL),

-- Failed ride (no driver found)
('a4444444-4444-4444-4444-444444444444', 'p4444444-4444-4444-4444-444444444444', NULL, 'FAILED',
 '{"lat": 37.5133, "lng": 127.0397, "address": "신논현역 5번출구"}', '{"lat": 37.4979, "lng": 127.0276, "address": "강남역 2번출구"}',
 '882a100c3ffffff', '882a100d9ffffff', 3.5, 13, 9000, 1.5, 13500,
 NOW() - INTERVAL '30 minutes', NULL, NULL, NULL);

-- Insert sample matching requests
INSERT INTO matching_requests (
    ride_id, passenger_id, pickup_h3_index, dropoff_h3_index, 
    status, surge_multiplier, requested_at, expires_at
) VALUES
('a4444444-4444-4444-4444-444444444444', 'p4444444-4444-4444-4444-444444444444', 
 '882a100c3ffffff', '882a100d9ffffff', 'EXPIRED', 1.5, 
 NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '25 minutes');

-- Insert sample driver calls
INSERT INTO driver_calls (
    ride_id, driver_id, status, sequence_number, 
    called_at, responded_at, response_time_seconds, rejection_reason
) VALUES
('a3333333-3333-3333-3333-333333333333', 'd3333333-3333-3333-3333-333333333333', 
 'ACCEPTED', 1, NOW() - INTERVAL '55 minutes', NOW() - INTERVAL '54 minutes 45 seconds', 15, NULL),
('a4444444-4444-4444-4444-444444444444', 'd5555555-5555-5555-5555-555555555555', 
 'REJECTED', 1, NOW() - INTERVAL '29 minutes', NOW() - INTERVAL '28 minutes 50 seconds', 10, 'TOO_FAR'),
('a4444444-4444-4444-4444-444444444444', 'd6666666-6666-6666-6666-666666666666', 
 'NO_RESPONSE', 2, NOW() - INTERVAL '28 minutes', NULL, NULL, NULL);

-- Insert sample state transitions
INSERT INTO ride_state_transitions (
    ride_id, from_state, to_state, transition_reason, metadata
) VALUES
('a1111111-1111-1111-1111-111111111111', NULL, 'REQUESTED', 'Ride requested by passenger', '{"source": "mobile_app"}'),
('a1111111-1111-1111-1111-111111111111', 'REQUESTED', 'MATCHED', 'Driver found and matched', '{"matching_score": 85.5}'),
('a1111111-1111-1111-1111-111111111111', 'MATCHED', 'DRIVER_ASSIGNED', 'Driver accepted the ride', '{"response_time": 12}'),
('a1111111-1111-1111-1111-111111111111', 'DRIVER_ASSIGNED', 'EN_ROUTE_TO_PICKUP', 'Driver started navigation', '{"estimated_arrival": 300}'),
('a1111111-1111-1111-1111-111111111111', 'EN_ROUTE_TO_PICKUP', 'ARRIVED_AT_PICKUP', 'Driver arrived at pickup', '{"wait_time": 0}'),
('a1111111-1111-1111-1111-111111111111', 'ARRIVED_AT_PICKUP', 'ON_TRIP', 'Trip started', '{"actual_pickup_location": {"lat": 37.4979, "lng": 127.0276}}'),
('a1111111-1111-1111-1111-111111111111', 'ON_TRIP', 'COMPLETED', 'Trip completed successfully', '{"actual_distance": 4.6, "actual_duration": 16}');

-- Create a function to generate test drivers at specific H3 locations
CREATE OR REPLACE FUNCTION generate_test_drivers() RETURNS VOID AS $$
DECLARE
    h3_indexes TEXT[] := ARRAY[
        '882a100d9ffffff', -- 강남역
        '882a100dbffffff', -- 역삼역
        '882a100ddffffff', -- 선릉역
        '882a100c1ffffff', -- 삼성역
        '882a100c3ffffff'  -- 신논현역
    ];
    h3_index TEXT;
    i INTEGER;
BEGIN
    -- Generate 20 drivers per H3 cell (100 total)
    FOREACH h3_index IN ARRAY h3_indexes LOOP
        FOR i IN 1..20 LOOP
            -- Driver data would be managed by User Service
            -- Here we just note the distribution for testing
            RAISE NOTICE 'Would place driver % at H3 cell %', 
                'test-driver-' || h3_index || '-' || i, h3_index;
        END LOOP;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Execute the function
SELECT generate_test_drivers();

-- Create sample surge pricing areas
INSERT INTO rides (passenger_id, status, pickup_location, dropoff_location, 
                  pickup_location_h3, dropoff_location_h3, requested_at)
SELECT 
    'p' || uuid_generate_v4(),
    'REQUESTED',
    '{"lat": 37.4979, "lng": 127.0276, "address": "강남역"}',
    '{"lat": 37.5172, "lng": 127.0473, "address": "삼성역"}',
    '882a100d9ffffff',
    '882a100c1ffffff',
    NOW() - (random() * INTERVAL '10 minutes')
FROM generate_series(1, 10); -- Create 10 recent requests in 강남역 area for surge testing