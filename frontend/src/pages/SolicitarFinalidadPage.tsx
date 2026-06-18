import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { DOMINIOS, addSolicitudFinalidad, type SolicitudDato, type TipoDato } from '../utils/mockData';
import styles from './SolicitarFinalidadPage.module.css';

const TIPOS_DATO: TipoDato[] = ['Texto', 'Email', 'Teléfono', 'Fecha', 'Número', 'RUT'];

let _datoCounter = 1;
const newDatoId = () => `dato-${_datoCounter++}`;

interface DatoRow extends SolicitudDato {
  _id: string;
}

interface FormState {
  nombre: string;
  descripcion: string;
  dominio: string;
  justificacion: string;
}

const EMPTY_FORM: FormState = {
  nombre: '',
  descripcion: '',
  dominio: DOMINIOS[0],
  justificacion: '',
};

const SolicitarFinalidadPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [datos, setDatos] = useState<DatoRow[]>([]);
  const [errors, setErrors] = useState<Partial<Record<keyof FormState | 'datos', string>>>({});
  const [saved, setSaved] = useState(false);

  const [editingDato, setEditingDato] = useState<string | null>(null);
  const [datoDraft, setDatoDraft] = useState<{ nombre: string; tipo: TipoDato; obligatorio: boolean }>({
    nombre: '', tipo: 'Texto', obligatorio: true,
  });

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => {
    setForm((p) => ({ ...p, [k]: v }));
    setErrors((p) => ({ ...p, [k]: undefined }));
  };

  const validate = () => {
    const e: typeof errors = {};
    if (!form.nombre.trim())        e.nombre       = 'El nombre es obligatorio.';
    if (!form.descripcion.trim())   e.descripcion  = 'La descripción es obligatoria.';
    if (!form.justificacion.trim()) e.justificacion = 'La justificación es obligatoria.';
    if (datos.length === 0)         e.datos        = 'Agrega al menos un dato requerido.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = () => {
    if (!validate()) return;
    addSolicitudFinalidad({
      nombre: form.nombre,
      descripcion: form.descripcion,
      dominio: form.dominio,
      justificacion: form.justificacion,
      datos: datos.map(({ nombre, tipo, obligatorio }) => ({ nombre, tipo, obligatorio })),
      creadoPor: user?.name ?? 'Jefe de Dominio',
    });
    setSaved(true);
    setTimeout(() => navigate('/finalidades'), 1400);
  };

  const startAdd = () => {
    const id = newDatoId();
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
            : d
        )
      );
    }
    setEditingDato(null);
    setErrors((p) => ({ ...p, datos: undefined }));
  };

  const cancelDato = () => {
    setDatos((prev) => prev.filter((d) => d.nombre !== '' || d._id !== editingDato));
    setEditingDato(null);
  };

  const removeDato = (id: string) => setDatos((prev) => prev.filter((d) => d._id !== id));

  const toggleObligatorio = (id: string) =>
    setDatos((prev) => prev.map((d) => d._id === id ? { ...d, obligatorio: !d.obligatorio } : d));

  return (
    <div className={styles.page}>
      <button className={styles.backLink} onClick={() => navigate('/finalidades')}>
        ← Volver a Mis Solicitudes
      </button>

      <div className={styles.pageHeader}>
        <h2 className={styles.title}>Solicitar Nueva Finalidad</h2>
        <p className={styles.subtitle}>
          Esta solicitud será revisada por el <strong>DPO</strong> para aprobación. Solo las finalidades aprobadas pueden ser usadas en plantillas.
        </p>
      </div>

      <div className={styles.formBody}>
        {/* ── Identificación ─── */}
        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Identificación de la finalidad</h3>
          </div>
          <div className={styles.sectionBody}>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>Nombre de la finalidad <span className={styles.req}>*</span></label>
              <input
                className={[styles.input, errors.nombre ? styles.inputError : ''].join(' ')}
                value={form.nombre}
                onChange={(e) => set('nombre', e.target.value)}
                placeholder="Ej: Marketing directo, Atención de pacientes…"
                maxLength={80}
              />
              {errors.nombre && <span className={styles.errorMsg}>{errors.nombre}</span>}
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>Descripción <span className={styles.req}>*</span></label>
              <textarea
                className={[styles.textarea, errors.descripcion ? styles.inputError : ''].join(' ')}
                value={form.descripcion}
                onChange={(e) => set('descripcion', e.target.value)}
                placeholder="Describe el propósito del tratamiento de datos…"
                rows={2}
                maxLength={300}
              />
              {errors.descripcion && <span className={styles.errorMsg}>{errors.descripcion}</span>}
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>Área o dominio solicitante <span className={styles.req}>*</span></label>
              <select
                className={styles.select}
                value={form.dominio}
                onChange={(e) => set('dominio', e.target.value)}
              >
                {DOMINIOS.map((d) => <option key={d} value={d}>{d}</option>)}
              </select>
            </div>
          </div>
        </section>

        {/* ── Justificación ─── */}
        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Justificación del tratamiento</h3>
          </div>
          <div className={styles.sectionBody}>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>Justificación <span className={styles.req}>*</span></label>
              <textarea
                className={[styles.textarea, errors.justificacion ? styles.inputError : ''].join(' ')}
                value={form.justificacion}
                onChange={(e) => set('justificacion', e.target.value)}
                placeholder="Explica por qué es necesario este tratamiento de datos y cuál es la base legal que lo sustenta…"
                rows={4}
                maxLength={600}
              />
              {errors.justificacion && <span className={styles.errorMsg}>{errors.justificacion}</span>}
            </div>
          </div>
        </section>

        {/* ── Datos requeridos ─── */}
        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>Datos requeridos para esta finalidad</h3>
            <button
              className={styles.addBtn}
              onClick={startAdd}
              disabled={editingDato !== null}
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
                        <label className={styles.label}>Nombre del dato <span className={styles.req}>*</span></label>
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
                          onClick={() => toggleObligatorio(dato._id)}
                          title="Clic para cambiar"
                        >
                          {dato.obligatorio ? 'Obligatorio' : 'Opcional'}
                        </span>
                      </div>
                    </div>
                    <div className={styles.datoActions}>
                      <button className={styles.iconBtn} onClick={() => { setEditingDato(dato._id); setDatoDraft({ nombre: dato.nombre, tipo: dato.tipo, obligatorio: dato.obligatorio }); }}>✏️</button>
                      <button className={styles.iconBtnDanger} onClick={() => removeDato(dato._id)}>🗑️</button>
                    </div>
                  </div>
                )
              )}
            </div>
          </div>
        </section>

        {/* ── Acciones ─── */}
        <div className={styles.formActions}>
          <button className={styles.cancelBtn2} onClick={() => navigate('/finalidades')}>Cancelar</button>
          <button className={styles.submitBtn} onClick={handleSubmit} disabled={saved}>
            {saved ? '✓ Solicitud enviada' : 'Enviar solicitud'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default SolicitarFinalidadPage;
