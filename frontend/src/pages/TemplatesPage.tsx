import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ConsentPreview from '../components/common/ConsentPreview';
import type { TemplateConfig } from '../components/common/ConsentPreview';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import { consentRecords } from '../utils/mockData';
import type { ConsentStatus, ConsentRecord } from '../utils/mockData';
import { AREAS } from '../features/domains/mockDomains';
import { formatDate } from '../utils/formatters';
import {
  getAllTemplates,
  getTemplateById,
  getTemplateByConsentId,
  addTemplate,
  addTemplateVersion,
} from '../features/templates/mockTemplates';
import type { TemplateRecord, TemplateVersion } from '../features/templates/mockTemplates';
import styles from './TemplatesPage.module.css';

type Tab = 'design' | 'config';

const DOMAINS            = ['Marketing', 'Publicidad', 'Análisis', 'Funcional'];
const REQUIRED_FIELDS    = ['Nombre', 'Teléfono', 'Email', 'RUT', 'Dirección'];
const BUTTON_SIZES       = [{ value: 'sm', label: 'Pequeño' }, { value: 'md', label: 'Mediano' }, { value: 'lg', label: 'Grande' }] as const;
const BUTTON_RADIUS_OPTS = [{ value: 'none', label: 'Sin borde' }, { value: 'sm', label: 'Redondeado' }, { value: 'full', label: 'Píldora' }] as const;
const ALL_STATUSES: ConsentStatus[] = ['activo', 'revocado', 'pendiente', 'expirado'];

const DEFAULT_CONFIG: TemplateConfig = {
  logo: '',
  primaryColor: '#4361ee',
  titleColor: '#111827',
  subtitleColor: '#6b7280',
  buttonLabel: 'Aceptar',
  buttonSize: 'md',
  buttonRadius: 'sm',
  domain: '',
  purpose: '',
  requiredFields: [],
};

