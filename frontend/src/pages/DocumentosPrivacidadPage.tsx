import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../features/auth/AuthContext';
import {
  listDocuments,
  createDocument,
  updateDocument,
  submitDocument,
  resubmitDocument,
  approveDocument,
  rejectDocument,
  publishDocument,
  archiveDocument,
  newDocumentVersion,
  addDocumentPurpose,
  removeDocumentPurpose,
  ApiError,
  type PrivacyDocumentDto,
  type DocumentStatus,
  type DocumentCategory,
  type CreateDocumentPayload,
} from '../api/privacyDocumentsApi';
import { getPurposes, type PurposeResponse } from '../api/purposesApi';
import { getTemplates, type TemplateResponse } from '../api/templatesApi';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import styles from './DocumentosPrivacidadPage.module.css';

// ── Etiquetas ─────────────────────────────────────────────────────────────────

const STATUS_LABEL: Record<DocumentStatus, string> = {
  DRAFT:      'Borrador',
  IN_REVIEW:  'En revisión',
  APPROVED:   'Aprobado',
  REJECTED:   'Rechazado',
  PUBLISHED:  'Publicado',
  ARCHIVED:   'Archivado',
};

const CATEGORY_LABEL: Record<DocumentCategory, string> = {
  POLITICA_PRIVACIDAD:    'Política de Privacidad',
  AVISO_COOKIES:          'Aviso de Cookies',
  DATOS_SENSIBLES:        'Datos Sensibles',
  MARKETING_DIRECTO:      'Marketing Directo',
  MENORES_EDAD:           'Menores de Edad',
  TRANSFERENCIA_TERCEROS: 'Transferencia a Terceros',
};

const CATEGORY_OPTIONS: DocumentCategory[] = [
  'POLITICA_PRIVACIDAD',
  'AVISO_COOKIES',
  'DATOS_SENSIBLES',
  'MARKETING_DIRECTO',
  'MENORES_EDAD',
  'TRANSFERENCIA_TERCEROS',
];

const STATUS_FILTERS: (DocumentStatus | 'TODOS')[] = [
  'TODOS', 'DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'PUBLISHED', 'ARCHIVED',
];

const STATUS_FILTER_LABEL: Record<string, string> = {
  TODOS:     'Todos',
  ...STATUS_LABEL,
};

const formatDate = (iso: string | null): string => {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });
};

// ── Componente principal ──────────────────────────────────────────────────────

interface EditForm {
  name: string;
  content: string;
  templateId: string;
}

