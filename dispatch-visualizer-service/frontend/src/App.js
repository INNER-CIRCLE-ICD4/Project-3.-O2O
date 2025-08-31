import React, { useState, useEffect } from 'react';

function App() {
  const [notifications, setNotifications] = useState([]);
  const [driverCount, setDriverCount] = useState(10);

  useEffect(() => {
    // WebSocket 연결
    const ws = new WebSocket(`ws://${window.location.host}/ws/notifications`);

    ws.onopen = () => {
      console.log('Connected to backend WebSocket');
      setNotifications(prev => [...prev, '[System] Connected to backend WebSocket']);
    };

    ws.onmessage = (event) => {
      console.log('Received notification:', event.data);
      setNotifications(prev => [...prev, `[Notification] ${event.data}`]);
    };

    ws.onclose = () => {
      console.log('Disconnected from backend WebSocket');
      setNotifications(prev => [...prev, '[System] Disconnected from backend WebSocket']);
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      setNotifications(prev => [...prev, `[System] WebSocket error: ${error.message}`]);
    };

    return () => {
      ws.close();
    };
  }, []);

  const handleStartSimulation = async () => {
    try {
      const response = await fetch(`/api/simulate/drivers/start?count=${driverCount}`, {
        method: 'POST',
      });
      const data = await response.text();
      setNotifications(prev => [...prev, `[API] ${data}`]);
    } catch (error) {
      console.error('Error starting simulation:', error);
      setNotifications(prev => [...prev, `[API Error] ${error.message}`]);
    }
  };

  const handleRequestRide = async () => {
    try {
      const response = await fetch('/api/simulate/rides/request', {
        method: 'POST',
      });
      const data = await response.text();
      setNotifications(prev => [...prev, `[API] ${data}`]);
    } catch (error) {
      console.error('Error requesting ride:', error);
      setNotifications(prev => [...prev, `[API Error] ${error.message}`]);
    }
  };

  return (
    <div style={{ fontFamily: 'Arial, sans-serif', padding: '20px' }}>
      <h1>Dispatch Visualizer</h1>

      <div style={{ marginBottom: '20px', border: '1px solid #ccc', padding: '15px', borderRadius: '8px' }}>
        <h2>Driver Simulation</h2>
        <label>
          Number of Drivers:
          <input
            type="number"
            value={driverCount}
            onChange={(e) => setDriverCount(parseInt(e.target.value))}
            min="1"
            style={{ marginLeft: '10px', padding: '5px' }}
          />
        </label>
        <button 
          onClick={handleStartSimulation}
          style={{ marginLeft: '20px', padding: '10px 15px', backgroundColor: '#4CAF50', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}
        >
          Start Driver Simulation
        </button>
      </div>

      <div style={{ marginBottom: '20px', border: '1px solid #ccc', padding: '15px', borderRadius: '8px' }}>
        <h2>Ride Request</h2>
        <button 
          onClick={handleRequestRide}
          style={{ padding: '10px 15px', backgroundColor: '#008CBA', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}
        >
          Request Ride
        </button>
      </div>

      <div style={{ border: '1px solid #ccc', padding: '15px', borderRadius: '8px' }}>
        <h2>Notifications</h2>
        <div style={{ height: '300px', overflowY: 'scroll', border: '1px solid #eee', padding: '10px', backgroundColor: '#f9f9f9' }}>
          {notifications.map((msg, index) => (
            <p key={index} style={{ margin: '5px 0', fontSize: '0.9em' }}>{msg}</p>
          ))}
        </div>
      </div>
    </div>
  );
}

export default App;
