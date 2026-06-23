import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import Button from './Button';
import styles from './ErrorBoundary.module.css';

interface Props {
  /** Mensaje mostrado al usuario. Permite diferenciar el boundary global del de cada portal. */
  title: string;
  message: string;
  /** Si true, el botón recarga toda la página (boundary global). Si false, solo reintenta el render. */
  fullReload?: boolean;
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary]', error, info.componentStack);
  }

  handleRetry = () => {
    this.setState({ hasError: false });
  };

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className={styles.fallback}>
          <div className={styles.icon}>
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 9v4" /><path d="M12 17h.01" />
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            </svg>
          </div>
          <h2 className={styles.title}>{this.props.title}</h2>
          <p className={styles.message}>{this.props.message}</p>
          <Button
            variant="primary"
            onClick={this.props.fullReload ? this.handleReload : this.handleRetry}
          >
            {this.props.fullReload ? 'Recargar página' : 'Reintentar'}
          </Button>
        </div>
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
