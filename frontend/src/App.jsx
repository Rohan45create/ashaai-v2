import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import AppErrorBoundary from './components/AppErrorBoundary';

// Guards & Layouts
import ASHAGuard from './guards/ASHAGuard.jsx';
import AdminGuard from './guards/AdminGuard.jsx';
import MobileLayout from './layouts/MobileLayout.jsx';
import AdminLayout from './layouts/AdminLayout.jsx';

// Auth Routes
const ASHALogin = lazy(() => import('./routes/auth/ASHALogin.jsx'));
const AdminLogin = lazy(() => import('./routes/auth/AdminLogin.jsx'));

// ASHA Routes
const Home = lazy(() => import('./routes/asha/Home.jsx'));
const Profile = lazy(() => import('./routes/asha/Profile.jsx'));
const AskAshaAI = lazy(() => import('./routes/asha/AskAshaAI.jsx'));
const PriorityList = lazy(() => import('./routes/asha/PriorityList.jsx'));
const RegisterScan = lazy(() => import('./routes/asha/RegisterScan.jsx'));
const FamilySurvey = lazy(() => import('./routes/asha/modules/FamilySurvey.jsx'));
const VillageHealth = lazy(() => import('./routes/asha/modules/VillageHealth.jsx'));
const ANCRegistration = lazy(() => import('./routes/asha/modules/ANCRegistration.jsx'));
const ChildGrowth = lazy(() => import('./routes/asha/modules/ChildGrowth.jsx'));
const Vaccination = lazy(() => import('./routes/asha/modules/Vaccination.jsx'));
const BirthRecord = lazy(() => import('./routes/asha/modules/BirthRecord.jsx'));
const DeathRecord = lazy(() => import('./routes/asha/modules/DeathRecord.jsx'));
const DiseaseSurveillance = lazy(() => import('./routes/asha/modules/DiseaseSurveillance.jsx'));
const NCDTracking = lazy(() => import('./routes/asha/modules/NCDTracking.jsx'));
const FamilyPlanning = lazy(() => import('./routes/asha/modules/FamilyPlanning.jsx'));
const Sanitation = lazy(() => import('./routes/asha/modules/Sanitation.jsx'));
const ElderlyCare = lazy(() => import('./routes/asha/modules/ElderlyCare.jsx'));
const DynamicSurvey = lazy(() => import('./routes/asha/modules/DynamicSurvey.jsx'));
const AppointmentsList = lazy(() => import('./routes/asha/AppointmentsList.jsx'));

// Admin Routes
const AdminDashboard = lazy(() => import('./routes/admin/Dashboard.jsx'));
const SurveyBuilder = lazy(() => import('./routes/admin/SurveyBuilder.jsx'));
const CoverageMap = lazy(() => import('./routes/admin/CoverageMap.jsx'));
const WorkerManagement = lazy(() => import('./routes/admin/WorkerManagement.jsx'));
const WorkerActivity = lazy(() => import('./routes/admin/WorkerActivity.jsx'));
const PendingReview = lazy(() => import('./routes/admin/PendingReview.jsx'));
const Reports = lazy(() => import('./routes/admin/Reports.jsx'));
const AdminReferrals = lazy(() => import('./routes/admin/Referrals.jsx'));
const NGOManagement = lazy(() => import('./routes/admin/NGOManagement.jsx'));

import { useAuthStore } from './stores/authStore';
import { useConfigStore } from './utils/configStore';

const LoadingFallback = ({ authTimedOut }) => (
  <div className="min-h-screen bg-[#F1EFE8] flex flex-col p-4 animate-pulse">
    {authTimedOut && (
      <div style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        justifyContent: 'center', minHeight: '50vh', gap: '12px', padding: '24px'
      }}>
        <p style={{ color: '#666', fontSize: '14px' }}>
          Taking longer than usual to load.
        </p>
        <button
          onClick={() => window.location.reload()}
          style={{
            padding: '10px 24px', background: '#1D9E75', color: 'white',
            border: 'none', borderRadius: '8px', fontSize: '14px', cursor: 'pointer'
          }}
        >
          Reload
        </button>
      </div>
    )}
    <div className="h-16 bg-gray-200 rounded-2xl w-full mb-6"></div>
    <div className="h-32 bg-[#EAF3DE] rounded-3xl w-full mb-6 opacity-60"></div>
    <div className="grid grid-cols-2 gap-4">
       <div className="h-28 bg-white rounded-3xl w-full"></div>
       <div className="h-28 bg-white rounded-3xl w-full"></div>
       <div className="h-28 bg-white rounded-3xl w-full"></div>
       <div className="h-28 bg-white rounded-3xl w-full"></div>
    </div>
  </div>
);

