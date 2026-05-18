import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './features/auth/AuthContext';
import ProtectedRoute from './features/shared/ProtectedRoute';
import DashboardLayout from './layouts/DashboardLayout';
import TitularLayout from './layouts/TitularLayout';
import LoginPage from './pages/LoginPage';
import TitularLoginPage from './pages/TitularLoginPage';
import DashboardPage from './pages/DashboardPage';
import ConsentimientosPage from './pages/ConsentimientosPage';
import NuevoConsentimientoPage from './pages/NuevoConsentimientoPage';
import UsuariosPage from './pages/UsuariosPage';
import AuditTrailPage from './pages/AuditTrailPage';
import TemplatesPage from './pages/TemplatesPage';
import CompliancePage from './pages/CompliancePage';
import PerfilPage from './pages/PerfilPage';
import TitularPortalPage from './pages/TitularPortalPage';
import NotFoundPage from './pages/NotFoundPage';

const App = () => (
  <AuthProvider>
    <BrowserRouter>
      <Routes>
        {/* Portales de login */}
        <Route path="/login"          element={<LoginPage />} />
        <Route path="/titular/login"  element={<TitularLoginPage />} />

        {/* Portal operativo (ADMIN, DPO, USER) */}
        <Route
          element={
            <ProtectedRoute roles={['ADMIN', 'DPO', 'USER']}>
              <DashboardLayout />
            </ProtectedRoute>
          }
        >
          <Route
            index
            element={
              <ProtectedRoute roles={['ADMIN', 'DPO']} fallback="/consentimientos">
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route path="consentimientos" element={
            <ProtectedRoute roles={['DPO', 'USER']}>
              <ConsentimientosPage />
            </ProtectedRoute>
          } />
          <Route path="consentimientos/nuevo" element={
            <ProtectedRoute roles={['DPO']}>
              <NuevoConsentimientoPage />
            </ProtectedRoute>
          } />
          <Route path="usuarios" element={
            <ProtectedRoute roles={['ADMIN']}>
              <UsuariosPage />
            </ProtectedRoute>
          } />
          <Route path="auditoria" element={
            <ProtectedRoute roles={['ADMIN', 'DPO']}>
              <AuditTrailPage />
            </ProtectedRoute>
          } />
          <Route path="cumplimiento" element={
            <ProtectedRoute roles={['ADMIN', 'DPO']}>
              <CompliancePage />
            </ProtectedRoute>
          } />
          <Route path="plantillas" element={
            <ProtectedRoute roles={['DPO']}>
              <TemplatesPage />
            </ProtectedRoute>
          } />
          <Route path="perfil" element={
            <ProtectedRoute roles={['ADMIN', 'DPO', 'USER']}>
              <PerfilPage />
            </ProtectedRoute>
          } />
        </Route>

        {/* Portal titular (TITULAR) */}
        <Route
          path="titular"
          element={
            <ProtectedRoute roles={['TITULAR']} fallback="/titular/login">
              <TitularLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="mis-consentimientos" replace />} />
          <Route path="mis-consentimientos" element={<TitularPortalPage />} />
          <Route path="perfil"              element={<PerfilPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  </AuthProvider>
);

export default App;
