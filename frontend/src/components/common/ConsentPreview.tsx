import { useState } from 'react';
import styles from './ConsentPreview.module.css';

export interface TemplateConfig {
  logo: string;
  primaryColor: string;
  titleColor: string;
  subtitleColor: string;
  buttonLabel: string;
  buttonSize: 'sm' | 'md' | 'lg';
  buttonRadius: 'none' | 'sm' | 'full';
  domain: string;
  purpose: string;
  requiredFields: string[];
}

export interface ConsentPurposeItem {
  id: string;
  nombre: string;
  obligatorio: boolean;
  descripcionTitular: string;
}

interface ConsentPreviewProps {
  config: TemplateConfig;
  dataItems?: ConsentPurposeItem[];
  templateName?: string;
  interactive?: boolean;
  showLabel?: boolean;
  onAccept?: (checkedIds: string[]) => void;
  onReject?: () => void;
}

const RADIUS_MAP = { none: '0px', sm: '8px', full: '999px' };
const SIZE_MAP   = { sm: '10px 18px', md: '12px 24px', lg: '14px 32px' };

const ConsentPreview = ({
  config,
  dataItems,
  templateName,
  interactive = false,
  showLabel = true,
  onAccept,
  onReject,
}: ConsentPreviewProps) => {
  const [checked, setChecked] = useState<Record<string, boolean>>({});
  const [validationError, setValidationError] = useState<string | null>(null);

  const btnStyle = {
    background: config.primaryColor,
    padding: SIZE_MAP[config.buttonSize],
    borderRadius: RADIUS_MAP[config.buttonRadius],
  };

  const toggle = (id: string) => {
    if (!interactive) return;
    setChecked((prev) => ({ ...prev, [id]: !prev[id] }));
    setValidationError(null);
  };

  const handleAccept = () => {
    if (!interactive) return;
    if (useItems) {
      const missing = dataItems!.filter((d) => d.obligatorio && !checked[d.id]);
      if (missing.length > 0) {
        setValidationError(
          `Debes aceptar los datos obligatorios: ${missing.map((d) => d.nombre).join(', ')}.`
        );
        return;
      }
    }
    setValidationError(null);
    onAccept?.(Object.entries(checked).filter(([, v]) => v).map(([k]) => k));
  };

  const useItems = dataItems && dataItems.length > 0;

  return (
    <div className={styles.frame}>
      {showLabel && <p className={styles.frameLabel}>Vista previa del widget de consentimiento</p>}

      <div className={styles.card}>
        {/* Logo */}
        {config.logo ? (
          <img src={config.logo} alt="Logo" className={styles.logo} />
        ) : (
          <div className={styles.logoPlaceholder}><span>Logo de la empresa</span></div>
        )}

        {/* Cabecera */}
        <h2 className={styles.formTitle} style={{ color: config.titleColor }}>
          {templateName ?? 'Solicitud de consentimiento'}
        </h2>
        <p className={styles.formSubtitle} style={{ color: config.subtitleColor }}>
          {config.domain ? `Dominio: ${config.domain}` : 'Sin dominio asignado'}
        </p>

        {/* Finalidad */}
        {config.purpose && (
          <p className={styles.formPurpose}>{config.purpose}</p>
        )}

        {/* Finalidades como checkboxes */}
        {useItems && (
          <div className={styles.fieldsSection}>
            <p className={styles.fieldsLabel}>Datos que se solicitarán:</p>
            <div className={styles.checkboxList}>
              {dataItems.map((item) => (
                <label
                  key={item.id}
                  className={[
                    styles.checkboxItem,
                    interactive ? styles.interactiveItem : '',
                    item.obligatorio && interactive && !checked[item.id] ? styles.itemRequired : '',
                  ].join(' ')}
                >
                  <input
                    type="checkbox"
                    className={styles.itemCheckbox}
                    checked={!!checked[item.id]}
                    onChange={() => toggle(item.id)}
                    disabled={!interactive}
                  />
                  <div className={styles.itemContent}>
                    <div className={styles.itemHeader}>
                      <span className={styles.itemTitle}>{item.nombre}</span>
                      <span className={item.obligatorio ? styles.badgeObligatorio : styles.badgeOpcional}>
                        {item.obligatorio ? 'Obligatorio' : 'Opcional'}
                      </span>
                    </div>
                    <span className={styles.itemDesc}>{item.descripcionTitular}</span>
                  </div>
                </label>
              ))}
            </div>
            {validationError && (
              <p className={styles.validationError}>{validationError}</p>
            )}
          </div>
        )}

        {/* Fallback: campos de texto (esquema anterior) */}
        {!useItems && config.requiredFields.length > 0 && (
          <div className={styles.fieldsSection}>
            <p className={styles.fieldsLabel}>Datos que se solicitarán:</p>
            <div className={styles.fieldsList}>
              {config.requiredFields.map((field) => (
                <div key={field} className={styles.fieldItem}>
                  <input type="text" placeholder={field} disabled className={styles.fieldInput} />
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Texto legal */}
        <p className={styles.legalText}>
          Al hacer clic en aceptar, confirma que ha leído y acepta nuestra política de
          privacidad conforme a la <strong>Ley 21.719</strong> de protección de datos personales.
        </p>

        {/* Acciones */}
        <div className={styles.actions}>
          <button
            className={styles.btnSecondary}
            onClick={interactive ? onReject : undefined}
            style={{ cursor: interactive ? 'pointer' : 'default' }}
          >
            Rechazar
          </button>
          <button
            className={styles.btnPrimary}
            style={{ ...btnStyle, cursor: interactive ? 'pointer' : 'default' }}
            onClick={handleAccept}
          >
            {config.buttonLabel || 'Aceptar'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ConsentPreview;
