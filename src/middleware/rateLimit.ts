import { NextFunction, Request, Response } from 'express';

interface RateLimitEntry {
  count: number;
  resetAt: number;
}

const requests = new Map<string, RateLimitEntry>();

/** Lightweight process-local limit for local/single-instance development. */
export function rateLimit(maxRequests: number, windowMs: number) {
  return (req: Request, res: Response, next: NextFunction) => {
    const now = Date.now();
    const clientId = req.ip || req.socket.remoteAddress || 'unknown';
    const key = `${clientId}:${req.path.startsWith('/proxy/') ? 'proxy' : 'api'}`;
    const existing = requests.get(key);
    const entry = !existing || now >= existing.resetAt
      ? { count: 0, resetAt: now + windowMs }
      : existing;

    entry.count += 1;
    requests.set(key, entry);
    res.setHeader('RateLimit-Limit', String(maxRequests));
    res.setHeader('RateLimit-Remaining', String(Math.max(0, maxRequests - entry.count)));

    if (entry.count > maxRequests) {
      res.setHeader('Retry-After', String(Math.ceil((entry.resetAt - now) / 1000)));
      res.status(429).json({ success: false, error: 'Demasiadas solicitudes. Inténtalo de nuevo en un momento.' });
      return;
    }

    next();
  };
}
