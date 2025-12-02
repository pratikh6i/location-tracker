import React, { useState, useEffect } from 'react';
import { Geolocation } from '@capacitor/geolocation';

export default function App() {
  const [isTracking, setIsTracking] = useState(false);
  const [currentLocation, setCurrentLocation] = useState(null);
  const [history, setHistory] = useState([]);
  const [interval, setInterval] = useState(5); // minutes
  const [showSettings, setShowSettings] = useState(false);
  const [permissionStatus, setPermissionStatus] = useState('unknown');

  // Load saved data and settings
  useEffect(() => {
    const saved = localStorage.getItem('location_history');
    if (saved) {
      setHistory(JSON.parse(saved));
    }

    const savedInterval = localStorage.getItem('tracking_interval');
    if (savedInterval) {
      setInterval(parseInt(savedInterval));
    }

    // Check permission status on load
    checkPermissions();
  }, []);

  const checkPermissions = async () => {
    try {
      const status = await Geolocation.checkPermissions();
      setPermissionStatus(status.location);
      console.log('Permission status:', status);
    } catch (error) {
      console.error('Error checking permissions:', error);
    }
  };

  const requestPermissions = async () => {
    try {
      const status = await Geolocation.requestPermissions();
      setPermissionStatus(status.location);
      console.log('Permission requested:', status);

      if (status.location === 'granted') {
        alert('Location permission granted! You can now start tracking.');
      } else if (status.location === 'denied') {
        alert('Location permission denied. Please enable it in Settings.');
      }
    } catch (error) {
      console.error('Error requesting permissions:', error);
      alert('Error requesting permissions: ' + error.message);
    }
  };

  // Tracking effect
  useEffect(() => {
    if (!isTracking) return;

    const trackLocation = async () => {
      try {
        // Check if we have permission
        const permission = await Geolocation.checkPermissions();
        if (permission.location !== 'granted') {
          alert('Location permission required. Please grant permission.');
          setIsTracking(false);
          return;
        }

        // Get current position
        const position = await Geolocation.getCurrentPosition({
          enableHighAccuracy: true,
          timeout: 15000,
          maximumAge: 0
        });

        const now = new Date();
        const istTime = new Date(now.toLocaleString('en-US', { timeZone: 'Asia/Kolkata' }));

        const newEntry = {
          id: Date.now(),
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracy: Math.round(position.coords.accuracy),
          timestamp: istTime.toLocaleString('en-IN', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
          })
        };

        setCurrentLocation(newEntry);

        setHistory(prev => {
          const updated = [newEntry, ...prev].slice(0, 50);
          localStorage.setItem('location_history', JSON.stringify(updated));
          return updated;
        });
      } catch (error) {
        console.error('Error getting location:', error);
        alert('Error: ' + error.message);
      }
    };

    // Track immediately
    trackLocation();

    // Then at the specified interval
    const intervalMs = interval * 60 * 1000;
    const timer = setInterval(trackLocation, intervalMs);

    return () => clearInterval(timer);
  }, [isTracking, interval]);

  const handleStartTracking = async () => {
    // Check permission first
    const permission = await Geolocation.checkPermissions();

    if (permission.location !== 'granted') {
      alert('Permission required. Requesting now...');
      await requestPermissions();
      return;
    }

    setIsTracking(true);
  };

  const clearHistory = () => {
    if (window.confirm('Clear all location history?')) {
      setHistory([]);
      localStorage.removeItem('location_history');
    }
  };

  const saveInterval = (newInterval) => {
    setInterval(newInterval);
    localStorage.setItem('tracking_interval', newInterval.toString());
    setShowSettings(false);

    if (isTracking) {
      alert('Interval updated. Stop and restart tracking for changes to take effect.');
    }
  };

  return (
    <div className="min-vh-100 bg-light">
      {/* Header */}
      <nav className="navbar navbar-dark bg-primary shadow-sm">
        <div className="container">
          <span className="navbar-brand mb-0 h1">
            <i className="bi bi-geo-alt-fill me-2"></i>
            Location Tracker
          </span>
          <div className="d-flex align-items-center gap-2">
            <span className={`badge ${isTracking ? 'bg-success' : 'bg-secondary'} fs-6`}>
              {isTracking ? '● TRACKING' : '○ STOPPED'}
            </span>
            <button
              className="btn btn-light btn-sm"
              onClick={() => setShowSettings(!showSettings)}
            >
              <i className="bi bi-gear-fill"></i>
            </button>
          </div>
        </div>
      </nav>

      <div className="container py-4">

        {/* Settings Panel */}
        {showSettings && (
          <div className="row mb-4">
            <div className="col-12">
              <div className="card shadow-sm border-warning">
                <div className="card-header bg-warning">
                  <h5 className="mb-0">
                    <i className="bi bi-gear me-2"></i>
                    Settings
                  </h5>
                </div>
                <div className="card-body">
                  <label className="form-label fw-bold">Tracking Interval</label>
                  <select
                    className="form-select mb-3"
                    value={interval}
                    onChange={(e) => setInterval(parseInt(e.target.value))}
                  >
                    <option value={1}>Every 1 minute</option>
                    <option value={2}>Every 2 minutes</option>
                    <option value={5}>Every 5 minutes</option>
                    <option value={10}>Every 10 minutes</option>
                    <option value={15}>Every 15 minutes</option>
                    <option value={30}>Every 30 minutes</option>
                    <option value={60}>Every 1 hour</option>
                  </select>

                  <div className="d-flex gap-2">
                    <button
                      className="btn btn-primary"
                      onClick={() => saveInterval(interval)}
                    >
                      Save Settings
                    </button>
                    <button
                      className="btn btn-secondary"
                      onClick={() => setShowSettings(false)}
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Permission Status */}
        {permissionStatus !== 'granted' && (
          <div className="row mb-4">
            <div className="col-12">
              <div className="alert alert-warning d-flex align-items-center justify-content-between">
                <div>
                  <i className="bi bi-exclamation-triangle me-2"></i>
                  <strong>Location permission required</strong>
                  <p className="mb-0 small">Grant permission to start tracking</p>
                </div>
                <button
                  className="btn btn-warning"
                  onClick={requestPermissions}
                >
                  Grant Permission
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Control Panel */}
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-sm">
              <div className="card-body text-center">
                <button
                  className={`btn btn-lg ${isTracking ? 'btn-danger' : 'btn-success'} px-5 me-3`}
                  onClick={() => isTracking ? setIsTracking(false) : handleStartTracking()}
                >
                  <i className={`bi ${isTracking ? 'bi-stop-circle-fill' : 'bi-play-circle-fill'} me-2`}></i>
                  {isTracking ? 'Stop Tracking' : 'Start Tracking'}
                </button>
                {history.length > 0 && (
                  <button
                    className="btn btn-lg btn-outline-danger px-5"
                    onClick={clearHistory}
                  >
                    <i className="bi bi-trash me-2"></i>
                    Clear History
                  </button>
                )}
              </div>
              <div className="card-footer text-muted text-center small">
                Tracking every {interval} minute{interval !== 1 ? 's' : ''}
              </div>
            </div>
          </div>
        </div>

        {/* Current Location Display */}
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-sm border-primary">
              <div className="card-header bg-primary text-white">
                <h5 className="mb-0">
                  <i className="bi bi-crosshair me-2"></i>
                  Current Location
                </h5>
              </div>
              <div className="card-body text-center">
                {currentLocation ? (
                  <>
                    <div className="row">
                      <div className="col-md-6 mb-3 mb-md-0">
                        <h6 className="text-muted small">LATITUDE</h6>
                        <h2 className="text-primary fw-bold">{currentLocation.latitude.toFixed(6)}</h2>
                      </div>
                      <div className="col-md-6">
                        <h6 className="text-muted small">LONGITUDE</h6>
                        <h2 className="text-primary fw-bold">{currentLocation.longitude.toFixed(6)}</h2>
                      </div>
                    </div>
                    <hr />
                    <div className="text-muted">
                      <i className="bi bi-clock me-2"></i>
                      <strong>{currentLocation.timestamp}</strong>
                      <span className="mx-2">•</span>
                      <i className="bi bi-bullseye me-2"></i>
                      Accuracy: {currentLocation.accuracy}m
                    </div>
                  </>
                ) : (
                  <div className="text-muted py-5">
                    <i className="bi bi-geo-alt display-1"></i>
                    <p className="mt-3">Waiting for location data...</p>
                    <p className="small">Click "Start Tracking" to begin</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* History Table */}
        <div className="row">
          <div className="col-12">
            <div className="card shadow-sm">
              <div className="card-header bg-dark text-white d-flex justify-content-between align-items-center">
                <h5 className="mb-0">
                  <i className="bi bi-list-ul me-2"></i>
                  Location History
                </h5>
                <span className="badge bg-light text-dark">{history.length} records</span>
              </div>
              <div className="card-body p-0">
                {history.length === 0 ? (
                  <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox display-3"></i>
                    <p className="mt-3">No location data recorded yet</p>
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-hover table-striped mb-0">
                      <thead className="table-dark">
                        <tr>
                          <th scope="col">#</th>
                          <th scope="col">
                            <i className="bi bi-clock me-1"></i>
                            Time (IST)
                          </th>
                          <th scope="col">
                            <i className="bi bi-geo-alt me-1"></i>
                            Latitude
                          </th>
                          <th scope="col">
                            <i className="bi bi-geo-alt me-1"></i>
                            Longitude
                          </th>
                          <th scope="col">
                            <i className="bi bi-bullseye me-1"></i>
                            Accuracy
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {history.map((entry, index) => (
                          <tr key={entry.id}>
                            <th scope="row">{index + 1}</th>
                            <td>{entry.timestamp}</td>
                            <td className="font-monospace">{entry.latitude.toFixed(6)}</td>
                            <td className="font-monospace">{entry.longitude.toFixed(6)}</td>
                            <td>{entry.accuracy}m</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Info Footer */}
        <div className="row mt-4">
          <div className="col-12">
            <div className="alert alert-info">
              <i className="bi bi-info-circle me-2"></i>
              <strong>How it works:</strong> Click the gear icon to change tracking interval.
              App requests location permission when you start tracking. All data stored locally.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
