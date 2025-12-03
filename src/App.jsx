import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Geolocation } from '@capacitor/geolocation';

// ============================================
// COMPREHENSIVE LOGGING SYSTEM
// ============================================
class Logger {
  constructor() {
    this.logs = [];
    this.maxLogs = 1000;
    this.loadLogs();
  }

  loadLogs() {
    try {
      const saved = localStorage.getItem('app_logs');
      if (saved) {
        this.logs = JSON.parse(saved);
      }
    } catch (e) {
      console.error('Failed to load logs:', e);
    }
  }

  saveLogs() {
    try {
      localStorage.setItem('app_logs', JSON.stringify(this.logs.slice(-this.maxLogs)));
    } catch (e) {
      console.error('Failed to save logs:', e);
    }
  }

  log(level, category, message, data = null) {
    const timestamp = new Date().toLocaleString('en-IN', {
      timeZone: 'Asia/Kolkata',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      fractionalSecondDigits: 3
    });

    const entry = {
      timestamp,
      level,
      category,
      message,
      data: data ? JSON.stringify(data) : null
    };

    this.logs.push(entry);

    // Console output for development
    const consoleMsg = `[${timestamp}] [${level}] [${category}] ${message}`;
    if (level === 'ERROR') console.error(consoleMsg, data);
    else if (level === 'WARN') console.warn(consoleMsg, data);
    else console.log(consoleMsg, data);

    // Save to localStorage
    if (this.logs.length >= this.maxLogs) {
      this.logs = this.logs.slice(-this.maxLogs);
    }
    this.saveLogs();
  }

  debug(category, message, data) { this.log('DEBUG', category, message, data); }
  info(category, message, data) { this.log('INFO', category, message, data); }
  warn(category, message, data) { this.log('WARN', category, message, data); }
  error(category, message, data) { this.log('ERROR', category, message, data); }

  getLogs() { return this.logs; }

  clearLogs() {
    this.logs = [];
    localStorage.removeItem('app_logs');
    this.info('SYSTEM', 'Logs cleared');
  }

  downloadLogs() {
    const text = this.logs.map(log =>
      `[${log.timestamp}] [${log.level}] [${log.category}] ${log.message}${log.data ? ' | ' + log.data : ''}`
    ).join('\n');

    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `location_tracker_logs_${Date.now()}.txt`;
    a.click();
    URL.revokeObjectURL(url);
    this.info('SYSTEM', 'Logs downloaded');
  }
}

const logger = new Logger();

