import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import Button from '../components/common/Button';
import {
  getTemplates,
  approveTemplate,
  activateTemplate,
  newTemplateVersion,
  ApiError,
  type TemplateResponse,
  type TemplateStatus,
} from '../api/templatesApi';
import styles from './TemplatesPage.module.css';

type FilterValue = TemplateStatus | 'todas';

const FILTER_LABEL: Record<FilterValue, string> = {
  todas:    'Todas',
  ACTIVE:   'Activas',
  APPROVED: 'Aprobadas',
  DRAFT:    'Borradores',
};

const STATUS_LABEL: Record<TemplateStatus, string> = {
  ACTIVE:   'Activa',
  APPROVED: 'Aprobada',
  DRAFT:    'Borrador',
};

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });

interface CardMessage { ok: boolean; text: string }

const TemplateCard = ({
  tpl,
  isProcessing,
  message,
  onEdit,
  onVersions,
  onPreview,
  onApprove,
  onActivate,
  onNewVersion,
}: {
  tpl: TemplateResponse;
  isProcessing: boolean;
  message?: CardMessage;
  onEdit: () => void;
  onVersions: () => void;
  onPreview: () => void;
  onApprove: () => void;
  onActivate: () => void;
  onNewVersion: () => void;
}) => (
  <div className={[styles.card, isProcessing ? styles.cardBusy : ''].join(' ')}>
    <div className={styles.cardTop}>
      <div className={styles.cardMeta}>
        <span className={[styles.badge, styles[`badge_${tpl.status.toLowerCase()}`]].join(' ')}>
          {STATUS_LABEL[tpl.status]}
        </span>
        <span className={styles.cardKey}>{tpl.templateKey}</span>
        <span className={styles.cardVersion}>v{tpl.version}</span>
      </div>
    </div>

    <h3 className={styles.cardName}>{tpl.name}</h3>
    {tpl.description && <p className={styles.cardDesc}>{tpl.description}</p>}

    {/* Acciones de flujo de trabajo */}
    <div className={styles.workflowRow}>
      {message && (
        <span className={message.ok ? styles.successMsg : styles.workflowError}>
          {message.text}
        </span>
      )}
      <div className={styles.workflowActions}>
        {tpl.status === 'DRAFT' && (
          <Button variant="secondary" size="sm" onClick={onApprove} disabled={isProcessing}>
            {isProcessing ? 'Aprobando…' : 'Aprobar'}
          </Button>
        )}
        {tpl.status === 'APPROVED' && (
          <>
            <Button variant="primary" size="sm" onClick={onActivate} disabled={isProcessing}>
              {isProcessing ? 'Activando…' : 'Activar'}
            </Button>
            <Button variant="ghost" size="sm" onClick={onNewVersion} disabled={isProcessing}>
              Nueva versión
            </Button>
          </>
        )}
        {tpl.status === 'ACTIVE' && (
          <Button variant="ghost" size="sm" onClick={onNewVersion} disabled={isProcessing}>
            {isProcessing ? 'Creando…' : 'Nueva versión'}
          </Button>
        )}
      </div>
    </div>

    <div className={styles.cardFooter}>
      <span className={styles.cardCreated}>
        Creada el {formatDate(tpl.createdAt)}
        {tpl.activationDate && ` · Activada el ${formatDate(tpl.activationDate)}`}
      </span>
      <div className={styles.cardActions}>
        <Button variant="ghost" size="sm" onClick={onEdit}>Editar</Button>
        <Button variant="ghost" size="sm" onClick={onVersions}>Versiones</Button>
        <Button variant="secondary" size="sm" onClick={onPreview}>Vista previa</Button>
      </div>
    </div>
  </div>
);

const FILTERS: FilterValue[] = ['todas', 'ACTIVE', 'APPROVED', 'DRAFT'];

