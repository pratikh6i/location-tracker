import React, { useState, useEffect } from 'react';
import { initializeApp } from 'firebase/app';
import { 
  getAuth, 
  signInWithPopup, 
  GoogleAuthProvider, 
  onAuthStateChanged, 
  signOut,
  signInAnonymously
} from 'firebase/auth';
import { 
  getFirestore, 
  doc, 
  setDoc, 
  onSnapshot
} from 'firebase/firestore';
import { 
  MapPin, Wifi, WifiOff, Settings, LogOut, UploadCloud, Smartphone, AlertTriangle, ShieldCheck, Activity, FileSpreadsheet
} from 'lucide-react';

// --- CONFIGURATION PLACEHOLDERS ---
// YOU MUST REPLACE THESE VALUES IN THE FIREBASE CONSOLE OR HERE BEFORE BUILDING
const firebaseConfig = {
  apiKey: "REPLACE_WITH_YOUR_API_KEY",
  authDomain: "REPLACE_WITH_YOUR_PROJECT_ID.firebaseapp.com",
  projectId: "REPLACE_WITH_YOUR_PROJECT_ID",
  storageBucket: "REPLACE_WITH_YOUR_PROJECT_ID.firebasestorage.app",
  messagingSenderId: "123456789",
  appId: "1:123456789:web:abcdef"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// ... (Rest of the component logic tailored for mobile) ...

const useAuth = () => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (u) => {
      setUser(u);
      setLoading(false);
    });
    return () => unsubscribe();
  }, []);

  const login = async () => {
    const provider = new GoogleAuthProvider();
    try {
      await signInWithPopup(auth, provider);
    } catch (error) {
      console.error("Login Error", error);
      // Fallback for demo/testing without full SHA-1 setup
      await signInAnonymously(auth);
    }
  };

  const logout = () => signOut(auth);
  return { user, loading, login, logout };
};

const LoginScreen = ({ onLogin }) => (
  <div className="min-h-screen flex flex-col items-center justify-center bg-gray-50 p-6">
    <div className="w-full max-w-md bg-white rounded-2xl shadow-xl p-8 text-center border border-gray-100">
      <div className="mx-auto w-16 h-16 bg-blue-50 rounded-full flex items-center justify-center mb-6">
        <MapPin className="text-blue-600 w-8 h-8" />
      </div>
      <h1 className="text-2xl font-bold text-gray-800 mb-2">Secure Tracker</h1>
      <button onClick={onLogin} className="w-full mt-6 bg-blue-600 text-white py-3 rounded-lg font-medium">
        Sign In / Start
      </button>
    </div>
  </div>
);

