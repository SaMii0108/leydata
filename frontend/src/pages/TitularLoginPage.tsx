import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { findUser } from '../features/auth/mockUsers';
import styles from './LoginPage.module.css';

const TitularLoginPage = () => {
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
    if (!user || user.role !== 'TITULAR') {
      setError('Correo o contraseña incorrectos.');
      return;
    }
    login(user);
    navigate('/titular/mis-consentimientos', { replace: true });
  };

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.brand}>
          <span className={styles.brandName}>Ley Data</span>
          <span className={styles.brandSub}>Portal de titulares · Ley 21.719</span>
        </div>

        <div className={styles.divider} />

        <h1 className={styles.title}>Portal de titulares</h1>
        <p className={styles.portalHint}>
          Accede para consultar y gestionar los consentimientos que has otorgado.
        </p>

        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.fieldGroup}>
            <label className={styles.label} htmlFor="t-email">Correo electrónico</label>
            <input
              id="t-email"
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
            <label className={styles.label} htmlFor="t-password">Contraseña</label>
            <input
              id="t-password"
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

        <button className={styles.hintToggle} onClick={() => setShowHint((v) => !v)}>
          {showHint ? '▲' : '▼'} Credenciales de demostración
        </button>
        {showHint && (
          <div className={styles.hintBox}>
            <p className={styles.hintTitle}>Contraseña para todos: <code>ley2024</code></p>
            <div className={styles.hintList}>
              <div className={styles.hintRow}>
                <span className={[styles.hintBadge, styles.hint_titular].join(' ')}>TITULAR</span>
                <code className={styles.hintEmail}>mjfuentes@email.com</code>
              </div>
              <div className={styles.hintRow}>
                <span className={[styles.hintBadge, styles.hint_titular].join(' ')}>TITULAR</span>
                <code className={styles.hintEmail}>caperez@email.com</code>
              </div>
              <div className={styles.hintRow}>
                <span className={[styles.hintBadge, styles.hint_titular].join(' ')}>TITULAR</span>
                <code className={styles.hintEmail}>vrojas@email.com</code>
              </div>
            </div>
          </div>
        )}

        <div className={styles.divider} />
        <p className={styles.titularLink}>
          ¿Eres operador del sistema?{' '}
          <Link to="/login" className={styles.link}>Accede al portal operativo →</Link>
        </p>
      </div>
    </div>
  );
};

export default TitularLoginPage;
