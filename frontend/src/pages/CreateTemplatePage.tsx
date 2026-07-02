import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/common/Button';
import { useAuth } from '../features/auth/AuthContext';
import { createTemplate, addTemplatePurpose, ApiError } from '../api/templatesApi';
import { getPurposes, type PurposeResponse } from '../api/purposesApi';
import { getActiveDomains, type DomainDto } from '../api/domainsApi';
import styles from './CreateTemplatePage.module.css';

interface FormState {
  domainId: string;
  templateKey: string;
  name: string;
  description: string;
  title: string;
}

type FormErrors = Partial<Record<keyof FormState, string>>;

const TEMPLATE_KEY_RE = /^[a-z0-9][a-z0-9_-]*$/;

const EMPTY: FormState = { domainId: '', templateKey: '', name: '', description: '', title: '' };

const CreateTemplatePage = () => {
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  const [form, setForm]       = useState<FormState>(EMPTY);
  const [errors, setErrors]   = useState<FormErrors>({});
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  const [allDomains, setAllDomains]             = useState<DomainDto[]>([]);
  const [domainsLoading, setDomainsLoading]     = useState(true);
  const [domainsLoadError, setDomainsLoadError] = useState<string | null>(null);

  const [allPurposes, setAllPurposes]                     = useState<PurposeResponse[]>([]);
  const [selectedPurposeIds, setSelectedPurposeIds]       = useState<string[]>([]);
  const [purposesLoading, setPurposesLoading]             = useState(true);
  const [purposesLoadError, setPurposesLoadError]         = useState<string | null>(null);
  const [purposeSelectionError, setPurposeSelectionError] = useState<string | null>(null);
  const [createdTemplateId, setCreatedTemplateId]         = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setDomainsLoading(true);
    setDomainsLoadError(null);
    getActiveDomains(accessToken)
      .then((data) => { if (!cancelled) setAllDomains(data); })
      .catch((err) => {
        if (!cancelled)
          setDomainsLoadError(
            err instanceof ApiError ? err.message : 'No se pudieron cargar los dominios disponibles.',
          );
      })
      .finally(() => { if (!cancelled) setDomainsLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  useEffect(() => {
    let cancelled = false;
    setPurposesLoading(true);
    setPurposesLoadError(null);
    getPurposes(accessToken)
      .then((data) => { if (!cancelled) setAllPurposes(data); })
      .catch((err) => {
        if (!cancelled)
          setPurposesLoadError(
            err instanceof ApiError ? err.message : 'No se pudieron cargar las finalidades disponibles.',
          );
      })
      .finally(() => { if (!cancelled) setPurposesLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  const set = <K extends keyof FormState>(key: K, val: string) => {
    setForm((prev) => ({ ...prev, [key]: val }));
    setErrors((prev) => ({ ...prev, [key]: undefined }));
    setApiError(null);
  };

  const togglePurpose = (id: string) => {
    setSelectedPurposeIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
    setPurposeSelectionError(null);
    setApiError(null);
  };

  const validate = (): boolean => {
    const e: FormErrors = {};
    if (!form.domainId)
      e.domainId = 'El dominio es obligatorio.';
    if (!form.name.trim())
      e.name = 'El nombre es obligatorio.';
    if (!form.templateKey.trim())
      e.templateKey = 'La clave de plantilla es obligatoria.';
    else if (!TEMPLATE_KEY_RE.test(form.templateKey.trim()))
      e.templateKey = 'Solo minúsculas, números, guiones (-) y guiones bajos (_). Debe empezar con letra o número.';
    setErrors(e);

    let purposesValid = true;
    if (selectedPurposeIds.length === 0) {
      setPurposeSelectionError('Debes seleccionar al menos una finalidad.');
      purposesValid = false;
    } else {
      setPurposeSelectionError(null);
    }

    return Object.keys(e).length === 0 && purposesValid;
  };

  const handleSubmit = async () => {
    if (!validate()) return;

    setLoading(true);
    setApiError(null);
    setPurposeSelectionError(null);

    let tpl;
    try {
      tpl = await createTemplate(
        {
          domainId: form.domainId,
          templateKey: form.templateKey.trim(),
          name: form.name.trim(),
          ...(form.description.trim() ? { description: form.description.trim() } : {}),
          ...(form.title.trim()       ? { title: form.title.trim() }             : {}),
        },
        accessToken,
      );
    } catch (err) {
      setApiError(
        err instanceof ApiError ? err.message : 'Error al crear la plantilla. Intenta nuevamente.',
      );
      setLoading(false);
      return;
    }

    // La plantilla ya existe en el backend. Guardamos el id para que, si falla
    // alguna asociación de finalidad, el usuario pueda ir a Editar sin recrearla.
    setCreatedTemplateId(tpl.id);

    // orderPosition sigue el orden de la lista mostrada en la interfaz, no el
    // orden en que el usuario marcó los checkboxes.
    const orderedSelected = allPurposes.filter((p) => selectedPurposeIds.includes(p.id));

    for (let i = 0; i < orderedSelected.length; i++) {
      try {
        await addTemplatePurpose(
          tpl.id,
          { purposeId: orderedSelected[i].id, orderPosition: i + 1, isVisible: true },
          accessToken,
        );
      } catch (err) {
        setApiError(
          `No se pudo asociar la finalidad "${orderedSelected[i].name}": ${err instanceof ApiError ? err.message : 'error de conexión'}. La plantilla fue creada — accede a "Editar plantilla" para completar la configuración.`,
        );
        setLoading(false);
        return;
      }
    }

    navigate(`/plantillas/${tpl.id}/editar`);
  };

  return (
    <div className={styles.page}>
      <button className={styles.backLink} onClick={() => navigate('/plantillas')}>
        ← Volver a Plantillas
      </button>

      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Nueva Plantilla</h2>
          <p className={styles.subtitle}>
            La plantilla se creará en estado <strong>Borrador</strong>.
            Solo el <strong>DPO</strong> puede crear plantillas de consentimiento.
          </p>
        </div>
      </div>

      <div className={styles.formBody}>
        <FormSection title="Identificación">
          <div className={styles.fieldGroup}>
            <label className={styles.label}>
              Dominio <span className={styles.required}>*</span>
            </label>
            {domainsLoadError ? (
              <p className={styles.submitError}>{domainsLoadError}</p>
            ) : (
              <select
                className={[styles.input, errors.domainId ? styles.inputError : ''].join(' ')}
                value={form.domainId}
                onChange={(e) => set('domainId', e.target.value)}
                disabled={loading || domainsLoading}
              >
                <option value="">{domainsLoading ? 'Cargando dominios…' : 'Selecciona un dominio'}</option>
                {allDomains.map((d) => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </select>
            )}
            {errors.domainId && <span className={styles.errorMsg}>{errors.domainId}</span>}
          </div>

          <div className={styles.fieldGroup}>
            <label className={styles.label}>
              Nombre <span className={styles.required}>*</span>
            </label>
            <input
              className={[styles.input, errors.name ? styles.inputError : ''].join(' ')}
              value={form.name}
              onChange={(e) => set('name', e.target.value)}
              placeholder="Ej: Consentimiento de Marketing Digital"
              maxLength={120}
              disabled={loading}
            />
            {errors.name && <span className={styles.errorMsg}>{errors.name}</span>}
          </div>

          <div className={styles.fieldGroup}>
            <label className={styles.label}>
              Clave de plantilla <span className={styles.required}>*</span>
            </label>
            <input
              className={[styles.input, errors.templateKey ? styles.inputError : ''].join(' ')}
              value={form.templateKey}
              onChange={(e) => set('templateKey', e.target.value)}
              placeholder="Ej: marketing-digital"
              maxLength={80}
              disabled={loading}
            />
            {errors.templateKey
              ? <span className={styles.errorMsg}>{errors.templateKey}</span>
              : <span className={styles.hint}>
                  Identifica la familia de versiones de esta plantilla. Solo minúsculas, números, <code>-</code> y <code>_</code>. No puede modificarse una vez creada.
                </span>
            }
          </div>
        </FormSection>

        <FormSection title="Contenido">
          <div className={styles.fieldGroup}>
            <label className={styles.label}>Título para el titular</label>
            <input
              className={styles.input}
              value={form.title}
              onChange={(e) => set('title', e.target.value)}
              placeholder="Ej: Solicitud de consentimiento para uso de datos"
              maxLength={200}
              disabled={loading}
            />
            <span className={styles.hint}>
              Texto que verá el titular en el encabezado del formulario de consentimiento.
            </span>
          </div>

          <div className={styles.fieldGroup}>
            <label className={styles.label}>Descripción</label>
            <textarea
              className={styles.textarea}
              value={form.description}
              onChange={(e) => set('description', e.target.value)}
              placeholder="Descripción interna de esta plantilla…"
              rows={3}
              maxLength={500}
              disabled={loading}
            />
            <span className={styles.charCount}>{form.description.length} / 500</span>
          </div>
        </FormSection>

        <FormSection title="Finalidades" required>
          {purposesLoading ? (
            <p className={styles.hint}>Cargando finalidades disponibles…</p>
          ) : purposesLoadError ? (
            <p className={styles.submitError}>{purposesLoadError}</p>
          ) : allPurposes.length === 0 ? (
            <p className={styles.hintWarn}>
              No hay finalidades activas disponibles. El Jefe de Dominio debe solicitar y el DPO
              aprobar una finalidad antes de poder crear plantillas.
            </p>
          ) : (
            <div className={styles.purposeChecklist}>
              {allPurposes.map((p) => {
                const position  = selectedPurposeIds.indexOf(p.id);
                const isSelected = position !== -1;
                return (
                  <label
                    key={p.id}
                    className={[
                      styles.purposeCheckItem,
                      isSelected ? styles.purposeCheckItemSelected : '',
                    ].join(' ')}
                  >
                    <input
                      type="checkbox"
                      className={styles.purposeCheck}
                      checked={isSelected}
                      onChange={() => togglePurpose(p.id)}
                      disabled={loading}
                    />
                    <span className={styles.purposeLabel}>
                      <span className={styles.purposeName}>{p.name}</span>
                      {p.domainName && (
                        <span className={styles.purposeDomain}>{p.domainName}</span>
                      )}
                    </span>
                    {isSelected && (
                      <span className={styles.purposePosition}>#{position + 1}</span>
                    )}
                  </label>
                );
              })}
            </div>
          )}
          {purposeSelectionError && (
            <span className={styles.errorMsg}>{purposeSelectionError}</span>
          )}
          {selectedPurposeIds.length > 0 && (
            <p className={styles.hint}>
              {selectedPurposeIds.length}{' '}
              finalidad{selectedPurposeIds.length !== 1 ? 'es' : ''} seleccionada
              {selectedPurposeIds.length !== 1 ? 's' : ''}. El número indica el orden inicial
              en la plantilla.
            </p>
          )}
        </FormSection>

        {apiError && <p className={styles.submitError}>{apiError}</p>}

        <div className={styles.formActions}>
          <Button variant="ghost" size="sm" onClick={() => navigate('/plantillas')} disabled={loading}>
            Cancelar
          </Button>
          {createdTemplateId && apiError ? (
            <Button
              variant="primary"
              size="sm"
              onClick={() => navigate(`/plantillas/${createdTemplateId}/editar`)}
            >
              Ir a Editar plantilla
            </Button>
          ) : (
            <Button
              variant="primary"
              size="sm"
              onClick={handleSubmit}
              disabled={loading || domainsLoading || purposesLoading || allPurposes.length === 0}
            >
              {loading ? 'Creando…' : 'Crear Plantilla'}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};

const FormSection = ({
  title,
  required,
  children,
}: {
  title: string;
  required?: boolean;
  children: React.ReactNode;
}) => (
  <section className={styles.section}>
    <div className={styles.sectionHeader}>
      <h3 className={styles.sectionTitle}>
        {title}
        {required && <span className={styles.sectionRequired}> *</span>}
      </h3>
    </div>
    <div className={styles.sectionBody}>{children}</div>
  </section>
);

export default CreateTemplatePage;
