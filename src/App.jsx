import React, { useState, useEffect } from 'react';

export default function App() {
  const [isTracking, setIsTracking] = useState(false);
  const [currentLocation, setCurrentLocation] = useState(null);
  const [history, setHistory] = useState([]);

  // Load history from localStorage on mount
  useEffect(() => {
    const saved = localStorage.getItem('location_history');
    if (saved) {
      setHistory(JSON.parse(saved));
    }
  }, []);

  // Tracking effect
  useEffect(() => {
    if (!isTracking) return;

    const trackLocation = () => {
      if (!navigator.geolocation) {
        alert('Geolocation is not supported by your device');
        return;
      }

      navigator.geolocation.getCurrentPosition(
        (position) => {
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
            const updated = [newEntry, ...prev].slice(0, 50); // Keep last 50
            localStorage.setItem('location_history', JSON.stringify(updated));
            return updated;
          });
        },
        (error) => {
          console.error('Error getting location:', error);
          alert('Error: ' + error.message);
        },
        {
          enableHighAccuracy: true,
          timeout: 15000,
          maximumAge: 0
        }
      );
    };

    // Track immediately
    trackLocation();

    // Then every 5 minutes
    const interval = setInterval(trackLocation, 5 * 60 * 1000);

    return () => clearInterval(interval);
  }, [isTracking]);

  const clearHistory = () => {
    if (window.confirm('Clear all location history?')) {
      setHistory([]);
      localStorage.removeItem('location_history');
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
          <span className={`badge ${isTracking ? 'bg-success' : 'bg-secondary'} fs-6`}>
            {isTracking ? '● TRACKING' : '○ STOPPED'}
          </span>
        </div>
      </nav>

      <div className="container py-4">
        {/* Control Panel */}
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-sm">
              <div className="card-body text-center">
                <button
                  className={`btn btn-lg ${isTracking ? 'btn-danger' : 'btn-success'} px-5 me-3`}
                  onClick={() => setIsTracking(!isTracking)}
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
              <strong>How it works:</strong> Location is captured every 5 minutes while tracking is active.
              All data is stored locally on your device.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
