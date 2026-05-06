import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { findUser } from '../features/auth/mockUsers';
import styles from './LoginPage.module.css';

const LoginPage = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [error, setError]       = useState('');
  const [showHint, setShowHint] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const user = findUser(email.trim(), password);
    if (!user) {
      setError('Correo o contraseña incorrectos.');
      return;
    }
    if (user.role === 'TITULAR') {
      setError('Los titulares de datos deben ingresar desde el portal de titulares.');
      return;
    }
    login(user);
    if (user.role === 'USER') navigate('/consentimientos', { replace: true });
    else navigate('/', { replace: true });
  };

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.brand}>
          <span className={styles.brandName}>Ley Data</span>
          <span className={styles.brandSub}>Portal operativo · Ley 21.719</span>
        </div>

        <div className={styles.divider} />

        <h1 className={styles.title}>Iniciar sesión</h1>

        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.fieldGroup}>
            <label className={styles.label} htmlFor="email">Correo electrónico</label>
            <input
              id="email"
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
            <label className={styles.label} htmlFor="password">Contraseña</label>
            <input
              id="password"
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

        {/* Demo hint */}
        <button className={styles.hintToggle} onClick={() => setShowHint((v) => !v)}>
          {showHint ? '▲' : '▼'} Credenciales de demostración
        </button>
        {showHint && (
          <div className={styles.hintBox}>
            <p className={styles.hintTitle}>Todos los usuarios usan la misma contraseña: <code>ley2024</code></p>
            <div className={styles.hintList}>
              <HintRow role="ADMIN"   email="ana@leydata.cl" />
              <HintRow role="DPO"     email="carlos@leydata.cl" />
              <HintRow role="USER"    email="maria@leydata.cl" label="RRHH" />
              <HintRow role="USER"    email="pedro@leydata.cl"  label="Marketing" />
            </div>
          </div>
        )}

        <div className={styles.divider} />
        <p className={styles.titularLink}>
          ¿Eres titular de datos?{' '}
          <Link to="/titular/login" className={styles.link}>Accede al portal de titulares →</Link>
        </p>
      </div>
    </div>
  );
};

const HintRow = ({ role, email, label }: { role: string; email: string; label?: string }) => (
  <div className={styles.hintRow}>
    <span className={[styles.hintBadge, styles[`hint_${role.toLowerCase()}`]].join(' ')}>
      {label ?? role}
    </span>
    <code className={styles.hintEmail}>{email}</code>
  </div>
);

export default LoginPage;
