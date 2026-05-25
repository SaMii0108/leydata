import { useState } from 'react';
import {
  MOCK_DOMAINS,
  addMockDomain,
  updateMockDomain,
  domainCodeExists,
} from '../features/domains/mockDomains';
import type { MockDomain } from '../features/domains/mockDomains';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import styles from './DominiosPage.module.css';

/* ─── Formulario vacío ─────────────────────────────────────────────────────── */
const EMPTY_FORM = { code: '', name: '', description: '' };

/* ─── Componente principal ─────────────────────────────────────────────────── */
const DominiosPage = () => {
  const [domains, setDomains]       = useState<MockDomain[]>([...MOCK_DOMAINS]);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm]             = useState(EMPTY_FORM);
  const [formError, setFormError]   = useState<string | null>(null);

  // Modal de confirmación para toggle
  const [confirmToggle, setConfirmToggle] = useState<MockDomain | null>(null);

  /* ── Crear dominio ────────────────────────────────────────────────────────── */
  const handleCreate = () => {
    const code = form.code.trim().toUpperCase();
    const name = form.name.trim();
    const description = form.description.trim();

    if (!code)  { setFormError('El código es obligatorio.'); return; }
    if (!name)  { setFormError('El nombre es obligatorio.'); return; }
    if (domainCodeExists(code)) { setFormError('Ya existe un dominio con ese código.'); return; }

    const newDomain: MockDomain = {
      id:          `d${Date.now()}`,
      code,
      name,
      description,
      active:      true,
    };
    addMockDomain(newDomain);
    setDomains((prev) => [...prev, newDomain]);
    setForm(EMPTY_FORM);
    setFormError(null);
    setShowCreate(false);
  };

  const handleCloseCreate = () => {
    setShowCreate(false);
    setForm(EMPTY_FORM);
    setFormError(null);
  };

  /* ── Toggle activo/inactivo ──────────────────────────────────────────────── */
  const handleConfirmToggle = () => {
    if (!confirmToggle) return;
    const nextActive = !confirmToggle.active;
    updateMockDomain(confirmToggle.id, { active: nextActive });
    setDomains((prev) =>
      prev.map((d) => (d.id === confirmToggle.id ? { ...d, active: nextActive } : d)),
    );
    setConfirmToggle(null);
  };

  return (
    <div className={styles.page}>
      {/* ── Encabezado ───────────────────────────────────────────────────────── */}
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Dominios</h2>
          <p className={styles.subtitle}>
            Define las áreas de datos de tu organización. Los dominios se usan al asignar responsables de área.
          </p>
        </div>
        <Button variant="primary" size="sm" onClick={() => setShowCreate(true)}>
          + Crear dominio
        </Button>
      </div>

      {/* ── Tabla ───────────────────────────────────────────────────────────── */}
      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Código</th>
                <th>Nombre</th>
                <th>Descripción</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {domains.length === 0 && (
                <tr>
                  <td colSpan={5} className={styles.emptyRow}>
                    No hay dominios registrados. Crea el primero.
                  </td>
                </tr>
              )}
              {domains.map((d) => (
                <tr key={d.id} className={!d.active ? styles.rowInactive : ''}>
                  <td>
                    <span className={styles.codeBadge}>{d.code}</span>
                  </td>
                  <td className={styles.cellName}>{d.name}</td>
                  <td className={styles.cellDesc}>{d.description || <span className={styles.noData}>—</span>}</td>
                  <td>
                    <span className={[styles.statusBadge, d.active ? styles.statusActive : styles.statusInactive].join(' ')}>
                      {d.active ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td>
                    <Button
                      variant={d.active ? 'ghost' : 'secondary'}
                      size="sm"
                      onClick={() => setConfirmToggle(d)}
                    >
                      {d.active ? 'Desactivar' : 'Reactivar'}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* ── Modal: Crear dominio ─────────────────────────────────────────────── */}
      <Modal open={showCreate} onClose={handleCloseCreate} variant="center">
        <div className={styles.modal}>
          <div className={styles.modalHeader}>
            <h3 className={styles.modalTitle}>Crear nuevo dominio</h3>
            <button className={styles.closeBtn} onClick={handleCloseCreate}>✕</button>
          </div>
          <p className={styles.modalHint}>
            Los dominios representan las áreas funcionales de tu organización que gestionan datos personales.
          </p>

          <div className={styles.fields}>
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="d-code">
                Código <span className={styles.required}>*</span>
              </label>
              <input
                id="d-code"
                type="text"
                className={styles.input}
                placeholder="Ej: MKT, RRHH, TEC"
                value={form.code}
                maxLength={10}
                onChange={(e) => { setForm((f) => ({ ...f, code: e.target.value.toUpperCase() })); setFormError(null); }}
              />
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="d-name">
                Nombre <span className={styles.required}>*</span>
              </label>
              <input
                id="d-name"
                type="text"
                className={styles.input}
                placeholder="Ej: Marketing"
                value={form.name}
                onChange={(e) => { setForm((f) => ({ ...f, name: e.target.value })); setFormError(null); }}
              />
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="d-desc">Descripción</label>
              <textarea
                id="d-desc"
                className={styles.textarea}
                placeholder="Descripción opcional del dominio..."
                value={form.description}
                rows={3}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              />
            </div>

            {formError && <p className={styles.formError}>{formError}</p>}
          </div>

          <div className={styles.modalFooter}>
            <Button variant="ghost" onClick={handleCloseCreate}>Cancelar</Button>
            <Button
              variant="primary"
              onClick={handleCreate}
              disabled={!form.code.trim() || !form.name.trim()}
            >
              Crear dominio
            </Button>
          </div>
        </div>
      </Modal>

      {/* ── Modal: Confirmar toggle ──────────────────────────────────────────── */}
      <Modal open={!!confirmToggle} onClose={() => setConfirmToggle(null)} variant="center">
        {confirmToggle && (
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>
                {confirmToggle.active ? 'Desactivar dominio' : 'Reactivar dominio'}
              </h3>
              <button className={styles.closeBtn} onClick={() => setConfirmToggle(null)}>✕</button>
            </div>

            <div className={styles.confirmBody}>
              <div className={[styles.confirmIcon, confirmToggle.active ? styles.confirmWarn : styles.confirmInfo].join(' ')}>
                {confirmToggle.active ? '⚠' : '↩'}
              </div>
              <p className={styles.confirmText}>
                {confirmToggle.active
                  ? <>¿Deseas desactivar el dominio <strong>{confirmToggle.name}</strong>? Los responsables de área asignados a este dominio no podrán recibir nuevas asignaciones.</>
                  : <>¿Deseas reactivar el dominio <strong>{confirmToggle.name}</strong>? Volverá a estar disponible para asignar responsables.</>
                }
              </p>
            </div>

            <div className={styles.modalFooter}>
              <Button variant="ghost" onClick={() => setConfirmToggle(null)}>Cancelar</Button>
              <Button
                variant={confirmToggle.active ? 'danger' : 'primary'}
                onClick={handleConfirmToggle}
              >
                {confirmToggle.active ? 'Sí, desactivar' : 'Sí, reactivar'}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default DominiosPage;