// ─────────────────────────────────────────────────────────────
// Main page
// ─────────────────────────────────────────────────────────────
const TemplatesPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const consentIdParam    = searchParams.get('consentId');
  const templateIdParam   = searchParams.get('templateId');
  const fromTemplateParam = searchParams.get('fromTemplate');
  const consentIdsParam   = searchParams.get('consentIds'); // multi: "C-0001,C-0002"
  const newParam          = searchParams.get('new');        // 'true' = plantilla en blanco

  const multiIds     = consentIdsParam ? consentIdsParam.split(',').filter(Boolean) : [];
  const isBlankMode  = newParam === 'true';
  const isEditorMode = !!(consentIdParam || templateIdParam || consentIdsParam || isBlankMode);

  // ── Editor state ──────────────────────────────────────────
  const [activeTab, setActiveTab]       = useState<Tab>('design');
  const [config, setConfig]             = useState<TemplateConfig>(DEFAULT_CONFIG);
  const [templateName, setTemplateName] = useState('');
  const [savedOk, setSavedOk]           = useState(false);

  // ── List state ────────────────────────────────────────────
  const [templates, setTemplates] = useState<TemplateRecord[]>(() => getAllTemplates());
  const [baseModal, setBaseModal] = useState<{ open: boolean; sourceId: string }>({ open: false, sourceId: '' });

  // Re-sync list when returning from editor
  useEffect(() => {
    if (!isEditorMode) {
      setTemplates([...getAllTemplates()]);
      setSavedOk(false);
    }
  }, [isEditorMode]);

  // Init editor state whenever URL params change
  useEffect(() => {
    if (!isEditorMode) return;

    // Plantilla en blanco
    if (isBlankMode) {
      setConfig({ ...DEFAULT_CONFIG });
      setTemplateName('Nueva plantilla');
      setActiveTab('design');
      return;
    }

    // Multi-consentimiento
    if (consentIdsParam && multiIds.length > 0) {
      const consents = multiIds
        .map((id) => consentRecords.find((c) => c.id === id))
        .filter(Boolean) as ConsentRecord[];
      const areas      = [...new Set(consents.map((c) => c.area))];
      const finalidades = [...new Set(consents.map((c) => c.finalidad))];
      setConfig({
        ...DEFAULT_CONFIG,
        domain:  areas.length === 1 ? areas[0] : '',
        purpose: finalidades.join('; '),
      });
      setTemplateName(`Plantilla múltiple — ${multiIds.length} consentimientos`);
      setActiveTab('config');
      return;
    }

    if (templateIdParam) {
      const tmpl = getTemplateById(templateIdParam);
      if (!tmpl) return;
      const activeVer =
        tmpl.versions.find((v) => v.status === 'activa') ??
        tmpl.versions[tmpl.versions.length - 1];
      setConfig({ ...activeVer.config });
      setTemplateName(tmpl.name);
      setActiveTab('design');
      return;
    }

    if (consentIdParam) {
      const existingForConsent = getTemplateByConsentId(consentIdParam);
      if (existingForConsent && !fromTemplateParam) {
        navigate(`/plantillas?templateId=${existingForConsent.id}`, { replace: true });
        return;
      }
      if (fromTemplateParam) {
        const srcTmpl = getTemplateById(fromTemplateParam);
        if (srcTmpl) {
          const srcVer =
            srcTmpl.versions.find((v) => v.status === 'activa') ??
            srcTmpl.versions[srcTmpl.versions.length - 1];
          setConfig({ ...srcVer.config });
          setTemplateName(`${srcTmpl.name} (adaptada)`);
          setActiveTab('design');
          return;
        }
      }
      const consent = consentRecords.find((c) => c.id === consentIdParam);
      setConfig({
        ...DEFAULT_CONFIG,
        domain: consent?.area ?? '',
        purpose: consent?.finalidad ?? '',
      });
      setTemplateName(
        consent ? `Plantilla ${consent.area} — ${consent.finalidad}` : 'Nueva plantilla',
      );
      setActiveTab('design');
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [consentIdParam, templateIdParam, fromTemplateParam, consentIdsParam, newParam]);

  // ── Helpers ───────────────────────────────────────────────
  const update = <K extends keyof TemplateConfig>(key: K, value: TemplateConfig[K]) =>
    setConfig((prev) => ({ ...prev, [key]: value }));

  const toggleField = (field: string) =>
    setConfig((prev) => ({
      ...prev,
      requiredFields: prev.requiredFields.includes(field)
        ? prev.requiredFields.filter((f) => f !== field)
        : [...prev.requiredFields, field],
    }));

  const handleReset = () => {
    if (templateIdParam) {
      const tmpl = getTemplateById(templateIdParam);
      if (tmpl) {
        const activeVer =
          tmpl.versions.find((v) => v.status === 'activa') ??
          tmpl.versions[tmpl.versions.length - 1];
        setConfig({ ...activeVer.config });
      }
    } else {
      setConfig(DEFAULT_CONFIG);
    }
  };

  const handleSave = () => {
    const now = new Date().toISOString();

    if (isBlankMode) {
      addTemplate({
        id: 'tmpl-' + String(Date.now()).slice(-6),
        name: templateName.trim() || 'Nueva plantilla',
        consentId: 'LIBRE',
        consentFinalidad: '',
        consentArea: '',
        createdAt: now,
        updatedAt: now,
        versions: [{ version: 1, status: 'activa', config: { ...config }, savedAt: now }],
      });
    } else if (consentIdsParam && multiIds.length > 0) {
      const consents = multiIds
        .map((id) => consentRecords.find((c) => c.id === id))
        .filter(Boolean) as ConsentRecord[];
      const areas      = [...new Set(consents.map((c) => c.area))];
      const finalidades = [...new Set(consents.map((c) => c.finalidad))];
      addTemplate({
        id: 'tmpl-' + String(Date.now()).slice(-6),
        name: templateName.trim() || `Plantilla múltiple — ${multiIds.length} consentimientos`,
        consentId: 'MULTI',
        consentIds: multiIds,
        consentFinalidad: finalidades.join(', '),
        consentArea: areas.join(', '),
        createdAt: now,
        updatedAt: now,
        versions: [{ version: 1, status: 'activa', config: { ...config }, savedAt: now }],
      });
    } else if (templateIdParam) {
      addTemplateVersion(templateIdParam, config);
    } else if (consentIdParam) {
      const existingForConsent = getTemplateByConsentId(consentIdParam);
      if (existingForConsent) {
        addTemplateVersion(existingForConsent.id, config);
      } else {
        const consent = consentRecords.find((c) => c.id === consentIdParam);
        addTemplate({
          id: 'tmpl-' + String(Date.now()).slice(-6),
          name: templateName.trim() || 'Nueva plantilla',
          consentId: consentIdParam,
          consentFinalidad: consent?.finalidad ?? '',
          consentArea: consent?.area ?? '',
          createdAt: now,
          updatedAt: now,
          versions: [{ version: 1, status: 'activa', config: { ...config }, savedAt: now }],
        });
      }
    }
    setSavedOk(true);
    setTimeout(() => navigate('/plantillas'), 1600);
  };

  const consentsWithoutTemplate = consentRecords.filter(
    (c) => !getTemplateByConsentId(c.id),
  );

  // ── Editor mode ───────────────────────────────────────────
  if (isEditorMode) {
    const isMultiMode = multiIds.length > 1;

    const existingTemplate = !isMultiMode && !isBlankMode && templateIdParam
      ? getTemplateById(templateIdParam)
      : !isMultiMode && !isBlankMode && consentIdParam
        ? getTemplateByConsentId(consentIdParam)
        : null;

    const contextConsent = !isMultiMode && !isBlankMode && consentIdParam
      ? consentRecords.find((c) => c.id === consentIdParam)
      : null;

    const contextConsentId = existingTemplate?.consentId ?? consentIdParam ?? '';
    const contextArea      = existingTemplate?.consentArea ?? contextConsent?.area ?? '';
    const contextFinalidad = existingTemplate?.consentFinalidad ?? contextConsent?.finalidad ?? '';

    const currentVersionNum = existingTemplate
      ? (existingTemplate.versions.find((v) => v.status === 'activa')?.version ??
         existingTemplate.versions.length)
      : 0;

    const editorTitle = isBlankMode
      ? 'Nueva plantilla'
      : isMultiMode
        ? 'Nueva plantilla múltiple'
        : existingTemplate ? 'Editar plantilla' : 'Nueva plantilla';

    const saveLabel = savedOk
      ? '✓ Guardado'
      : existingTemplate ? 'Guardar nueva versión' : 'Crear plantilla';

    return (
      <div className={styles.page}>
        <div className={styles.pageHeader}>
          <div>
            <button className={styles.backBtn} onClick={() => navigate('/plantillas')}>
              ← Volver a plantillas
            </button>
            <h2 className={styles.title}>{editorTitle}</h2>
            <p className={styles.subtitle}>
              {isBlankMode && 'Sin consentimiento asociado'}
              {isMultiMode && `${multiIds.length} consentimientos seleccionados`}
              {!isBlankMode && !isMultiMode && contextConsentId && `${contextConsentId} · ${contextArea}`}
              {!isBlankMode && !isMultiMode && fromTemplateParam && ' · Basada en plantilla existente'}
            </p>
          </div>
          <div className={styles.headerActions}>
            <Button variant="ghost" size="sm" onClick={handleReset}>Restablecer</Button>
            <Button variant="primary" size="sm" onClick={handleSave} disabled={savedOk}>
              {saveLabel}
            </Button>
          </div>
        </div>

        {/* Banner — en blanco */}
        {isBlankMode && (
          <div className={styles.contextBanner}>
            <span className={styles.bannerLibre}>Plantilla independiente</span>
            <span className={styles.bannerHint}>Sin consentimiento asociado · Se creará v1 al guardar</span>
          </div>
        )}

        {/* Banner — multi */}
        {isMultiMode && (
          <div className={styles.contextBanner}>
            <span className={styles.bannerMulti}>{multiIds.length} consentimientos</span>
            {multiIds.map((id) => (
              <span key={id} className={styles.bannerChip}>{id}</span>
            ))}
            <span className={styles.bannerHint}>Se creará v1 al guardar</span>
          </div>
        )}

        {/* Banner — single */}
        {!isBlankMode && !isMultiMode && (
          <div className={styles.contextBanner}>
            {contextConsentId && <span className={styles.bannerChip}>{contextConsentId}</span>}
            {contextArea && <><span className={styles.bannerSep}>·</span><span className={styles.bannerText}>{contextArea}</span></>}
            {contextFinalidad && <><span className={styles.bannerSep}>·</span><span className={styles.bannerText}>{contextFinalidad}</span></>}
            {existingTemplate && currentVersionNum > 0 && (
              <><span className={styles.bannerSep}>·</span>
              <span className={styles.bannerVersion}>v{currentVersionNum} activa</span>
              <span className={styles.bannerHint}>Guardar creará v{currentVersionNum + 1}</span></>
            )}
            {!existingTemplate && <span className={styles.bannerHint}>Se creará v1 al guardar</span>}
          </div>
        )}

        {/* Nombre */}
        <div className={styles.nameRow}>
          <label className={styles.nameLabel} htmlFor="tmpl-name">Nombre de la plantilla</label>
          <input
            id="tmpl-name"
            className={styles.nameInput}
            value={templateName}
            onChange={(e) => setTemplateName(e.target.value)}
            placeholder="Nombre descriptivo de la plantilla"
            maxLength={80}
          />
        </div>

        {/* Editor + preview */}
        <div className={styles.layout}>
          <div className={styles.editor}>
            <div className={styles.tabs}>
              <button
                className={[styles.tab, activeTab === 'design' ? styles.tabActive : ''].join(' ')}
                onClick={() => setActiveTab('design')}
              >
                Diseño Visual
              </button>
              <button
                className={[styles.tab, activeTab === 'config' ? styles.tabActive : ''].join(' ')}
                onClick={() => setActiveTab('config')}
              >
                Configuración
              </button>
            </div>

            {activeTab === 'design' && (
              <div className={styles.tabContent}>
                <Section title="Logo">
                  <label className={styles.logoUpload}>
                    <span className={styles.logoIcon}>↑</span>
                    <span>Subir imagen (PNG, SVG)</span>
                    <input
                      type="file"
                      accept="image/png,image/svg+xml,image/jpeg"
                      className={styles.fileInput}
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (!file) return;
                        const reader = new FileReader();
                        reader.onload = (ev) => update('logo', ev.target?.result as string);
                        reader.readAsDataURL(file);
                      }}
                    />
                  </label>
                  {config.logo && (
                    <button className={styles.removeBtn} onClick={() => update('logo', '')}>
                      Quitar logo
                    </button>
                  )}
                </Section>

                <Section title="Colores">
                  <ColorRow label="Color principal (botón)" value={config.primaryColor}  onChange={(v) => update('primaryColor', v)} />
                  <ColorRow label="Color del título"        value={config.titleColor}    onChange={(v) => update('titleColor', v)} />
                  <ColorRow label="Color del subtítulo"     value={config.subtitleColor} onChange={(v) => update('subtitleColor', v)} />
                </Section>

                <Section title="Botón de aceptación">
                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel}>Texto del botón</label>
                    <input
                      type="text"
                      className={styles.textInput}
                      value={config.buttonLabel}
                      onChange={(e) => update('buttonLabel', e.target.value)}
                      placeholder="Aceptar"
                      maxLength={30}
                    />
                  </div>
                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel}>Tamaño</label>
                    <div className={styles.optionRow}>
                      {BUTTON_SIZES.map((opt) => (
                        <button
                          key={opt.value}
                          className={[styles.optionBtn, config.buttonSize === opt.value ? styles.optionActive : ''].join(' ')}
                          onClick={() => update('buttonSize', opt.value)}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel}>Estilo de borde</label>
                    <div className={styles.optionRow}>
                      {BUTTON_RADIUS_OPTS.map((opt) => (
                        <button
                          key={opt.value}
                          className={[styles.optionBtn, config.buttonRadius === opt.value ? styles.optionActive : ''].join(' ')}
                          onClick={() => update('buttonRadius', opt.value)}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </Section>
              </div>
            )}

            {activeTab === 'config' && (
              <div className={styles.tabContent}>
                <Section title="Dominio del consentimiento">
                  <div className={styles.domainGrid}>
                    {DOMAINS.map((domain) => (
                      <button
                        key={domain}
                        className={[styles.domainBtn, config.domain === domain ? styles.domainActive : ''].join(' ')}
                        onClick={() => update('domain', config.domain === domain ? '' : domain)}
                      >
                        {domain}
                      </button>
                    ))}
                  </div>
                </Section>

                <Section title="Finalidad del consentimiento">
                  <textarea
                    className={styles.textarea}
                    placeholder="Describe la finalidad para la que se solicitará el consentimiento..."
                    rows={4}
                    value={config.purpose}
                    onChange={(e) => update('purpose', e.target.value)}
                    maxLength={400}
                  />
                  <p className={styles.charCount}>{config.purpose.length}/400</p>
                </Section>

                <Section title="Datos requeridos">
                  <p className={styles.sectionHint}>Selecciona los campos que se mostrarán en el formulario.</p>
                  <div className={styles.checkList}>
                    {REQUIRED_FIELDS.map((field) => (
                      <label key={field} className={styles.checkItem}>
                        <input
                          type="checkbox"
                          className={styles.checkbox}
                          checked={config.requiredFields.includes(field)}
                          onChange={() => toggleField(field)}
                        />
                        <span>{field}</span>
                      </label>
                    ))}
                  </div>
                </Section>
              </div>
            )}
          </div>

          <div className={styles.preview}>
            <ConsentPreview config={config} />

            {!isMultiMode && !isBlankMode && existingTemplate && existingTemplate.versions.length > 0 && (
              <div className={styles.versionsPanel}>
                <p className={styles.versionsPanelTitle}>Historial de versiones</p>
                {[...existingTemplate.versions].reverse().map((v) => (
                  <VersionRow key={v.version} v={v} onLoad={() => setConfig({ ...v.config })} />
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  // ── List mode ─────────────────────────────────────────────
  return (
    <div className={styles.page}>
      {/* Header */}
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Plantillas de consentimiento</h2>
          <p className={styles.subtitle}>Gestiona y versiona los formularios de consentimiento</p>
        </div>
        <div className={styles.headerActions}>
          <Button variant="primary" size="sm" onClick={() => navigate('/plantillas?new=true')}>
            + Crear nueva plantilla
          </Button>
        </div>
      </div>

      {/* BLOQUE SUPERIOR: Consentimientos disponibles */}
      <ConsentTableBlock />

      {/* BLOQUE INFERIOR: Plantillas guardadas */}
      <section className={styles.savedSection}>
        <div className={styles.savedHeader}>
          <p className={styles.savedTitle}>Plantillas guardadas</p>
          <span className={styles.savedCount}>
            {templates.length} plantilla{templates.length !== 1 ? 's' : ''}
          </span>
        </div>

        {templates.length === 0 ? (
          <div className={styles.emptyTemplates}>
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" className={styles.emptyIcon}>
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
            </svg>
            <p className={styles.emptyTitle}>No hay plantillas guardadas</p>
            <p className={styles.emptyHint}>
              Haz clic en <strong>+ Plantilla</strong> en la tabla de consentimientos
              o en <strong>+ Crear nueva plantilla</strong> para comenzar.
            </p>
          </div>
        ) : (
          <div className={styles.templateGrid}>
            {templates.map((tmpl) => (
              <TemplateCard
                key={tmpl.id}
                tmpl={tmpl}
                onEdit={() => navigate(`/plantillas?templateId=${tmpl.id}`)}
                onUseAsBase={() => setBaseModal({ open: true, sourceId: tmpl.id })}
              />
            ))}
          </div>
        )}
      </section>

      {/* Modal "Usar como base" */}
      <Modal
        open={baseModal.open}
        onClose={() => setBaseModal({ open: false, sourceId: '' })}
      >
        <div className={styles.baseModal}>
          <p className={styles.baseModalTitle}>Seleccionar consentimiento destino</p>
          <p className={styles.baseModalHint}>
            La plantilla seleccionada se usará como base. La original no se modificará.
            Podrás ajustar los datos antes de guardar.
          </p>

          {consentsWithoutTemplate.length === 0 ? (
            <p className={styles.baseModalEmpty}>
              Todos los consentimientos ya tienen plantilla asignada.
            </p>
          ) : (
            <div className={styles.baseModalList}>
              {consentsWithoutTemplate.map((c) => (
                <button
                  key={c.id}
                  className={styles.baseModalItem}
                  onClick={() => {
                    const srcId = baseModal.sourceId;
                    setBaseModal({ open: false, sourceId: '' });
                    navigate(`/plantillas?consentId=${c.id}&fromTemplate=${srcId}`);
                  }}
                >
                  <span className={styles.baseModalItemId}>{c.id}</span>
                  <span className={styles.baseModalItemInfo}>{c.area} · {c.finalidad}</span>
                </button>
              ))}
            </div>
          )}

          <div className={styles.baseModalFooter}>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setBaseModal({ open: false, sourceId: '' })}
            >
              Cancelar
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// ConsentTableBlock — bloque superior en modo lista
// ─────────────────────────────────────────────────────────────
const ConsentTableBlock = () => {
  const navigate = useNavigate();

  const [search, setSearch]           = useState('');
  const [statusFilter, setStatusFilter] = useState<ConsentStatus | 'todos'>('todos');
  const [areaFilter, setAreaFilter]   = useState('todas');
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return consentRecords.filter((r) => {
      if (statusFilter !== 'todos' && r.estado !== statusFilter) return false;
      if (areaFilter !== 'todas' && r.area !== areaFilter) return false;
      if (
        q &&
        !r.id.toLowerCase().includes(q) &&
        !r.finalidad.toLowerCase().includes(q) &&
        !r.area.toLowerCase().includes(q)
      ) return false;
      return true;
    });
  }, [search, statusFilter, areaFilter]);

  const hasActiveFilters = search || statusFilter !== 'todos' || areaFilter !== 'todas';

  const clearFilters = () => {
    setSearch('');
    setStatusFilter('todos');
    setAreaFilter('todas');
  };

  const toggleSelection = (id: string) =>
    setSelectedIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  const exitSelectionMode = () => {
    setSelectionMode(false);
    setSelectedIds(new Set());
  };

  const handleGenerateForSelected = () => {
    const ids = [...selectedIds];
    exitSelectionMode();
    if (ids.length === 1) {
      const tmpl = getTemplateByConsentId(ids[0]);
      navigate(tmpl
        ? `/plantillas?templateId=${tmpl.id}`
        : `/plantillas?consentId=${ids[0]}`);
    } else {
      navigate(`/plantillas?consentIds=${ids.join(',')}`);
    }
  };

  return (
    <section className={styles.cbSection}>
      {/* Section header */}
      <div className={styles.cbSectionHeader}>
        <p className={styles.cbSectionTitle}>Consentimientos disponibles</p>
        <span className={styles.cbSectionHint}>
          Genera una plantilla directamente desde un consentimiento existente
        </span>
      </div>

      {/* Filter bar */}
      <div className={styles.cbFilterBar}>
        <div className={styles.cbSearchBox}>
          <span className={styles.cbSearchIcon}>⌕</span>
          <input
            type="text"
            className={styles.cbSearchInput}
            placeholder="Buscar por ID, finalidad o área..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {search && (
            <button
              className={styles.cbClearInput}
              onClick={() => setSearch('')}
              aria-label="Limpiar"
            >
              ✕
            </button>
          )}
        </div>

        <div className={styles.cbFilters}>
          <div className={styles.cbFilterGroup}>
            <span className={styles.cbFilterLabel}>Estado</span>
            <div className={styles.cbPills}>
              <button
                className={[styles.cbPill, statusFilter === 'todos' ? styles.cbPillActive : ''].join(' ')}
                onClick={() => setStatusFilter('todos')}
              >
                Todos
              </button>
              {ALL_STATUSES.map((s) => (
                <button
                  key={s}
                  className={[styles.cbPill, statusFilter === s ? styles.cbPillActive : ''].join(' ')}
                  onClick={() => setStatusFilter(s)}
                >
                  {s.charAt(0).toUpperCase() + s.slice(1)}
                </button>
              ))}
            </div>
          </div>

          <div className={styles.cbFilterGroup}>
            <label className={styles.cbFilterLabel} htmlFor="cb-area">Área</label>
            <select
              id="cb-area"
              className={styles.cbSelect}
              value={areaFilter}
              onChange={(e) => setAreaFilter(e.target.value)}
            >
              <option value="todas">Todas</option>
              {AREAS.map((a) => <option key={a} value={a}>{a}</option>)}
            </select>
          </div>

          {hasActiveFilters && (
            <button className={styles.cbClearAll} onClick={clearFilters}>
              Limpiar filtros
            </button>
          )}
        </div>
      </div>

      {/* Table header */}
      <div className={styles.cbTableHeader}>
        {selectionMode ? (
          <>
            <span className={styles.cbSelectionCount}>
              {selectedIds.size > 0
                ? `${selectedIds.size} seleccionado${selectedIds.size !== 1 ? 's' : ''}`
                : 'Selecciona uno o más consentimientos'}
            </span>
            <div className={styles.cbTableActions}>
              <button
                className={[
                  styles.cbGenerateBtn,
                  selectedIds.size === 0 ? styles.cbGenerateBtnDisabled : '',
                ].join(' ')}
                disabled={selectedIds.size === 0}
                onClick={handleGenerateForSelected}
              >
                Generar plantilla para seleccionados
              </button>
              <Button variant="ghost" size="sm" onClick={exitSelectionMode}>
                Cancelar
              </Button>
            </div>
          </>
        ) : (
          <>
            <span className={styles.cbResultCount}>
              {filtered.length} resultado{filtered.length !== 1 ? 's' : ''}
              {hasActiveFilters && ' (filtrado)'}
            </span>
            <div className={styles.cbTableActions}>
              <Button variant="ghost" size="sm" onClick={() => setSelectionMode(true)}>
                Seleccionar
              </Button>
            </div>
          </>
        )}
      </div>

      {/* Table */}
      <div className={styles.cbTableWrapper}>
        {filtered.length === 0 ? (
          <div className={styles.cbEmpty}>
            <p>No se encontraron consentimientos con los filtros aplicados.</p>
            {hasActiveFilters && (
              <button className={styles.cbClearAll} onClick={clearFilters}>
                Limpiar filtros
              </button>
            )}
          </div>
        ) : (
          <table className={styles.cbTable}>
            <thead>
              <tr>
                {selectionMode && <th className={styles.cbCheckboxCol}></th>}
                <th>ID</th>
                <th>Área</th>
                <th>Finalidad</th>
                <th>Estado</th>
                <th>Otorgamiento</th>
                <th>Expiración</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((record) => {
                const existingTemplate = getTemplateByConsentId(record.id);
                const isSelected = selectedIds.has(record.id);
                return (
                  <tr
                    key={record.id}
                    className={isSelected ? styles.cbRowSelected : ''}
                    onClick={selectionMode ? () => toggleSelection(record.id) : undefined}
                    style={selectionMode ? { cursor: 'pointer' } : undefined}
                  >
                    {selectionMode && (
                      <td
                        className={styles.cbCheckboxCol}
                        onClick={(e) => e.stopPropagation()}
                      >
                        <input
                          type="checkbox"
                          className={styles.cbCheckboxInput}
                          checked={isSelected}
                          onChange={() => toggleSelection(record.id)}
                          aria-label={`Seleccionar ${record.id}`}
                        />
                      </td>
                    )}
                    <td className={styles.cbCellId}>{record.id}</td>
                    <td>
                      <span className={styles.cbAreaBadge}>{record.area}</span>
                    </td>
                    <td className={styles.cbCellFinalidad}>{record.finalidad}</td>
                    <td><Badge status={record.estado} /></td>
                    <td className={styles.cbCellDate}>{formatDate(record.fechaOtorgamiento)}</td>
                    <td className={styles.cbCellDate}>{formatDate(record.fechaExpiracion)}</td>
                    <td>
                      <div className={styles.cbRowActions}>
                        {existingTemplate ? (
                          <button
                            className={styles.cbTemplateBtn}
                            onClick={() => navigate(`/plantillas?templateId=${existingTemplate.id}`)}
                          >
                            Ver plantilla
                          </button>
                        ) : (
                          <button
                            className={styles.cbTemplateBtnNew}
                            onClick={() => navigate(`/plantillas?consentId=${record.id}`)}
                          >
                            + Plantilla
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
};

// ─────────────────────────────────────────────────────────────
// Editor sub-components (stateless)
// ─────────────────────────────────────────────────────────────

const Section = ({ title, children }: { title: string; children: React.ReactNode }) => (
  <div className={styles.section}>
    <p className={styles.sectionTitle}>{title}</p>
    {children}
  </div>
);

const ColorRow = ({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) => (
  <div className={styles.colorRow}>
    <label className={styles.colorLabel}>{label}</label>
    <div className={styles.colorInputWrap}>
      <input
        type="color"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={styles.colorPicker}
      />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={styles.colorText}
        maxLength={7}
      />
    </div>
  </div>
);

const TemplateCard = ({
  tmpl,
  onEdit,
  onUseAsBase,
}: {
  tmpl: TemplateRecord;
  onEdit: () => void;
  onUseAsBase: () => void;
}) => {
  const activeVer =
    tmpl.versions.find((v) => v.status === 'activa') ??
    tmpl.versions[tmpl.versions.length - 1];

  const [expanded, setExpanded] = useState(false);

  const isMulti = tmpl.consentId === 'MULTI';
  const isLibre = tmpl.consentId === 'LIBRE';

  return (
    <div className={styles.tmplCard}>
      <div className={styles.tmplCardHead}>
        <div className={styles.tmplPills}>
          <span className={styles.vPill}>v{activeVer.version}</span>
          <span className={[
            styles.sPill,
            activeVer.status === 'activa' ? styles.sPillActive : styles.sPillArchived,
          ].join(' ')}>
            {activeVer.status}
          </span>
          {isMulti && (
            <span className={styles.sPillMulti}>
              {tmpl.consentIds?.length ?? '?'} consentimientos
            </span>
          )}
          {isLibre && <span className={styles.sPillLibre}>Independiente</span>}
        </div>
        <p className={styles.tmplName}>{tmpl.name}</p>
        <p className={styles.tmplMeta}>
          {isMulti ? (
            <span className={styles.tmplMultiIds}>{tmpl.consentIds?.join(' · ')}</span>
          ) : isLibre ? (
            <span className={styles.tmplLibreTag}>Sin consentimiento asociado</span>
          ) : (
            <>
              <span className={styles.tmplConsentId}>{tmpl.consentId}</span>
              {' · '}
              {tmpl.consentArea}
            </>
          )}
        </p>
        {!isLibre && <p className={styles.tmplFinalidad}>{tmpl.consentFinalidad}</p>}
      </div>

      <div className={styles.tmplVersionsWrap}>
        <button
          className={styles.tmplVersionsToggle}
          onClick={() => setExpanded((x) => !x)}
        >
          {tmpl.versions.length} versión{tmpl.versions.length !== 1 ? 'es' : ''}
          <span className={styles.toggleChevron}>{expanded ? '▲' : '▼'}</span>
        </button>

        {expanded && (
          <div className={styles.tmplVersionsList}>
            {[...tmpl.versions].reverse().map((v) => (
              <div
                key={v.version}
                className={[
                  styles.tmplVersionRow,
                  v.status === 'activa' ? styles.tmplVersionRowActive : '',
                ].join(' ')}
              >
                <span className={styles.tmplVerNum}>v{v.version}</span>
                <span className={[
                  styles.tmplVerStatus,
                  v.status === 'activa' ? styles.tmplVerStatusActive : styles.tmplVerStatusArchived,
                ].join(' ')}>
                  {v.status}
                </span>
                <span className={styles.tmplVerDate}>{formatDate(v.savedAt)}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className={styles.tmplCardActions}>
        <Button variant="ghost" size="sm" onClick={onEdit}>Ver/editar</Button>
        <Button variant="ghost" size="sm" onClick={onUseAsBase}>Usar como base</Button>
      </div>
    </div>
  );
};

const VersionRow = ({
  v,
  onLoad,
}: {
  v: TemplateVersion;
  onLoad: () => void;
}) => (
  <div
    className={[
      styles.versionHistoryRow,
      v.status === 'activa' ? styles.versionHistoryRowActive : '',
    ].join(' ')}
  >
    <div className={styles.versionHistoryLeft}>
      <span className={styles.versionHistoryNum}>v{v.version}</span>
      <span className={[
        styles.versionHistoryStatus,
        v.status === 'activa'
          ? styles.versionHistoryStatusActive
          : styles.versionHistoryStatusArchived,
      ].join(' ')}>
        {v.status}
      </span>
    </div>
    <span className={styles.versionHistoryDate}>{formatDate(v.savedAt)}</span>
    {v.status !== 'activa' && (
      <button className={styles.versionHistoryLoad} onClick={onLoad}>Cargar</button>
    )}
  </div>
);

export default TemplatesPage;
