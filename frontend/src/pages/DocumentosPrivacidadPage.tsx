import { useState } from 'react';
import Modal from '../components/common/Modal';
import {
  DOCUMENTOS_PRIVACIDAD, addDocumento, updateDocumento,
  type DocumentoEstado, type DocumentoPrivacidad,
} from '../utils/mockData';
import styles from './DocumentosPrivacidadPage.module.css';

type Filtro = 'todos' | DocumentoEstado;

const ESTADO_LABEL: Record<DocumentoEstado, string> = {
  vigente:  'Vigente',
  borrador: 'Borrador',
  obsoleto: 'Obsoleto',
};

interface DocForm {
  nombre: string;
  descripcion: string;
  version: string;
  estado: DocumentoEstado;
  fechaVigencia: string;
}

const EMPTY_FORM: DocForm = {
  nombre: '',
  descripcion: '',
  version: '1.0',
  estado: 'borrador',
  fechaVigencia: '',
};

const DocumentosPrivacidadPage = () => {
  const [filtro, setFiltro] = useState<Filtro>('todos');
  const [, forceUpdate] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<DocForm>(EMPTY_FORM);
  const [errors, setErrors] = useState<Partial<DocForm>>({});

  const lista = filtro === 'todos'
    ? DOCUMENTOS_PRIVACIDAD
    : DOCUMENTOS_PRIVACIDAD.filter((d) => d.estado === filtro);

  const counts = {
    todos:    DOCUMENTOS_PRIVACIDAD.length,
    vigente:  DOCUMENTOS_PRIVACIDAD.filter((d) => d.estado === 'vigente').length,
    borrador: DOCUMENTOS_PRIVACIDAD.filter((d) => d.estado === 'borrador').length,
    obsoleto: DOCUMENTOS_PRIVACIDAD.filter((d) => d.estado === 'obsoleto').length,
  };

  const openCreate = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setErrors({});
    setModalOpen(true);
  };

  const openEdit = (doc: DocumentoPrivacidad) => {
    setEditingId(doc.id);
    setForm({
      nombre: doc.nombre,
      descripcion: doc.descripcion,
      version: doc.version,
      estado: doc.estado,
      fechaVigencia: doc.fechaVigencia,
    });
    setErrors({});
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditingId(null);
  };

  const set = <K extends keyof DocForm>(k: K, v: DocForm[K]) => {
    setForm((p) => ({ ...p, [k]: v }));
    setErrors((p) => ({ ...p, [k]: undefined }));
  };

  const validate = (): boolean => {
    const e: Partial<DocForm> = {};
    if (!form.nombre.trim())      e.nombre        = 'El nombre es obligatorio.';
    if (!form.descripcion.trim()) e.descripcion   = 'La descripción es obligatoria.';
    if (!form.version.trim())     e.version       = 'La versión es obligatoria.';
    if (!form.fechaVigencia)      e.fechaVigencia = 'La fecha de vigencia es obligatoria.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = () => {
    if (!validate()) return;
    if (editingId) {
      updateDocumento(editingId, form);
    } else {
      addDocumento(form);
    }
    forceUpdate((n) => n + 1);
    closeModal();
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Documentos de Privacidad</h2>
          <p className={styles.subtitle}>
            Repositorio de documentos legales que respaldan las finalidades de tratamiento de datos conforme a la Ley 21.719.
          </p>
        </div>
        <button className={styles.newBtn} onClick={openCreate}>
          + Crear Documento
        </button>
      </div>

      <div className={styles.filters}>
        {(['todos', 'vigente', 'borrador', 'obsoleto'] as Filtro[]).map((f) => (
          <button
            key={f}
            className={[styles.filterBtn, filtro === f ? styles.filterActive : ''].join(' ')}
            onClick={() => setFiltro(f)}
          >
            {f === 'todos' ? 'Todos' : ESTADO_LABEL[f as DocumentoEstado]}
            <span className={styles.filterCount}>{counts[f as keyof typeof counts]}</span>
          </button>
        ))}
      </div>

      {lista.length === 0 ? (
        <div className={styles.empty}>
          <p>No hay documentos en esta categoría.</p>
        </div>
      ) : (
        <div className={styles.grid}>
          {lista.map((doc) => (
            <div key={doc.id} className={styles.card}>
              <div className={styles.cardTop}>
                <span className={[styles.badge, styles[`badge_${doc.estado}`]].join(' ')}>
                  {ESTADO_LABEL[doc.estado]}
                </span>
                <span className={styles.version}>v{doc.version}</span>
              </div>
              <h3 className={styles.cardTitle}>{doc.nombre}</h3>
              <p className={styles.cardDesc}>{doc.descripcion}</p>
              <div className={styles.cardFooter}>
                <span className={styles.vigencia}>
                  Vigente hasta: <strong>{doc.fechaVigencia}</strong>
                </span>
                <button className={styles.editBtn} onClick={() => openEdit(doc)}>
                  Editar
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={closeModal} variant="center">
        <div className={styles.modalContent}>
          <div className={styles.modalHeader}>
            <h3 className={styles.modalTitle}>
              {editingId ? 'Editar Documento' : 'Nuevo Documento de Privacidad'}
            </h3>
            <button className={styles.modalClose} onClick={closeModal} aria-label="Cerrar">✕</button>
          </div>

          <div className={styles.formBody}>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Nombre <span className={styles.req}>*</span>
              </label>
              <input
                className={[styles.input, errors.nombre ? styles.inputError : ''].join(' ')}
                value={form.nombre}
                onChange={(e) => set('nombre', e.target.value)}
                placeholder="Ej: Política de Privacidad General"
                maxLength={120}
              />
              {errors.nombre && <span className={styles.errorMsg}>{errors.nombre}</span>}
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Descripción <span className={styles.req}>*</span>
              </label>
              <textarea
                className={[styles.textarea, errors.descripcion ? styles.inputError : ''].join(' ')}
                value={form.descripcion}
                onChange={(e) => set('descripcion', e.target.value)}
                placeholder="Describe el alcance y propósito del documento…"
                rows={3}
                maxLength={400}
              />
              {errors.descripcion && <span className={styles.errorMsg}>{errors.descripcion}</span>}
            </div>

            <div className={styles.fieldRow}>
              <div className={styles.fieldGroup}>
                <label className={styles.label}>
                  Versión <span className={styles.req}>*</span>
                </label>
                <input
                  className={[styles.input, errors.version ? styles.inputError : ''].join(' ')}
                  value={form.version}
                  onChange={(e) => set('version', e.target.value)}
                  placeholder="Ej: 1.0"
                  maxLength={10}
                />
                {errors.version && <span className={styles.errorMsg}>{errors.version}</span>}
              </div>

              <div className={styles.fieldGroup}>
                <label className={styles.label}>Estado</label>
                <select
                  className={styles.select}
                  value={form.estado}
                  onChange={(e) => set('estado', e.target.value as DocumentoEstado)}
                >
                  <option value="borrador">Borrador</option>
                  <option value="vigente">Vigente</option>
                  <option value="obsoleto">Obsoleto</option>
                </select>
              </div>
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>
                Fecha de vigencia <span className={styles.req}>*</span>
              </label>
              <input
                type="date"
                className={[styles.input, errors.fechaVigencia ? styles.inputError : ''].join(' ')}
                value={form.fechaVigencia}
                onChange={(e) => set('fechaVigencia', e.target.value)}
              />
              {errors.fechaVigencia && <span className={styles.errorMsg}>{errors.fechaVigencia}</span>}
            </div>
          </div>

          <div className={styles.modalActions}>
            <button className={styles.cancelBtn} onClick={closeModal}>Cancelar</button>
            <button className={styles.saveBtn} onClick={handleSave}>
              {editingId ? 'Guardar cambios' : 'Crear documento'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default DocumentosPrivacidadPage;
