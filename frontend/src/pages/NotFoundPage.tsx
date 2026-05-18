import { useNavigate } from 'react-router-dom';
import styles from './NotFoundPage.module.css';

const NotFoundPage = () => {
  const navigate = useNavigate();

  return (
    <div className={styles.page}>
      <div className={styles.content}>
        <span className={styles.code}>404</span>
        <h1 className={styles.title}>Página no encontrada</h1>
        <p className={styles.hint}>
          La ruta que buscas no existe o no tienes acceso a ella.
        </p>
        <button className={styles.backBtn} onClick={() => navigate(-1)}>
          ← Volver
        </button>
      </div>
    </div>
  );
};

export default NotFoundPage;
