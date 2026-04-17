import { inject } from '@angular/core';
import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpEvent } from '@angular/common/http';
import { Observable, EMPTY, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
): Observable<HttpEvent<unknown>> => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const reqWithCredentials = req.clone({ withCredentials: true });

  return next(reqWithCredentials).pipe(
    catchError((err) => {
      // ✅ Gateway peut répondre 401 OU 403 selon Spring Security
      if (err.status === 401 || err.status === 403) {
        // Si c'est /api/user (checkSession) → AuthService + authGuard gèrent
        // On ne redirige PAS ici pour éviter la boucle infinie
        const isSessionCheck = req.url.includes('/api/user');
        if (!isSessionCheck) {
          authService.login(router.url);
        }
        return EMPTY;
      }
      return throwError(() => err);
    })
  );
};