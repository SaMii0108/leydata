import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './features/auth/AuthContext';
import ProtectedRoute from './features/shared/ProtectedRoute';
import ErrorBoundary from './components/common/ErrorBoundary';
import DashboardLayout from './layouts/DashboardLayout';
import TitularLayout from './layouts/TitularLayout';
import LoginPage from './pages/LoginPage';
import TitularLoginPage from './pages/TitularLoginPage';
import RoleSelectPage from './pages/RoleSelectPage';
import DashboardPage from './pages/DashboardPage';
import ConsentimientosPage from './pages/ConsentimientosPage';
import NuevoConsentimientoPage from './pages/NuevoConsentimientoPage';
import UsuariosPage from './pages/UsuariosPage';
import DomainsPage from './pages/DomainsPage';
import AuditTrailPage from './pages/AuditTrailPage';
import TemplatesPage from './pages/TemplatesPage';
import CreateTemplatePage from './pages/CreateTemplatePage';
import TemplateVersionsPage from './pages/TemplateVersionsPage';
import TemplatePreviewPage from './pages/TemplatePreviewPage';
import FinalidadesPage from './pages/FinalidadesPage';
import CrearFinalidadPage from './pages/CrearFinalidadPage';
import DocumentosPrivacidadPage from './pages/DocumentosPrivacidadPage';
import CompliancePage from './pages/CompliancePage';
import PerfilPage from './pages/PerfilPage';
import MisSolicitudesPage from './pages/MisSolicitudesPage';
import NuevaSolicitudPage from './pages/NuevaSolicitudPage';
import AprobacionSolicitudesPage from './pages/AprobacionSolicitudesPage';
import TitularPortalPage from './pages/TitularPortalPage';
import MockRegisterPage from './pages/MockRegisterPage';
import NotFoundPage from './pages/NotFoundPage';

const App = () => (
  <AuthProvider>
    <ErrorBoundary
      title="Algo salió mal"
      message="Ocurrió un error inesperado en la aplicación. Intenta recargar la página."
      fullReload
    >
      <BrowserRouter>
        <Routes>
          {/* Portales de login */}
          <Route path="/login"           element={<LoginPage />} />
          <Route path="/titular/login"   element={<TitularLoginPage />} />
          <Route path="/seleccionar-rol" element={<RoleSelectPage />} />

          {/* Demo pública — sin autenticación */}
          <Route path="/mock/register" element={<MockRegisterPage />} />

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
                <ProtectedRoute roles={['ADMIN', 'DPO']} fallback="/consentimientos">
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route path="consentimientos" element={
              <ProtectedRoute roles={['DPO', 'JEFE_DOMINIO']}>
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
            <Route path="dominios" element={
              <ProtectedRoute roles={['ADMIN']}>
                <DomainsPage />
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

            {/* Finalidades */}
            <Route path="finalidades" element={
              <ProtectedRoute roles={['DPO', 'JEFE_DOMINIO']}>
                <FinalidadesPage />
              </ProtectedRoute>
            } />
            <Route path="finalidades/nueva" element={
              <ProtectedRoute roles={['DPO']}>
                <CrearFinalidadPage />
              </ProtectedRoute>
            } />

            {/* Documentos de Privacidad — DPO */}
            <Route path="documentos" element={
              <ProtectedRoute roles={['DPO']}>
                <DocumentosPrivacidadPage />
              </ProtectedRoute>
            } />

            {/* Plantillas — solo DPO */}
            <Route path="plantillas" element={
              <ProtectedRoute roles={['DPO']}>
                <TemplatesPage />
              </ProtectedRoute>
            } />
            <Route path="solicitudes" element={
              <ProtectedRoute roles={['JEFE_DOMINIO']}>
                <MisSolicitudesPage />
              </ProtectedRoute>
            } />
            <Route path="solicitudes/nueva" element={
              <ProtectedRoute roles={['JEFE_DOMINIO']}>
                <NuevaSolicitudPage />
              </ProtectedRoute>
            } />
            <Route path="aprobacion-solicitudes" element={
              <ProtectedRoute roles={['DPO']}>
                <AprobacionSolicitudesPage />
              </ProtectedRoute>
            } />
            <Route path="plantillas/nueva" element={
              <ProtectedRoute roles={['DPO']}>
                <CreateTemplatePage />
              </ProtectedRoute>
            } />
            <Route path="plantillas/:id/editar" element={
              <ProtectedRoute roles={['DPO']}>
                <CreateTemplatePage />
              </ProtectedRoute>
            } />
            <Route path="plantillas/:id/versiones" element={
              <ProtectedRoute roles={['DPO']}>
                <TemplateVersionsPage />
              </ProtectedRoute>
            } />

            {/* Vista previa de plantilla */}
            <Route path="preview/template/:id" element={
              <ProtectedRoute roles={['DPO']}>
                <TemplatePreviewPage />
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
    </ErrorBoundary>
  </AuthProvider>
);

export default App;
