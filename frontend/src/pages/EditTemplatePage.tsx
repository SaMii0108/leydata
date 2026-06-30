import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import Button from '../components/common/Button';
import {
  getTemplate,
  getTemplatePurposes,
  addTemplatePurpose,
  removeTemplatePurpose,
  updateTemplatePurpose,
  ApiError,
  type TemplateResponse,
  type TemplatePurposeResponse,
} from '../api/templatesApi';
import { getPurposes, type PurposeResponse } from '../api/purposesApi';
import styles from './EditTemplatePage.module.css';

const STATUS_LABEL: Record<string, string> = {
  DRAFT:    'Borrador',
  APPROVED: 'Aprobada',
  ACTIVE:   'Activa',
};

const EditTemplatePage = () => {
  const { id } = useParams<{ id: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  const [template,    setTemplate]    = useState<TemplateResponse | null>(null);
  const [purposes,    setPurposes]    = useState<TemplatePurposeResponse[]>([]);
  const [allPurposes, setAllPurposes] = useState<PurposeResponse[]>([]);

  const [loading,   setLoading]   = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);
  const [selectedId,  setSelectedId]  = useState('');
  const [adding,      setAdding]      = useState(false);
  const [removing,    setRemoving]    = useState<string | null>(null);
  const [updating,    setUpdating]    = useState<string | null>(null);

  const [localPositions, setLocalPositions] = useState<Record<string, number>>({});

  const syncPositions = (purps: TemplatePurposeResponse[]) =>
    setLocalPositions(Object.fromEntries(purps.map((p) => [p.purposeId, p.orderPosition])));

  useEffect(() => {
    if (!id) return;
    let cancelled = false;

    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const [tpl, purps, allPurps] = await Promise.all([
          getTemplate(id, accessToken),
          getTemplatePurposes(id, accessToken),
          getPurposes(accessToken),
        ]);
        if (cancelled) return;
        setTemplate(tpl);
        setPurposes(purps);
        setAllPurposes(allPurps);
        syncPositions(purps);
      } catch (err) {
        if (!cancelled)
          setLoadError(err instanceof ApiError ? err.message : 'Error al cargar la plantilla.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, [id, accessToken]);

  const reloadPurposes = async () => {
    if (!id) return;
    const purps = await getTemplatePurposes(id, accessToken);
    setPurposes(purps);
    syncPositions(purps);
  };

  const handleAdd = async () => {
    if (!id || !selectedId) return;
    setAdding(true);
    setActionError(null);
    try {
      const nextPos = purposes.length > 0
        ? Math.max(...purposes.map((p) => p.orderPosition)) + 1
        : 1;
      await addTemplatePurpose(id, { purposeId: selectedId, orderPosition: nextPos, isVisible: true }, accessToken);
      setSelectedId('');
      await reloadPurposes();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Error al agregar la finalidad.');
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (purposeId: string) => {
    if (!id) return;
    setRemoving(purposeId);
    setActionError(null);
    try {
      await removeTemplatePurpose(id, purposeId, accessToken);
      await reloadPurposes();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Error al eliminar la finalidad.');
    } finally {
      setRemoving(null);
    }
  };

  const handlePositionBlur = async (purposeId: string) => {
    if (!id) return;
    const current = purposes.find((p) => p.purposeId === purposeId);
    const newPos  = localPositions[purposeId];
    if (!current || isNaN(newPos) || newPos < 1 || newPos === current.orderPosition) {
      setLocalPositions((prev) => ({ ...prev, [purposeId]: current?.orderPosition ?? 1 }));
      return;
    }
    setUpdating(purposeId);
    setActionError(null);
    try {
      await updateTemplatePurpose(id, purposeId, { orderPosition: newPos }, accessToken);
      await reloadPurposes();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Error al actualizar la posición.');
      setLocalPositions((prev) => ({ ...prev, [purposeId]: current.orderPosition }));
    } finally {
      setUpdating(null);
    }
  };

  const handleVisibilityToggle = async (purposeId: string, isVisible: boolean) => {
    if (!id) return;
    setUpdating(purposeId);
    setActionError(null);
    try {
      const updated = await updateTemplatePurpose(id, purposeId, { isVisible }, accessToken);
      setPurposes((prev) =>
        prev.map((p) => p.purposeId === purposeId ? updated : p)
      );
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Error al actualizar la visibilidad.');
    } finally {
      setUpdating(null);
    }
  };

  const addedIds      = new Set(purposes.map((p) => p.purposeId));
  const availableToAdd = allPurposes.filter((p) => !addedIds.has(p.id));
  const sorted        = [...purposes].sort((a, b) => a.orderPosition - b.orderPosition);

  if (loading) {
    return <div className={styles.page}><p className={styles.stateMsg}>Cargando plantilla…</p></div>;
  }

  if (loadError || !template) {
    return (
      <div className={styles.page}>
        <button className={styles.backLink} onClick={() => navigate('/plantillas')}>← Volver a Plantillas</button>
        <p className={styles.loadError}>{loadError ?? 'Plantilla no encontrada.'}</p>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <button className={styles.backLink} onClick={() => navigate('/plantillas')}>
        ← Volver a Plantillas
      </button>

      {/* Cabecera de la plantilla */}
      <div className={styles.pageHeader}>
        <div>
          <div className={styles.templateMeta}>
            <span className={[styles.badge, styles[`badge_${template.status.toLowerCase()}`]].join(' ')}>
              {STATUS_LABEL[template.status] ?? template.status}
            </span>
            <code className={styles.templateKey}>{template.templateKey}</code>
            <span className={styles.templateVersion}>v{template.version}</span>
          </div>
          <h2 className={styles.title}>{template.name}</h2>
          {template.description && <p className={styles.subtitle}>{template.description}</p>}
        </div>
      </div>

      {/* Finalidades asociadas */}
      <section className={styles.section}>
        <div className={styles.sectionHeader}>
          <h3 className={styles.sectionTitle}>Finalidades asociadas</h3>
          <span className={styles.sectionCount}>{purposes.length}</span>
        </div>

        <div className={styles.sectionBody}>
          {actionError && <p className={styles.actionError}>{actionError}</p>}

          {sorted.length === 0 ? (
            <div className={styles.emptyPurposes}>
              <p>Esta plantilla no tiene finalidades asociadas.</p>
              <p className={styles.emptyHint}>
                Para poder aprobarla necesita al menos una finalidad visible.
              </p>
            </div>
          ) : (
            <div className={styles.purposeList}>
              {sorted.map((p) => {
                const isBusy = removing === p.purposeId || updating === p.purposeId;
                return (
                  <div
                    key={p.purposeId}
                    className={[styles.purposeRow, isBusy ? styles.purposeRowBusy : ''].join(' ')}
                  >
                    <div className={styles.positionCell}>
                      <span className={styles.cellLabel}>Orden</span>
                      <input
                        type="number"
                        className={styles.positionInput}
                        min={1}
                        value={localPositions[p.purposeId] ?? p.orderPosition}
                        onChange={(e) =>
                          setLocalPositions((prev) => ({
                            ...prev,
                            [p.purposeId]: parseInt(e.target.value, 10),
                          }))
                        }
                        onBlur={() => handlePositionBlur(p.purposeId)}
                        disabled={isBusy}
                      />
                    </div>

                    <div className={styles.visibilityCell}>
                      <span className={styles.cellLabel}>Visible</span>
                      <input
                        type="checkbox"
                        className={styles.visibleCheck}
                        checked={p.isVisible}
                        onChange={(e) => handleVisibilityToggle(p.purposeId, e.target.checked)}
                        disabled={isBusy}
                      />
                    </div>

                    <span className={styles.purposeName}>{p.purposeName}</span>

                    <button
                      className={styles.removeBtn}
                      onClick={() => handleRemove(p.purposeId)}
                      disabled={isBusy}
                      title="Eliminar finalidad"
                    >
                      {removing === p.purposeId ? '…' : '✕'}
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          {/* Agregar finalidad */}
          <div className={styles.addRow}>
            <select
              className={styles.purposeSelect}
              value={selectedId}
              onChange={(e) => { setSelectedId(e.target.value); setActionError(null); }}
              disabled={adding || availableToAdd.length === 0}
            >
              <option value="">
                {availableToAdd.length === 0
                  ? 'Todas las finalidades activas ya están asociadas'
                  : '— Selecciona una finalidad para agregar —'}
              </option>
              {availableToAdd.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}{p.domainName ? ` · ${p.domainName}` : ''}
                </option>
              ))}
            </select>
            <Button
              variant="primary"
              size="sm"
              onClick={handleAdd}
              disabled={!selectedId || adding}
            >
              {adding ? 'Agregando…' : 'Agregar'}
            </Button>
          </div>
        </div>
      </section>
    </div>
  );
};

export default EditTemplatePage;
