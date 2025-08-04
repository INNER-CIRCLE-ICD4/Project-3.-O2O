import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';
import { randomItem, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// Custom metrics
const matchingSuccessRate = new Rate('matching_success_rate');
const matchingTime = new Trend('matching_time');
const rideRequests = new Counter('ride_requests');
const activeDrivers = new Gauge('active_drivers');

// Test configuration
export const options = {
  scenarios: {
    // Simulate 강남 rush hour traffic
    rush_hour_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },  // Ramp up to 100 users
        { duration: '5m', target: 500 },  // Ramp up to 500 users (rush hour)
        { duration: '10m', target: 1000 }, // Peak load - 1000 concurrent users
        { duration: '5m', target: 500 },  // Scale down
        { duration: '2m', target: 0 },    // Ramp down to 0 users
      ],
      gracefulRampDown: '30s',
    },
    // Simulate surge pricing scenario
    surge_scenario: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 200,
      maxVUs: 500,
      exec: 'surgePricingTest',
      startTime: '10m',
    },
    // Test driver availability changes
    driver_availability: {
      executor: 'per-vu-iterations',
      vus: 50,
      iterations: 10,
      exec: 'driverAvailabilityTest',
      startTime: '5m',
    }
  },
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'], // 95% of requests under 1s
    matching_success_rate: ['rate>0.95'], // 95% success rate
    matching_time: ['p(95)<1000'], // 95% of matches under 1s
    http_req_failed: ['rate<0.05'], // Less than 5% failure rate
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'; // nginx load balancer

// 강남 area H3 indexes
const GANGNAM_H3_CELLS = [
  '882a100d9ffffff', // 강남역
  '882a100dbffffff', // 역삼역
  '882a100ddffffff', // 선릉역
  '882a100c1ffffff', // 삼성역
  '882a100c3ffffff', // 신논현역
];

// Test data generators
function generateRideRequest() {
  const pickupH3 = randomItem(GANGNAM_H3_CELLS);
  const dropoffH3 = randomItem(GANGNAM_H3_CELLS.filter(h3 => h3 !== pickupH3));
  
  return {
    passengerId: `p${randomIntBetween(1000000, 9999999)}-test-${__VU}-${__ITER}`,
    pickupLocation: {
      latitude: 37.4979 + (Math.random() - 0.5) * 0.01,
      longitude: 127.0276 + (Math.random() - 0.5) * 0.01,
      h3Index: pickupH3,
      address: `Test pickup location ${__VU}-${__ITER}`
    },
    dropoffLocation: {
      latitude: 37.5172 + (Math.random() - 0.5) * 0.01,
      longitude: 127.0473 + (Math.random() - 0.5) * 0.01,
      h3Index: dropoffH3,
      address: `Test dropoff location ${__VU}-${__ITER}`
    }
  };
}

// Main test scenario - ride request flow
export default function () {
  const rideRequest = generateRideRequest();
  
  // Step 1: Create ride request
  const createResponse = http.post(
    `${BASE_URL}/api/v1/rides`,
    JSON.stringify(rideRequest),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': rideRequest.passengerId,
      },
      tags: { name: 'CreateRide' },
    }
  );
  
  const rideCreated = check(createResponse, {
    'ride created successfully': (r) => r.status === 201,
    'ride ID returned': (r) => r.json('rideId') !== undefined,
  });
  
  if (!rideCreated) {
    return;
  }
  
  rideRequests.add(1);
  const rideId = createResponse.json('rideId');
  const startTime = new Date();
  
  // Step 2: Poll for matching status
  let matched = false;
  let attempts = 0;
  const maxAttempts = 30; // 30 seconds timeout
  
  while (!matched && attempts < maxAttempts) {
    sleep(1);
    
    const statusResponse = http.get(
      `${BASE_URL}/api/v1/rides/${rideId}`,
      {
        headers: {
          'X-User-Id': rideRequest.passengerId,
        },
        tags: { name: 'CheckRideStatus' },
      }
    );
    
    if (statusResponse.status === 200) {
      const ride = statusResponse.json();
      if (ride.status === 'MATCHED' || ride.status === 'DRIVER_ASSIGNED') {
        matched = true;
        const matchTime = new Date() - startTime;
        matchingTime.add(matchTime);
        matchingSuccessRate.add(1);
        
        check(statusResponse, {
          'driver assigned': (r) => r.json('driverId') !== undefined,
          'match time under 1s': () => matchTime < 1000,
        });
      } else if (ride.status === 'FAILED' || ride.status === 'CANCELLED') {
        matchingSuccessRate.add(0);
        break;
      }
    }
    
    attempts++;
  }
  
  if (!matched) {
    matchingSuccessRate.add(0);
  }
  
  // Step 3: Simulate ride completion or cancellation
  if (matched && Math.random() > 0.1) { // 90% complete, 10% cancel
    sleep(randomIntBetween(5, 10)); // Simulate ride duration
    
    // Complete the ride
    const completeResponse = http.post(
      `${BASE_URL}/api/v1/rides/${rideId}/complete`,
      JSON.stringify({
        actualDropoffLocation: rideRequest.dropoffLocation,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': rideRequest.passengerId,
        },
        tags: { name: 'CompleteRide' },
      }
    );
    
    check(completeResponse, {
      'ride completed': (r) => r.status === 200,
    });
  } else if (matched) {
    // Cancel the ride
    const cancelResponse = http.post(
      `${BASE_URL}/api/v1/rides/${rideId}/cancel`,
      JSON.stringify({
        reason: 'PASSENGER_CANCELLED',
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': rideRequest.passengerId,
        },
        tags: { name: 'CancelRide' },
      }
    );
    
    check(cancelResponse, {
      'ride cancelled': (r) => r.status === 200,
    });
  }
}