export default function App() {
  const { user, loading, login, logout } = useAuth();
  const [activeTab, setActiveTab] = useState('dashboard');
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [lastLocation, setLastLocation] = useState(null);
  const [locationQueue, setLocationQueue] = useState([]);
  const [settings, setSettings] = useState({ smsNumber: '', offlineThresholdHours: 2, sheetUrl: '' });
  const [alertMessage, setAlertMessage] = useState(null);

  // Load Settings
  useEffect(() => {
    if (!user) return;
    const savedQueue = localStorage.getItem(`queue_${user.uid}`);
    if (savedQueue) setLocationQueue(JSON.parse(savedQueue));
    const unsub = onSnapshot(doc(db, 'users', user.uid, 'settings', 'config'), (d) => {
      if (d.exists()) setSettings(d.data());
    });
    return () => unsub();
  }, [user]);

  // Persist Queue
  useEffect(() => {
    if (!user) return;
    localStorage.setItem(`queue_${user.uid}`, JSON.stringify(locationQueue));
  }, [locationQueue, user]);

  // Network Monitor
  useEffect(() => {
    const handleStatus = () => setIsOnline(navigator.onLine);
    window.addEventListener('online', handleStatus);
    window.addEventListener('offline', handleStatus);
    return () => { window.removeEventListener('online', handleStatus); window.removeEventListener('offline', handleStatus); };
  }, []);

  // Tracking Loop
  useEffect(() => {
    if (!user) return;
    const track = () => {
      if (!navigator.geolocation) return;
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const log = {
            lat: pos.coords.latitude,
            lng: pos.coords.longitude,
            time: new Date().toISOString()
          };
          setLastLocation(log);
          if (navigator.onLine) {
             // Simulate upload or call your cloud function here
             console.log("Uploading", log);
          } else {
             setLocationQueue(prev => [...prev, log]);
          }
        },
        (err) => console.error(err),
        { enableHighAccuracy: true }
      );
    };
    track();
    const interval = setInterval(track, 300000); // 5 mins
    return () => clearInterval(interval);
  }, [user]);

  const handleSave = async () => {
    if (!user) return;
    await setDoc(doc(db, 'users', user.uid, 'settings', 'config'), settings);
    setAlertMessage("Settings Saved");
    setTimeout(() => setAlertMessage(null), 3000);
  };

  const generateSMSLink = () => {
    const body = `HELP: Last loc ${lastLocation?.lat},${lastLocation?.lng}`;
    return `sms:${settings.smsNumber}?body=${encodeURIComponent(body)}`;
  };

  if (loading) return <div>Loading...</div>;
  if (!user) return <LoginScreen onLogin={login} />;

  return (
    <div className="min-h-screen bg-gray-50 pb-20 font-sans">
      <header className="bg-white p-4 shadow-sm flex justify-between items-center sticky top-0 z-10">
        <div className="font-bold text-lg flex items-center gap-2">
          <div className={`w-3 h-3 rounded-full ${isOnline ? 'bg-green-500' : 'bg-red-500'}`} />
          LocationSync
        </div>
        <button onClick={logout}><LogOut className="w-5 h-5" /></button>
      </header>

      {alertMessage && <div className="bg-green-100 p-3 text-center text-green-800 text-sm">{alertMessage}</div>}

      <main className="p-4 space-y-4">
        {activeTab === 'dashboard' && (
          <>
            <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 text-center">
              <div className="text-gray-500 text-sm uppercase tracking-wide mb-2">Current Status</div>
              <div className="text-2xl font-bold mb-4">{isOnline ? 'Online & Syncing' : 'Offline Mode'}</div>
              {lastLocation ? (
                 <div className="bg-blue-50 p-4 rounded-lg font-mono text-blue-800">
                   {lastLocation.lat.toFixed(5)}, {lastLocation.lng.toFixed(5)}
                 </div>
              ) : (
                <div className="text-gray-400">Locating...</div>
              )}
            </div>

            {!isOnline && (
              <div className="bg-orange-50 p-4 rounded-xl border border-orange-100 flex items-center justify-between">
                <span className="text-orange-800 font-bold">{locationQueue.length} Pending Logs</span>
                <Activity className="text-orange-400" />
              </div>
            )}

            <a href={generateSMSLink()} className="block w-full bg-white border border-gray-200 p-4 rounded-xl text-center shadow-sm hover:bg-gray-50">
              <Smartphone className="inline-block w-5 h-5 mr-2 text-gray-600" />
              <span className="font-medium">Test SMS Alert</span>
            </a>
          </>
        )}

        {activeTab === 'settings' && (
          <div className="bg-white p-6 rounded-xl shadow-sm space-y-4">
            <h2 className="font-bold text-lg">Config</h2>
            <input 
              type="text" 
              placeholder="Google Script URL" 
              className="w-full p-3 border rounded-lg"
              value={settings.sheetUrl}
              onChange={e => setSettings({...settings, sheetUrl: e.target.value})}
            />
            <input 
              type="tel" 
              placeholder="Emergency SMS Number" 
              className="w-full p-3 border rounded-lg"
              value={settings.smsNumber}
              onChange={e => setSettings({...settings, smsNumber: e.target.value})}
            />
            <button onClick={handleSave} className="w-full bg-blue-600 text-white p-3 rounded-lg font-bold">Save Settings</button>
          </div>
        )}
      </main>

      <nav className="fixed bottom-0 w-full bg-white border-t flex justify-around p-4 z-20">
        <button onClick={() => setActiveTab('dashboard')} className={activeTab === 'dashboard' ? 'text-blue-600' : 'text-gray-400'}>
          <Activity />
        </button>
        <button onClick={() => setActiveTab('settings')} className={activeTab === 'settings' ? 'text-blue-600' : 'text-gray-400'}>
          <Settings />
        </button>
      </nav>
    </div>
  );
}
