import React, { useState, useEffect, useRef } from 'react';
import BackgroundGeolocation from '@transistorsoft/capacitor-background-geolocation';

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

    const consoleMsg = `[${timestamp}] [${level}] [${category}] ${message}`;
    if (level === 'ERROR') console.error(consoleMsg, data);
    else if (level === 'WARN') console.warn(consoleMsg, data);
    else console.log(consoleMsg, data);

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

// ============================================
// STORAGE MANAGER - Prevents data loss
// ============================================
class StorageManager {
  static saveHistory(history) {
    try {
      localStorage.setItem('location_history', JSON.stringify(history));
      localStorage.setItem('location_history_backup', JSON.stringify(history));
      logger.debug('STORAGE', 'History saved with backup');
      return true;
    } catch (e) {
      logger.error('STORAGE', 'Failed to save history', e);
      return false;
    }
  }

  static loadHistory() {
    try {
      const primary = localStorage.getItem('location_history');
      if (primary) {
        return JSON.parse(primary);
      }

      const backup = localStorage.getItem('location_history_backup');
      if (backup) {
        logger.warn('STORAGE', 'Using backup history');
        return JSON.parse(backup);
      }

      return [];
    } catch (e) {
      logger.error('STORAGE', 'Failed to load history', e);
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
  const [bgInitialized, setBgInitialized] = useState(false);

  // Tracking lock to prevent concurrent calls
  const trackingLock = useRef(false);
  const mounted = useRef(true);

  // Load data on mount
  useEffect(() => {
    logger.info('APP', 'Application started');
    mounted.current = true;

    try {
      const loadedHistory = StorageManager.loadHistory();
      setHistory(loadedHistory);
      logger.info('STORAGE', `Loaded ${loadedHistory.length} history records`);

      const savedInterval = localStorage.getItem('tracking_interval');
      if (savedInterval) {
        setInterval(parseInt(savedInterval));
        logger.info('STORAGE', `Loaded interval: ${savedInterval} minutes`);
      }

      // Initialize background geolocation
      initializeBackgroundGeolocation();
    } catch (e) {
      logger.error('APP', 'Error during initialization', e);
    }

    return () => {
      mounted.current = false;
      logger.info('APP', 'Application closing');
    };
  }, []);

  // Refresh logs
  useEffect(() => {
    if (showLogs) {
      setLogs(logger.getLogs());
    }
  }, [showLogs]);

  // Initialize background geolocation
  const initializeBackgroundGeolocation = async () => {
    try {
      logger.info('BG_GEO', 'Initializing background geolocation');

      await BackgroundGeolocation.ready({
        // Geolocation Config
        desiredAccuracy: BackgroundGeolocation.DESIRED_ACCURACY_HIGH,
        distanceFilter: 0,
        stopTimeout: 5,

        // Activity Recognition
        stopOnTerminate: false,
        startOnBoot: true,

        // Application config
        debug: false,
        logLevel: BackgroundGeolocation.LOG_LEVEL_VERBOSE,

        // Android specific
        foregroundService: true,
        notification: {
          title: 'Location Tracker',
          text: 'Tracking your location',
          color: '#667eea'
        },

        // Heartbeat (for periodic tracking)
        heartbeatInterval: 60
      });

      // Location event listener
      BackgroundGeolocation.onLocation(onBackgroundLocation, onLocationError);

      // Heartbeat event (fires every minute when tracking)
      BackgroundGeolocation.onHeartbeat(onHeartbeat);

      setBgInitialized(true);
      logger.info('BG_GEO', 'Background geolocation initialized');
    } catch (e) {
      logger.error('BG_GEO', 'Failed to initialize', e);
    }
  };

  // Background location callback
  const onBackgroundLocation = (location) => {
    if (trackingLock.current) {
      logger.debug('BG_GEO', 'Location received but locked, skipping');
      return;
    }

    trackingLock.current = true;

    try {
      logger.info('BG_GEO', 'Background location received', {
        lat: location.coords.latitude,
        lng: location.coords.longitude
      });

      const istDate = new Date();
      const newEntry = {
        id: Date.now(),
        latitude: location.coords.latitude,
        longitude: location.coords.longitude,
        accuracy: Math.round(location.coords.accuracy),
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

      if (mounted.current) {
        setCurrentLocation(newEntry);

        setHistory(prev => {
          const isDuplicate = prev.some(item =>
            Math.abs(item.id - newEntry.id) < 5000 // 5 second window
          );

          if (isDuplicate) {
            logger.warn('BG_GEO', 'Prevented duplicate entry');
            return prev;
          }

          const updated = [newEntry, ...prev].slice(0, 50);
          StorageManager.saveHistory(updated);
          logger.info('BG_GEO', `History updated: ${updated.length} records`);
          return updated;
        });
      }
    } catch (e) {
      logger.error('BG_GEO', 'Error processing location', e);
    } finally {
      trackingLock.current = false;
    }
  };

  const onLocationError = (error) => {
    logger.error('BG_GEO', 'Location error', error);
  };

  const onHeartbeat = async (event) => {
    logger.debug('BG_GEO', 'Heartbeat event');

    // Request location on heartbeat
    try {
      const location = await BackgroundGeolocation.getCurrentPosition({
        timeout: 30,
        maximumAge: 5000,
        persist: false
      });
      onBackgroundLocation(location);
    } catch (e) {
      logger.warn('BG_GEO', 'Heartbeat location failed', e);
    }
  };

  // Start/Stop tracking
  const handleStartTracking = async () => {
    try {
      logger.info('USER', 'Start tracking requested');

      if (!bgInitialized) {
        logger.warn('USER', 'Background geo not initialized, waiting...');
        await initializeBackgroundGeolocation();
      }

      await BackgroundGeolocation.start();
      setIsTracking(true);
      logger.info('BG_GEO', 'Tracking started');

      // Set interval for heartbeat
      await BackgroundGeolocation.setConfig({
        heartbeatInterval: interval * 60 // Convert minutes to seconds
      });

    } catch (e) {
      logger.error('USER', 'Failed to start tracking', e);
      alert('Failed to start tracking: ' + e.message);
    }
  };

  const handleStopTracking = async () => {
    try {
      logger.info('USER', 'Stop tracking requested');
      await BackgroundGeolocation.stop();
      setIsTracking(false);
      logger.info('BG_GEO', 'Tracking stopped');
    } catch (e) {
      logger.error('USER', 'Failed to stop tracking', e);
    }
  };

  const clearHistory = () => {
    if (window.confirm('Clear all location history?')) {
      logger.info('USER', 'Clearing history');
      setHistory([]);
      StorageManager.saveHistory([]);
    }
  };

  const saveInterval = async (newInterval) => {
    logger.info('USER', `Saving interval: ${newInterval} minutes`);
    setInterval(newInterval);
    localStorage.setItem('tracking_interval', newInterval.toString());

    if (isTracking) {
      try {
        await BackgroundGeolocation.setConfig({
          heartbeatInterval: newInterval * 60
        });
        logger.info('BG_GEO', 'Interval updated while tracking');
      } catch (e) {
        logger.error('BG_GEO', 'Failed to update interval', e);
      }
    }

    setShowSettings(false);
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
                <div className="card-body p-0" style={{ maxHeight: '300px', overflow: 'auto', background: '#1e1e1e', color: '#d4d4d4', fontFamily: 'monospace', fontSize: '11px' }}>
                  {logs.length === 0 ? (
                    <div className="p-3 text-center text-muted">No logs</div>
                  ) : (
                    <table className="table table-sm table-dark table-striped mb-0">
                      <thead style={{ position: 'sticky', top: 0, background: '#2d2d2d' }}>
                        <tr>
                          <th style={{ width: '160px', fontSize: '10px' }}>Time</th>
                          <th style={{ width: '70px' }}>Level</th>
                          <th style={{ width: '100px' }}>Category</th>
                          <th>Message</th>
                        </tr>
                      </thead>
                      <tbody>
                        {logs.slice().reverse().slice(0, 100).map((log, i) => (
                          <tr key={i}>
                            <td style={{ fontSize: '9px' }}>{log.timestamp}</td>
                            <td><span className={`badge bg-${log.level === 'ERROR' ? 'danger' : log.level === 'WARN' ? 'warning' : log.level === 'INFO' ? 'info' : 'secondary'} small`}>{log.level}</span></td>
                            <td>{log.category}</td>
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
                    <button className="btn btn-primary" onClick={() => saveInterval(interval)}>
                      Save
                    </button>
                    <button className="btn btn-secondary" onClick={() => setShowSettings(false)}>
                      Cancel
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Info Banner */}
        <div className="row mb-4">
          <div className="col-12">
            <div className="alert alert-info shadow-lg border-0">
              <i className="bi bi-info-circle me-2"></i>
              <strong>Background Tracking Active:</strong> App will continue tracking even when closed. Works after device reboot.
            </div>
          </div>
        </div>

        {/* Control Buttons */}
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
                  Every {interval} minute{interval !== 1 ? 's' : ''}
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
                  {history.length} records
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
                      {currentLocation.accuracy}m
                    </div>
                  </>
                ) : (
                  <div className="text-muted py-5">
                    <i className="bi bi-geo-alt display-1"></i>
                    <p className="mt-3">Waiting for location...</p>
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
              <div className="card-header d-flex justify-content-between align-items-center" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', color: 'white' }}>
                <h5 className="mb-0 fw-bold">
                  <i className="bi bi-list-ul me-2"></i>
                  Location History
                </h5>
                <span className="badge bg-light text-dark">{history.length}</span>
              </div>
              <div className="card-body p-0">
                {history.length === 0 ? (
                  <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox display-3"></i>
                    <p className="mt-3">No data yet</p>
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-hover mb-0">
                      <thead style={{ background: '#f8f9fa' }}>
                        <tr>
                          <th>#</th>
                          <th><i className="bi bi-clock me-1"></i>Time (IST)</th>
                          <th><i className="bi bi-geo-alt me-1"></i>Latitude</th>
                          <th><i className="bi bi-geo-alt me-1"></i>Longitude</th>
                          <th><i className="bi bi-bullseye me-1"></i>Accuracy</th>
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
