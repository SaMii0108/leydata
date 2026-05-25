import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../features/auth/useAuth';
import { findUser } from '../features/auth/mockUsers';
import LoginForm, { HintBox, HintRow } from '../components/common/LoginForm';
import styles from './LoginPage.module.css';

const LoginPage = () => {
  const { login, setPendingUser } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = (email: string, password: string): string | null => {
    const user = findUser(email, password);

    if (!user) return 'Correo o contraseña incorrectos.';

    // Titular intenta entrar por el portal operativo
    if (user.roles.every((r) => r === 'TITULAR'))
      return 'Los titulares deben ingresar desde el portal de titulares.';

    // Cuenta bloqueada permanentemente
    if (user.blocked)
      return 'Esta cuenta ha sido bloqueada permanentemente. Contacte al administrador.';

    // Cuenta desactivada temporalmente
    if (!user.active)
      return 'Esta cuenta ha sido desactivada temporalmente. Contacte al administrador.';

    // Multirol: el usuario tiene más de un rol no-TITULAR → pantalla de selección
    const operationalRoles = user.roles.filter((r) => r !== 'TITULAR');
    if (operationalRoles.length > 1) {
      setPendingUser(user);
      navigate('/seleccionar-rol', { replace: true });
      return null;
    }

    // Un solo rol → entra directamente
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
              <HintRow role="ADMIN"  email="ana@leydata.cl"    />
              <HintRow role="DPO"    email="carlos@leydata.cl" />
              <HintRow role="USER"   email="maria@leydata.cl"  label="RRHH" />
              <HintRow role="ADMIN"  email="aurora@leydata.cl" label="ADMIN+DPO (multirol)" />
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
