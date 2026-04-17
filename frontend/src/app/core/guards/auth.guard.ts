import { inject } from '@angular/core';
import { CanActivateFn, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { toObservable } from '@angular/core/rxjs-interop';
import { filter, map, take } from 'rxjs/operators';

// ✅ Flag global pour éviter les redirects multiples vers Keycloak
let redirectingToLogin = false;

export const authGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
) => {
  const authService = inject(AuthService);

  return toObservable(authService.isLoading).pipe(
    filter((loading) => !loading),
    take(1),
    map(() => {
      if (authService.isAuthenticated()) {
        redirectingToLogin = false;
        return true;
      }

      // ✅ Une seule redirection vers Keycloak, même si plusieurs guards s'activent
      if (!redirectingToLogin) {
        redirectingToLogin = true;
        console.log('[Guard] Non authentifié → redirect Keycloak pour:', state.url);
        authService.login(state.url);
      }
      return false;
    })
  );
};