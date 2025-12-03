import React, { useState, useEffect, useRef } from 'react';
import { Geolocation } from '@capacitor/geolocation';
import { App as CapApp } from '@capacitor/app';

// ============================================
// LOGGING SYSTEM
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
      if (saved) this.logs = JSON.parse(saved);
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

    const entry = { timestamp, level, category, message, data: data ? JSON.stringify(data) : null };
    this.logs.push(entry);

    const consoleMsg = `[${timestamp}] [${level}] [${category}] ${message}`;
    if (level === 'ERROR') console.error(consoleMsg, data);
    else if (level === 'WARN') console.warn(consoleMsg, data);
    else console.log(consoleMsg, data);

    if (this.logs.length >= this.maxLogs) this.logs = this.logs.slice(-this.maxLogs);
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

// ============================================
// STORAGE MANAGER
// ============================================
class StorageManager {
  static saveHistory(history) {
    try {
      localStorage.setItem('location_history', JSON.stringify(history));
      localStorage.setItem('location_history_backup', JSON.stringify(history));
      return true;
    } catch (e) {
      logger.error('STORAGE', 'Save failed', e);
      return false;
    }
  }

  static loadHistory() {
    try {
      const primary = localStorage.getItem('location_history');
      if (primary) return JSON.parse(primary);

      const backup = localStorage.getItem('location_history_backup');
      if (backup) {
        logger.warn('STORAGE', 'Using backup');
        return JSON.parse(backup);
      }
      return [];
    } catch (e) {
      logger.error('STORAGE', 'Load failed', e);
      return [];
    }
  }
}

