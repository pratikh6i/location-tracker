import React, { useState, useEffect, useRef } from 'react';
import { Geolocation } from '@capacitor/geolocation';
import { App as CapApp } from '@capacitor/app';
import { Filesystem, Directory } from '@capacitor/filesystem';

// ============================================
// LOGGER WITH FILESYSTEM
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
        this.log('INFO', 'LOGGER', `Loaded ${this.logs.length} logs from storage`);
      }
    } catch (e) {
      console.error('Failed to load logs:', e);
    }
  }

  saveLogs() {
    try {
      const logsToSave = this.logs.slice(-this.maxLogs);
      localStorage.setItem('app_logs', JSON.stringify(logsToSave));
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
    else if (level === 'INFO') console.info(consoleMsg, data);
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
    console.log('Logs cleared');
  }

  async downloadLogs() {
    try {
      const text = this.logs.map(log =>
        `[${log.timestamp}] [${log.level}] [${log.category}] ${log.message}${log.data ? ' | ' + log.data : ''}`
      ).join('\n');

      const fileName = `location_tracker_logs_${Date.now()}.txt`;

      await Filesystem.writeFile({
        path: fileName,
        data: text,
        directory: Directory.Documents,
        encoding: 'utf8'
      });

      this.info('LOGGER', `Logs saved to Documents/${fileName}`);
      alert(`Logs saved to Documents/${fileName}`);
    } catch (e) {
      this.error('LOGGER', 'Download failed', e);
      alert('Failed to save logs: ' + e.message);
    }
  }
}

const logger = new Logger();

// ============================================
// STORAGE MANAGER WITH VERIFICATION
// ============================================
class StorageManager {
  static async saveHistory(history) {
    try {
      const data = JSON.stringify(history);

      // Primary storage
      localStorage.setItem('location_history', data);

      // Backup storage
      localStorage.setItem('location_history_backup', data);

      // Verification
      const verified = localStorage.getItem('location_history');
      if (verified === data) {
        logger.debug('STORAGE', `Verified save: ${history.length} records`);
        return true;
      } else {
        logger.error('STORAGE', 'Verification failed!');
        return false;
      }
    } catch (e) {
      logger.error('STORAGE', 'Save failed', e);
      return false;
    }
  }

