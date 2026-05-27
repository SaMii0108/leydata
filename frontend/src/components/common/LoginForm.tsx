import { useState } from 'react';
import type { ReactNode } from 'react';
import styles from './LoginForm.module.css';

interface LoginFormProps {
  onSubmit: (email: string, password: string) => string | null;
  demoHintContent?: ReactNode;
  footer?: ReactNode;
}

const LoginForm = ({ onSubmit, demoHintContent, footer }: LoginFormProps) => {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [error, setError]       = useState('');
  const [showHint, setShowHint] = useState(true);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const result = onSubmit(email.trim(), password);
    if (result) setError(result);
  };

  return (
    <div className={styles.wrap}>
      <form className={styles.form} onSubmit={handleSubmit}>
        <div className={styles.fieldGroup}>
          <label className={styles.label} htmlFor="lf-email">Correo electrónico</label>
          <input
            id="lf-email"
            type="email"
            className={styles.input}
            placeholder="tu@correo.cl"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
        </div>

        <div className={styles.fieldGroup}>
          <label className={styles.label} htmlFor="lf-password">Contraseña</label>
          <input
            id="lf-password"
            type="password"
            className={styles.input}
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </div>

        {error && <p className={styles.error}>{error}</p>}

        <button type="submit" className={styles.submitBtn}>
          Ingresar
        </button>
      </form>

      {demoHintContent && (
        <>
          <button
            type="button"
            className={styles.hintToggle}
            onClick={() => setShowHint((v) => !v)}
          >
            {showHint ? '▲' : '▼'} Credenciales de demostración
          </button>
          {showHint && (
            <div className={styles.hintBox}>
              {demoHintContent}
            </div>
          )}
        </>
      )}

      {footer && <>{footer}</>}
    </div>
  );
};

export const HintRow = ({ role, email, label }: { role: string; email: string; label?: string }) => (
  <div className={styles.hintRow}>
    <span className={[styles.hintBadge, styles[`hint_${role.toLowerCase()}`]].join(' ')}>
      {label ?? role}
    </span>
    <code className={styles.hintEmail}>{email}</code>
  </div>
);

export const HintBox = ({ children, password = 'ley2024' }: { children: ReactNode; password?: string }) => (
  <>
    <p className={styles.hintTitle}>
      Contraseña para todos: <code>{password}</code>
    </p>
    <div className={styles.hintList}>{children}</div>
  </>
);

export default LoginForm;
