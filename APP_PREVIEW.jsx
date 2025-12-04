import React, { useState, useEffect, useRef } from 'react';
import { Geolocation } from '@capacitor/geolocation';
import { App as CapApp } from '@capacitor/app';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { registerPlugin } from '@capacitor/core';

// Register native service plugin
const LocationService = registerPlugin('LocationService');

// [LOGGER CLASS - UNCHANGED]
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
        else if (level === 'INFO') console.info(consoleMsg, data);
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
    }

    async downloadLogs() {
        try {
            const text = this.logs.map(log =>
                `[${log.timestamp}] [${log.level}] [${log.category}] ${log.message}${log.data ? ' | ' + log.data : ''}`
            ).join('\n');

            const fileName = `traceract_logs_${Date.now()}.txt`;

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

// [STORAGE MANAGER - UNCHANGED]
class StorageManager {
    static async saveHistory(history) {
        try {
            const data = JSON.stringify(history);
            localStorage.setItem('location_history', data);
            localStorage.setItem('location_history_backup', data);

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

// SETUP SCREEN COMPONENT
function SetupScreen({ onComplete }) {
    const [deviceName, setDeviceName] = useState('');
    const [sheetsUrl, setSheetsUrl] = useState('');

    const handleSubmit = () => {
        if (!deviceName.trim()) {
            alert('Please enter a device name');
            return;
        }

        localStorage.setItem('device_name', deviceName);
        localStorage.setItem('sheets_url', sheetsUrl);
        localStorage.setItem('setup_complete', 'true');

        logger.info('SETUP', 'Setup completed', { deviceName, hasSheets: !!sheetsUrl });
        onComplete(deviceName, sheetsUrl);
    };

    return (
        <div className="min-vh-100 d-flex align-items-center justify-content-center" style={{
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
        }}>
            <div className="container">
                <div className="row justify-content-center">
                    <div className="col-md-6">
                        <div className="card shadow-lg border-0 scale-in" style={{ borderRadius: '24px' }}>
                            <div className="card-body p-5">
                                <div className="text-center mb-4">
                                    <div className="mb-3">
                                        <i className="bi bi-geo-alt-fill" style={{ fontSize: '64px', color: '#667eea' }}></i>
                                    </div>
                                    <h2 className="fw-bold">Welcome to Traceract</h2>
                                    <p className="text-muted">Let's set up your device</p>
                                </div>

                                <div className="mb-4">
                                    <label className="form-label fw-bold">Device Name <span className="text-danger">*</span></label>
                                    <input
                                        type="text"
                                        className="form-control form-control-lg"
                                        placeholder="e.g., My Phone"
                                        value={deviceName}
                                        onChange={(e) => setDeviceName(e.target.value)}
                                        style={{ borderRadius: '12px' }}
                                    />
                                    <small className="text-muted">This will appear in Google Sheets</small>
                                </div>

                                <div className="mb-4">
                                    <label className="form-label fw-bold">
                                        Google Sheets URL
                                        <span className="text-muted ms-2">(Optional)</span>
                                    </label>
                                    <textarea
                                        className="form-control"
                                        rows="3"
                                        placeholder="Paste your Google Apps Script Web App URL here"
                                        value={sheetsUrl}
                                        onChange={(e) => setSheetsUrl(e.target.value)}
                                        style={{ borderRadius: '12px', fontSize: '14px' }}
                                    />
                                    <small className="text-muted">Leave empty to skip Google Sheets integration</small>
                                </div>

                                <button
                                    className="btn btn-lg w-100 btn-primary btn-press spring-transition"
                                    onClick={handleSubmit}
                                    style={{
                                        borderRadius: '16px',
                                        padding: '16px',
                                        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                                        border: 'none'
                                    }}
                                >
                                    <i className="bi bi-check-circle me-2"></i>
                                    Continue
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default function App() {
    // Setup state
    const [setupComplete, setSetupComplete] = useState(false);
    const [deviceName, setDeviceName] = useState('');
    const [sheetsUrl, setSheetsUrl] = useState('');

    // App state
    const [isTracking, setIsTracking] = useState(false);
    const [currentLocation, setCurrentLocation] = useState(null);
    const [history, setHistory] = useState([]);
    const [trackingInterval, setTrackingInterval] = useState(5);
    const [showSettings, setShowSettings] = useState(false);
    const [showLogs, setShowLogs] = useState(false);
    const [logs, setLogs] = useState([]);
    const [gpsEnabled, setGpsEnabled] = useState(true);

    const trackingLock = useRef(false);
    const intervalRef = useRef(null);
    const lastCaptureTime = useRef(0);
    const gpsCheckInterval = useRef(null);

    // Check setup on mount
    useEffect(() => {
        logger.info('APP', '═══ Application Started ═══');

        const isSetup = localStorage.getItem('setup_complete') === 'true';
        if (isSetup) {
            const savedDeviceName = localStorage.getItem('device_name');
            const savedSheetsUrl = localStorage.getItem('sheets_url');

            setDeviceName(savedDeviceName || '');
            setSheetsUrl(savedSheetsUrl || '');
            setSetupComplete(true);

            logger.info('SETUP', 'Setup already completed', { deviceName: savedDeviceName });

            // Load history and settings
            loadAppData();
        }
    }, []);

    const loadAppData = () => {
        try {
            const loadedHistory = StorageManager.loadHistory();
            setHistory(loadedHistory);

            const savedInterval = localStorage.getItem('tracking_interval');
            if (savedInterval) {
                const parsedInterval = parseInt(savedInterval);
                setTrackingInterval(parsedInterval);
                logger.info('SETTINGS', `Loaded interval: ${parsedInterval} min`);
            } else {
                logger.info('SETTINGS', 'Using default interval: 5 min');
            }

            const wasTracking = localStorage.getItem('was_tracking') === 'true';
            if (wasTracking) {
                logger.info('APP', 'Auto-resuming tracking');
                setIsTracking(true);
            }

            CapApp.addListener('appStateChange', ({ isActive }) => {
                logger.info('APP_STATE', isActive ? 'Foreground' : 'Background');
            });

            startGPSChecker();
        } catch (e) {
            logger.error('APP', 'Initialization error', e);
        }
    };

    const handleSetupComplete = (name, url) => {
        setDeviceName(name);
        setSheetsUrl(url);
        setSetupComplete(true);
        loadAppData();
    };

    if (!setupComplete) {
        return <SetupScreen onComplete={handleSetupComplete} />;
    }

    // [REST OF THE APP - GPS CHECKER, CAPTURE, TRACKING LOGIC CONTINUES IN NEXT FILE]
    // Due to length, I'll continue this in the actual write_to_file

    return <div>App Loaded</div>;
}
