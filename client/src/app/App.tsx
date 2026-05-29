import { AppRouter } from './router/AppRouter';
import { ErrorBoundary } from '../shared/components';

export default function App() {
  return (
    <ErrorBoundary>
      <AppRouter />
    </ErrorBoundary>
  );
}
