import { useState } from 'react';
import ConsentPreview from '../components/common/ConsentPreview';
import type { TemplateConfig } from '../components/common/ConsentPreview';
import Button from '../components/common/Button';
import styles from './TemplatesPage.module.css';

type Tab = 'design' | 'config';

const DOMAINS = ['Marketing', 'Publicidad', 'Análisis', 'Funcional'];
const REQUIRED_FIELD_OPTIONS = ['Nombre', 'Teléfono', 'Email', 'RUT', 'Dirección'];
const BUTTON_SIZES  = [{ value: 'sm', label: 'Pequeño' }, { value: 'md', label: 'Mediano' }, { value: 'lg', label: 'Grande' }] as const;
const BUTTON_RADIUS = [{ value: 'none', label: 'Sin borde' }, { value: 'sm', label: 'Redondeado' }, { value: 'full', label: 'Píldora' }] as const;

const DEFAULT_CONFIG: TemplateConfig = {
  logo:          '',
  primaryColor:  '#4361ee',
  titleColor:    '#111827',
  subtitleColor: '#6b7280',
  buttonLabel:   'Aceptar',
  buttonSize:    'md',
  buttonRadius:  'sm',
  domain:        '',
  purpose:       '',
  requiredFields: [],
};

const TemplatesPage = () => {
  const [activeTab, setActiveTab]   = useState<Tab>('design');
  const [config, setConfig]         = useState<TemplateConfig>(DEFAULT_CONFIG);
  const [savedMessage, setSavedMessage] = useState(false);

  const update = <K extends keyof TemplateConfig>(key: K, value: TemplateConfig[K]) =>
    setConfig((prev) => ({ ...prev, [key]: value }));

  const toggleField = (field: string) =>
    setConfig((prev) => ({
      ...prev,
      requiredFields: prev.requiredFields.includes(field)
        ? prev.requiredFields.filter((f) => f !== field)
        : [...prev.requiredFields, field],
    }));

  const handleSave = () => {
    setSavedMessage(true);
    setTimeout(() => setSavedMessage(false), 2500);
  };

  const handleReset = () => setConfig(DEFAULT_CONFIG);

  return (
    <div className={styles.page}>
      {/* Page header */}
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Personalización de Plantillas</h2>
          <p className={styles.subtitle}>Configura el formulario de consentimiento que verán tus usuarios</p>
        </div>
        <div className={styles.headerActions}>
          <Button variant="ghost" size="sm" onClick={handleReset}>Restablecer</Button>
          <Button variant="primary" size="sm" onClick={handleSave}>
            {savedMessage ? '✓ Guardado' : 'Guardar cambios'}
          </Button>
        </div>
      </div>

      <div className={styles.layout}>
        {/* Left panel — editor */}
        <div className={styles.editor}>
          {/* Tabs */}
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

          {/* Design tab */}
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
                <ColorRow
                  label="Color principal (botón)"
                  value={config.primaryColor}
                  onChange={(v) => update('primaryColor', v)}
                />
                <ColorRow
                  label="Color del título"
                  value={config.titleColor}
                  onChange={(v) => update('titleColor', v)}
                />
                <ColorRow
                  label="Color del subtítulo"
                  value={config.subtitleColor}
                  onChange={(v) => update('subtitleColor', v)}
                />
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
                    {BUTTON_RADIUS.map((opt) => (
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

          {/* Config tab */}
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
                  {REQUIRED_FIELD_OPTIONS.map((field) => (
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

        {/* Right panel — preview */}
        <div className={styles.preview}>
          <ConsentPreview config={config} />
        </div>
      </div>
    </div>
  );
};

const Section = ({ title, children }: { title: string; children: React.ReactNode }) => (
  <div className={styles.section}>
    <p className={styles.sectionTitle}>{title}</p>
    {children}
  </div>
);

const ColorRow = ({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) => (
  <div className={styles.colorRow}>
    <label className={styles.colorLabel}>{label}</label>
    <div className={styles.colorInputWrap}>
      <input type="color" value={value} onChange={(e) => onChange(e.target.value)} className={styles.colorPicker} />
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

export default TemplatesPage;