// Surge pricing test scenario
export function surgePricingTest() {
  // Create multiple requests in the same area to trigger surge
  const targetH3 = randomItem(GANGNAM_H3_CELLS);
  const requests = [];
  
  for (let i = 0; i < 10; i++) {
    const rideRequest = {
      passengerId: `surge-test-${__VU}-${__ITER}-${i}`,
      pickupLocation: {
        latitude: 37.4979 + (Math.random() - 0.5) * 0.001,
        longitude: 127.0276 + (Math.random() - 0.5) * 0.001,
        h3Index: targetH3,
        address: `Surge test location ${i}`
      },
      dropoffLocation: {
        latitude: 37.5172,
        longitude: 127.0473,
        h3Index: '882a100c1ffffff',
        address: 'Surge test destination'
      }
    };
    
    const response = http.post(
      `${BASE_URL}/api/v1/rides`,
      JSON.stringify(rideRequest),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': rideRequest.passengerId,
        },
        tags: { name: 'SurgeRideRequest' },
      }
    );
    
    if (response.status === 201) {
      requests.push(response.json());
    }
  }
  
  // Check if surge pricing was applied
  sleep(2);
  
  requests.forEach(ride => {
    const statusResponse = http.get(
      `${BASE_URL}/api/v1/rides/${ride.rideId}`,
      {
        headers: {
          'X-User-Id': ride.passengerId,
        },
        tags: { name: 'CheckSurgePrice' },
      }
    );
    
    check(statusResponse, {
      'surge multiplier applied': (r) => r.json('surgeMultiplier') > 1.0,
    });
  });
}

// Driver availability test
export function driverAvailabilityTest() {
  const driverId = `d${randomIntBetween(1000000, 9999999)}-test-${__VU}`;
  const h3Cell = randomItem(GANGNAM_H3_CELLS);
  
  // Update driver location
  const updateResponse = http.post(
    `${BASE_URL}/api/v1/drivers/${driverId}/location`,
    JSON.stringify({
      latitude: 37.4979 + (Math.random() - 0.5) * 0.01,
      longitude: 127.0276 + (Math.random() - 0.5) * 0.01,
      h3Index: h3Cell,
      available: true,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Driver-Id': driverId,
      },
      tags: { name: 'UpdateDriverLocation' },
    }
  );
  
  check(updateResponse, {
    'driver location updated': (r) => r.status === 200 || r.status === 201,
  });
  
  activeDrivers.add(1);
  
  // Simulate driver going offline after some time
  sleep(randomIntBetween(30, 120));
  
  const offlineResponse = http.post(
    `${BASE_URL}/api/v1/drivers/${driverId}/status`,
    JSON.stringify({
      status: 'OFFLINE',
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Driver-Id': driverId,
      },
      tags: { name: 'DriverGoOffline' },
    }
  );
  
  check(offlineResponse, {
    'driver went offline': (r) => r.status === 200,
  });
  
  activeDrivers.add(-1);
}

// WebSocket test for real-time updates (optional)
export function websocketTest() {
  // This would test WebSocket connections for real-time ride tracking
  // Implementation depends on K6 WebSocket support
}

// Cleanup function
export function teardown(data) {
  console.log('Test completed');
  console.log(`Total ride requests: ${rideRequests}`);
  console.log(`Matching success rate: ${matchingSuccessRate}`);
  console.log(`Average matching time: ${matchingTime}`);
}