export default function App() {
  const [isTracking, setIsTracking] = useState(false);
  const [currentLocation, setCurrentLocation] = useState(null);
  const [history, setHistory] = useState([]);
  const [interval, setInterval] = useState(5);
  const [showSettings, setShowSettings] = useState(false);
  const [showLogs, setShowLogs] = useState(false);
  const [logs, setLogs] = useState([]);

  const trackingLock = useRef(false);
  const intervalRef = useRef(null);
  const lastCaptureTime = useRef(0);

  // Load data
  useEffect(() => {
    logger.info('APP', 'Started');

    const loadedHistory = StorageManager.loadHistory();
    setHistory(loadedHistory);
    logger.info('STORAGE', `Loaded ${loadedHistory.length} records`);

    const savedInterval = localStorage.getItem('tracking_interval');
    if (savedInterval) {
      setInterval(parseInt(savedInterval));
      logger.info('STORAGE', `Interval: ${savedInterval} min`);
    }

    // Resume tracking if was active
    const wasTracking = localStorage.getItem('was_tracking') === 'true';
    if (wasTracking) {
      logger.info('APP', 'Resuming tracking');
      setIsTracking(true);
    }

    // App state listener (for background tracking)
    CapApp.addListener('appStateChange', ({ isActive }) => {
      logger.info('APP_STATE', isActive ? 'Foreground' : 'Background');
    });

    return () => {
      logger.info('APP', 'Closing');
    };
  }, []);

  // Track location function
  const captureLocation = async () => {
    const now = Date.now();

    // Prevent rapid duplicate calls
    if (trackingLock.current || (now - lastCaptureTime.current) < 10000) {
      logger.debug('TRACK', 'Skipped (locked or too soon)');
      return;
    }

    trackingLock.current = true;

    try {
      logger.debug('TRACK', 'Requesting position');

      const position = await Geolocation.getCurrentPosition({
        enableHighAccuracy: true,
        timeout: 30000,
        maximumAge: 0
      });

      const istDate = new Date();
      const newEntry = {
        id: now,
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

      logger.info('TRACK', 'Captured', { lat: newEntry.latitude, lng: newEntry.longitude });

      setCurrentLocation(newEntry);
      lastCaptureTime.current = now;

      setHistory(prev => {
        // Strict duplicate check
        const isDuplicate = prev.some(item =>
          Math.abs(item.latitude - newEntry.latitude) < 0.00001 &&
          Math.abs(item.longitude - newEntry.longitude) < 0.00001 &&
          Math.abs(item.id - newEntry.id) < 60000
        );

        if (isDuplicate) {
          logger.warn('TRACK', 'Duplicate prevented');
          return prev;
        }

        const updated = [newEntry, ...prev].slice(0, 50);
        StorageManager.saveHistory(updated);
        logger.info('STORAGE', `Saved: ${updated.length} records`);
        return updated;
      });
    } catch (error) {
      logger.error('TRACK', 'Failed', error);
    } finally {
      trackingLock.current = false;
    }
  };

  // Tracking control
  useEffect(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    if (!isTracking) {
      logger.info('TRACK', 'Stopped');
      localStorage.setItem('was_tracking', 'false');
      return;
    }

    logger.info('TRACK', `Started (${interval} min interval)`);
    localStorage.setItem('was_tracking', 'true');

    // Capture immediately
    captureLocation();

    // Then at intervals
    const intervalMs = interval * 60 * 1000;
    intervalRef.current = setInterval(() => {
      logger.debug('TRACK', 'Interval trigger');
      captureLocation();
    }, intervalMs);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [isTracking, interval]);

  // Refresh logs
  useEffect(() => {
    if (showLogs) setLogs(logger.getLogs());
  }, [showLogs]);

  const handleStartTracking = async () => {
    try {
      logger.info('USER', 'Start requested');
      const permission = await Geolocation.checkPermissions();

      if (permission.location !== 'granted') {
        logger.warn('USER', 'Requesting permission');
        await Geolocation.requestPermissions();
        return;
      }

      setIsTracking(true);
    } catch (e) {
      logger.error('USER', 'Start failed', e);
      alert('Failed to start: ' + e.message);
    }
  };

  const handleStopTracking = () => {
    logger.info('USER', 'Stop requested');
    setIsTracking(false);
  };

  const clearHistory = () => {
    if (window.confirm('Clear all history?')) {
      logger.info('USER', 'Clearing history');
      setHistory([]);
      StorageManager.saveHistory([]);
    }
  };

  const saveInterval = (newInterval) => {
    logger.info('USER', `Interval: ${newInterval} min`);
    setInterval(newInterval);
    localStorage.setItem('tracking_interval', newInterval.toString());
    setShowSettings(false);

    if (isTracking) {
      alert('Restart tracking for change to take effect');
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

        {/* Logs */}
        {showLogs && (
          <div className="row mb-4">
            <div className="col-12">
              <div className="card shadow-lg border-0">
                <div className="card-header d-flex justify-content-between align-items-center" style={{ background: '#764ba2', color: 'white' }}>
                  <h5 className="mb-0">Logs ({logs.length})</h5>
                  <div>
                    <button className="btn btn-sm btn-light me-2" onClick={() => setLogs(logger.getLogs())}>
                      <i className="bi bi-arrow-clockwise"></i>
                    </button>
                    <button className="btn btn-sm btn-light me-2" onClick={() => logger.downloadLogs()}>
                      <i className="bi bi-download"></i>
                    </button>
                    <button className="btn btn-sm btn-danger" onClick={() => { logger.clearLogs(); setLogs([]); }}>
                      <i className="bi bi-trash"></i>
                    </button>
                  </div>
                </div>
                <div className="card-body p-0" style={{ maxHeight: '300px', overflow: 'auto', background: '#1e1e1e', color: '#d4d4d4' }}>
                  {logs.length === 0 ? (
                    <div className="p-3 text-center">No logs</div>
                  ) : (
                    <table className="table table-sm table-dark mb-0" style={{ fontSize: '11px' }}>
                      <tbody>
                        {logs.slice().reverse().slice(0, 100).map((log, i) => (
                          <tr key={i}>
                            <td style={{ width: '160px', fontSize: '9px' }}>{log.timestamp}</td>
                            <td style={{ width: '60px' }}><span className={`badge bg-${log.level === 'ERROR' ? 'danger' : log.level === 'WARN' ? 'warning' : 'info'}`}>{log.level}</span></td>
                            <td style={{ width: '80px' }}>{log.category}</td>
                            <td>{log.message}</td>
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

        {/* Settings */}
        {showSettings && (
          <div className="row mb-4">
            <div className="col-12">
              <div className="card shadow-lg border-0">
                <div className="card-header" style={{ background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', color: 'white' }}>
                  <h5 className="mb-0"><i className="bi bi-gear me-2"></i>Settings</h5>
                </div>
                <div className="card-body">
                  <label className="form-label fw-bold">Tracking Interval</label>
                  <select className="form-select mb-3" value={interval} onChange={(e) => setInterval(parseInt(e.target.value))}>
                    <option value={1}>1 minute</option>
                    <option value={2}>2 minutes</option>
                    <option value={5}>5 minutes</option>
                    <option value={10}>10 minutes</option>
                    <option value={15}>15 minutes</option>
                    <option value={30}>30 minutes</option>
                    <option value={60}>1 hour</option>
                  </select>
                  <div className="d-flex gap-2">
                    <button className="btn btn-primary" onClick={() => saveInterval(interval)}>Save</button>
                    <button className="btn btn-secondary" onClick={() => setShowSettings(false)}>Cancel</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Controls */}
        <div className="row mb-4">
          <div className="col-md-6 mb-3">
            <div className="card shadow-lg border-0 h-100">
              <div className="card-body d-flex flex-column justify-content-center">
                <button
                  className={`btn btn-lg w-100 ${isTracking ? 'btn-danger' : 'btn-success'}`}
                  onClick={isTracking ? handleStopTracking : handleStartTracking}
                  style={{ borderRadius: '15px', padding: '20px', fontWeight: 'bold' }}
                >
                  <i className={`bi ${isTracking ? 'bi-stop-circle-fill' : 'bi-play-circle-fill'} me-2`}></i>
                  {isTracking ? 'Stop' : 'Start'}
                </button>
                <div className="text-center mt-3 text-muted small">Every {interval} min</div>
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
                  style={{ borderRadius: '15px', padding: '20px', fontWeight: 'bold' }}
                >
                  <i className="bi bi-trash me-2"></i>Clear
                </button>
                <div className="text-center mt-3 text-muted small">{history.length} records</div>
              </div>
            </div>
          </div>
        </div>

        {/* Current Location */}
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-lg border-0" style={{ background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' }}>
              <div className="card-header border-0 text-white">
                <h5 className="mb-0 fw-bold"><i className="bi bi-crosshair me-2"></i>Current Location</h5>
              </div>
              <div className="card-body text-center bg-white">
                {currentLocation ? (
                  <>
                    <div className="row">
                      <div className="col-md-6 mb-3">
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
                      <i className="bi bi-clock me-2"></i><strong>{currentLocation.timestamp}</strong>
                      <span className="mx-2">•</span>
                      <i className="bi bi-bullseye me-2"></i>{currentLocation.accuracy}m
                    </div>
                  </>
                ) : (
                  <div className="text-muted py-5">
                    <i className="bi bi-geo-alt display-1"></i>
                    <p className="mt-3">Waiting...</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* History */}
        <div className="row">
          <div className="col-12">
            <div className="card shadow-lg border-0">
              <div className="card-header d-flex justify-content-between" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', color: 'white' }}>
                <h5 className="mb-0 fw-bold"><i className="bi bi-list-ul me-2"></i>History</h5>
                <span className="badge bg-light text-dark">{history.length}</span>
              </div>
              <div className="card-body p-0">
                {history.length === 0 ? (
                  <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox display-3"></i>
                    <p className="mt-3">No data</p>
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-hover mb-0">
                      <thead style={{ background: '#f8f9fa' }}>
                        <tr>
                          <th>#</th>
                          <th>Time (IST)</th>
                          <th>Latitude</th>
                          <th>Longitude</th>
                          <th>Accuracy</th>
                        </tr>
                      </thead>
                      <tbody>
                        {history.map((entry, index) => (
                          <tr key={entry.id}>
                            <th>{index + 1}</th>
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
