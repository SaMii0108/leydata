import { useNavigate, Link } from 'react-router-dom';
import { useAuth, parseKeycloakToken } from '../features/auth/AuthContext';
import LoginForm, { HintBox, HintRow } from '../components/common/LoginForm';
import styles from './LoginPage.module.css';

const KEYCLOAK_TOKEN_URL =
  `${import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180/realms/leydata'}/protocol/openid-connect/token`;

const LoginPage = () => {
  const { login } = useAuth();
  const navigate  = useNavigate();

  const handleSubmit = async (email: string, password: string): Promise<string | null> => {
    try {
      const params = new URLSearchParams({
        grant_type: 'password',
        client_id:  'leydata-frontend',
        username:   email,
        password,
      });

      const res = await fetch(KEYCLOAK_TOKEN_URL, {
        method:  'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body:    params.toString(),
      });

      if (!res.ok) return 'Correo o contraseña incorrectos.';

      const data = await res.json();
      const user = parseKeycloakToken(data.access_token);

      if (!user) return 'No se pudo identificar el rol del usuario.';
      if (user.role === 'TITULAR')
        return 'Los titulares deben ingresar desde el portal de titulares.';

      login(user, { access: data.access_token, refresh: data.refresh_token });
      navigate(user.role === 'JEFE_DOMINIO' ? '/consentimientos' : '/', { replace: true });
      return null;
    } catch {
      return 'Error de conexión. Verifica que el servidor esté disponible.';
    }
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
            <HintBox password="Admin1234!">
              <HintRow role="ADMIN" email="admin@leydata.cl" />
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
