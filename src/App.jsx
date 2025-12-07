import React, { useState, useEffect } from 'react';
import { Geolocation } from '@capacitor/geolocation';
import { registerPlugin } from '@capacitor/core';

const LocationService = registerPlugin('LocationService');

function App() {
  const [isTracking, setIsTracking] = useState(false);
  const [lastLocation, setLastLocation] = useState(null);
  const [logs, setLogs] = useState([]);
  const [deviceName, setDeviceName] = useState('');
  const [sheetsUrl, setSheetsUrl] = useState('');
  const [showSetup, setShowSetup] = useState(true);

  useEffect(() => {
    const savedName = localStorage.getItem('device_name');
    const savedUrl = localStorage.getItem('sheets_url');

    if (savedName && savedUrl) {
      setDeviceName(savedName);
      setSheetsUrl(savedUrl);
      setShowSetup(false);
      addLog('App initialized');
    }
  }, []);

  const addLog = (msg) => {
    const time = new Date().toLocaleTimeString('en-IN');
    setLogs(prev => [`[${time}] ${msg}`, ...prev].slice(0, 50));
  };

  const handleSetup = () => {
    if (!deviceName || !sheetsUrl) {
      alert('Please fill all fields');
      return;
    }
    localStorage.setItem('device_name', deviceName);
    localStorage.setItem('sheets_url', sheetsUrl);
    setShowSetup(false);
    addLog('Setup complete');
  };

  const startTracking = async () => {
    try {
      const perm = await Geolocation.checkPermissions();
      if (perm.location !== 'granted') {
        await Geolocation.requestPermissions();
      }

      setIsTracking(true);
      addLog('Tracking started');

      // Start native service
      try {
        await LocationService.startService();
        addLog('Native service started');
      } catch (e) {
        addLog('Service error: ' + e.message);
      }

      // Capture first location
      captureLocation();

      // Set interval
      window.trackingInterval = setInterval(captureLocation, 60000); // 1 min

    } catch (e) {
      addLog('Error: ' + e.message);
    }
  };

  const stopTracking = async () => {
    clearInterval(window.trackingInterval);
    setIsTracking(false);
    addLog('Tracking stopped');

    try {
      await LocationService.stopService();
    } catch (e) {
      addLog('Stop service error: ' + e.message);
    }
  };

  const captureLocation = async () => {
    try {
      const pos = await Geolocation.getCurrentPosition({
        enableHighAccuracy: true,
        timeout: 60000
      });

      const loc = {
        lat: pos.coords.latitude,
        lng: pos.coords.longitude,
        time: new Date().toLocaleString('en-IN')
      };

      setLastLocation(loc);
      addLog(`📍 ${loc.lat.toFixed(6)}, ${loc.lng.toFixed(6)}`);

      // Post to Sheets
      postToSheets(loc);

    } catch (e) {
      addLog('❌ Location error: ' + e.message);
    }
  };

  const postToSheets = async (loc) => {
    try {
      const now = new Date();
      const payload = {
        deviceName: deviceName,
        day: now.toLocaleDateString('en-US', { weekday: 'long' }),
        date: now.toLocaleDateString('en-IN'),
        time: now.toLocaleTimeString('en-IN'),
        latitude: loc.lat,
        longitude: loc.lng,
        battery: 100,
        carrier: 'Unknown'
      };

      addLog('→ Posting to Sheets...');

      const response = await fetch(sheetsUrl, {
        method: 'POST',
        mode: 'no-cors',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      addLog('✓ Posted to Google Sheets');

    } catch (e) {
      addLog('❌ Sheets error: ' + e.message);
    }
  };

  if (showSetup) {
    return (
      <div style={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #1e3c72 0%, #2a5298 100%)',
        padding: '20px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center'
      }}>
        <div style={{
          background: 'white',
          borderRadius: '20px',
          padding: '40px',
          maxWidth: '500px',
          width: '100%',
          boxShadow: '0 20px 60px rgba(0,0,0,0.3)'
        }}>
          <h1 style={{
            fontSize: '32px',
            fontWeight: '700',
            marginBottom: '10px',
            color: '#1e3c72'
          }}>Traceract</h1>
          <p style={{ color: '#666', marginBottom: '30px' }}>Location Tracking Setup</p>

          <div style={{ marginBottom: '20px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              Device Name
            </label>
            <input
              type="text"
              placeholder="My Phone"
              value={deviceName}
              onChange={(e) => setDeviceName(e.target.value)}
              style={{
                width: '100%',
                padding: '12px',
                borderRadius: '10px',
                border: '2px solid #e0e0e0',
                fontSize: '16px'
              }}
            />
          </div>

          <div style={{ marginBottom: '30px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600' }}>
              Google Sheets URL
            </label>
            <textarea
              placeholder="https://script.google.com/..."
              value={sheetsUrl}
              onChange={(e) => setSheetsUrl(e.target.value)}
              rows="3"
              style={{
                width: '100%',
                padding: '12px',
                borderRadius: '10px',
                border: '2px solid #e0e0e0',
                fontSize: '14px',
                resize: 'none'
              }}
            />
          </div>

          <button
            onClick={handleSetup}
            style={{
              width: '100%',
              padding: '16px',
              background: 'linear-gradient(135deg, #1e3c72 0%, #2a5298 100%)',
              color: 'white',
              border: 'none',
              borderRadius: '12px',
              fontSize: '18px',
              fontWeight: '600',
              cursor: 'pointer'
            }}
          >
            Continue →
          </button>
        </div>
      </div>
    );
  }

  return (
    <div style={{
      minHeight: '100vh',
      background: '#f5f5f5',
      padding: '20px'
    }}>
      {/* Header */}
      <div style={{
        background: 'white',
        borderRadius: '16px',
        padding: '20px',
        marginBottom: '20px',
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h2 style={{ margin: 0, fontSize: '24px', fontWeight: '700' }}>Traceract</h2>
            <p style={{ margin: '5px 0 0 0', color: '#666', fontSize: '14px' }}>{deviceName}</p>
          </div>
          <div style={{
            width: '60px',
            height: '60px',
            borderRadius: '50%',
            background: isTracking
              ? 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)'
              : 'linear-gradient(135deg, #e74c3c 0%, #c0392b 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '24px'
          }}>
            {isTracking ? '▶' : '⏸'}
          </div>
        </div>
      </div>

      {/* Last Location */}
      {lastLocation && (
        <div style={{
          background: 'white',
          borderRadius: '16px',
          padding: '20px',
          marginBottom: '20px',
          boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
        }}>
          <div style={{ fontSize: '12px', color: '#999', marginBottom: '8px' }}>
            LAST LOCATION
          </div>
          <div style={{ fontSize: '18px', fontWeight: '600', marginBottom: '5px' }}>
            {lastLocation.lat.toFixed(6)}, {lastLocation.lng.toFixed(6)}
          </div>
          <div style={{ fontSize: '14px', color: '#666' }}>
            {lastLocation.time}
          </div>
        </div>
      )}

      {/* Control Button */}
      <button
        onClick={isTracking ? stopTracking : startTracking}
        style={{
          width: '100%',
          padding: '20px',
          background: isTracking
            ? 'linear-gradient(135deg, #e74c3c 0%, #c0392b 100%)'
            : 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)',
          color: 'white',
          border: 'none',
          borderRadius: '16px',
          fontSize: '20px',
          fontWeight: '700',
          cursor: 'pointer',
          marginBottom: '20px',
          boxShadow: '0 4px 12px rgba(0,0,0,0.15)'
        }}
      >
        {isTracking ? 'STOP TRACKING' : 'START TRACKING'}
      </button>

      {/* Logs */}
      <div style={{
        background: '#1a1a1a',
        borderRadius: '16px',
        padding: '20px',
        maxHeight: '300px',
        overflowY: 'auto'
      }}>
        <div style={{ fontSize: '12px', color: '#999', marginBottom: '10px' }}>
          ACTIVITY LOG
        </div>
        {logs.map((log, i) => (
          <div key={i} style={{
            color: '#00ff00',
            fontFamily: 'monospace',
            fontSize: '13px',
            marginBottom: '5px'
          }}>
            {log}
          </div>
        ))}
        {logs.length === 0 && (
          <div style={{ color: '#666', fontStyle: 'italic' }}>No activity yet</div>
        )}
      </div>

      {/* Settings Button */}
      <button
        onClick={() => setShowSetup(true)}
        style={{
          width: '100%',
          padding: '16px',
          background: 'white',
          border: '2px solid #e0e0e0',
          borderRadius: '12px',
          marginTop: '20px',
          fontSize: '16px',
          fontWeight: '600',
          cursor: 'pointer',
          color: '#666'
        }}
      >
        ⚙ Settings
      </button>
    </div>
  );
}

export default App;
