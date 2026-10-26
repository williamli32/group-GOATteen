import { environment } from '../../../environments/environment';

export function isApiRequest(url: string): boolean {
  try {
    const target = new URL(url, window.location.origin);
    const api = new URL(environment.apiUrl, window.location.origin);
    const path = api.pathname.replace(/\/$/, '');
    return target.origin === api.origin &&
      (target.pathname === path || target.pathname.startsWith(path + '/'));
  } catch { return false; }
}