const App = () => {
  const { setUser, setRole, setLoading, setHeadId, setAshaId, isLoading } = useAuthStore();
  const { isConfigLoaded, fetchConfig, error } = useConfigStore();
  const [authTimedOut, setAuthTimedOut] = React.useState(false);

  React.useEffect(() => {
    if (!isConfigLoaded && !error) {
      fetchConfig();
    }
  }, [isConfigLoaded, fetchConfig, error]);

  React.useEffect(() => {
    const timer = setTimeout(() => {
      if (isLoading) {
        setAuthTimedOut(true);
      }
    }, 15000);
    return () => clearTimeout(timer);
  }, [isLoading]);

  React.useEffect(() => {
    if (!isConfigLoaded) return;

    const handleLogout = () => {
      useAuthStore.getState().logout();
    };
    window.addEventListener('auth:logout', handleLogout);

    let subscription;
    try {
      const supabase = useAuthStore.getState().getSupabase();

      const handleAuthSession = async (session, event) => {
        if (session?.user) {
          setUser(session.user);

          // Only call resolveIdentity here for session RESTORE (page reload).
          // When an active login just completed via authStore.login / authStore.verifyOtp,
          // those functions already resolved identity and set role/docId in the store.
          // Running resolveIdentity again here would create a race condition that can
          // wipe the role right after it was set. We detect "active login already resolved"
          // by checking whether the store already has a role set.
          const alreadyResolved = useAuthStore.getState().role !== null;
          if (!alreadyResolved) {
            try {
              const token = session.access_token;
              const { resolveIdentity } = await import('./utils/api');
              const identity = await resolveIdentity(token);

              setRole(identity.role);
              useAuthStore.setState({ docId: identity.doc_id });

              if (identity.role === 'asha_head') {
                setHeadId(identity.doc_id);
                localStorage.setItem('headId', identity.doc_id);
              } else {
                setAshaId(identity.doc_id);
                localStorage.setItem('ashaId', identity.doc_id);
              }
            } catch (err) {
              console.error('Error in auth state handler:', err);
              setRole(null);
              useAuthStore.setState({ docId: null });
            }
          }
        } else {
          setUser(null);
          setRole(null);
          setHeadId(null);
          setAshaId(null);
          useAuthStore.setState({ docId: null });
          localStorage.removeItem('headId');
          localStorage.removeItem('ashaId');
        }
        setLoading(false);
      };

      // Initialize state from existing session (page reload / tab restore)
      supabase.auth.getSession().then(({ data: { session } }) => {
        handleAuthSession(session, 'INITIAL_SESSION');
      });

      // Listen for auth state changes (login, logout, token refresh)
      const { data } = supabase.auth.onAuthStateChange((event, session) => {
        handleAuthSession(session, event);
      });
      subscription = data.subscription;

    } catch (err) {
      console.error('Error initializing supabase auth:', err);
      setLoading(false);
    }

    return () => {
      if (subscription) subscription.unsubscribe();
      window.removeEventListener('auth:logout', handleLogout);
    };
  }, [isConfigLoaded, setUser, setRole, setLoading, setHeadId, setAshaId]);

  if (error) {
    return (
      <div className="min-h-screen bg-[#F1EFE8] flex items-center justify-center p-4">
        <div className="bg-white p-6 rounded-2xl shadow text-center max-w-sm">
          <h2 className="text-[#E24B4A] font-bold text-lg mb-2">Configuration Error</h2>
          <p className="text-gray-600 text-sm mb-4">{error}</p>
          <button onClick={() => window.location.reload()} className="bg-[#1D9E75] text-white px-4 py-2 rounded-lg text-sm">Retry</button>
        </div>
      </div>
    );
  }

  if (isLoading || !isConfigLoaded) {
    return <LoadingFallback authTimedOut={authTimedOut} />;
  }

  return (
    <AppErrorBoundary>
      <BrowserRouter>
        <Suspense fallback={<LoadingFallback authTimedOut={false} />}>
          <Routes>
            <Route path="/login" element={<ASHALogin />} />
            <Route path="/admin/login" element={<AdminLogin />} />

            {/* ASHA Protected Routes */}
            <Route element={<ASHAGuard><MobileLayout /></ASHAGuard>}>
              <Route path="/asha/home" element={<Home />} />
              <Route path="/asha/profile" element={<Profile />} />
              <Route path="/asha/ask" element={<AskAshaAI />} />
              <Route path="/asha/priority-list" element={<PriorityList />} />
              <Route path="/asha/register-scan" element={<RegisterScan />} />
              <Route path="/asha/family-survey" element={<FamilySurvey />} />
              <Route path="/asha/village-health" element={<VillageHealth />} />
              <Route path="/asha/anc" element={<ANCRegistration />} />
              <Route path="/asha/child-growth" element={<ChildGrowth />} />
              <Route path="/asha/vaccination" element={<Vaccination />} />
              <Route path="/asha/birth-record" element={<BirthRecord />} />
              <Route path="/asha/death-record" element={<DeathRecord />} />
              <Route path="/asha/disease-surveillance" element={<DiseaseSurveillance />} />
              <Route path="/asha/ncd-tracking" element={<NCDTracking />} />
              <Route path="/asha/family-planning" element={<FamilyPlanning />} />
              <Route path="/asha/sanitation" element={<Sanitation />} />
              <Route path="/asha/elderly-care" element={<ElderlyCare />} />
              <Route path="/asha/dynamic-survey" element={<DynamicSurvey />} />
              <Route path="/asha/appointments" element={<AppointmentsList />} />
            </Route>

            {/* Admin Protected Routes */}
            <Route element={<AdminGuard><AdminLayout /></AdminGuard>}>
              <Route path="/admin/dashboard" element={<AdminDashboard />} />
              <Route path="/admin/workers" element={<WorkerManagement />} />
              <Route path="/admin/worker/:id" element={<WorkerActivity />} />
              <Route path="/admin/review" element={<PendingReview />} />
              <Route path="/admin/builder" element={<SurveyBuilder />} />
              <Route path="/admin/map" element={<CoverageMap />} />
              <Route path="/admin/reports" element={<Reports />} />
              <Route path="/admin/referrals" element={<AdminReferrals />} />
              <Route path="/admin/ngos" element={<NGOManagement />} />
            </Route>

            {/* Redirects */}
            <Route path="/" element={<Navigate to="/asha/home" replace />} />
            <Route path="/asha" element={<Navigate to="/asha/home" replace />} />
            <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
    </AppErrorBoundary>
  );
};

export default App;
