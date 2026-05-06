import { useEffect, type ReactNode } from 'react';
import styles from './Modal.module.css';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  children: ReactNode;
  /** 'center' = modal centrado | 'drawer' = panel lateral derecho */
  variant?: 'center' | 'drawer';
}

const Modal = ({ open, onClose, children, variant = 'center' }: ModalProps) => {
  useEffect(() => {
    if (!open) return;
    const handleKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handleKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', handleKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className={[styles.backdrop, open ? styles.visible : ''].join(' ')}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      <div
        className={[styles.panel, styles[variant]].join(' ')}
        onClick={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
};

export default Modal;
