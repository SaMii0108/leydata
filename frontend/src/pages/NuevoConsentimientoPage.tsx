import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import Button from '../components/common/Button';
import {
  getTemplates,
  getTemplatePurposes,
  ApiError,
  type TemplateResponse,
  type TemplatePurposeResponse,
} from '../api/templatesApi';
import { createAgreement } from '../api/agreementsApi';
import styles from './NuevoConsentimientoPage.module.css';

const NuevoConsentimientoPage = () => {
  const navigate = useNavigate();
  const { accessToken } = useAuth();

  const [templates, setTemplates]               = useState<TemplateResponse[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(true);
  const [templatesError, setTemplatesError]     = useState<string | null>(null);
  const [selectedTemplateId, setSelectedTemplateId] = useState('');

  const [purposes, setPurposes]             = useState<TemplatePurposeResponse[]>([]);
  const [purposesLoading, setPurposesLoading] = useState(false);
  const [purposesError, setPurposesError]   = useState<string | null>(null);
  const [decisions, setDecisions]           = useState<Record<string, boolean>>({});

  const [subjectIdentifier, setSubjectIdentifier] = useState('');

  const [loading, setLoading]   = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setTemplatesLoading(true);
    setTemplatesError(null);
    getTemplates({ isActive: true }, accessToken)
      .then((data) => { if (!cancelled) setTemplates(data); })
      .catch((err) => {
        if (!cancelled)
          setTemplatesError(
            err instanceof ApiError ? err.message : 'No se pudieron cargar las plantillas activas.',
          );
      })
      .finally(() => { if (!cancelled) setTemplatesLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  useEffect(() => {
    if (!selectedTemplateId) {
      setPurposes([]);
      setDecisions({});
      return;
    }
    let cancelled = false;
    setPurposesLoading(true);
    setPurposesError(null);
    getTemplatePurposes(selectedTemplateId, accessToken)
      .then((data) => {
        if (!cancelled) {
          const sorted = [...data].sort((a, b) => a.orderPosition - b.orderPosition);
          setPurposes(sorted);
          const init: Record<string, boolean> = {};
          sorted.forEach((p) => { init[p.purposeId] = true; });
          setDecisions(init);
        }
      })
      .catch((err) => {
        if (!cancelled)
          setPurposesError(
            err instanceof ApiError ? err.message : 'No se pudieron cargar las finalidades de la plantilla.',
          );
      })
      .finally(() => { if (!cancelled) setPurposesLoading(false); });
    return () => { cancelled = true; };
  }, [selectedTemplateId, accessToken]);

  const setDecision = (purposeId: string, accepted: boolean) => {
    setDecisions((prev) => ({ ...prev, [purposeId]: accepted }));
    setApiError(null);
  };

  const canSubmit = !!(selectedTemplateId && purposes.length > 0 && !loading && !purposesLoading);

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setLoading(true);
    setApiError(null);
    try {
      await createAgreement(
        {
          templateId: selectedTemplateId,
          ...(subjectIdentifier.trim() ? { subjectIdentifier: subjectIdentifier.trim() } : {}),
          purposes: purposes.map((p) => ({
            purposeId: p.purposeId,
            accepted: decisions[p.purposeId] ?? true,
          })),
        },
        accessToken,
      );
      setSubmitted(true);
      setTimeout(() => navigate('/consentimientos'), 2000);
    } catch (err) {
      setApiError(
        err instanceof ApiError ? err.message : 'Error al registrar el consentimiento. Intenta nuevamente.',
      );
    } finally {
      setLoading(false);
    }
  };

  if (submitted) {
    return (
      <div className={styles.successWrap}>
        <div className={styles.successCard}>
          <span className={styles.successIcon}>✓</span>
          <h2 className={styles.successTitle}>Consentimiento registrado</h2>
          <p className={styles.successHint}>Redirigiendo al registro de consentimientos…</p>
        </div>
      </div>
    );
  }

  const selectedTemplate = templates.find((t) => t.id === selectedTemplateId);

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <button className={styles.backBtn} onClick={() => navigate('/consentimientos')}>
            ← Volver
          </button>
          <h2 className={styles.title}>Nuevo Consentimiento</h2>
          <p className={styles.subtitle}>Registra un acuerdo de consentimiento bajo Ley 21.719</p>
        </div>
      </div>

      <div className={styles.form}>
        <Section title="Plantilla de consentimiento">
          <div className={styles.fieldGroup}>
            <label className={styles.fieldLabel} htmlFor="nc-template">
              Plantilla activa <span className={styles.required}>*</span>
            </label>
            {templatesError ? (
              <p className={styles.submitError}>{templatesError}</p>
            ) : (
              <select
                id="nc-template"
                className={styles.select}
                value={selectedTemplateId}
                onChange={(e) => { setSelectedTemplateId(e.target.value); setApiError(null); }}
                disabled={templatesLoading || loading}
              >
                <option value="">
                  {templatesLoading ? 'Cargando plantillas…' : templates.length === 0 ? 'No hay plantillas activas' : 'Selecciona una plantilla'}
                </option>
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>{t.name}</option>
                ))}
              </select>
            )}
            {selectedTemplate && (selectedTemplate.description || selectedTemplate.title) && (
              <p className={styles.hint}>{selectedTemplate.description ?? selectedTemplate.title}</p>
            )}
          </div>
        </Section>

        <Section title="Identificación del titular">
          <div className={styles.fieldGroup}>
            <label className={styles.fieldLabel} htmlFor="nc-subject">
              Identificador del titular{' '}
              <span className={styles.hintInline}>(opcional — RUT, email u otro)</span>
            </label>
            <input
              id="nc-subject"
              type="text"
              className={styles.input}
              placeholder="Ej: 12345678-9"
              value={subjectIdentifier}
              onChange={(e) => setSubjectIdentifier(e.target.value)}
              disabled={loading}
            />
          </div>
        </Section>

        {selectedTemplateId && (
          <Section title="Decisión por finalidad">
            {purposesLoading ? (
              <p className={styles.hint}>Cargando finalidades de la plantilla…</p>
            ) : purposesError ? (
              <p className={styles.submitError}>{purposesError}</p>
            ) : purposes.length === 0 ? (
              <p className={styles.hintWarn}>
                Esta plantilla no tiene finalidades configuradas. Selecciona otra plantilla.
              </p>
            ) : (
              <div className={styles.purposeList}>
                {purposes.map((p) => (
                  <div key={p.purposeId} className={styles.purposeItem}>
                    <div className={styles.purposeInfo}>
                      <span className={styles.purposeName}>{p.purposeName}</span>
                    </div>
                    <div className={styles.decisionBtns}>
                      <button
                        type="button"
                        className={[
                          styles.decisionBtn,
                          decisions[p.purposeId] === true ? styles.decisionAccept : '',
                        ].join(' ')}
                        onClick={() => setDecision(p.purposeId, true)}
                        disabled={loading}
                      >
                        Aceptar
                      </button>
                      <button
                        type="button"
                        className={[
                          styles.decisionBtn,
                          decisions[p.purposeId] === false ? styles.decisionReject : '',
                        ].join(' ')}
                        onClick={() => setDecision(p.purposeId, false)}
                        disabled={loading}
                      >
                        Rechazar
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Section>
        )}

        <div className={styles.legalNotice}>
          <p className={styles.legalText}>
            Este consentimiento se registrará bajo la <strong>Ley 21.719</strong> de Protección de
            Datos Personales de Chile. Base legal: consentimiento expreso del titular (Art. 12).
          </p>
        </div>

        {apiError && <p className={styles.submitError}>{apiError}</p>}

        <div className={styles.actions}>
          <Button variant="ghost" onClick={() => navigate('/consentimientos')} disabled={loading}>
            Cancelar
          </Button>
          <Button variant="primary" onClick={handleSubmit} disabled={!canSubmit}>
            {loading ? 'Registrando…' : 'Registrar consentimiento'}
          </Button>
        </div>
      </div>
    </div>
  );
};

const Section = ({ title, children }: { title: string; children: React.ReactNode }) => (
  <div className={styles.section}>
    <p className={styles.sectionTitle}>{title}</p>
    {children}
  </div>
);

export default NuevoConsentimientoPage;
