import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Button from '../components/common/Button';
import { useAuth } from '../features/auth/AuthContext';
import {
  BASES_LICITUD, DOMINIOS,
  addTemplate, updateTemplate, getTemplate,
  getFinalidadesActivas, getFinalidad, getDocumento,
  type DataItem, type TemplateEstado,
} from '../utils/mockData';
import styles from './CreateTemplatePage.module.css';

interface FormState {
  nombre: string;
  descripcion: string;
  finalidadId: string;
  baseLicitud: string;
  estado: TemplateEstado;
  dominio: string;
  primaryColor: string;
  buttonLabel: string;
}

const EMPTY_FORM: FormState = {
  nombre: '',
  descripcion: '',
  finalidadId: '',
  baseLicitud: BASES_LICITUD[0],
  estado: 'borrador',
  dominio: DOMINIOS[0],
  primaryColor: '#4361ee',
  buttonLabel: 'Aceptar',
};

const CreateTemplatePage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const isEdit = Boolean(id);

  const activas = getFinalidadesActivas();

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [dataItems, setDataItems] = useState<DataItem[]>([]);
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({});
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!isEdit || !id) return;
    const tpl = getTemplate(id);
    if (!tpl) { navigate('/plantillas'); return; }
    setForm({
      nombre: tpl.nombre,
      descripcion: tpl.descripcion,
      finalidadId: tpl.finalidadId,
      baseLicitud: tpl.baseLicitud,
      estado: tpl.estado,
      dominio: tpl.dominio,
      primaryColor: tpl.primaryColor,
      buttonLabel: tpl.buttonLabel,
    });
    setDataItems(tpl.dataItems.map((d) => ({ ...d })));
  }, [id, isEdit, navigate]);

  const set = <K extends keyof FormState>(key: K, val: FormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: val }));
    setErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  const handleFinalidadChange = (finalidadId: string) => {
    set('finalidadId', finalidadId);
    if (!finalidadId) { setDataItems([]); return; }
    const fin = getFinalidad(finalidadId);
    if (fin) {
      const now = Date.now();
      setDataItems(
        fin.datos.map((d, i) => ({
          id: `di-auto-${now}-${i}`,
          nombre: d.nombre,
          tipo: d.tipo,
          obligatorio: d.obligatorio,
          descripcionTitular: '',
        }))
      );
    }
  };

  const updateDescripcionTitular = (itemId: string, desc: string) => {
    setDataItems((prev) =>
      prev.map((d) => d.id === itemId ? { ...d, descripcionTitular: desc } : d)
    );
  };

  const validate = (): boolean => {
    const e: Partial<Record<keyof FormState, string>> = {};
    if (!form.nombre.trim())  e.nombre      = 'El nombre es obligatorio.';
    if (!form.finalidadId)    e.finalidadId = 'Selecciona una finalidad aprobada.';
    if (!form.dominio)        e.dominio     = 'Selecciona un dominio.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = () => {
    if (!validate()) return;
    const payload = {
      nombre: form.nombre,
      descripcion: form.descripcion,
      finalidadId: form.finalidadId,
      baseLicitud: form.baseLicitud,
      estado: form.estado,
      dominio: form.dominio,
      primaryColor: form.primaryColor,
      buttonLabel: form.buttonLabel,
      dataItems,
      creadoPor: user?.name ?? 'DPO',
    };
    if (isEdit && id) {
      updateTemplate(id, payload, user?.name ?? 'DPO');
    } else {
      addTemplate(payload);
    }
    setSaved(true);
    setTimeout(() => navigate('/plantillas'), 1200);
  };

  const finalidadSeleccionada = form.finalidadId ? getFinalidad(form.finalidadId) : null;
  const docPrivacidad = finalidadSeleccionada ? getDocumento(finalidadSeleccionada.documentoPrivacidadId) : null;

  return (
    <div className={styles.page}>
      <button className={styles.backLink} onClick={() => navigate('/plantillas')}>
        ← Volver a Plantillas
      </button>

      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>{isEdit ? 'Editar Plantilla' : 'Nueva Plantilla'}</h2>
          <p className={styles.subtitle}>
            Solo el <strong>DPO</strong> puede crear y editar plantillas de consentimiento
          </p>
        </div>
      </div>

      <div className={styles.formBody}>
        {/* ── Información básica ─────────────────────────────── */}
        <FormSection title="Información básica">
          <div className={styles.fieldGroup}>
            <label className={styles.label}>
              Nombre de plantilla <span className={styles.required}>*</span>
            </label>
            <input
              className={[styles.input, errors.nombre ? styles.inputError : ''].join(' ')}
              value={form.nombre}
              onChange={(e) => set('nombre', e.target.value)}
              placeholder="Ej: Consentimiento Marketing"
              maxLength={80}
            />
            {errors.nombre && <span className={styles.errorMsg}>{errors.nombre}</span>}
          </div>

          <div className={styles.fieldGroup}>
            <label className={styles.label}>Descripción</label>
            <textarea
              className={styles.textarea}
              value={form.descripcion}
              onChange={(e) => set('descripcion', e.target.value)}
              placeholder="Descripción breve de la plantilla..."
              rows={2}
              maxLength={200}
            />
          </div>

          <div className={styles.row2}>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Estado <span className={styles.required}>*</span>
              </label>
              <select
                className={styles.select}
                value={form.estado}
                onChange={(e) => set('estado', e.target.value as TemplateEstado)}
              >
                <option value="borrador">Borrador</option>
                <option value="activa">Activa</option>
                <option value="inactiva">Inactiva</option>
              </select>
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Dominio <span className={styles.required}>*</span>
              </label>
              <select
                className={[styles.select, errors.dominio ? styles.inputError : ''].join(' ')}
                value={form.dominio}
                onChange={(e) => set('dominio', e.target.value)}
              >
                {DOMINIOS.map((d) => <option key={d} value={d}>{d}</option>)}
              </select>
              {errors.dominio && <span className={styles.errorMsg}>{errors.dominio}</span>}
            </div>
          </div>
        </FormSection>

        {/* ── Configuración legal ────────────────────────────── */}
        <FormSection title="Configuración legal (Ley 21.719)">
          <div className={styles.fieldGroup}>
            <label className={styles.label}>
              Finalidad aprobada <span className={styles.required}>*</span>
            </label>
            <select
              className={[styles.select, errors.finalidadId ? styles.inputError : ''].join(' ')}
              value={form.finalidadId}
              onChange={(e) => handleFinalidadChange(e.target.value)}
            >
              <option value="">— Selecciona una finalidad aprobada —</option>
              {activas.map((f) => (
                <option key={f.id} value={f.id}>{f.nombre} · {f.dominio}</option>
              ))}
            </select>
            {errors.finalidadId && <span className={styles.errorMsg}>{errors.finalidadId}</span>}
            {finalidadSeleccionada && (
              <p className={styles.hint}>{finalidadSeleccionada.descripcion}</p>
            )}
            {!form.finalidadId && activas.length === 0 && (
              <p className={styles.hintWarn}>
                No hay finalidades aprobadas. El Jefe de Dominio debe solicitar y el DPO aprobar una finalidad antes de crear plantillas.
              </p>
            )}
          </div>

          {docPrivacidad && (
            <div className={styles.docPrivacidadBanner}>
              <span className={styles.docPrivacidadIcon}>📄</span>
              <div>
                <p className={styles.docPrivacidadNombre}>{docPrivacidad.nombre} <span className={styles.docVersion}>v{docPrivacidad.version}</span></p>
                <p className={styles.docPrivacidadDesc}>{docPrivacidad.descripcion}</p>
              </div>
            </div>
          )}

          <div className={styles.fieldGroup}>
            <label className={styles.label}>
              Base de licitud <span className={styles.required}>*</span>
            </label>
            <select
              className={styles.select}
              value={form.baseLicitud}
              onChange={(e) => set('baseLicitud', e.target.value)}
            >
              {BASES_LICITUD.map((b) => <option key={b} value={b}>{b}</option>)}
            </select>
            <p className={styles.hint}>
              Artículo 12 de la Ley 21.719 — Bases de licitud del tratamiento de datos personales.
            </p>
          </div>
        </FormSection>

        {/* ── Datos heredados de la finalidad ──────────────── */}
        <FormSection title="Datos del titular">
          {!form.finalidadId ? (
            <div className={styles.emptyItems}>
              <p>Selecciona una finalidad aprobada para cargar los datos automáticamente.</p>
            </div>
          ) : dataItems.length === 0 ? (
            <div className={styles.emptyItems}>
              <p>La finalidad seleccionada no tiene datos definidos.</p>
            </div>
          ) : (
            <>
              <div className={styles.autoloadNote}>
                Datos heredados de la finalidad. El nombre, tipo y obligatoriedad vienen definidos por la solicitud aprobada. Puedes editar únicamente la <strong>descripción visible para el titular</strong>.
              </div>
              <div className={styles.itemsList}>
                {dataItems.map((item) => (
                  <div key={item.id} className={styles.itemCard}>
                    <div className={styles.itemCardHeader}>
                      <span className={styles.itemCardNombre}>{item.nombre}</span>
                      <span className={styles.itemCardTipo}>{item.tipo}</span>
                      <span className={item.obligatorio ? styles.badgeObligatorio : styles.badgeOpcional}>
                        {item.obligatorio ? 'Obligatorio' : 'Opcional'}
                      </span>
                    </div>
                    <div className={styles.fieldGroup} style={{ marginTop: 8 }}>
                      <label className={styles.labelSm}>Descripción para el titular</label>
                      <textarea
                        className={styles.textareaSm}
                        value={item.descripcionTitular}
                        onChange={(e) => updateDescripcionTitular(item.id, e.target.value)}
                        placeholder="Ej: Utilizaremos este dato para comunicaciones personalizadas…"
                        rows={2}
                        maxLength={250}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </FormSection>

        {/* ── Acciones ──────────────────────────────────────── */}
        <div className={styles.formActions}>
          <Button variant="ghost" size="sm" onClick={() => navigate('/plantillas')}>
            Cancelar
          </Button>
          <Button variant="primary" size="sm" onClick={handleSave} disabled={saved}>
            {saved ? '✓ Guardado' : isEdit ? 'Guardar cambios' : 'Crear Plantilla'}
          </Button>
        </div>
      </div>
    </div>
  );
};

const FormSection = ({
  title,
  children,
  action,
}: {
  title: string;
  children: React.ReactNode;
  action?: React.ReactNode;
}) => (
  <section className={styles.section}>
    <div className={styles.sectionHeader}>
      <h3 className={styles.sectionTitle}>{title}</h3>
      {action}
    </div>
    <div className={styles.sectionBody}>{children}</div>
  </section>
);

export default CreateTemplatePage;