  static loadHistory() {
    try {
      const primary = localStorage.getItem('location_history');
      if (primary) {
        const data = JSON.parse(primary);
        logger.info('STORAGE', `Loaded ${data.length} records from primary`);
        return data;
      }

      const backup = localStorage.getItem('location_history_backup');
      if (backup) {
        const data = JSON.parse(backup);
        logger.warn('STORAGE', `Loaded ${data.length} records from BACKUP`);
        return data;
      }

      logger.info('STORAGE', 'No history found');
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
  const [gpsEnabled, setGpsEnabled] = useState(true);

  const trackingLock = useRef(false);
  const intervalRef = useRef(null);
  const lastCaptureTime = useRef(0);
  const gpsCheckInterval = useRef(null);

  // Load data on mount
  useEffect(() => {
    logger.info('APP', '═══ Application Started ═══');

    try {
      // Load history
      const loadedHistory = StorageManager.loadHistory();
      setHistory(loadedHistory);

      // Load interval setting
      const savedInterval = localStorage.getItem('tracking_interval');
      if (savedInterval) {
        const parsedInterval = parseInt(savedInterval);
        setInterval(parsedInterval);
        logger.info('SETTINGS', `Loaded interval: ${parsedInterval} min`);
      } else {
        logger.info('SETTINGS', 'Using default interval: 5 min');
      }

      // Resume tracking if was active
      const wasTracking = localStorage.getItem('was_tracking') === 'true';
      if (wasTracking) {
        logger.info('APP', 'Auto-resuming tracking');
        setIsTracking(true);
      }

      // App state listener
      CapApp.addListener('appStateChange', ({ isActive }) => {
        logger.info('APP_STATE', isActive ? 'Foreground' : 'Background');
      });

      // GPS status checker
      startGPSChecker();

    } catch (e) {
      logger.error('APP', 'Initialization error', e);
    }

    return () => {
      logger.info('APP', '═══ Application Closing ═══');
      if (gpsCheckInterval.current) {
        clearInterval(gpsCheckInterval.current);
      }
    };
  }, []);

  // GPS Status Checker
  const startGPSChecker = () => {
    const checkGPS = async () => {
      try {
        await Geolocation.getCurrentPosition({ timeout: 5000 });
        if (!gpsEnabled) {
          logger.info('GPS', 'GPS enabled');
          setGpsEnabled(true);
        }
      } catch (e) {
        if (e.message.includes('location') || e.message.includes('denied')) {
          if (gpsEnabled) {
            logger.warn('GPS', 'GPS disabled');
            setGpsEnabled(false);
          }
        }
      }
    };

    checkGPS();
    gpsCheckInterval.current = setInterval(checkGPS, 10000); // Check every 10s
  };

  // Capture location function
  const captureLocation = async () => {
    const now = Date.now();

    // Prevent rapid calls
    if (trackingLock.current) {
      logger.debug('TRACK', 'Skipped: already capturing');
      return;
    }

    if ((now - lastCaptureTime.current) < 10000 && lastCaptureTime.current !== 0) {
      logger.debug('TRACK', 'Skipped: too soon');
      return;
    }

    trackingLock.current = true;
    logger.info('TRACK', '▶ Starting capture');

    try {
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

      logger.info('TRACK', '✓ Location captured', {
        lat: newEntry.latitude.toFixed(6),
        lng: newEntry.longitude.toFixed(6),
        acc: newEntry.accuracy
      });

      setCurrentLocation(newEntry);
      lastCaptureTime.current = now;

      // Update history with strict duplicate prevention
      setHistory(prev => {
        const isDuplicate = prev.some(item =>
          item.id === newEntry.id || (
            Math.abs(item.latitude - newEntry.latitude) < 0.000001 &&
            Math.abs(item.longitude - newEntry.longitude) < 0.000001 &&
            Math.abs(item.id - newEntry.id) < 30000 // 30 second window
          )
        );

        if (isDuplicate) {
          logger.warn('TRACK', '⚠ Duplicate prevented');
          return prev;
        }

        const updated = [newEntry, ...prev].slice(0, 50);

        // Immediate save with verification
        StorageManager.saveHistory(updated).then(success => {
          if (success) {
            logger.info('STORAGE', `✓ Saved ${updated.length} records`);
          } else {
            logger.error('STORAGE', '✗ Save verification failed');
          }
        });

        return updated;
      });

    } catch (error) {
      logger.error('TRACK', '✗ Capture failed', { message: error.message });

      if (error.message.includes('location') || error.message.includes('denied')) {
        setGpsEnabled(false);
      }
    } finally {
      trackingLock.current = false;
      logger.debug('TRACK', '■ Capture complete');
    }
  };

  // Tracking control with interval reload
  useEffect(() => {
    // Clear existing interval
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
      logger.debug('TRACK', 'Cleared previous interval');
    }

    if (!isTracking) {
      logger.info('TRACK', '⏸ Tracking stopped');
      localStorage.setItem('was_tracking', 'false');
      return;
    }

    // CRITICAL: Reload interval from storage EVERY time tracking starts
    const currentInterval = parseInt(localStorage.getItem('tracking_interval') || '5');
    setInterval(currentInterval);

    logger.info('TRACK', `▶ Tracking started (interval: ${currentInterval} min)`);
    localStorage.setItem('was_tracking', 'true');

    // Capture immediately
    captureLocation();

    // Set up interval
    const intervalMs = currentInterval * 60 * 1000;
    intervalRef.current = setInterval(() => {
      logger.info('TRACK', `⏰ Interval trigger (${currentInterval} min)`);
      captureLocation();
    }, intervalMs);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [isTracking]); // Only depend on isTracking, not interval

  // Refresh logs
  useEffect(() => {
    if (showLogs) setLogs(logger.getLogs());
  }, [showLogs]);

  const handleStartTracking = async () => {
    try {
      logger.info('USER', 'Start button clicked');
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
    logger.info('USER', 'Stop button clicked');
    setIsTracking(false);
  };

  const clearHistory = () => {
    if (window.confirm('Clear all location history?')) {
      logger.info('USER', 'Clearing history');
      setHistory([]);
      StorageManager.saveHistory([]);
    }
  };

  const saveIntervalSetting = (newInterval) => {
    logger.info('USER', `Saving interval: ${newInterval} min`);

    // Save to localStorage FIRST
    localStorage.setItem('tracking_interval', newInterval.toString());

    // Verify it was saved
    const verified = localStorage.getItem('tracking_interval');
    if (verified === newInterval.toString()) {
      logger.info('SETTINGS', `✓ Interval saved and verified: ${newInterval} min`);
      setInterval(newInterval);
      setShowSettings(false);

      if (isTracking) {
        alert(`Interval changed to ${newInterval} min. Restart tracking for changes to take effect.`);
      } else {
        alert(`Interval set to ${newInterval} min`);
      }
    } else {
      logger.error('SETTINGS', '✗ Interval save verification failed');
      alert('Failed to save settings. Please try again.');
    }
  };

  const openLocationSettings = () => {
    logger.info('USER', 'Opening location settings');
    alert('Please enable Location in your device Settings:\n\nSettings > Location > Turn ON');
  };

  return (
    <div className="min-vh-100" style={{
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      minHeight: '100vh'
    }}>
      {/* Header with frosted glass effect */}
      <nav className="navbar shadow-lg fade-in" style={{
        background: 'rgba(255,255,255,0.95)',
        backdropFilter: 'blur(20px)',
        borderBottom: '1px solid rgba(255,255,255,0.2)'
      }}>
        <div className="container">
          <span className="navbar-brand mb-0 h1" style={{
            color: '#667eea',
            fontWeight: '700',
            letterSpacing: '-0.5px'
          }}>
            <i className="bi bi-geo-alt-fill me-2"></i>
            Location Tracker
          </span>
          <div className="d-flex align-items-center gap-2">
            <span className={`badge fs-6 smooth-transition ${isTracking ? 'bg-success pulse-animation' : 'bg-secondary'}`}>
              {isTracking ? '● TRACKING' : '○ STOPPED'}
            </span>
            <button
              className="btn btn-sm btn-press smooth-transition"
              style={{ background: '#667eea', color: 'white', borderRadius: '12px' }}
              onClick={() => setShowSettings(!showSettings)}
            >
              <i className="bi bi-gear-fill"></i>
            </button>
            <button
              className="btn btn-sm btn-press smooth-transition"
              style={{ background: '#764ba2', color: 'white', borderRadius: '12px' }}
              onClick={() => setShowLogs(!showLogs)}
            >
              <i className="bi bi-file-text-fill"></i>
            </button>
          </div>
        </div>
      </nav>

      <div className="container py-4">

        {/* GPS Warning Banner */}
        {!gpsEnabled && (
          <div className="row mb-4 scale-in">
            <div className="col-12">
              <div className="alert alert-warning shadow-lg border-0 d-flex align-items-center justify-content-between" style={{ borderRadius: '16px' }}>
                <div>
                  <i className="bi bi-exclamation-triangle-fill me-2"></i>
                  <strong>Location services disabled</strong>
                  <p className="mb-0 small">Enable location to start tracking</p>
                </div>
                <button className="btn btn-warning btn-press" onClick={openLocationSettings}>
                  Open Settings
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Log Viewer */}
        {showLogs && (
          <div className="row mb-4 fade-in">
            <div className="col-12">
              <div className="card shadow-lg border-0" style={{ borderRadius: '20px', overflow: 'hidden' }}>
                <div className="card-header d-flex justify-content-between align-items-center" style={{
                  background: 'linear-gradient(135deg, #764ba2 0%, #667eea 100%)',
                  color: 'white',
                  padding: '16px 20px'
                }}>
                  <h5 className="mb-0 fw-bold">Debug Logs ({logs.length})</h5>
                  <div>
                    <button className="btn btn-sm btn-light me-2 btn-press" onClick={() => setLogs(logger.getLogs())}>
                      <i className="bi bi-arrow-clockwise"></i>
                    </button>
                    <button className="btn btn-sm btn-light me-2 btn-press" onClick={() => logger.downloadLogs()}>
                      <i className="bi bi-download"></i>
                    </button>
                    <button className="btn btn-sm btn-danger btn-press" onClick={() => { logger.clearLogs(); setLogs([]); }}>
                      <i className="bi bi-trash"></i>
                    </button>
                  </div>
                </div>
                <div className="card-body p-0" style={{
                  maxHeight: '300px',
                  overflow: 'auto',
                  background: '#1e1e1e',
                  color: '#d4d4d4'
                }}>
                  {logs.length === 0 ? (
                    <div className="p-4 text-center text-muted">No logs yet</div>
                  ) : (
                    <table className="table table-sm table-dark mb-0" style={{ fontSize: '11px' }}>
                      <tbody>
                        {logs.slice().reverse().slice(0, 100).map((log, i) => (
                          <tr key={i} className="smooth-transition">
                            <td style={{ width: '160px', fontSize: '9px', fontFamily: 'monospace' }}>{log.timestamp}</td>
                            <td style={{ width: '60px' }}>
                              <span className={`badge bg-${log.level === 'ERROR' ? 'danger' :
                                  log.level === 'WARN' ? 'warning' :
                                    log.level === 'INFO' ? 'info' : 'secondary'
                                } small`}>{log.level}</span>
                            </td>
                            <td style={{ width: '80px', fontWeight: '600' }}>{log.category}</td>
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
          <div className="row mb-4 scale-in">
            <div className="col-12">
              <div className="card shadow-lg border-0" style={{ borderRadius: '20px', overflow: 'hidden' }}>
                <div className="card-header" style={{
                  background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
                  color: 'white',
                  padding: '16px 20px'
                }}>
                  <h5 className="mb-0 fw-bold">
                    <i className="bi bi-sliders me-2"></i>
                    Settings
                  </h5>
                </div>
                <div className="card-body" style={{ padding: '24px' }}>
                  <label className="form-label fw-bold">Tracking Interval</label>
                  <select
                    className="form-select mb-3 smooth-transition"
                    style={{ borderRadius: '12px', fontSize: '15px', padding: '12px' }}
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
                      className="btn btn-primary btn-press smooth-transition"
                      style={{ borderRadius: '12px', padding: '10px 24px' }}
                      onClick={() => saveIntervalSetting(interval)}
                    >
                      <i className="bi bi-check-circle me-2"></i>
                      Save Changes
                    </button>
                    <button
                      className="btn btn-secondary btn-press smooth-transition"
                      style={{ borderRadius: '12px', padding: '10px 24px' }}
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

        {/* Control Buttons */}
        <div className="row mb-4">
          <div className="col-md-6 mb-3">
            <div className="card shadow-lg border-0 h-100 card-hover" style={{ borderRadius: '20px', overflow: 'hidden' }}>
              <div className="card-body d-flex flex-column justify-content-center" style={{ padding: '24px' }}>
                <button
                  className={`btn btn-lg w-100 btn-press spring-transition ${isTracking ? 'btn-danger' : 'btn-success'}`}
                  onClick={isTracking ? handleStopTracking : handleStartTracking}
                  style={{
                    borderRadius: '16px',
                    padding: '20px',
                    fontWeight: '700',
                    fontSize: '18px',
                    boxShadow: '0 8px 16px rgba(0,0,0,0.15)'
                  }}
                >
                  <i className={`bi ${isTracking ? 'bi-stop-circle-fill' : 'bi-play-circle-fill'} me-2`}></i>
                  {isTracking ? 'Stop Tracking' : 'Start Tracking'}
                </button>
                <div className="text-center mt-3 text-muted small fw-medium">
                  Every {interval} minute{interval !== 1 ? 's' : ''}
                </div>
              </div>
            </div>
          </div>
          <div className="col-md-6">
            <div className="card shadow-lg border-0 h-100 card-hover" style={{ borderRadius: '20px', overflow: 'hidden' }}>
              <div className="card-body d-flex flex-column justify-content-center" style={{ padding: '24px' }}>
                <button
                  className="btn btn-lg btn-outline-danger w-100 btn-press spring-transition"
                  onClick={clearHistory}
                  disabled={history.length === 0}
                  style={{
                    borderRadius: '16px',
                    padding: '20px',
                    fontWeight: '700',
                    fontSize: '18px',
                    borderWidth: '2px'
                  }}
                >
                  <i className="bi bi-trash me-2"></i>
                  Clear History
                </button>
                <div className="text-center mt-3 text-muted small fw-medium">
                  {history.length} record{history.length !== 1 ? 's' : ''} stored
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Current Location */}
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-lg border-0 card-hover" style={{
              borderRadius: '20px',
              overflow: 'hidden',
              background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)'
            }}>
              <div className="card-header border-0 text-white" style={{ padding: '16px 20px' }}>
                <h5 className="mb-0 fw-bold">
                  <i className="bi bi-crosshair me-2"></i>
                  Current Location
                </h5>
              </div>
              <div className="card-body text-center bg-white" style={{ padding: '32px' }}>
                {currentLocation ? (
                  <div className="fade-in">
                    <div className="row">
                      <div className="col-md-6 mb-3">
                        <h6 className="text-muted small fw-bold mb-2">LATITUDE</h6>
                        <h2 className="fw-bold" style={{
                          color: '#4facfe',
                          fontSize: '2.5rem',
                          letterSpacing: '-1px'
                        }}>
                          {currentLocation.latitude.toFixed(6)}
                        </h2>
                      </div>
                      <div className="col-md-6">
                        <h6 className="text-muted small fw-bold mb-2">LONGITUDE</h6>
                        <h2 className="fw-bold" style={{
                          color: '#00f2fe',
                          fontSize: '2.5rem',
                          letterSpacing: '-1px'
                        }}>
                          {currentLocation.longitude.toFixed(6)}
                        </h2>
                      </div>
                    </div>
                    <hr style={{ margin: '24px 0' }} />
                    <div className="text-muted">
                      <i className="bi bi-clock me-2"></i>
                      <strong>{currentLocation.timestamp}</strong>
                      <span className="mx-3">•</span>
                      <i className="bi bi-bullseye me-2"></i>
                      <strong>±{currentLocation.accuracy}m</strong>
                    </div>
                  </div>
                ) : (
                  <div className="text-muted py-5">
                    <i className="bi bi-geo-alt display-1 pulse-animation"></i>
                    <p className="mt-3 fw-medium">Waiting for location data...</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* History */}
        <div className="row">
          <div className="col-12">
            <div className="card shadow-lg border-0 card-hover" style={{ borderRadius: '20px', overflow: 'hidden' }}>
              <div className="card-header d-flex justify-content-between align-items-center" style={{
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                color: 'white',
                padding: '16px 20px'
              }}>
                <h5 className="mb-0 fw-bold">
                  <i className="bi bi-clock-history me-2"></i>
                  Location History
                </h5>
                <span className="badge bg-light text-dark fw-bold">{history.length}</span>
              </div>
              <div className="card-body p-0">
                {history.length === 0 ? (
                  <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox display-3"></i>
                    <p className="mt-3 fw-medium">No locations recorded yet</p>
                    <p className="small">Start tracking to see your location history</p>
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-hover mb-0">
                      <thead style={{ background: '#f8f9fa' }}>
                        <tr>
                          <th style={{ padding: '12px 16px', fontWeight: '600' }}>#</th>
                          <th style={{ padding: '12px 16px', fontWeight: '600' }}>
                            <i className="bi bi-clock me-1"></i>Time (IST)
                          </th>
                          <th style={{ padding: '12px 16px', fontWeight: '600' }}>
                            <i className="bi bi-geo-alt me-1"></i>Latitude
                          </th>
                          <th style={{ padding: '12px 16px', fontWeight: '600' }}>
                            <i className="bi bi-geo-alt me-1"></i>Longitude
                          </th>
                          <th style={{ padding: '12px 16px', fontWeight: '600' }}>
                            <i className="bi bi-bullseye me-1"></i>Accuracy
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {history.map((entry, index) => (
                          <tr key={entry.id} className="smooth-transition">
                            <th style={{ padding: '12px 16px' }}>{index + 1}</th>
                            <td style={{ padding: '12px 16px' }}>{entry.timestamp}</td>
                            <td className="font-monospace text-primary fw-medium" style={{ padding: '12px 16px' }}>
                              {entry.latitude.toFixed(6)}
                            </td>
                            <td className="font-monospace text-info fw-medium" style={{ padding: '12px 16px' }}>
                              {entry.longitude.toFixed(6)}
                            </td>
                            <td style={{ padding: '12px 16px' }}>±{entry.accuracy}m</td>
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
