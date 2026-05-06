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

interface ConsentPreviewProps {
  config: TemplateConfig;
}

const RADIUS_MAP = { none: '0px', sm: '8px', full: '999px' };
const SIZE_MAP   = { sm: '10px 18px', md: '12px 24px', lg: '14px 32px' };

const ConsentPreview = ({ config }: ConsentPreviewProps) => {
  const btnStyle = {
    background: config.primaryColor,
    padding: SIZE_MAP[config.buttonSize],
    borderRadius: RADIUS_MAP[config.buttonRadius],
  };

  return (
    <div className={styles.frame}>
      <p className={styles.frameLabel}>Vista previa del formulario</p>

      <div className={styles.card}>
        {/* Logo */}
        {config.logo ? (
          <img src={config.logo} alt="Logo" className={styles.logo} />
        ) : (
          <div className={styles.logoPlaceholder}>
            <span>Logo de la empresa</span>
          </div>
        )}

        {/* Header */}
        <h2 className={styles.formTitle} style={{ color: config.titleColor }}>
          Solicitud de consentimiento
        </h2>
        <p className={styles.formSubtitle} style={{ color: config.subtitleColor }}>
          {config.domain
            ? `Dominio: ${config.domain}`
            : 'Selecciona un dominio en Configuración'}
        </p>

        {/* Purpose */}
        {config.purpose && (
          <p className={styles.formPurpose}>{config.purpose}</p>
        )}

        {/* Required fields */}
        {config.requiredFields.length > 0 && (
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

        {/* Legal */}
        <p className={styles.legalText}>
          Al hacer clic en aceptar, confirma que ha leído y acepta nuestra política
          de privacidad conforme a la <strong>Ley 21.719</strong> de protección de datos personales.
        </p>

        {/* Actions */}
        <div className={styles.actions}>
          <button className={styles.btnSecondary}>Rechazar</button>
          <button className={styles.btnPrimary} style={btnStyle}>
            {config.buttonLabel || 'Aceptar'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ConsentPreview;
