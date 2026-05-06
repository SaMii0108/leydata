import styles from './Header.module.css';

const Header = () => (
  <header className={styles.header}>
    <div className={styles.search}>
      <svg className={styles.searchIcon} width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
      </svg>
      <input
        type="text"
        placeholder="Buscar por usuario o correo..."
        className={styles.searchInput}
      />
    </div>

    <div className={styles.right}>
      <button className={styles.notifBtn} aria-label="Notificaciones">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
        </svg>
        <span className={styles.notifDot} />
      </button>

      <div className={styles.avatar} title="Aurora González">
        AG
      </div>
    </div>
  </header>
);

export default Header;
