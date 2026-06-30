import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { createPurpose, ApiError } from '../api/purposesApi';
import { getLegalBasis } from '../api/legalBasisApi';
import { getAllDomains } from '../api/domainsApi';
import type { LegalBasisDto } from '../api/legalBasisApi';
import type { DomainDto } from '../api/domainsApi';
import type { PurposeRequestSummary } from '../api/purposeRequestsApi';
import Button from '../components/common/Button';
import styles from './CrearFinalidadPage.module.css';

interface DatoRow {
  nombre: string;
  tipo: string;
  obligatorio: boolean;
}

const parseDatos = (raw: string | null | undefined): DatoRow[] => {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });

interface FormState {
  code: string;
  name: string;
  description: string;
  shortDescription: string;
  legalBasisId: string;
  domainId: string;
  required: boolean;
  revocable: boolean;
  consentStatement: string;
}

const CrearFinalidadPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { accessToken } = useAuth();

  const request = (location.state as { request?: PurposeRequestSummary } | null)?.request ?? null;
  const fromRequest = request !== null;

  const [legalBasisList, setLegalBasisList] = useState<LegalBasisDto[]>([]);
  const [loadingLegal, setLoadingLegal] = useState(true);
  const [legalError, setLegalError] = useState<string | null>(null);

  const [domains, setDomains] = useState<DomainDto[]>([]);
  const [loadingDomains, setLoadingDomains] = useState(!fromRequest);
  const [domainsError, setDomainsError] = useState<string | null>(null);

  const [form, setForm] = useState<FormState>({
    code: '',
    name: request?.title ?? '',
    description: '',
    shortDescription: '',
    legalBasisId: '',
    domainId: '',
    required: false,
    revocable: true,
    consentStatement: '',
  });

  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getLegalBasis(accessToken)
      .then((data) => { if (!cancelled) setLegalBasisList(data.filter((lb) => lb.isActive)); })
      .catch((err) => {
        if (!cancelled)
          setLegalError(err instanceof Error ? err.message : 'No se pudo cargar la base legal');
      })
      .finally(() => { if (!cancelled) setLoadingLegal(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  useEffect(() => {
    if (fromRequest) return;
    let cancelled = false;
    getAllDomains(accessToken)
      .then((data) => { if (!cancelled) setDomains(data.filter((d) => d.active)); })
      .catch((err) => {
        if (!cancelled)
          setDomainsError(err instanceof Error ? err.message : 'No se pudieron cargar los dominios');
      })
      .finally(() => { if (!cancelled) setLoadingDomains(false); });
    return () => { cancelled = true; };
  }, [accessToken, fromRequest]);

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => {
    setForm((p) => ({ ...p, [k]: v }));
    setErrors((p) => { const next = { ...p }; delete next[k]; return next; });
  };

  const validate = () => {
    const e: Partial<Record<keyof FormState, string>> = {};
    if (!form.code.trim()) e.code = 'El código es obligatorio.';
    if (!form.name.trim()) e.name = 'El nombre es obligatorio.';
    if (!form.description.trim()) e.description = 'La descripción es obligatoria.';
    if (!form.legalBasisId) e.legalBasisId = 'Selecciona una base legal.';
    if (!fromRequest && !form.domainId) e.domainId = 'Selecciona un dominio.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await createPurpose(
        {
          code: form.code.trim(),
          name: form.name.trim(),
          description: form.description.trim(),
          ...(form.shortDescription.trim() ? { shortDescription: form.shortDescription.trim() } : {}),
          required: form.required,
          revocable: form.revocable,
          legalBasisId: form.legalBasisId,
          domainId: fromRequest ? request!.domainId : form.domainId,
          ...(request?.id ? { purposeRequestId: request.id } : {}),
          ...(form.consentStatement.trim() ? { consentStatement: form.consentStatement.trim() } : {}),
        },
        accessToken,
      );
      navigate(
        fromRequest ? '/aprobacion-solicitudes' : '/finalidades',
        { replace: true, state: fromRequest ? { purposeCreated: true } : undefined },
      );
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Error al crear la finalidad');
    } finally {
      setSubmitting(false);
    }
  };

  const datos = fromRequest ? parseDatos(request?.requestedData) : [];
  const selectedLegalBasis = legalBasisList.find((lb) => lb.id === form.legalBasisId);

  return (
    <div className={styles.page}>
      <button
        className={styles.backLink}
        onClick={() => navigate(fromRequest ? '/aprobacion-solicitudes' : '/finalidades')}
      >
        ← {fromRequest ? 'Volver a Aprobación de Solicitudes' : 'Volver a Finalidades'}
      </button>

      <div className={styles.pageHeader}>
        <h2 className={styles.title}>Nueva Finalidad</h2>
        <p className={styles.subtitle}>
          {fromRequest
            ? 'Registra la finalidad a partir de la solicitud aprobada.'
            : 'Registra una nueva finalidad de tratamiento de datos.'}
        </p>
      </div>

      <div className={styles.formBody}>
        {fromRequest && (
          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <h3 className={styles.sectionTitle}>Solicitud de origen</h3>
            </div>
            <div className={styles.contextBody}>
              <p className={styles.contextTitle}>{request!.title}</p>
              <p className={styles.contextMeta}>
                {request!.requesterName} · {request!.domainName} · {formatDate(request!.createdAt)}
              </p>
              <div className={styles.contextField}>
                <p className={styles.contextLabel}>Justificación</p>
                <p className={styles.contextText}>{request!.justification}</p>
              </div>
              {datos.length > 0 && (
                <div className={styles.contextField}>
                  <p className={styles.contextLabel}>Datos declarados</p>
                  <div className={styles.datosList}>
                    {datos.map((d, i) => (
                      <div key={i} className={styles.datoItem}>
                        <span className={styles.datoNombre}>{d.nombre}</span>
                        <span className={styles.tipoPill}>{d.tipo}</span>
                        <span className={d.obligatorio ? styles.badgeObligatorio : styles.badgeOpcional}>
                          {d.obligatorio ? 'Obligatorio' : 'Opcional'}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </section>
        )}

        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Identificación</h3>
          </div>
          <div className={styles.sectionBody}>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Nombre <span className={styles.req}>*</span>
              </label>
              <input
                type="text"
                className={[styles.input, errors.name ? styles.inputError : ''].join(' ')}
                placeholder="Ej: Tratamiento de datos para registro de pacientes"
                value={form.name}
                onChange={(e) => set('name', e.target.value)}
                disabled={submitting}
              />
              {errors.name && <p className={styles.errorMsg}>{errors.name}</p>}
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Código <span className={styles.req}>*</span>
              </label>
              <input
                type="text"
                className={[styles.input, errors.code ? styles.inputError : ''].join(' ')}
                placeholder="Ej: FIN-001"
                value={form.code}
                onChange={(e) => set('code', e.target.value.toUpperCase())}
                disabled={submitting}
              />
              {errors.code && <p className={styles.errorMsg}>{errors.code}</p>}
            </div>

            {fromRequest ? (
              <div className={styles.fieldGroup}>
                <label className={styles.label}>Dominio</label>
                <div className={styles.readonlyValue}>{request!.domainName}</div>
              </div>
            ) : (
              <div className={styles.fieldGroup}>
                <label className={styles.label}>
                  Dominio <span className={styles.req}>*</span>
                </label>
                {loadingDomains ? (
                  <div className={styles.readonlyValue} style={{ color: 'var(--color-text-muted)' }}>
                    Cargando dominios…
                  </div>
                ) : domainsError ? (
                  <p className={styles.domainError}>{domainsError}</p>
                ) : (
                  <select
                    className={[styles.select, errors.domainId ? styles.inputError : ''].join(' ')}
                    value={form.domainId}
                    onChange={(e) => set('domainId', e.target.value)}
                    disabled={submitting}
                  >
                    <option value="">Selecciona un dominio</option>
                    {domains.map((d) => (
                      <option key={d.id} value={d.id}>{d.name}</option>
                    ))}
                  </select>
                )}
                {errors.domainId && <p className={styles.errorMsg}>{errors.domainId}</p>}
              </div>
            )}
          </div>
        </section>

        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Descripción</h3>
          </div>
          <div className={styles.sectionBody}>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Descripción completa <span className={styles.req}>*</span>
              </label>
              <textarea
                className={[styles.textarea, errors.description ? styles.inputError : ''].join(' ')}
                rows={4}
                placeholder="Describe detalladamente el propósito del tratamiento de datos…"
                value={form.description}
                onChange={(e) => set('description', e.target.value)}
                disabled={submitting}
              />
              {errors.description && <p className={styles.errorMsg}>{errors.description}</p>}
            </div>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Descripción corta <span className={styles.optional}>(opcional)</span>
              </label>
              <input
                type="text"
                className={styles.input}
                placeholder="Resumen breve para el titular (máx. 160 caracteres)"
                value={form.shortDescription}
                onChange={(e) => set('shortDescription', e.target.value)}
                disabled={submitting}
                maxLength={160}
              />
            </div>
          </div>
        </section>

        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Base legal</h3>
          </div>
          <div className={styles.sectionBody}>
            {legalError ? (
              <p className={styles.sectionError}>{legalError}</p>
            ) : (
              <div className={styles.fieldGroup}>
                <label className={styles.label}>
                  Base legal aplicable <span className={styles.req}>*</span>
                </label>
                <select
                  className={[styles.select, errors.legalBasisId ? styles.inputError : ''].join(' ')}
                  value={form.legalBasisId}
                  onChange={(e) => set('legalBasisId', e.target.value)}
                  disabled={submitting || loadingLegal}
                >
                  <option value="">{loadingLegal ? 'Cargando…' : 'Selecciona una base legal'}</option>
                  {legalBasisList.map((lb) => (
                    <option key={lb.id} value={lb.id}>{lb.code} — {lb.name}</option>
                  ))}
                </select>
                {errors.legalBasisId && <p className={styles.errorMsg}>{errors.legalBasisId}</p>}
                {selectedLegalBasis && (
                  <p className={styles.legalHint}>{selectedLegalBasis.description}</p>
                )}
              </div>
            )}
          </div>
        </section>

        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Configuración</h3>
          </div>
          <div className={styles.sectionBody}>
            <label className={styles.toggleLabel}>
              <input
                type="checkbox"
                className={styles.toggleCheck}
                checked={form.required}
                onChange={(e) => set('required', e.target.checked)}
                disabled={submitting}
              />
              <span>Finalidad requerida</span>
              <span className={styles.toggleHint}>(el titular no puede optar por no aceptarla)</span>
            </label>
            <label className={styles.toggleLabel}>
              <input
                type="checkbox"
                className={styles.toggleCheck}
                checked={form.revocable}
                onChange={(e) => set('revocable', e.target.checked)}
                disabled={submitting}
              />
              <span>Revocable por el titular</span>
              <span className={styles.toggleHint}>(el titular puede revocar su consentimiento en cualquier momento)</span>
            </label>
          </div>
        </section>

        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>
              Frase de consentimiento{' '}
              <span className={styles.optionalTitle}>(opcional)</span>
            </h3>
          </div>
          <div className={styles.sectionBody}>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>Texto de consentimiento para el titular</label>
              <textarea
                className={styles.textarea}
                rows={3}
                placeholder="Ej: Autorizo el tratamiento de mis datos personales para los fines descritos…"
                value={form.consentStatement}
                onChange={(e) => set('consentStatement', e.target.value)}
                disabled={submitting}
              />
            </div>
          </div>
        </section>

        {submitError && (
          <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
            {submitError}
          </p>
        )}

        <div className={styles.formActions}>
          <button
            className={styles.cancelBtn}
            onClick={() => navigate(fromRequest ? '/aprobacion-solicitudes' : '/finalidades')}
            disabled={submitting}
          >
            Cancelar
          </button>
          <Button variant="primary" onClick={handleSubmit} disabled={submitting || (!fromRequest && !!domainsError)}>
            {submitting ? 'Creando…' : 'Crear Finalidad'}
          </Button>
        </div>
      </div>
    </div>
  );
};

export default CrearFinalidadPage;
