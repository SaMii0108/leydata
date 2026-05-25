import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AREAS } from '../features/domains/mockDomains';
import Button from '../components/common/Button';
import styles from './NuevoConsentimientoPage.module.css';

const FINALIDADES = [
  'Marketing directo',
  'Análisis de datos internos',
  'Transferencia a terceros',
  'Investigación académica',
  'Publicidad personalizada',
];

const DOMINIOS = ['Marketing', 'Publicidad', 'Análisis', 'Funcional'] as const;

interface FormState {
  area: string;
  finalidad: string;
  dominio: string;
  fechaExpiracion: string;
  notas: string;
}

const INITIAL: FormState = {
  area: AREAS[0],
  finalidad: FINALIDADES[0],
  dominio: '',
  fechaExpiracion: '',
  notas: '',
};

const NuevoConsentimientoPage = () => {
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>(INITIAL);
  const [submitted, setSubmitted] = useState(false);

  const update = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const isValid = form.area && form.finalidad && form.fechaExpiracion;

  const handleSubmit = () => {
    if (!isValid) return;
    setSubmitted(true);
    setTimeout(() => navigate('/consentimientos'), 2000);
  };

  if (submitted) {
    return (
      <div className={styles.successWrap}>
        <div className={styles.successCard}>
          <span className={styles.successIcon}>✓</span>
          <h2 className={styles.successTitle}>Consentimiento creado</h2>
          <p className={styles.successHint}>Redirigiendo al registro de consentimientos...</p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <button className={styles.backBtn} onClick={() => navigate('/consentimientos')}>
            ← Volver
          </button>
          <h2 className={styles.title}>Nuevo Consentimiento</h2>
          <p className={styles.subtitle}>Registra un nuevo consentimiento bajo Ley 21.719</p>
        </div>
      </div>

      <div className={styles.layout}>
        <div className={styles.form}>
          {/* Área */}
          <Section title="Área responsable">
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="nc-area">Área organizacional</label>
              <select
                id="nc-area"
                className={styles.select}
                value={form.area}
                onChange={(e) => update('area', e.target.value)}
              >
                {AREAS.map((a) => <option key={a} value={a}>{a}</option>)}
              </select>
            </div>
          </Section>

          {/* Finalidad */}
          <Section title="Finalidad del tratamiento">
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="nc-finalidad">Finalidad</label>
              <select
                id="nc-finalidad"
                className={styles.select}
                value={form.finalidad}
                onChange={(e) => update('finalidad', e.target.value)}
              >
                {FINALIDADES.map((f) => <option key={f} value={f}>{f}</option>)}
              </select>
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel}>Dominio</label>
              <div className={styles.chips}>
                {DOMINIOS.map((d) => (
                  <button
                    key={d}
                    className={[styles.chip, form.dominio === d ? styles.chipActive : ''].join(' ')}
                    onClick={() => update('dominio', form.dominio === d ? '' : d)}
                    type="button"
                  >
                    {d}
                  </button>
                ))}
              </div>
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="nc-notas">Notas adicionales (opcional)</label>
              <textarea
                id="nc-notas"
                className={styles.textarea}
                rows={3}
                placeholder="Describe el contexto o condiciones especiales de este consentimiento..."
                value={form.notas}
                onChange={(e) => update('notas', e.target.value)}
                maxLength={400}
              />
              <p className={styles.charCount}>{form.notas.length}/400</p>
            </div>
          </Section>

          {/* Fechas */}
          <Section title="Vigencia">
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="nc-expiracion">
                Fecha de expiración <span className={styles.required}>*</span>
              </label>
              <input
                id="nc-expiracion"
                type="date"
                className={styles.input}
                value={form.fechaExpiracion}
                onChange={(e) => update('fechaExpiracion', e.target.value)}
                min={new Date().toISOString().split('T')[0]}
              />
            </div>
          </Section>

          {/* Marco legal */}
          <div className={styles.legalNotice}>
            <p className={styles.legalText}>
              Este consentimiento se registrará bajo la <strong>Ley 21.719</strong> de Protección de
              Datos Personales de Chile. Base legal: consentimiento expreso del titular (Art. 12).
            </p>
          </div>

          <div className={styles.actions}>
            <Button variant="ghost" onClick={() => navigate('/consentimientos')}>Cancelar</Button>
            <Button variant="primary" onClick={handleSubmit} disabled={!isValid}>
              Registrar consentimiento
            </Button>
          </div>
        </div>

        {/* Preview */}
        <aside className={styles.preview}>
          <p className={styles.previewLabel}>Vista previa del registro</p>
          <div className={styles.previewCard}>
            <div className={styles.previewRow}>
              <span className={styles.previewKey}>ID</span>
              <span className={styles.previewVal}>C-{String(Date.now()).slice(-4)}</span>
            </div>
            <div className={styles.previewRow}>
              <span className={styles.previewKey}>Área</span>
              <span className={styles.previewVal}>{form.area || '—'}</span>
            </div>
            <div className={styles.previewRow}>
              <span className={styles.previewKey}>Finalidad</span>
              <span className={styles.previewVal}>{form.finalidad || '—'}</span>
            </div>
            <div className={styles.previewRow}>
              <span className={styles.previewKey}>Dominio</span>
              <span className={styles.previewVal}>{form.dominio || '—'}</span>
            </div>
            <div className={styles.previewRow}>
              <span className={styles.previewKey}>Estado</span>
              <span className={styles.statusPill}>Pendiente</span>
            </div>
            <div className={styles.previewRow}>
              <span className={styles.previewKey}>Otorgamiento</span>
              <span className={styles.previewVal}>{new Date().toLocaleDateString('es-CL')}</span>
            </div>
            <div className={styles.previewRow}>
              <span className={styles.previewKey}>Expiración</span>
              <span className={styles.previewVal}>
                {form.fechaExpiracion
                  ? new Date(form.fechaExpiracion).toLocaleDateString('es-CL')
                  : '—'}
              </span>
            </div>
          </div>
          <p className={styles.previewHint}>El registro se creará con estado <strong>Pendiente</strong> hasta su validación.</p>
        </aside>
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
