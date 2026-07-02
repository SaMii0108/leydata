import { useState, useEffect, useRef } from 'react';
import Button from '../components/common/Button';
import {
  getDataCategories,
  getPurposeDataCategories,
  linkPurposeDataCategory,
  unlinkPurposeDataCategory,
  ApiError,
  ALL_DATA_USES,
  DATA_USE_LABELS,
  RETENTION_UNIT_LABELS,
  type DataCategoryResponse,
  type PurposeDataCategoryResponse,
  type PurposeDataCategoryRequest,
  type DataUseType,
} from '../api/purposeDataCategoriesApi';
import styles from './PurposeDataCategoriesSection.module.css';

interface Props {
  purposeId: string;
  accessToken: string | null;
  onContinue: () => void;
  continueLabel?: string;
}

interface ModalForm {
  dataCategoryId: string;
  required: boolean;
  dataUses: Set<DataUseType>;
  retentionPeriod: string;
  retentionUnit: 'DAYS' | 'MONTHS' | 'YEARS';
  legalJustification: string;
  anonymizeAfter: boolean;
}

const EMPTY_MODAL_FORM: ModalForm = {
  dataCategoryId: '',
  required: false,
  dataUses: new Set(),
  retentionPeriod: '',
  retentionUnit: 'YEARS',
  legalJustification: '',
  anonymizeAfter: true,
};

const DATA_USE_DESCRIPTIONS: Record<DataUseType, string> = {
  STORAGE: 'Almacenar los datos en sistemas o bases de datos propias.',
  PROCESSING: 'Operar, transformar o utilizar los datos en procesos internos.',
  TRANSFER_TO_THIRD_PARTIES: 'Compartir o ceder los datos a terceros, proveedores u organismos externos.',
  PROFILING: 'Construir perfiles individuales o inferir características del titular a partir de sus datos.',
  ANALYSIS: 'Analizar los datos de forma agregada para estadísticas, informes o investigación.',
};

function formFromPdc(pdc: PurposeDataCategoryResponse): ModalForm {
  return {
    dataCategoryId: pdc.dataCategoryId,
    required: pdc.required,
    dataUses: new Set(pdc.dataUses),
    retentionPeriod: pdc.retentionPeriod?.toString() ?? '',
    retentionUnit: (pdc.retentionUnit as 'DAYS' | 'MONTHS' | 'YEARS') ?? 'YEARS',
    legalJustification: pdc.legalJustification ?? '',
    anonymizeAfter: pdc.anonymizeAfter ?? true,
  };
}