const TemplatesPage = () => {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const isMounted = useRef(true);

  useEffect(() => () => { isMounted.current = false; }, []);

  const [templates, setTemplates] = useState<TemplateResponse[]>([]);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState<string | null>(null);
  const [filter, setFilter]       = useState<FilterValue>('todas');
  const [search, setSearch]       = useState('');

  const [processingId, setProcessingId] = useState<string | null>(null);
  const [messages, setMessages]         = useState<Record<string, CardMessage>>({});

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getTemplates(undefined, accessToken)
      .then((data) => { if (!cancelled) setTemplates(data); })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof ApiError ? err.message : 'No se pudieron cargar las plantillas');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  const reloadTemplates = async () => {
    try {
      const data = await getTemplates(undefined, accessToken);
      if (isMounted.current) setTemplates(data);
    } catch {
      // Fallo silencioso — la lista mantiene el último estado conocido
    }
  };

  const showMessage = (id: string, ok: boolean, text: string) => {
    if (!isMounted.current) return;
    setMessages((prev) => ({ ...prev, [id]: { ok, text } }));
    setTimeout(() => {
      if (!isMounted.current) return;
      setMessages((prev) => { const n = { ...prev }; delete n[id]; return n; });
    }, 4000);
  };

  const handleApprove = async (id: string) => {
    console.log('[APPROVE] START id=', id, 'processingId=', processingId);
    setProcessingId(id);
    try {
      console.log('[APPROVE] calling approveTemplate...');
      const result = await approveTemplate(id, accessToken);
      console.log('[APPROVE] approveTemplate OK result=', result);
      console.log('[APPROVE] calling reloadTemplates...');
      await reloadTemplates();
      console.log('[APPROVE] reloadTemplates OK');
      showMessage(id, true, 'Plantilla aprobada correctamente.');
    } catch (err) {
      console.log('[APPROVE] CATCH err=', err);
      showMessage(id, false, err instanceof ApiError ? err.message : 'Error al aprobar la plantilla.');
    } finally {
      console.log('[APPROVE] FINALLY — calling setProcessingId(null)');
      setProcessingId(null);
      console.log('[APPROVE] FINALLY — done');
    }
  };

  const handleActivate = async (id: string) => {
    setProcessingId(id);
    try {
      await activateTemplate(id, accessToken);
      await reloadTemplates();
      showMessage(id, true, 'Plantilla activada correctamente.');
    } catch (err) {
      showMessage(id, false, err instanceof ApiError ? err.message : 'Error al activar la plantilla.');
    } finally {
      setProcessingId(null);
    }
  };

  const handleNewVersion = async (id: string) => {
    setProcessingId(id);
    try {
      await newTemplateVersion(id, accessToken);
      await reloadTemplates();
      showMessage(id, true, 'Nueva versión en Borrador creada. Búscala en el listado.');
    } catch (err) {
      showMessage(id, false, err instanceof ApiError ? err.message : 'Error al crear nueva versión.');
    } finally {
      setProcessingId(null);
    }
  };

  const filtered = templates.filter((t) => {
    if (filter !== 'todas' && t.status !== filter) return false;
    if (search.trim()) {
      const q = search.toLowerCase();
      return (
        t.name.toLowerCase().includes(q) ||
        t.templateKey.toLowerCase().includes(q) ||
        (t.description ?? '').toLowerCase().includes(q)
      );
    }
    return true;
  });

  const countOf = (s: TemplateStatus) => templates.filter((t) => t.status === s).length;

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Plantillas</h2>
          <p className={styles.subtitle}>
            Gestiona las plantillas de consentimiento del módulo · Ley 21.719
          </p>
        </div>
        <Button variant="primary" size="sm" onClick={() => navigate('/plantillas/nueva')}>
          + Crear Plantilla
        </Button>
      </div>

      <input
        type="text"
        className={styles.searchInput}
        placeholder="Buscar por nombre, clave o descripción…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />

      <div className={styles.filters}>
        {FILTERS.map((f) => (
          <button
            key={f}
            className={[styles.filterBtn, filter === f ? styles.filterActive : ''].join(' ')}
            onClick={() => setFilter(f)}
          >
            {FILTER_LABEL[f]}
            <span className={styles.filterCount}>
              {f === 'todas' ? templates.length : countOf(f)}
            </span>
          </button>
        ))}
      </div>

      {loading ? (
        <p className={styles.stateMsg}>Cargando plantillas…</p>
      ) : error ? (
        <p className={styles.errorMsg}>{error}</p>
      ) : filtered.length === 0 ? (
        <div className={styles.empty}>
          <p className={styles.emptyText}>
            {templates.length === 0
              ? 'Todavía no hay plantillas registradas. Crea la primera.'
              : 'Ninguna plantilla coincide con los filtros aplicados.'}
          </p>
        </div>
      ) : (
        <div className={styles.list}>
          {filtered.map((tpl) => (
            <TemplateCard
              key={tpl.id}
              tpl={tpl}
              isProcessing={processingId === tpl.id}
              message={messages[tpl.id]}
              onEdit={() => navigate(`/plantillas/${tpl.id}/editar`)}
              onVersions={() => navigate(`/plantillas/${tpl.id}/versiones`)}
              onPreview={() => navigate(`/preview/template/${tpl.id}`)}
              onApprove={() => handleApprove(tpl.id)}
              onActivate={() => handleActivate(tpl.id)}
              onNewVersion={() => handleNewVersion(tpl.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default TemplatesPage;
