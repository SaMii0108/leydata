import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { getUser } from '../api/usersApi';
import type { UserDomain } from '../api/usersApi';
import { createPurposeRequest } from '../api/purposeRequestsApi';
import styles from './NuevaSolicitudPage.module.css';

type TipoDato = 'Texto' | 'Email' | 'Teléfono' | 'Fecha' | 'Número' | 'RUT';
const TIPOS_DATO: TipoDato[] = ['Texto', 'Email', 'Teléfono', 'Fecha', 'Número', 'RUT'];

let _datoCounter = 1;
const nextDatoId = () => `dato-${_datoCounter++}`;

interface DatoRow {
  _id: string;
  nombre: string;
  tipo: TipoDato;
  obligatorio: boolean;
}

interface FormState {
  title: string;
  justification: string;
  domainId: string;
}

const NuevaSolicitudPage = () => {
  const navigate = useNavigate();
  const { user, accessToken } = useAuth();

  const [domains, setDomains] = useState<UserDomain[]>([]);
  const [loadingDomains, setLoadingDomains] = useState(true);
  const [domainsError, setDomainsError] = useState<string | null>(null);

  const [form, setForm] = useState<FormState>({ title: '', justification: '', domainId: '' });
  const [datos, setDatos] = useState<DatoRow[]>([]);
  const [errors, setErrors] = useState<Partial<Record<keyof FormState | 'datos', string>>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [editingDato, setEditingDato] = useState<string | null>(null);
  const [datoDraft, setDatoDraft] = useState<{ nombre: string; tipo: TipoDato; obligatorio: boolean }>({
    nombre: '', tipo: 'Texto', obligatorio: true,
  });

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    setLoadingDomains(true);
    setDomainsError(null);
    getUser(user.id, accessToken)
      .then((u) => {
        if (cancelled) return;
        setDomains(u.domains);
        if (u.domains.length === 1) {
          setForm((f) => ({ ...f, domainId: u.domains[0].id }));
        }
      })
      .catch((err) => {
        if (!cancelled)
          setDomainsError(err instanceof Error ? err.message : 'No se pudo obtener la información del usuario');
      })
      .finally(() => { if (!cancelled) setLoadingDomains(false); });
    return () => { cancelled = true; };
  }, [user, accessToken]);

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => {
    setForm((p) => ({ ...p, [k]: v }));
    setErrors((p) => ({ ...p, [k]: undefined }));
  };

  const validate = () => {
    const e: typeof errors = {};
    if (!form.title.trim())         e.title         = 'El nombre es obligatorio.';
    if (!form.justification.trim()) e.justification = 'La justificación es obligatoria.';
    if (!form.domainId)             e.domainId      = 'Debes tener un dominio asignado.';
    if (datos.length === 0)         e.datos         = 'Agrega al menos un dato requerido.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await createPurposeRequest(
        {
          title: form.title.trim(),
          justification: form.justification.trim(),
          domainId: form.domainId,
          requestedData: JSON.stringify(
            datos.map(({ nombre, tipo, obligatorio }) => ({ nombre, tipo, obligatorio })),
          ),
        },
        accessToken,
      );
      navigate('/solicitudes', { replace: true });
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Error al enviar la solicitud');
    } finally {
      setSubmitting(false);
    }
  };

  const startAdd = () => {
    const id = nextDatoId();
    setDatos((prev) => [...prev, { _id: id, nombre: '', tipo: 'Texto', obligatorio: true }]);
    setEditingDato(id);
    setDatoDraft({ nombre: '', tipo: 'Texto', obligatorio: true });
  };

  const confirmDato = () => {
    if (!editingDato) return;
    if (!datoDraft.nombre.trim()) {
      setDatos((prev) => prev.filter((d) => d._id !== editingDato));
    } else {
      setDatos((prev) =>
        prev.map((d) =>
          d._id === editingDato
            ? { ...d, nombre: datoDraft.nombre, tipo: datoDraft.tipo, obligatorio: datoDraft.obligatorio }
            : d,
        ),
      );
      setErrors((p) => ({ ...p, datos: undefined }));
    }
    setEditingDato(null);
  };

  const cancelDato = () => {
    setDatos((prev) => prev.filter((d) => d.nombre !== '' || d._id !== editingDato));
    setEditingDato(null);
  };

  const removeDato = (id: string) => setDatos((prev) => prev.filter((d) => d._id !== id));

  const toggleObligatorio = (id: string) =>
    setDatos((prev) => prev.map((d) => d._id === id ? { ...d, obligatorio: !d.obligatorio } : d));

  const currentDomain = domains.find((d) => d.id === form.domainId);
  const canSubmit = !submitting && !loadingDomains && domains.length > 0;

  return (
    <div className={styles.page}>
      <button className={styles.backLink} onClick={() => navigate('/solicitudes')}>
        ← Volver a Mis Solicitudes
      </button>

      <div className={styles.pageHeader}>
        <h2 className={styles.title}>Nueva Solicitud de Finalidad</h2>
        <p className={styles.subtitle}>
          Esta solicitud será revisada por el <strong>DPO</strong> para aprobación. Solo las finalidades aprobadas pueden ser utilizadas en documentos de privacidad.
        </p>
      </div>

      <div className={styles.formBody}>
        {/* Identificación */}
        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Identificación de la finalidad</h3>
          </div>
          <div className={styles.sectionBody}>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Nombre de la finalidad <span className={styles.req}>*</span>
              </label>
              <input
                className={[styles.input, errors.title ? styles.inputError : ''].join(' ')}
                value={form.title}
                onChange={(e) => set('title', e.target.value)}
                placeholder="Ej: Marketing directo, Atención de pacientes…"
                maxLength={80}
                disabled={submitting}
              />
              {errors.title && <span className={styles.errorMsg}>{errors.title}</span>}
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>Dominio solicitante</label>
              {loadingDomains ? (
                <div className={styles.dominioValue}>
                  <span className={styles.dominioMuted}>Cargando…</span>
                </div>
              ) : domainsError ? (
                <div className={[styles.dominioValue, styles.inputError].join(' ')}>
                  <span className={styles.dominioMuted}>{domainsError}</span>
                </div>
              ) : domains.length === 0 ? (
                <div className={styles.dominioValue}>
                  <span className={styles.dominioMuted}>Sin dominio asignado — contacta al Administrador</span>
                </div>
              ) : domains.length === 1 ? (
                <div className={styles.dominioValue}>{currentDomain?.name}</div>
              ) : (
                <select
                  className={[styles.select, errors.domainId ? styles.inputError : ''].join(' ')}
                  value={form.domainId}
                  onChange={(e) => set('domainId', e.target.value)}
                  disabled={submitting}
                >
                  <option value="">— Selecciona un dominio —</option>
                  {domains.map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
              )}
              {errors.domainId && <span className={styles.errorMsg}>{errors.domainId}</span>}
            </div>
          </div>
        </section>

        {/* Justificación */}
        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Justificación del tratamiento</h3>
          </div>
          <div className={styles.sectionBody}>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Justificación <span className={styles.req}>*</span>
              </label>
              <textarea
                className={[styles.textarea, errors.justification ? styles.inputError : ''].join(' ')}
                value={form.justification}
                onChange={(e) => set('justification', e.target.value)}
                placeholder="Explica por qué es necesario este tratamiento de datos y cuál es la base legal que lo sustenta…"
                rows={4}
                maxLength={600}
                disabled={submitting}
              />
              {errors.justification && <span className={styles.errorMsg}>{errors.justification}</span>}
            </div>
          </div>
        </section>

        {/* Datos requeridos */}
        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Datos requeridos para esta finalidad</h3>
            <button
              className={styles.addBtn}
              onClick={startAdd}
              disabled={editingDato !== null || submitting}
            >
              + Agregar dato
            </button>
          </div>
          <div className={styles.sectionBody}>
            {errors.datos && <p className={styles.sectionError}>{errors.datos}</p>}

            {datos.length === 0 && editingDato === null && (
              <div className={styles.emptyDatos}>
                <p>Agrega los datos personales que serán solicitados al titular en esta finalidad.</p>
              </div>
            )}

            <div className={styles.datosList}>
              {datos.map((dato) =>
                editingDato === dato._id ? (
                  <div key={dato._id} className={styles.datoEditing}>
                    <div className={styles.datoEditRow}>
                      <div className={styles.fieldGroup} style={{ flex: 2 }}>
                        <label className={styles.label}>
                          Nombre del dato <span className={styles.req}>*</span>
                        </label>
                        <input
                          className={styles.input}
                          value={datoDraft.nombre}
                          onChange={(e) => setDatoDraft((p) => ({ ...p, nombre: e.target.value }))}
                          placeholder="Ej: Correo electrónico, RUT, Teléfono…"
                          autoFocus
                          maxLength={80}
                        />
                      </div>
                      <div className={styles.fieldGroup} style={{ flex: 1 }}>
                        <label className={styles.label}>Tipo de dato</label>
                        <select
                          className={styles.select}
                          value={datoDraft.tipo}
                          onChange={(e) => setDatoDraft((p) => ({ ...p, tipo: e.target.value as TipoDato }))}
                        >
                          {TIPOS_DATO.map((t) => <option key={t} value={t}>{t}</option>)}
                        </select>
                      </div>
                    </div>
                    <label className={styles.obligatorioLabel}>
                      <input
                        type="checkbox"
                        checked={datoDraft.obligatorio}
                        onChange={(e) => setDatoDraft((p) => ({ ...p, obligatorio: e.target.checked }))}
                        className={styles.obligatorioCheck}
                      />
                      <span>Dato obligatorio</span>
                      <span className={styles.obligatorioHint}>(el titular deberá aceptarlo para consentir)</span>
                    </label>
                    <div className={styles.datoEditActions}>
                      <button className={styles.confirmBtn} onClick={confirmDato}>Confirmar</button>
                      <button className={styles.cancelBtn} onClick={cancelDato}>Cancelar</button>
                    </div>
                  </div>
                ) : (
                  <div key={dato._id} className={styles.datoCard}>
                    <div className={styles.datoInfo}>
                      <div className={styles.datoHeader}>
                        <span className={styles.datoTitulo}>{dato.nombre}</span>
                        <span className={styles.tipoPill}>{dato.tipo}</span>
                        <span
                          className={dato.obligatorio ? styles.badgeObligatorio : styles.badgeOpcional}
                          onClick={() => !submitting && toggleObligatorio(dato._id)}
                          title="Clic para cambiar"
                        >
                          {dato.obligatorio ? 'Obligatorio' : 'Opcional'}
                        </span>
                      </div>
                    </div>
                    <div className={styles.datoActions}>
                      <button
                        className={styles.iconBtn}
                        disabled={submitting}
                        onClick={() => {
                          setEditingDato(dato._id);
                          setDatoDraft({ nombre: dato.nombre, tipo: dato.tipo, obligatorio: dato.obligatorio });
                        }}
                      >
                        ✏️
                      </button>
                      <button
                        className={styles.iconBtnDanger}
                        disabled={submitting}
                        onClick={() => removeDato(dato._id)}
                      >
                        🗑️
                      </button>
                    </div>
                  </div>
                )
              )}
            </div>
          </div>
        </section>

        {submitError && (
          <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
            {submitError}
          </p>
        )}

        {/* Acciones */}
        <div className={styles.formActions}>
          <button
            className={styles.cancelBtn2}
            onClick={() => navigate('/solicitudes')}
            disabled={submitting}
          >
            Cancelar
          </button>
          <button
            className={styles.submitBtn}
            onClick={handleSubmit}
            disabled={!canSubmit}
          >
            {submitting ? 'Enviando…' : 'Enviar solicitud'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default NuevaSolicitudPage;
