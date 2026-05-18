import type { ButtonHTMLAttributes } from 'react';
import styles from './Button.module.css';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

const Button = ({ variant = 'primary', size = 'md', className = '', children, ...rest }: ButtonProps) => (
  <button
    className={[styles.btn, styles[variant], styles[size], className].filter(Boolean).join(' ')}
    {...rest}
  >
    {children}
  </button>
);

export default Button;
