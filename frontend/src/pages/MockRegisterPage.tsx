import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ConsentPreview from '../components/common/ConsentPreview';
import type { TemplateConfig } from '../components/common/ConsentPreview';
import { TEMPLATES } from '../utils/mockData';
import styles from './MockRegisterPage.module.css';

type Step = 1 | 2 | 3;

interface UserForm {
  nombre: string;
  email: string;
  telefono: string;
}

const EMPTY_USER: UserForm = { nombre: '', email: '', telefono: '' };

const MockRegisterPage = () => {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>(1);
  const [userForm, setUserForm] = useState<UserForm>(EMPTY_USER);
  const [errors, setErrors] = useState<Partial<UserForm>>({});
  const [consentId] = useState(() => `CONS-${Math.random().toString(36).slice(2, 10).toUpperCase()}`);
  const [checkedItems, setCheckedItems] = useState<string[]>([]);
  const [consentDecision, setConsentDecision] = useState<'accepted' | 'rejected' | null>(null);

  // Usar la primera plantilla activa para la demo
  const tpl = TEMPLATES.find((t) => t.estado === 'activa') ?? TEMPLATES[0];

  const config: TemplateConfig = {
    logo: '',
    primaryColor: tpl.primaryColor,
    titleColor: '#111827',
    subtitleColor: '#6b7280',
    buttonLabel: tpl.buttonLabel,
    buttonSize: 'md',
    buttonRadius: 'sm',
    domain: tpl.dominio,
    purpose: tpl.descripcion,
    requiredFields: [],
  };

  const setField = (k: keyof UserForm, v: string) => {
    setUserForm((p) => ({ ...p, [k]: v }));
    setErrors((p) => ({ ...p, [k]: undefined }));
  };

  const validateStep1 = (): boolean => {
    const e: Partial<UserForm> = {};
    if (!userForm.nombre.trim()) e.nombre = 'El nombre es obligatorio.';
    if (!userForm.email.trim())  e.email  = 'El correo es obligatorio.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(userForm.email)) e.email = 'Correo no válido.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleContinue = () => { if (validateStep1()) setStep(2); };

  const handleAccept = (ids: string[]) => {
    setCheckedItems(ids);
    setConsentDecision('accepted');
    setStep(3);
  };

  const handleReject = () => {
    setCheckedItems([]);
    setConsentDecision('rejected');
    setStep(3);
  };

  const handleReset = () => {
    setStep(1);
    setUserForm(EMPTY_USER);
    setErrors({});
    setConsentDecision(null);
    setCheckedItems([]);
  };

  return (
    <div className={styles.wrapper}>
      {/* Barra */}
      <div className={styles.topBar}>
        <div className={styles.brand}>
          <span className={styles.brandName}>LeyData</span>
          <span className={styles.brandTag}>Demo — Registro de Titular</span>
        </div>
        <button className={styles.exitBtn} onClick={() => navigate('/plantillas')}>
          Salir de la demo →
        </button>
      </div>

      {/* Stepper */}
      <div className={styles.stepperWrap}>
        <div className={styles.stepper}>
          {([1, 2, 3] as Step[]).map((s) => (
            <div key={s} className={styles.stepGroup}>
              <div className={[styles.stepDot, step >= s ? styles.stepDotActive : '', step === s ? styles.stepDotCurrent : ''].join(' ')}>
                {step > s ? '✓' : s}
              </div>
              <span className={[styles.stepLabel, step >= s ? styles.stepLabelActive : ''].join(' ')}>
                {s === 1 ? 'Datos' : s === 2 ? 'Consentimiento' : 'Confirmación'}
              </span>
              {s < 3 && <div className={[styles.stepLine, step > s ? styles.stepLineDone : ''].join(' ')} />}
            </div>
          ))}
        </div>
      </div>

      {/* Contenido */}
      <div className={styles.content}>

        {/* PASO 1 — Datos de registro */}
        {step === 1 && (
          <div className={styles.formCard}>
            <h2 className={styles.stepTitle}>Datos de registro</h2>
            <p className={styles.stepSubtitle}>Ingresa tus datos para crear tu cuenta en el sistema.</p>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>Nombre completo <span className={styles.req}>*</span></label>
              <input
                className={[styles.input, errors.nombre ? styles.inputError : ''].join(' ')}
                value={userForm.nombre}
                onChange={(e) => setField('nombre', e.target.value)}
                placeholder="Ej: María José Fuentes"
              />
              {errors.nombre && <span className={styles.errorMsg}>{errors.nombre}</span>}
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>Correo electrónico <span className={styles.req}>*</span></label>
              <input
                className={[styles.input, errors.email ? styles.inputError : ''].join(' ')}
                type="email"
                value={userForm.email}
                onChange={(e) => setField('email', e.target.value)}
                placeholder="Ej: mjfuentes@email.com"
              />
              {errors.email && <span className={styles.errorMsg}>{errors.email}</span>}
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.label}>Teléfono (opcional)</label>
              <input
                className={styles.input}
                type="tel"
                value={userForm.telefono}
                onChange={(e) => setField('telefono', e.target.value)}
                placeholder="Ej: +56 9 1234 5678"
              />
            </div>

            <button className={styles.primaryBtn} onClick={handleContinue}>
              Continuar →
            </button>
          </div>
        )}

        {/* PASO 2 — Consentimiento */}
        {step === 2 && (
          <div className={styles.consentStep}>
            <div className={styles.consentHeader}>
              <h2 className={styles.stepTitle}>Solicitud de consentimiento</h2>
              <p className={styles.stepSubtitle}>
                Para finalizar tu registro, revisa los datos que se solicitarán y decide si otorgas tu consentimiento.
              </p>
              <div className={styles.userTag}>
                Registrando como: <strong>{userForm.nombre}</strong> ({userForm.email})
              </div>
            </div>

            <div className={styles.widgetWrap}>
              <ConsentPreview
                config={config}
                dataItems={tpl.dataItems}
                templateName={tpl.nombre}
                interactive
                showLabel={false}
                onAccept={handleAccept}
                onReject={handleReject}
              />
            </div>

            <button className={styles.backStepBtn} onClick={() => setStep(1)}>
              ← Volver a mis datos
            </button>
          </div>
        )}

        {/* PASO 3 — Confirmación */}
        {step === 3 && (
          <div className={[styles.resultCard, consentDecision === 'accepted' ? styles.resultAccepted : styles.resultRejected].join(' ')}>
            <div className={styles.resultIcon}>
              {consentDecision === 'accepted' ? '✓' : '✕'}
            </div>

            <h2 className={styles.resultTitle}>
              {consentDecision === 'accepted'
                ? '¡Registro completado!'
                : 'Registro sin consentimiento'}
            </h2>

            <p className={styles.resultSubtitle}>
              {consentDecision === 'accepted'
                ? 'Tu consentimiento ha sido registrado exitosamente en el sistema.'
                : 'El titular rechazó el tratamiento de datos. El registro se marcó como pendiente.'}
            </p>

            {consentDecision === 'accepted' && (
              <div className={styles.summaryBox}>
                <div className={styles.summaryRow}>
                  <span className={styles.summaryKey}>ID de consentimiento</span>
                  <span className={styles.summaryVal}>{consentId}</span>
                </div>
                <div className={styles.summaryRow}>
                  <span className={styles.summaryKey}>Titular</span>
                  <span className={styles.summaryVal}>{userForm.nombre}</span>
                </div>
                <div className={styles.summaryRow}>
                  <span className={styles.summaryKey}>Correo</span>
                  <span className={styles.summaryVal}>{userForm.email}</span>
                </div>
                <div className={styles.summaryRow}>
                  <span className={styles.summaryKey}>Plantilla</span>
                  <span className={styles.summaryVal}>{tpl.nombre} · v{tpl.version}</span>
                </div>
                <div className={styles.summaryRow}>
                  <span className={styles.summaryKey}>Fecha</span>
                  <span className={styles.summaryVal}>{new Date().toLocaleDateString('es-CL')}</span>
                </div>
                {checkedItems.length > 0 && (
                  <div className={styles.summaryRow} style={{ flexDirection: 'column', alignItems: 'flex-start', gap: 4 }}>
                    <span className={styles.summaryKey}>Datos autorizados</span>
                    {tpl.dataItems
                      .filter((d) => checkedItems.includes(d.id))
                      .map((d) => (
                        <span key={d.id} className={styles.checkedItem}>✓ {d.nombre}</span>
                      ))}
                  </div>
                )}
                <div className={styles.integrityNote}>
                  🔐 En producción: se calcularía hash SHA-256 de integridad y se seudonimizarían los datos identificatorios (RF.16, RF.17 Ley 21.719).
                </div>
              </div>
            )}

            <button className={styles.resetBtn} onClick={handleReset}>
              Reiniciar demo
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default MockRegisterPage;
