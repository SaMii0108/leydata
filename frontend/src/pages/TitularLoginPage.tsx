import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../features/auth/useAuth';
import { findUser } from '../features/auth/mockUsers';
import LoginForm, { HintBox, HintRow } from '../components/common/LoginForm';
import styles from './LoginPage.module.css';

const TitularLoginPage = () => {
  const { login } = useAuth();
  const navigate  = useNavigate();

  const handleSubmit = (email: string, password: string): string | null => {
    const user = findUser(email, password);
    if (!user || user.role !== 'TITULAR') return 'Correo o contraseña incorrectos.';
    login(user);
    navigate('/titular/mis-consentimientos', { replace: true });
    return null;
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

        <LoginForm
          onSubmit={handleSubmit}
          demoHintContent={
            <HintBox>
              <HintRow role="TITULAR" email="mjfuentes@email.com" label="Titular 1" />
              <HintRow role="TITULAR" email="caperez@email.com"   label="Titular 2" />
              <HintRow role="TITULAR" email="vrojas@email.com"    label="Titular 3" />
            </HintBox>
          }
          footer={
            <>
              <div className={styles.divider} />
              <p className={styles.titularLink}>
                ¿Eres operador del sistema?{' '}
                <Link to="/login" className={styles.link}>
                  Accede al portal operativo →
                </Link>
              </p>
            </>
          }
        />
      </div>
    </div>
  );
};

export default TitularLoginPage;