export default function App() {
  const [isTracking, setIsTracking] = useState(false);
  const [currentLocation, setCurrentLocation] = useState(null);
  const [history, setHistory] = useState([]);
  const [interval, setInterval] = useState(5);
  const [showSettings, setShowSettings] = useState(false);
  const [showLogs, setShowLogs] = useState(false);
  const [permissionStatus, setPermissionStatus] = useState('unknown');
  const [logs, setLogs] = useState([]);

  // Refs to prevent duplicate tracking
  const lastCaptureTime = useRef(0);
  const trackingIntervalRef = useRef(null);

  // Load saved data
  useEffect(() => {
    logger.info('APP', 'Application started');

    const saved = localStorage.getItem('location_history');
    if (saved) {
      const loadedHistory = JSON.parse(saved);
      setHistory(loadedHistory);
      logger.info('STORAGE', `Loaded ${loadedHistory.length} history records`);
    }

    const savedInterval = localStorage.getItem('tracking_interval');
    if (savedInterval) {
      setInterval(parseInt(savedInterval));
      logger.info('STORAGE', `Loaded tracking interval: ${savedInterval} minutes`);
    }

    checkPermissions();

    return () => {
      logger.info('APP', 'Application closing');
    };
  }, []);

  // Refresh logs display
  useEffect(() => {
    if (showLogs) {
      setLogs(logger.getLogs());
    }
  }, [showLogs]);

  const checkPermissions = async () => {
    try {
      logger.debug('PERMISSION', 'Checking location permissions');
      const status = await Geolocation.checkPermissions();
      setPermissionStatus(status.location);
      logger.info('PERMISSION', `Permission status: ${status.location}`, status);
    } catch (error) {
      logger.error('PERMISSION', 'Failed to check permissions', error);
    }
  };

  const requestPermissions = async () => {
    try {
      logger.info('PERMISSION', 'Requesting location permissions');
      const status = await Geolocation.requestPermissions();
      setPermissionStatus(status.location);
      logger.info('PERMISSION', `Permission result: ${status.location}`, status);

      if (status.location === 'granted') {
        alert('Location permission granted!');
      } else if (status.location === 'denied') {
        alert('Permission denied. Enable in Settings.');
        logger.warn('PERMISSION', 'User denied location permission');
      }
    } catch (error) {
      logger.error('PERMISSION', 'Error requesting permissions', error);
      alert('Error: ' + error.message);
    }
  };

  // Tracking function with deduplication
  const trackLocation = useCallback(async () => {
    const now = Date.now();
    const timeSinceLastCapture = now - lastCaptureTime.current;

    // Prevent duplicates: require at least 30 seconds between captures
    if (timeSinceLastCapture < 30000 && lastCaptureTime.current !== 0) {
      logger.debug('TRACKING', `Skipping duplicate capture (${Math.round(timeSinceLastCapture / 1000)}s since last)`, { timeSinceLastCapture });
      return;
    }

    try {
      logger.debug('TRACKING', 'Checking permissions before capture');
      const permission = await Geolocation.checkPermissions();
      if (permission.location !== 'granted') {
        logger.warn('TRACKING', 'Permission not granted, stopping tracking');
        alert('Location permission required');
        setIsTracking(false);
        return;
      }

      logger.info('TRACKING', 'Requesting current position');
      const position = await Geolocation.getCurrentPosition({
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 0
      });

      logger.debug('TRACKING', 'Position received', {
        lat: position.coords.latitude,
        lng: position.coords.longitude,
        accuracy: position.coords.accuracy
      });

      const istDate = new Date();
      const newEntry = {
        id: now, // Use capture time as unique ID
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracy: Math.round(position.coords.accuracy),
        timestamp: istDate.toLocaleString('en-IN', {
          timeZone: 'Asia/Kolkata',
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit'
        })
      };

      logger.info('TRACKING', 'Location captured successfully', newEntry);

      setCurrentLocation(newEntry);
      lastCaptureTime.current = now;

      setHistory(prev => {
        // Additional deduplication check
        const isDuplicate = prev.some(item => item.id === newEntry.id);
        if (isDuplicate) {
          logger.warn('TRACKING', 'Prevented duplicate entry in history', { id: newEntry.id });
          return prev;
        }

        const updated = [newEntry, ...prev].slice(0, 50);
        localStorage.setItem('location_history', JSON.stringify(updated));
        logger.info('STORAGE', `History updated: ${updated.length} records`);
        return updated;
      });
    } catch (error) {
      logger.error('TRACKING', 'Failed to get location', error);
      alert('Location error: ' + error.message);
    }
  }, []);

  // Tracking control
  useEffect(() => {
    if (!isTracking) {
      if (trackingIntervalRef.current) {
        clearInterval(trackingIntervalRef.current);
        trackingIntervalRef.current = null;
        logger.info('TRACKING', 'Tracking stopped');
      }
      return;
    }

    logger.info('TRACKING', `Starting tracking with ${interval} minute interval`);

    // Initial capture
    trackLocation();

    // Set up interval
    const intervalMs = interval * 60 * 1000;
    trackingIntervalRef.current = setInterval(trackLocation, intervalMs);
    logger.debug('TRACKING', `Interval set: ${intervalMs}ms`);

    return () => {
      if (trackingIntervalRef.current) {
        clearInterval(trackingIntervalRef.current);
        trackingIntervalRef.current = null;
      }
    };
  }, [isTracking, interval, trackLocation]);

  const handleStartTracking = async () => {
    logger.info('USER', 'Start tracking button clicked');
    const permission = await Geolocation.checkPermissions();

    if (permission.location !== 'granted') {
      logger.warn('USER', 'Permission not granted, requesting');
      alert('Permission required. Requesting now...');
      await requestPermissions();
      return;
    }

    setIsTracking(true);
  };

  const handleStopTracking = () => {
    logger.info('USER', 'Stop tracking button clicked');
    setIsTracking(false);
  };

  const clearHistory = () => {
    if (window.confirm('Clear all location history?')) {
      logger.info('USER', 'Clearing history');
      setHistory([]);
      localStorage.removeItem('location_history');
    }
  };

  const saveInterval = (newInterval) => {
    logger.info('USER', `Saving new interval: ${newInterval} minutes`);
    setInterval(newInterval);
    localStorage.setItem('tracking_interval', newInterval.toString());
    setShowSettings(false);

    if (isTracking) {
      alert('Interval updated. Restart tracking for changes to take effect.');
      logger.warn('SETTINGS', 'Interval changed while tracking active');
    }
  };

  return (
    <div className="min-vh-100" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
      {/* Header */}
      <nav className="navbar shadow-lg" style={{ background: 'rgba(255,255,255,0.95)', backdropFilter: 'blur(10px)' }}>
        <div className="container">
          <span className="navbar-brand mb-0 h1" style={{ color: '#667eea', fontWeight: 'bold' }}>
            <i className="bi bi-geo-alt-fill me-2"></i>
            Location Tracker
          </span>
          <div className="d-flex align-items-center gap-2">
            <span className={`badge fs-6 ${isTracking ? 'bg-success' : 'bg-secondary'}`}>
              {isTracking ? '● TRACKING' : '○ STOPPED'}
            </span>
            <button
              className="btn btn-sm"
              style={{ background: '#667eea', color: 'white' }}
              onClick={() => setShowSettings(!showSettings)}
            >
              <i className="bi bi-gear-fill"></i>
            </button>
            <button
              className="btn btn-sm"
              style={{ background: '#764ba2', color: 'white' }}
              onClick={() => setShowLogs(!showLogs)}
            >
              <i className="bi bi-file-text-fill"></i>
            </button>
          </div>
        </div>
      </nav>

      <div className="container py-4">

        {/* Log Viewer */}
        {showLogs && (
          <div className="row mb-4">
            <div className="col-12">
              <div className="card shadow-lg border-0">
                <div className="card-header d-flex justify-content-between align-items-center" style={{ background: '#764ba2', color: 'white' }}>
                  <h5 className="mb-0">
                    <i className="bi bi-file-text me-2"></i>
                    Debug Logs ({logs.length})
                  </h5>
                  <div>
                    <button className="btn btn-sm btn-light me-2" onClick={() => setLogs(logger.getLogs())}>
                      <i className="bi bi-arrow-clockwise"></i> Refresh
                    </button>
                    <button className="btn btn-sm btn-light me-2" onClick={() => logger.downloadLogs()}>
                      <i className="bi bi-download"></i> Download
                    </button>
                    <button className="btn btn-sm btn-danger" onClick={() => { logger.clearLogs(); setLogs([]); }}>
                      <i className="bi bi-trash"></i> Clear
                    </button>
                  </div>
                </div>
                <div className="card-body p-0" style={{ maxHeight: '400px', overflow: 'auto', background: '#1e1e1e', color: '#d4d4d4', fontFamily: 'monospace', fontSize: '12px' }}>
                  {logs.length === 0 ? (
                    <div className="p-3 text-center text-muted">No logs yet</div>
                  ) : (
                    <table className="table table-sm table-dark table-striped mb-0">
                      <thead style={{ position: 'sticky', top: 0, background: '#2d2d2d' }}>
                        <tr>
                          <th style={{ width: '180px' }}>Time</th>
                          <th style={{ width: '80px' }}>Level</th>
                          <th style={{ width: '120px' }}>Category</th>
                          <th>Message</th>
                        </tr>
                      </thead>
                      <tbody>
                        {logs.slice().reverse().map((log, i) => (
                          <tr key={i} className={log.level === 'ERROR' ? 'table-danger' : log.level === 'WARN' ? 'table-warning' : ''}>
                            <td style={{ fontSize: '10px' }}>{log.timestamp}</td>
                            <td><span className={`badge bg-${log.level === 'ERROR' ? 'danger' : log.level === 'WARN' ? 'warning' : log.level === 'INFO' ? 'info' : 'secondary'}`}>{log.level}</span></td>
                            <td>{log.category}</td>
                            <td>{log.message} {log.data && <small className="text-muted">({log.data})</small>}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Settings Panel */}
        {showSettings && (
          <div className="row mb-4">
            <div className="col-12">
              <div className="card shadow-lg border-0">
                <div className="card-header" style={{ background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', color: 'white' }}>
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

        {/* Permission Warning */}
        {permissionStatus !== 'granted' && (
          <div className="row mb-4">
            <div className="col-12">
              <div className="alert alert-warning shadow-lg d-flex align-items-center justify-content-between border-0">
                <div>
                  <i className="bi bi-exclamation-triangle me-2"></i>
                  <strong>Location permission required</strong>
                </div>
                <button className="btn btn-warning" onClick={requestPermissions}>
                  Grant Permission
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Control Buttons - Separated */}
        <div className="row mb-4">
          <div className="col-md-6 mb-3 mb-md-0">
            <div className="card shadow-lg border-0 h-100">
              <div className="card-body d-flex flex-column justify-content-center">
                <button
                  className={`btn btn-lg w-100 ${isTracking ? 'btn-danger' : 'btn-success'}`}
                  onClick={() => isTracking ? handleStopTracking() : handleStartTracking()}
                  style={{ borderRadius: '15px', padding: '20px', fontSize: '18px', fontWeight: 'bold' }}
                >
                  <i className={`bi ${isTracking ? 'bi-stop-circle-fill' : 'bi-play-circle-fill'} me-2`} style={{ fontSize: '24px' }}></i>
                  {isTracking ? 'Stop Tracking' : 'Start Tracking'}
                </button>
                <div className="text-center mt-3 text-muted small">
                  Tracking every {interval} minute{interval !== 1 ? 's' : ''}
                </div>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card shadow-lg border-0 h-100">
              <div className="card-body d-flex flex-column justify-content-center">
                <button
                  className="btn btn-lg btn-outline-danger w-100"
                  onClick={clearHistory}
                  disabled={history.length === 0}
                  style={{ borderRadius: '15px', padding: '20px', fontSize: '18px', fontWeight: 'bold' }}
                >
                  <i className="bi bi-trash me-2" style={{ fontSize: '24px' }}></i>
                  Clear History
                </button>
                <div className="text-center mt-3 text-muted small">
                  {history.length} records stored
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Current Location */}
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-lg border-0" style={{ background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' }}>
              <div className="card-header border-0 text-white">
                <h5 className="mb-0 fw-bold">
                  <i className="bi bi-crosshair me-2"></i>
                  Current Location
                </h5>
              </div>
              <div className="card-body text-center bg-white">
                {currentLocation ? (
                  <>
                    <div className="row">
                      <div className="col-md-6 mb-3 mb-md-0">
                        <h6 className="text-muted small">LATITUDE</h6>
                        <h2 className="fw-bold" style={{ color: '#4facfe' }}>{currentLocation.latitude.toFixed(6)}</h2>
                      </div>
                      <div className="col-md-6">
                        <h6 className="text-muted small">LONGITUDE</h6>
                        <h2 className="fw-bold" style={{ color: '#00f2fe' }}>{currentLocation.longitude.toFixed(6)}</h2>
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
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* History Table */}
        <div className="row">
          <div className="col-12">
            <div className="card shadow-lg border-0">
              <div className="card-header d-flex justify-content-between align-items-center" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', color: 'white' }}>
                <h5 className="mb-0 fw-bold">
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
                    <table className="table table-hover mb-0">
                      <thead style={{ background: '#f8f9fa' }}>
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
                            <td className="font-monospace text-primary">{entry.latitude.toFixed(6)}</td>
                            <td className="font-monospace text-info">{entry.longitude.toFixed(6)}</td>
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
      </div>
    </div>
  );
}