const PurposeDataCategoriesSection = ({
  purposeId,
  accessToken,
  onContinue,
  continueLabel = 'Finalizar',
}: Props) => {
  const [linked, setLinked] = useState<PurposeDataCategoryResponse[]>([]);
  const [catalog, setCatalog] = useState<DataCategoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editingPdc, setEditingPdc] = useState<PurposeDataCategoryResponse | null>(null);
  const [modalForm, setModalForm] = useState<ModalForm>({ ...EMPTY_MODAL_FORM, dataUses: new Set() });
  const [modalDirty, setModalDirty] = useState(false);
  const [modalErrors, setModalErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const dialogRef = useRef<HTMLDivElement>(null);
  const lastFocusRef = useRef<Element | null>(null);
  const requestCloseModalRef = useRef<() => void>(() => {});

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const [cats, lnk] = await Promise.all([
          getDataCategories(accessToken),
          getPurposeDataCategories(purposeId, accessToken),
        ]);
        if (!cancelled) {
          setCatalog(cats);
          setLinked(lnk);
        }
      } catch (err) {
        if (!cancelled)
          setLoadError(
            err instanceof ApiError
              ? err.message
              : 'No se pudieron cargar los datos. Recarga la página para intentarlo nuevamente.',
          );
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [purposeId, accessToken]);

  useEffect(() => {
    if (modalOpen && dialogRef.current) {
      dialogRef.current.focus();
    }
  }, [modalOpen]);

  useEffect(() => {
    if (!modalOpen) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') requestCloseModalRef.current();
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [modalOpen]);

  const linkedCategoryIds = new Set(linked.map((p) => p.dataCategoryId));
  const availableForAdd = catalog.filter((c) => !linkedCategoryIds.has(c.id));

  const closeModal = () => {
    setModalOpen(false);
    setEditingPdc(null);
    setTimeout(() => {
      if (lastFocusRef.current instanceof HTMLElement) {
        lastFocusRef.current.focus();
      }
    }, 0);
  };

  requestCloseModalRef.current = () => {
    if (saving) return;
    if (modalDirty && !window.confirm('Tienes cambios sin guardar. ¿Descartarlos?')) return;
    closeModal();
  };

  const openCreate = () => {
    lastFocusRef.current = document.activeElement;
    setEditingPdc(null);
    setModalForm({ ...EMPTY_MODAL_FORM, dataUses: new Set() });
    setModalDirty(false);
    setModalErrors({});
    setSaveError(null);
    setModalOpen(true);
  };

  const openEdit = (pdc: PurposeDataCategoryResponse) => {
    lastFocusRef.current = document.activeElement;
    setEditingPdc(pdc);
    setModalForm(formFromPdc(pdc));
    setModalDirty(false);
    setModalErrors({});
    setSaveError(null);
    setModalOpen(true);
  };

  const setField = <K extends keyof ModalForm>(k: K, v: ModalForm[K]) => {
    setModalForm((prev) => ({ ...prev, [k]: v }));
    setModalErrors((prev) => { const n = { ...prev }; delete n[k]; return n; });
    setModalDirty(true);
  };

  const toggleUse = (use: DataUseType) => {
    setModalForm((prev) => {
      const next = new Set(prev.dataUses);
      if (next.has(use)) next.delete(use);
      else next.add(use);
      return { ...prev, dataUses: next };
    });
    setModalErrors((prev) => { const n = { ...prev }; delete n.dataUses; return n; });
    setModalDirty(true);
  };

  const validateModal = (): boolean => {
    const errs: Record<string, string> = {};
    if (!editingPdc && !modalForm.dataCategoryId)
      errs.dataCategoryId = 'Selecciona una categoría.';
    if (modalForm.dataUses.size === 0)
      errs.dataUses = 'Selecciona al menos un uso del dato.';
    const period = parseInt(modalForm.retentionPeriod, 10);
    if (!modalForm.retentionPeriod || isNaN(period) || period < 1)
      errs.retentionPeriod = 'Ingresa un período válido (mínimo 1).';
    setModalErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSave = async () => {
    if (!validateModal()) return;
    setSaving(true);
    setSaveError(null);

    const catId = editingPdc ? editingPdc.dataCategoryId : modalForm.dataCategoryId;

    const payload: PurposeDataCategoryRequest = {
      dataCategoryId: catId,
      required: modalForm.required,
      dataUses: Array.from(modalForm.dataUses),
      retention: {
        retentionPeriod: parseInt(modalForm.retentionPeriod, 10),
        retentionUnit: modalForm.retentionUnit,
        ...(modalForm.legalJustification.trim()
          ? { legalJustification: modalForm.legalJustification.trim() }
          : {}),
        anonymizeAfter: modalForm.anonymizeAfter,
      },
    };

    let deleteSucceeded = false;

    try {
      if (editingPdc) {
        await unlinkPurposeDataCategory(purposeId, editingPdc.id, accessToken);
        deleteSucceeded = true;
        setLinked((prev) => prev.filter((p) => p.id !== editingPdc.id));
        await linkPurposeDataCategory(purposeId, payload, accessToken);
      } else {
        await linkPurposeDataCategory(purposeId, payload, accessToken);
      }
      const updated = await getPurposeDataCategories(purposeId, accessToken);
      setLinked(updated);
      closeModal();
    } catch (err) {
      if (deleteSucceeded) {
        setSaveError(
          'La asociación anterior fue eliminada del servidor, pero la nueva configuración no pudo guardarse. ' +
          'Cierra este diálogo y vuelve a agregar la categoría manualmente.',
        );
      } else {
        setSaveError(
          err instanceof ApiError
            ? err.message
            : 'No se pudo guardar la categoría. Verifica tu conexión e intenta nuevamente.',
        );
      }
    } finally {
      setSaving(false);
    }
  };

  const handleRemove = async (pdc: PurposeDataCategoryResponse) => {
    setRemovingId(pdc.id);
    setActionError(null);
    try {
      await unlinkPurposeDataCategory(purposeId, pdc.id, accessToken);
      setLinked((prev) => prev.filter((p) => p.id !== pdc.id));
    } catch (err) {
      setActionError(
        err instanceof ApiError
          ? err.message
          : 'No se pudo eliminar la categoría. Verifica tu conexión e intenta nuevamente.',
      );
    } finally {
      setRemovingId(null);
    }
  };

  const handleOverlayKeyDown = (e: React.KeyboardEvent) => {
    if (e.key !== 'Tab' || !dialogRef.current) return;
    const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    );
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  };

  const allCatalogLinked = !loading && !loadError && availableForAdd.length === 0 && linked.length > 0;
  const showRequirement = !loading && !loadError && linked.length === 0;

  const selectedCategory = modalForm.dataCategoryId
    ? catalog.find((c) => c.id === modalForm.dataCategoryId) ?? null
    : null;

  const editCategoryDesc = editingPdc
    ? (catalog.find((c) => c.id === editingPdc.dataCategoryId)?.description ?? null)
    : null;

  return (
    <>
      <section className={styles.section}>
        <div className={styles.sectionHeader}>
          <div className={styles.sectionTitleGroup}>
            <h3 className={styles.sectionTitle}>Categorías de datos personales</h3>
            {!loading && !loadError && (
              <span className={styles.sectionCount}>{linked.length}</span>
            )}
          </div>
          {!loading && !loadError && (
            <div className={styles.addBtnGroup}>
              <Button
                variant="primary"
                size="sm"
                onClick={openCreate}
                disabled={availableForAdd.length === 0}
              >
                + Agregar categoría
              </Button>
              {allCatalogLinked && (
                <span className={styles.addBtnHint}>Todas las categorías ya están asociadas</span>
              )}
            </div>
          )}
        </div>

        <div className={styles.sectionBody}>
          {loading ? (
            <p className={styles.stateMsg}>Cargando catálogo…</p>
          ) : loadError ? (
            <p className={styles.actionError}>{loadError}</p>
          ) : (
            <>
              {actionError && <p className={styles.actionError}>{actionError}</p>}

              {linked.length === 0 ? (
                <div className={styles.empty}>
                  <p className={styles.emptyText}>
                    Aún no hay categorías de datos asociadas a esta finalidad.
                  </p>
                  <p className={styles.emptyHint}>
                    Debes agregar al menos una para poder completar el registro.
                  </p>
                </div>
              ) : (
                <div className={styles.categoryList}>
                  {linked.map((pdc) => {
                    const isBusy = removingId === pdc.id;
                    return (
                      <div
                        key={pdc.id}
                        className={[styles.categoryCard, isBusy ? styles.categoryCardBusy : ''].join(' ')}
                      >
                        <div className={styles.cardTop}>
                          <div className={styles.cardTitleRow}>
                            <p className={styles.categoryName}>{pdc.dataCategoryName}</p>
                            <span className={styles.categoryCode}>{pdc.dataCategoryCode}</span>
                          </div>
                        </div>

                        <div className={styles.badgesRow}>
                          {pdc.isSensitive && (
                            <span className={styles.badgeSensitive}>Dato sensible</span>
                          )}
                          <span className={pdc.required ? styles.badgeRequired : styles.badgeOptional}>
                            {pdc.required ? 'Obligatorio' : 'Opcional'}
                          </span>
                          {pdc.dataUses.map((u) => (
                            <span key={u} className={styles.usePill}>
                              {DATA_USE_LABELS[u]}
                            </span>
                          ))}
                        </div>

                        <div className={styles.retentionRow}>
                          <span className={styles.retentionLabel}>Retención</span>
                          {pdc.retentionPeriod != null && pdc.retentionUnit != null ? (
                            <>
                              <span>
                                {pdc.retentionPeriod}{' '}
                                {RETENTION_UNIT_LABELS[pdc.retentionUnit] ?? pdc.retentionUnit.toLowerCase()}
                              </span>
                              <span>·</span>
                              <span>
                                {pdc.anonymizeAfter ? 'Anonimizar al vencer' : 'Eliminar al vencer'}
                              </span>
                            </>
                          ) : (
                            <span>Sin política definida</span>
                          )}
                        </div>

                        <div className={styles.cardActions}>
                          {pdc.retentionLocked ? (
                            <span className={styles.lockedNote}>Bloqueada por documento publicado</span>
                          ) : (
                            <>
                              <button
                                className={styles.editBtn}
                                onClick={() => openEdit(pdc)}
                                disabled={isBusy}
                              >
                                Editar
                              </button>
                              <button
                                className={styles.removeBtn}
                                onClick={() => handleRemove(pdc)}
                                disabled={isBusy}
                              >
                                {removingId === pdc.id ? 'Eliminando…' : 'Eliminar'}
                              </button>
                            </>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}
        </div>
      </section>

      <div className={styles.continueRow}>
        {showRequirement && (
          <p className={styles.requirementMsg}>
            Se requiere al menos una categoría de datos personales para completar la finalidad.
          </p>
        )}
        <Button
          variant="primary"
          size="sm"
          onClick={onContinue}
          disabled={linked.length === 0 || loading}
        >
          {continueLabel}
        </Button>
      </div>

      {modalOpen && (
        <div
          className={styles.overlay}
          onClick={(e) => { if (e.target === e.currentTarget) requestCloseModalRef.current(); }}
          onKeyDown={handleOverlayKeyDown}
        >
          <div
            ref={dialogRef}
            className={styles.dialog}
            role="dialog"
            aria-modal="true"
            aria-labelledby="purpose-modal-title"
            tabIndex={-1}
          >
            <div className={styles.dialogHeader}>
              <h4 id="purpose-modal-title" className={styles.dialogTitle}>
                {editingPdc ? 'Editar categoría' : 'Agregar categoría de datos'}
              </h4>
              <button
                className={styles.dialogClose}
                onClick={() => requestCloseModalRef.current()}
                disabled={saving}
                aria-label="Cerrar diálogo"
              >
                ✕
              </button>
            </div>

            <div className={styles.dialogBody}>
              <div className={styles.modalSection}>
                <p className={styles.modalSectionTitle}>Categoría</p>

                {editingPdc ? (
                  <div className={styles.fieldGroup}>
                    <span className={styles.label}>Categoría seleccionada</span>
                    <div className={styles.readonlyCategory}>
                      <span>{editingPdc.dataCategoryName}</span>
                      <span className={styles.readonlyCategoryCode}>{editingPdc.dataCategoryCode}</span>
                    </div>
                    {editCategoryDesc && (
                      <p className={styles.categoryDescHint}>{editCategoryDesc}</p>
                    )}
                  </div>
                ) : (
                  <div className={styles.fieldGroup}>
                    <label className={styles.label}>
                      Tipo de dato <span className={styles.req}>*</span>
                    </label>
                    <select
                      className={[styles.select, modalErrors.dataCategoryId ? styles.inputError : ''].join(' ')}
                      value={modalForm.dataCategoryId}
                      onChange={(e) => setField('dataCategoryId', e.target.value)}
                      disabled={saving}
                    >
                      <option value="">— Selecciona un tipo de dato —</option>
                      {availableForAdd.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}{c.isSensitive ? ' ⚠ Sensible' : ''}
                        </option>
                      ))}
                    </select>
                    {modalErrors.dataCategoryId && (
                      <p className={styles.errorMsg}>{modalErrors.dataCategoryId}</p>
                    )}
                    {selectedCategory?.description && (
                      <p className={styles.categoryDescHint}>{selectedCategory.description}</p>
                    )}
                  </div>
                )}

                <div className={styles.fieldGroup}>
                  <label className={styles.checkLabel}>
                    <input
                      type="checkbox"
                      className={styles.check}
                      checked={modalForm.required}
                      onChange={(e) => setField('required', e.target.checked)}
                      disabled={saving}
                    />
                    <span>Dato obligatorio</span>
                  </label>
                  <p className={styles.useHint}>
                    Si está marcado, el titular no puede omitir este dato al otorgar el consentimiento.
                  </p>
                </div>
              </div>

              <div className={styles.modalSection}>
                <p className={styles.modalSectionTitle}>
                  Usos del dato <span className={styles.req}>*</span>
                </p>
                <div className={styles.checkList}>
                  {ALL_DATA_USES.map((use) => (
                    <div key={use} className={styles.useItem}>
                      <label className={styles.checkLabel}>
                        <input
                          type="checkbox"
                          className={styles.check}
                          checked={modalForm.dataUses.has(use)}
                          onChange={() => toggleUse(use)}
                          disabled={saving}
                        />
                        <span>{DATA_USE_LABELS[use]}</span>
                      </label>
                      <p className={styles.useHint}>{DATA_USE_DESCRIPTIONS[use]}</p>
                    </div>
                  ))}
                </div>
                {modalErrors.dataUses && (
                  <p className={styles.errorMsg}>{modalErrors.dataUses}</p>
                )}
              </div>

              <div className={styles.modalSection}>
                <p className={styles.modalSectionTitle}>
                  Política de retención <span className={styles.req}>*</span>
                </p>
                <div className={styles.retentionGrid}>
                  <div className={styles.fieldGroup}>
                    <label className={styles.label}>Período</label>
                    <input
                      type="number"
                      className={[styles.input, modalErrors.retentionPeriod ? styles.inputError : ''].join(' ')}
                      min={1}
                      placeholder="Ej: 5"
                      value={modalForm.retentionPeriod}
                      onChange={(e) => setField('retentionPeriod', e.target.value)}
                      disabled={saving}
                    />
                    {modalErrors.retentionPeriod && (
                      <p className={styles.errorMsg}>{modalErrors.retentionPeriod}</p>
                    )}
                  </div>
                  <div className={styles.fieldGroup}>
                    <label className={styles.label}>Unidad</label>
                    <select
                      className={styles.select}
                      value={modalForm.retentionUnit}
                      onChange={(e) => setField('retentionUnit', e.target.value as 'DAYS' | 'MONTHS' | 'YEARS')}
                      disabled={saving}
                    >
                      <option value="DAYS">Días</option>
                      <option value="MONTHS">Meses</option>
                      <option value="YEARS">Años</option>
                    </select>
                  </div>
                </div>
                <label className={styles.checkLabel}>
                  <input
                    type="checkbox"
                    className={styles.check}
                    checked={modalForm.anonymizeAfter}
                    onChange={(e) => setField('anonymizeAfter', e.target.checked)}
                    disabled={saving}
                  />
                  <span>Anonimizar al vencer el período</span>
                </label>
                <p className={styles.useHint}>
                  Si no se marca, los datos serán eliminados definitivamente al vencer el período.
                </p>
                <div className={styles.fieldGroup}>
                  <label className={styles.label}>
                    Justificación legal <span className={styles.optional}>(opcional)</span>
                  </label>
                  <textarea
                    className={styles.textarea}
                    rows={2}
                    placeholder="Ej: Art. 17 Ley 21.719 — datos retenidos por obligación tributaria"
                    value={modalForm.legalJustification}
                    onChange={(e) => setField('legalJustification', e.target.value)}
                    disabled={saving}
                  />
                </div>
              </div>
            </div>

            <div className={styles.dialogFooter}>
              {saveError && <p className={styles.saveError}>{saveError}</p>}
              <button
                className={styles.cancelBtn}
                onClick={() => requestCloseModalRef.current()}
                disabled={saving}
              >
                Cancelar
              </button>
              <Button variant="primary" size="sm" onClick={handleSave} disabled={saving}>
                {saving ? 'Guardando…' : 'Guardar'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default PurposeDataCategoriesSection;