const DocumentosPrivacidadPage = () => {
  const { accessToken, user } = useAuth();
  const isDpo = user?.role === 'DPO';

  // ── Data ──────────────────────────────────────────────────────────────────
  const [docs, setDocs]             = useState<PrivacyDocumentDto[]>([]);
  const [loading, setLoading]       = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  // ── Filtros ───────────────────────────────────────────────────────────────
  const [statusFilter, setStatusFilter] = useState<DocumentStatus | 'TODOS'>('TODOS');

  // ── Crear ─────────────────────────────────────────────────────────────────
  const [showCreate, setShowCreate]     = useState(false);
  const [createForm, setCreateForm]     = useState<{ name: string; category: DocumentCategory }>({
    name: '', category: 'POLITICA_PRIVACIDAD',
  });
  const [creating, setCreating]         = useState(false);
  const [createError, setCreateError]   = useState<string | null>(null);

  // ── Editar ────────────────────────────────────────────────────────────────
  const [editingDoc, setEditingDoc]     = useState<PrivacyDocumentDto | null>(null);
  const [editForm, setEditForm]         = useState<EditForm>({ name: '', content: '', templateId: '' });
  const [saving, setSaving]             = useState(false);
  const [saveError, setSaveError]       = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess]   = useState(false);

  // ── Rechazar ──────────────────────────────────────────────────────────────
  const [rejectingDoc, setRejectingDoc] = useState<PrivacyDocumentDto | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [rejecting, setRejecting]       = useState(false);
  const [rejectError, setRejectError]   = useState<string | null>(null);

  // ── Acción en curso ───────────────────────────────────────────────────────
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionError, setActionError]     = useState<{ id: string; msg: string } | null>(null);

  // ── Plantillas (drawer) ───────────────────────────────────────────────────
  const [allTemplates, setAllTemplates]         = useState<TemplateResponse[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);

  // ── Finalidades (drawer) ──────────────────────────────────────────────────
  const [allPurposes, setAllPurposes]             = useState<PurposeResponse[]>([]);
  const [loadingPurposes, setLoadingPurposes]     = useState(false);
  const [selectedPurposeId, setSelectedPurposeId] = useState('');
  const [purposeOpLoading, setPurposeOpLoading]   = useState<string | null>(null);
  const [purposeError, setPurposeError]           = useState<string | null>(null);
  const [purposeSuccess, setPurposeSuccess]       = useState<string | null>(null);

  // ── Fetch ─────────────────────────────────────────────────────────────────
  const fetchDocs = useCallback(async () => {
    setLoading(true);
    setFetchError(null);
    try {
      const result = await listDocuments(null, null, accessToken);
      setDocs(result);
    } catch (err) {
      setFetchError(err instanceof ApiError ? err.message : 'Error al cargar los documentos');
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => { fetchDocs(); }, [fetchDocs]);

  // ── Filtrado local ────────────────────────────────────────────────────────
  const filteredDocs = statusFilter === 'TODOS'
    ? docs
    : docs.filter((d) => d.status === statusFilter);

  const countsByStatus = STATUS_FILTERS.reduce<Record<string, number>>((acc, s) => {
    acc[s] = s === 'TODOS' ? docs.length : docs.filter((d) => d.status === s).length;
    return acc;
  }, {});

  // ── Crear documento ───────────────────────────────────────────────────────
  const handleCreate = async () => {
    if (!createForm.name.trim()) return;
    setCreating(true);
    setCreateError(null);
    try {
      const payload: CreateDocumentPayload = {
        category: createForm.category,
        name: createForm.name.trim(),
      };
      const created = await createDocument(payload, accessToken);
      setDocs((prev) => [created, ...prev]);
      setShowCreate(false);
      setCreateForm({ name: '', category: 'POLITICA_PRIVACIDAD' });
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'No se pudo crear el documento');
    } finally {
      setCreating(false);
    }
  };

  // ── Abrir editor ─────────────────────────────────────────────────────────
  const openEdit = (doc: PrivacyDocumentDto) => {
    setEditingDoc(doc);
    setEditForm({
      name: doc.name,
      content: doc.content ?? '',
      templateId: doc.templateId ?? '',
    });
    setSaveError(null);
    setSaveSuccess(false);
    setPurposeError(null);
    setPurposeSuccess(null);
    setSelectedPurposeId('');
    setAllTemplates([]);
    setAllPurposes([]);

    setLoadingTemplates(true);
    getTemplates(undefined, accessToken)
      .then((tpls) => setAllTemplates(tpls))
      .catch(() => {})
      .finally(() => setLoadingTemplates(false));

    setLoadingPurposes(true);
    getPurposes(accessToken)
      .then((purps) => setAllPurposes(purps))
      .catch(() => {})
      .finally(() => setLoadingPurposes(false));
  };

  // ── Guardar edición ───────────────────────────────────────────────────────
  const handleSave = async () => {
    if (!editingDoc || !editForm.name.trim()) return;
    setSaving(true);
    setSaveError(null);
    setSaveSuccess(false);
    try {
      const updated = await updateDocument(editingDoc.id, {
        name: editForm.name.trim(),
        content: editForm.content || undefined,
        templateId: editForm.templateId || undefined,
      }, accessToken);
      setDocs((prev) => prev.map((d) => (d.id === updated.id ? updated : d)));
      setEditingDoc(updated);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : 'No se pudo guardar');
    } finally {
      setSaving(false);
    }
  };

  // ── Transiciones de estado ────────────────────────────────────────────────
  const runTransition = async (
    doc: PrivacyDocumentDto,
    action: (id: string, token?: string | null) => Promise<PrivacyDocumentDto>,
  ) => {
    setActionLoading(doc.id);
    setActionError(null);
    try {
      const updated = await action(doc.id, accessToken);
      if (updated.id !== doc.id) {
        // Nueva versión: el backend devuelve un documento nuevo (distinto ID)
        setDocs((prev) => [updated, ...prev]);
      } else {
        setDocs((prev) => prev.map((d) => (d.id === updated.id ? updated : d)));
        if (editingDoc?.id === doc.id) setEditingDoc(updated);
      }
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Error al ejecutar la acción';
      setActionError({ id: doc.id, msg });
    } finally {
      setActionLoading(null);
    }
  };

  // ── Gestión de finalidades ────────────────────────────────────────────────
  const handleAddPurpose = async () => {
    if (!editingDoc || !selectedPurposeId) return;
    setPurposeOpLoading(`add-${selectedPurposeId}`);
    setPurposeError(null);
    setPurposeSuccess(null);
    try {
      await addDocumentPurpose(editingDoc.id, selectedPurposeId, accessToken);
      const newIds = [...editingDoc.purposeIds, selectedPurposeId];
      const updatedDoc = { ...editingDoc, purposeIds: newIds };
      setEditingDoc(updatedDoc);
      setDocs((prev) => prev.map((d) => (d.id === editingDoc.id ? updatedDoc : d)));
      setSelectedPurposeId('');
      setPurposeSuccess('Finalidad agregada correctamente');
      setTimeout(() => setPurposeSuccess(null), 3000);
    } catch (err) {
      setPurposeError(err instanceof ApiError ? err.message : 'No se pudo agregar la finalidad');
    } finally {
      setPurposeOpLoading(null);
    }
  };

  const handleRemovePurpose = async (purposeId: string) => {
    if (!editingDoc) return;
    setPurposeOpLoading(`remove-${purposeId}`);
    setPurposeError(null);
    setPurposeSuccess(null);
    try {
      await removeDocumentPurpose(editingDoc.id, purposeId, accessToken);
      const newIds = editingDoc.purposeIds.filter((id) => id !== purposeId);
      const updatedDoc = { ...editingDoc, purposeIds: newIds };
      setEditingDoc(updatedDoc);
      setDocs((prev) => prev.map((d) => (d.id === editingDoc.id ? updatedDoc : d)));
      setPurposeSuccess('Finalidad eliminada correctamente');
      setTimeout(() => setPurposeSuccess(null), 3000);
    } catch (err) {
      setPurposeError(err instanceof ApiError ? err.message : 'No se pudo eliminar la finalidad');
    } finally {
      setPurposeOpLoading(null);
    }
  };

  // ── Rechazar con motivo ───────────────────────────────────────────────────
  const handleReject = async () => {
    if (!rejectingDoc || !rejectReason.trim()) return;
    setRejecting(true);
    setRejectError(null);
    try {
      const updated = await rejectDocument(rejectingDoc.id, rejectReason.trim(), accessToken);
      setDocs((prev) => prev.map((d) => (d.id === updated.id ? updated : d)));
      if (editingDoc?.id === rejectingDoc.id) setEditingDoc(updated);
      setRejectingDoc(null);
      setRejectReason('');
    } catch (err) {
      setRejectError(err instanceof ApiError ? err.message : 'No se pudo rechazar el documento');
    } finally {
      setRejecting(false);
    }
  };

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className={styles.page}>
      {/* Header */}
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Documentos de Privacidad</h2>
          <p className={styles.subtitle}>
            Repositorio de documentos legales que respaldan las finalidades de tratamiento de datos conforme a la Ley 21.719.
          </p>
        </div>
        {isDpo && (
          <Button variant="primary" onClick={() => { setShowCreate(true); setCreateError(null); }}>
            + Nuevo documento
          </Button>
        )}
      </div>

      {/* Filtros por estado */}
      <div className={styles.filters}>
        {STATUS_FILTERS.map((s) => (
          <button
            key={s}
            className={[styles.filterBtn, statusFilter === s ? styles.filterActive : ''].join(' ')}
            onClick={() => setStatusFilter(s)}
          >
            {STATUS_FILTER_LABEL[s]}
            <span className={styles.filterCount}>{countsByStatus[s] ?? 0}</span>
          </button>
        ))}
      </div>

      {/* Loading / error */}
      {loading && <p className={styles.loadingMsg}>Cargando documentos…</p>}
      {fetchError && <p className={styles.errorMsg}>{fetchError}</p>}

      {/* Grid de cards */}
      {!loading && !fetchError && (
        filteredDocs.length === 0 ? (
          <div className={styles.empty}>
            <p>No hay documentos{statusFilter !== 'TODOS' ? ` en estado ${STATUS_LABEL[statusFilter as DocumentStatus]}` : ''}.</p>
          </div>
        ) : (
          <div className={styles.grid}>
            {filteredDocs.map((doc) => {
              const busy = actionLoading === doc.id;
              const err  = actionError?.id === doc.id ? actionError.msg : null;
              return (
                <div key={doc.id} className={styles.card}>
                  {/* Top */}
                  <div className={styles.cardTop}>
                    <span className={[styles.badge, styles[`badge_${doc.status}`]].join(' ')}>
                      {STATUS_LABEL[doc.status]}
                    </span>
                    <span className={styles.version}>v{doc.version}</span>
                    <span className={styles.categoryTag}>
                      {CATEGORY_LABEL[doc.category]}
                    </span>
                  </div>

                  {/* Nombre */}
                  <h3 className={styles.cardTitle}>{doc.name}</h3>

                  {/* Motivo de rechazo */}
                  {doc.status === 'REJECTED' && doc.rejectionReason && (
                    <p className={styles.rejectionNote}>
                      Motivo: {doc.rejectionReason}
                    </p>
                  )}

                  {/* Finalidades vinculadas */}
                  <p className={styles.purposeCount}>
                    {doc.purposeIds.length === 0
                      ? 'Sin finalidades vinculadas'
                      : `${doc.purposeIds.length} finalidad${doc.purposeIds.length !== 1 ? 'es' : ''} vinculada${doc.purposeIds.length !== 1 ? 's' : ''}`}
                  </p>

                  {/* Error de acción */}
                  {err && <p className={styles.actionError}>{err}</p>}

                  {/* Footer */}
                  <div className={styles.cardFooter}>
                    <span className={styles.dateInfo}>
                      Creado {formatDate(doc.createdAt)}
                      {doc.publishAt && ` · Publicado ${formatDate(doc.publishAt)}`}
                    </span>
                  </div>

                  {/* Acciones */}
                  <div className={styles.cardActions}>
                    {/* Editar (DPO, solo DRAFT) */}
                    {isDpo && doc.status === 'DRAFT' && (
                      <button className={styles.actionBtn} onClick={() => openEdit(doc)}>
                        Editar
                      </button>
                    )}
                    {/* Enviar a revisión (DPO, DRAFT) */}
                    {isDpo && doc.status === 'DRAFT' && (
                      <button
                        className={styles.actionBtn}
                        disabled={busy}
                        onClick={() => runTransition(doc, submitDocument)}
                      >
                        {busy ? '…' : 'Enviar a revisión'}
                      </button>
                    )}
                    {/* Reenviar (DPO, REJECTED) */}
                    {isDpo && doc.status === 'REJECTED' && (
                      <button
                        className={styles.actionBtn}
                        disabled={busy}
                        onClick={() => runTransition(doc, resubmitDocument)}
                      >
                        {busy ? '…' : 'Reenviar a revisión'}
                      </button>
                    )}
                    {/* Aprobar / Rechazar (DPO, IN_REVIEW) */}
                    {isDpo && doc.status === 'IN_REVIEW' && (
                      <>
                        <button
                          className={styles.actionBtnSuccess}
                          disabled={busy}
                          onClick={() => runTransition(doc, approveDocument)}
                        >
                          {busy ? '…' : 'Aprobar'}
                        </button>
                        <button
                          className={styles.actionBtnDanger}
                          disabled={busy}
                          onClick={() => { setRejectingDoc(doc); setRejectReason(''); setRejectError(null); }}
                        >
                          Rechazar
                        </button>
                      </>
                    )}
                    {/* Publicar (DPO, APPROVED) */}
                    {isDpo && doc.status === 'APPROVED' && (
                      <button
                        className={styles.actionBtnSuccess}
                        disabled={busy}
                        onClick={() => runTransition(doc, publishDocument)}
                      >
                        {busy ? '…' : 'Publicar'}
                      </button>
                    )}
                    {/* Descargar PDF (PUBLISHED / ARCHIVED) */}
                    {doc.hasPdf && (
                      <a
                        href={`${import.meta.env.VITE_API_URL ?? ''}/api/privacy-documents/${doc.id}/pdf`}
                        className={styles.actionBtn}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Descargar PDF
                      </a>
                    )}
                    {/* Archivar (DPO, PUBLISHED) */}
                    {isDpo && doc.status === 'PUBLISHED' && (
                      <button
                        className={styles.actionBtnMuted}
                        disabled={busy}
                        onClick={() => runTransition(doc, archiveDocument)}
                      >
                        {busy ? '…' : 'Archivar'}
                      </button>
                    )}
                    {/* Nueva versión (DPO, PUBLISHED) */}
                    {isDpo && doc.status === 'PUBLISHED' && (
                      <button
                        className={styles.actionBtn}
                        disabled={busy}
                        onClick={() => runTransition(doc, newDocumentVersion)}
                      >
                        {busy ? '…' : 'Nueva versión'}
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )
      )}

      {/* Modal: Crear documento */}
      <Modal open={showCreate} onClose={() => setShowCreate(false)} variant="center">
        <div className={styles.modal}>
          <div className={styles.modalHeader}>
            <h3 className={styles.modalTitle}>Nuevo documento de privacidad</h3>
            <button className={styles.closeBtn} onClick={() => setShowCreate(false)}>×</button>
          </div>
          <div className={styles.fields}>
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel}>Categoría</label>
              <select
                className={styles.select}
                value={createForm.category}
                onChange={(e) => setCreateForm((f) => ({ ...f, category: e.target.value as DocumentCategory }))}
              >
                {CATEGORY_OPTIONS.map((c) => (
                  <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>
                ))}
              </select>
            </div>
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel}>Nombre del documento</label>
              <input
                className={styles.input}
                type="text"
                placeholder="Ej: Política de Privacidad — Marketing Digital 2026"
                value={createForm.name}
                onChange={(e) => setCreateForm((f) => ({ ...f, name: e.target.value }))}
              />
            </div>
            {createError && <p className={styles.errorMsg}>{createError}</p>}
          </div>
          <div className={styles.modalFooter}>
            <Button variant="secondary" onClick={() => setShowCreate(false)}>Cancelar</Button>
            <Button variant="primary" onClick={handleCreate} disabled={creating || !createForm.name.trim()}>
              {creating ? 'Creando…' : 'Crear borrador'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Drawer: Editar documento (solo DRAFT) */}
      <Modal open={!!editingDoc} onClose={() => setEditingDoc(null)} variant="drawer">
        {editingDoc && (
          <div className={styles.drawer}>
            <div className={styles.drawerHeader}>
              <div>
                <p className={styles.drawerCategory}>{CATEGORY_LABEL[editingDoc.category]}</p>
                <h3 className={styles.drawerTitle}>{editingDoc.name}</h3>
              </div>
              <button className={styles.closeBtn} onClick={() => setEditingDoc(null)}>×</button>
            </div>
            <div className={styles.drawerBody}>
              {/* Nombre */}
              <div className={styles.drawerSection}>
                <p className={styles.sectionTitle}>Información básica</p>
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel}>Nombre</label>
                  <input
                    className={styles.input}
                    type="text"
                    value={editForm.name}
                    onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))}
                  />
                </div>
                <div className={[styles.fieldGroup, styles.fieldGroupMt].join(' ')}>
                  <label className={styles.fieldLabel}>Plantilla</label>
                  {loadingTemplates ? (
                    <p className={styles.noteText}>Cargando plantillas…</p>
                  ) : (
                    <select
                      className={styles.select}
                      value={editForm.templateId}
                      onChange={(e) => setEditForm((f) => ({ ...f, templateId: e.target.value }))}
                    >
                      <option value="">— Sin plantilla —</option>
                      {allTemplates.map((tpl) => (
                        <option key={tpl.id} value={tpl.id}>
                          {tpl.name} (v{tpl.version}) — {tpl.status}{tpl.templateKey ? ` · ${tpl.templateKey}` : ''}
                        </option>
                      ))}
                    </select>
                  )}
                </div>
              </div>
              {/* Contenido */}
              <div className={styles.drawerSection}>
                <p className={styles.sectionTitle}>Contenido legal</p>
                <textarea
                  className={styles.textarea}
                  rows={10}
                  placeholder="Redacta aquí el texto legal del documento. Requerido antes de enviar a revisión."
                  value={editForm.content}
                  onChange={(e) => setEditForm((f) => ({ ...f, content: e.target.value }))}
                />
              </div>
              {/* Finalidades */}
              <div className={styles.drawerSection}>
                <p className={styles.sectionTitle}>Finalidades vinculadas</p>
                {loadingPurposes ? (
                  <p className={styles.noteText}>Cargando finalidades…</p>
                ) : (
                  <>
                    {editingDoc.purposeIds.length === 0 ? (
                      <p className={styles.noteText}>Sin finalidades vinculadas.</p>
                    ) : (
                      <ul className={styles.purposeList}>
                        {editingDoc.purposeIds.map((pid) => {
                          const p = allPurposes.find((ap) => ap.id === pid);
                          const busy = purposeOpLoading === `remove-${pid}`;
                          return (
                            <li key={pid} className={styles.purposeRow}>
                              <span className={styles.purposeName}>
                                {p ? `${p.name} (${p.code})` : pid}
                              </span>
                              <button
                                className={styles.purposeRemoveBtn}
                                disabled={!!purposeOpLoading}
                                onClick={() => handleRemovePurpose(pid)}
                                title="Quitar finalidad"
                              >
                                {busy ? '…' : '×'}
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                    {allPurposes.filter((p) => p.isActive && !editingDoc.purposeIds.includes(p.id)).length > 0 && (
                      <div className={styles.addPurposeRow}>
                        <div style={{ flex: 1 }}>
                          <select
                            className={styles.select}
                            value={selectedPurposeId}
                            onChange={(e) => setSelectedPurposeId(e.target.value)}
                            disabled={!!purposeOpLoading}
                          >
                            <option value="">— Seleccionar finalidad —</option>
                            {allPurposes
                              .filter((p) => p.isActive && !editingDoc.purposeIds.includes(p.id))
                              .map((p) => (
                                <option key={p.id} value={p.id}>
                                  {p.name} ({p.domainName})
                                </option>
                              ))}
                          </select>
                        </div>
                        <button
                          className={styles.actionBtn}
                          disabled={!selectedPurposeId || !!purposeOpLoading}
                          onClick={handleAddPurpose}
                        >
                          {purposeOpLoading?.startsWith('add-') ? '…' : 'Agregar'}
                        </button>
                      </div>
                    )}
                    {purposeError && (
                      <p className={[styles.errorMsg, styles.drawerMsg].join(' ')}>{purposeError}</p>
                    )}
                    {purposeSuccess && (
                      <p className={[styles.successBanner, styles.drawerMsg].join(' ')}>{purposeSuccess}</p>
                    )}
                  </>
                )}
              </div>
              {saveError && <p className={[styles.errorMsg, styles.drawerMsg].join(' ')}>{saveError}</p>}
              {saveSuccess && <p className={[styles.successBanner, styles.drawerMsg].join(' ')}>✓ Cambios guardados</p>}
            </div>
            <div className={styles.drawerFooter}>
              <Button variant="secondary" onClick={() => setEditingDoc(null)}>Cancelar</Button>
              <Button variant="primary" onClick={handleSave} disabled={saving || !editForm.name.trim()}>
                {saving ? 'Guardando…' : 'Guardar cambios'}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* Modal: Rechazar con motivo */}
      <Modal open={!!rejectingDoc} onClose={() => setRejectingDoc(null)} variant="center">
        <div className={styles.modal}>
          <div className={styles.modalHeader}>
            <h3 className={styles.modalTitle}>Rechazar documento</h3>
            <button className={styles.closeBtn} onClick={() => setRejectingDoc(null)}>×</button>
          </div>
          <p className={styles.modalHint}>
            Indica el motivo del rechazo. El DPO podrá corregirlo y reenviar a revisión.
          </p>
          <div className={styles.fields}>
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel}>Motivo de rechazo</label>
              <textarea
                className={[styles.input, styles.textareaSmall].join(' ')}
                rows={4}
                placeholder="Describe qué debe corregirse…"
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
              />
            </div>
            {rejectError && <p className={styles.errorMsg}>{rejectError}</p>}
          </div>
          <div className={styles.modalFooter}>
            <Button variant="secondary" onClick={() => setRejectingDoc(null)}>Cancelar</Button>
            <Button variant="danger" onClick={handleReject} disabled={rejecting || !rejectReason.trim()}>
              {rejecting ? 'Rechazando…' : 'Rechazar documento'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default DocumentosPrivacidadPage;
