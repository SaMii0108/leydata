import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { findUser } from '../features/auth/mockUsers';
import LoginForm, { HintBox, HintRow } from '../components/common/LoginForm';
import styles from './LoginPage.module.css';

const LoginPage = () => {
  const { login } = useAuth();
  const navigate  = useNavigate();

  const handleSubmit = (email: string, password: string): string | null => {
    const user = findUser(email, password);
    if (!user) return 'Correo o contraseña incorrectos.';
    if (user.role === 'TITULAR')
      return 'Los titulares deben ingresar desde el portal de titulares.';
    login(user);
    navigate(user.role === 'USER' ? '/consentimientos' : '/', { replace: true });
    return null;
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

        <LoginForm
          onSubmit={handleSubmit}
          demoHintContent={
            <HintBox>
              <HintRow role="ADMIN" email="ana@leydata.cl" />
              <HintRow role="DPO"   email="carlos@leydata.cl" />
              <HintRow role="USER"  email="maria@leydata.cl"  label="RRHH" />
              <HintRow role="USER"  email="pedro@leydata.cl"  label="Marketing" />
            </HintBox>
          }
          footer={
            <>
              <div className={styles.divider} />
              <p className={styles.titularLink}>
                ¿Eres titular de datos?{' '}
                <Link to="/titular/login" className={styles.link}>
                  Accede al portal de titulares →
                </Link>
              </p>
            </>
          }
        />
      </div>
    </div>
  );
};

export default LoginPage;
