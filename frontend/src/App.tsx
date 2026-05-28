import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './features/auth/AuthContext';
import ProtectedRoute from './features/shared/ProtectedRoute';
import DashboardLayout from './layouts/DashboardLayout';
import TitularLayout from './layouts/TitularLayout';
import LoginPage from './pages/LoginPage';
import TitularLoginPage from './pages/TitularLoginPage';
import RoleSelectPage from './pages/RoleSelectPage';
import DashboardPage from './pages/DashboardPage';
import ConsentimientosPage from './pages/ConsentimientosPage';
import NuevoConsentimientoPage from './pages/NuevoConsentimientoPage';
import UsuariosPage from './pages/UsuariosPage';
import DominiosPage from './pages/DominiosPage';
import AuditTrailPage from './pages/AuditTrailPage';
import TemplatesPage from './pages/TemplatesPage';
import CompliancePage from './pages/CompliancePage';
import PerfilPage from './pages/PerfilPage';
import TitularPortalPage from './pages/TitularPortalPage';
import SolicitudesPage from './pages/SolicitudesPage';
import NotFoundPage from './pages/NotFoundPage';

const App = () => (
  <AuthProvider>
    <BrowserRouter>
      <Routes>
        {/* Portales de login y selección de rol */}
        <Route path="/login"           element={<LoginPage />} />
        <Route path="/titular/login"   element={<TitularLoginPage />} />
        <Route path="/seleccionar-rol" element={<RoleSelectPage />} />

        {/* Portal operativo (ADMIN, DPO, JEFE_DOMINIO) */}
        <Route
          element={
            <ProtectedRoute roles={['ADMIN', 'DPO', 'JEFE_DOMINIO']}>
              <DashboardLayout />
            </ProtectedRoute>
          }
        >
          <Route
            index
            element={
              <ProtectedRoute roles={['ADMIN', 'DPO', 'JEFE_DOMINIO']}>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route path="consentimientos" element={
            <ProtectedRoute roles={['DPO', 'JEFE_DOMINIO']}>
              <ConsentimientosPage />
            </ProtectedRoute>
          } />
          <Route path="solicitudes" element={
            <ProtectedRoute roles={['DPO', 'JEFE_DOMINIO']}>
              <SolicitudesPage />
            </ProtectedRoute>
          } />
          <Route path="consentimientos/nuevo" element={
            <ProtectedRoute roles={['DPO']}>
              <NuevoConsentimientoPage />
            </ProtectedRoute>
          } />
          <Route path="dominios" element={
            <ProtectedRoute roles={['ADMIN']}>
              <DominiosPage />
            </ProtectedRoute>
          } />
          <Route path="usuarios" element={
            <ProtectedRoute roles={['ADMIN']}>
              <UsuariosPage />
            </ProtectedRoute>
          } />
          <Route path="auditoria" element={
            <ProtectedRoute roles={['DPO']}>
              <AuditTrailPage />
            </ProtectedRoute>
          } />
          <Route path="cumplimiento" element={
            <ProtectedRoute roles={['DPO']}>
              <CompliancePage />
            </ProtectedRoute>
          } />
          <Route path="plantillas" element={
            <ProtectedRoute roles={['DPO']}>
              <TemplatesPage />
            </ProtectedRoute>
          } />
          <Route path="perfil" element={
            <ProtectedRoute roles={['ADMIN', 'DPO', 'JEFE_DOMINIO']}>
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
