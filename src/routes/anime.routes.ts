import { Router, Request, Response } from 'express';
import { ScraperService } from '../services/scraper.service';

const router = Router();

/**
 * @route GET /api/v1/health
 * @desc Health check
 */
router.get('/health', (_req: Request, res: Response) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    service: 'APK-AnimeTV Backend Gateway',
  });
});

/**
 * @route GET /api/v1/anime/hero
 * @desc Get featured anime banners for TV home screen
 */
router.get('/anime/hero', async (_req: Request, res: Response) => {
  try {
    const banners = await ScraperService.getHeroBanners();
    res.json({ success: true, data: banners });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/latest
 * @desc Get latest currently releasing anime (Animes en emisión)
 */
router.get('/anime/latest', async (_req: Request, res: Response) => {
  try {
    const results = await ScraperService.getLatestReleasing();
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/search
 * @desc Search anime by query string
 */
router.get('/anime/search', async (req: Request, res: Response) => {
  try {
    const query = req.query.q as string;
    if (!query) {
      return res.status(400).json({ success: false, error: 'Query parameter "q" is required' });
    }
    const results = await ScraperService.searchAnime(query);
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/catalog
 * @desc Get anime catalog filtered by genre
 */
router.get('/anime/catalog', async (req: Request, res: Response) => {
  try {
    const genre = req.query.genre as string;
    const page = parseInt(req.query.page as string || '1', 10);
    const results = await ScraperService.getCatalog(genre, page);
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/details/:slug
 * @desc Get anime details, metadata, and episode list
 */
router.get('/anime/details/:slug', async (req: Request, res: Response) => {
  try {
    const slug = req.params.slug as string;
    const details = await ScraperService.getAnimeDetails(slug);
    res.json({ success: true, data: details });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/watch/*
 * @desc Get episode streaming sources for ExoPlayer
 */
router.get('/anime/watch/*', async (req: Request, res: Response) => {
  try {
    const episodeSlug = Array.isArray(req.params[0]) ? req.params[0].join('/') : (req.params[0] || '');
    const protocol = req.headers['x-forwarded-proto'] || req.protocol;
    const host = req.headers['x-forwarded-host'] || req.get('host');
    const backendBase = `${protocol}://${host}`;
    const stream = await ScraperService.getStreamSources(episodeSlug, backendBase);
    res.json({ success: true, data: stream });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/proxy/m3u8
 * @desc Proxy HLS manifest and rewrite dead CDN domains to working ones
 */
router.get('/proxy/m3u8', async (req: Request, res: Response) => {
  try {
    const url = req.query.url as string;
    if (!url) {
      res.status(400).json({ success: false, error: 'Missing url parameter' });
      return;
    }

    const axios = (await import('axios')).default;
    const { data: manifest } = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://jkanime.net/',
        'Origin': 'https://jkanime.net',
      },
      timeout: 8000,
      responseType: 'text',
    });

    const fixed = manifest;

    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.send(fixed);
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/proxy/ts
 * @desc Proxy TS video segments with required headers
 */
router.get('/proxy/ts', async (req: Request, res: Response) => {
  try {
    const url = req.query.url as string;
    if (!url) {
      res.status(400).json({ success: false, error: 'Missing url parameter' });
      return;
    }

    const axios = (await import('axios')).default;
    const response = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://jkanime.net/',
        'Origin': 'https://jkanime.net',
      },
      timeout: 15000,
      responseType: 'arraybuffer',
    });

    res.setHeader('Content-Type', 'video/mp2t');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Cache-Control', 'public, max-age=86400');
    res.send(Buffer.from(response.data));
  } catch (error: any) {
    res.status(502).json({ success: false, error: 'Segment fetch failed' });
  }
});

interface CachedStream {
  directUrl: string;
  refererHeader: string;
  timestamp: number;
}
const resolvedStreamCache = new Map<string, CachedStream>();

/**
 * @route GET /api/v1/proxy/video
 * @desc Stream direct MP4/HLS video bytes directly to ExoPlayer/Browser (Tokianime architecture)
 */
router.get('/proxy/video', async (req: Request, res: Response) => {
  try {
    const embedUrl = req.query.url as string;
    if (!embedUrl) {
      res.status(400).json({ success: false, error: 'Missing url parameter' });
      return;
    }

    const axios = (await import('axios')).default;
    const cheerio = (await import('cheerio')).default;
    const DEFAULT_HEADERS = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    };

    let directUrl: string | null = null;
    let refererHeader = 'https://google.com/';

    // Helper to resolve direct stream URL from embed
    const getOrResolveDirectUrl = async (forceRefresh = false): Promise<{ directUrl: string; refererHeader: string } | null> => {
      if (forceRefresh) {
        resolvedStreamCache.delete(embedUrl);
      }
      
      const cached = resolvedStreamCache.get(embedUrl);
      if (cached && (Date.now() - cached.timestamp < 30 * 60 * 1000) && !forceRefresh) {
        return { directUrl: cached.directUrl, refererHeader: cached.refererHeader };
      }

      let extractedUrl: string | null = null;
      let referer = 'https://google.com/';

      // 1. YourUpload
      if (embedUrl.includes('yourupload')) {
        try {
          const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 4000 });
          const m = html.match(/jwplayer\("[^"]+"\)\.setup\(\{\s*file:\s*["']([^"']+\.mp4[^"']*)["']/i) || html.match(/(https?:\/\/[^\s"'<>]+\.mp4[^\s"'<>]*)/i);
          if (m && m[1]) {
            extractedUrl = m[1];
            referer = 'https://www.yourupload.com/';
          }
        } catch {}
      }

      // 2. Mp4Upload
      if (!extractedUrl && embedUrl.includes('mp4upload')) {
        try {
          const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 4000 });
          const m = html.match(/src:\s*["'](https?:\/\/[^"']+\.mp4[^"']*)["']/i) || html.match(/(https?:\/\/[^\s"'<>]+\.mp4[^\s"'<>]*)/i);
          if (m && m[1]) {
            extractedUrl = m[1];
            referer = 'https://www.mp4upload.com/';
          }
        } catch {}
      }

      // 3. Filemoon
      if (!extractedUrl && embedUrl.includes('filemoon')) {
        try {
          const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 4000 });
          const packedMatch = html.match(/eval\(function\(p,a,c,k,e,d\)\{.*\}\('(.+?)',(\d+),(\d+),'(.+?)'\.split\('\|'\)/s);
          if (packedMatch) {
            const p = packedMatch[1];
            const a = parseInt(packedMatch[2], 10);
            const c = parseInt(packedMatch[3], 10);
            const k = packedMatch[4].split('|');
            let currentC = c;
            let result = p;
            while (currentC--) {
              if (k[currentC]) {
                result = result.replace(new RegExp('\\b' + currentC.toString(a) + '\\b', 'g'), k[currentC]);
              }
            }
            const m3u8 = result.match(/(https?:\/\/[^\s"'<>]+\.m3u8[^\s"'<>]*)/i);
            if (m3u8 && m3u8[1]) {
              extractedUrl = m3u8[1];
              referer = 'https://filemoon.sx/';
            }
          }
        } catch {}
      }

      // 4. OK.ru
      if (!extractedUrl && embedUrl.includes('ok.ru')) {
        try {
          const secureUrl = embedUrl.replace('http://', 'https://');
          const { data: html } = await axios.get(secureUrl, { headers: DEFAULT_HEADERS, timeout: 4000 });
          const optionsAttr = cheerio.load(html)('div[data-options]').attr('data-options');
          if (optionsAttr) {
            const decoded = optionsAttr.replace(/&quot;/g, '"').replace(/&amp;/g, '&');
            const json = JSON.parse(decoded);
            const flashvars = JSON.parse(json.flashvars.metadata);
            if (flashvars.videos && flashvars.videos.length > 0) {
              const preferred = flashvars.videos.find((v: any) => v.name === 'hd') || flashvars.videos.find((v: any) => v.name === 'sd') || flashvars.videos[0];
              if (preferred && preferred.url) {
                extractedUrl = preferred.url;
                referer = 'https://ok.ru/';
              }
            }
          }
        } catch {}
      }

      if (!extractedUrl) {
        if (embedUrl.includes('.mp4') || embedUrl.includes('.m3u8')) {
          extractedUrl = embedUrl;
        }
      }

      if (extractedUrl) {
        resolvedStreamCache.set(embedUrl, {
          directUrl: extractedUrl,
          refererHeader: referer,
          timestamp: Date.now(),
        });
        return { directUrl: extractedUrl, refererHeader: referer };
      }

      return null;
    };

    let resolved = await getOrResolveDirectUrl(false);
    if (!resolved) {
      res.redirect(302, embedUrl);
      return;
    }

    const buildStreamHeaders = (ref: string): Record<string, string> => {
      const h: Record<string, string> = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Referer': ref,
      };
      if (req.headers.range) {
        h['Range'] = req.headers.range as string;
      }
      return h;
    };

    let streamResponse: any = null;
    try {
      streamResponse = await axios.get(resolved.directUrl, {
        headers: buildStreamHeaders(resolved.refererHeader),
        responseType: 'stream',
        timeout: 15000,
      });
    } catch (fetchErr) {
      // Token expired or link dead — force fresh resolution and retry once
      resolved = await getOrResolveDirectUrl(true);
      if (resolved) {
        streamResponse = await axios.get(resolved.directUrl, {
          headers: buildStreamHeaders(resolved.refererHeader),
          responseType: 'stream',
          timeout: 15000,
        });
      } else {
        throw fetchErr;
      }
    }

    const rawContentType = streamResponse.headers['content-type'] ? String(streamResponse.headers['content-type']) : '';
    const isMp4 = resolved.directUrl.includes('.mp4') || rawContentType.includes('octet-stream') || rawContentType.includes('mp4');
    const contentType = isMp4 ? 'video/mp4' : (rawContentType || 'video/mp4');

    res.setHeader('Content-Type', contentType);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Accept-Ranges', 'bytes');
    if (streamResponse.headers['content-range']) {
      res.setHeader('Content-Range', String(streamResponse.headers['content-range']));
      res.status(206);
    } else {
      res.status(streamResponse.status);
    }
    if (streamResponse.headers['content-length']) {
      res.setHeader('Content-Length', String(streamResponse.headers['content-length']));
    }

    streamResponse.data.pipe(res);
  } catch (error: any) {
    if (!res.headersSent) {
      res.status(502).json({ success: false, error: 'Video stream proxy failed' });
    }
  }
});

export default router;
