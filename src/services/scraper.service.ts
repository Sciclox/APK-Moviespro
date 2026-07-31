import axios from 'axios';
import * as cheerio from 'cheerio';
import { CONFIG } from '../config/constants';
import { AnimeItem, AnimeDetail, WatchResponse, StreamSource, EpisodeItem } from '../types/anime';
import { TMDBService } from './tmdb.service';

function cleanSlug(rawSlug: string): string {
  if (!rawSlug) return 'anime';
  let s = rawSlug;
  if (s.includes('/')) {
    const parts = s.replace(/\/$/, '').split('/');
    s = parts[parts.length - 1];
  }
  // Strip artificial prefixes like "slug-", "anime-", "id-"
  s = s.replace(/^(slug|anime|id)-/gi, '');
  // Normalize common slug variation mismatches (e.g. re-zero -> rezero)
  s = s.replace(/re-zero/gi, 'rezero');
  return s;
}

function cleanTitle(title: string, slug: string): string {
  if (title && !title.startsWith('http')) {
    return title
      .replace(/[\u00a0\u2423\r\n\t_]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }
  const slugClean = cleanSlug(slug);
  return slugClean.replace(/-/g, ' ').replace(/\b\w/g, (l) => l.toUpperCase());
}

interface CacheEntry<T> {
  data: T;
  expiresAt: number;
}

class MemoryCache {
  private cache = new Map<string, CacheEntry<any>>();

  get<T>(key: string): T | null {
    const entry = this.cache.get(key);
    if (!entry) return null;
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      return null;
    }
    return entry.data as T;
  }

  set<T>(key: string, data: T, ttlSeconds: number): void {
    this.cache.set(key, {
      data,
      expiresAt: Date.now() + ttlSeconds * 1000,
    });
  }
}

const apiCache = new MemoryCache();

export class ScraperService {
  /**
   * Fetch Hero Banners for TV Home Screen
   */
  static async getHeroBanners(): Promise<AnimeItem[]> {
    const cached = apiCache.get<AnimeItem[]>('hero_banners');
    if (cached) return cached;
    try {
      const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/hero`;
      const { data } = await axios.get(url, { timeout: 6000 });

      if (data && data.success && Array.isArray(data.data)) {
        const result = data.data.map((item: any) => {
          const rawSlug = item.slug || item.title || '';
          const slug = cleanSlug(rawSlug);

          const title = cleanTitle(item.title || item.tmdb?.name, slug);
          const backdrop = item.tmdb?.backdrop_path
            ? `${CONFIG.TMDB_IMAGE_BASE}${item.tmdb.backdrop_path}`
            : item.image;

          return {
            id: slug,
            title: title,
            slug: slug,
            image: item.image,
            backdrop: backdrop,
            synopsis: item.tmdb?.overview || null,
            provider: 'AnimeVerse',
          };
        });
        apiCache.set('hero_banners', result, 900); // 15-min cache
        return result;
      }
    } catch (e) {
      console.error('Error fetching hero banners:', e);
    }

    return [
      {
        id: 'sword-art-online',
        title: 'Sword Art Online',
        slug: 'sword-art-online',
        image: 'https://cdn.animeav1.com/covers/222.jpg',
        backdrop: 'https://image.tmdb.org/t/p/original/6A8IHWrtMLtadNXnMTjggvVY8cs.jpg',
        synopsis: 'En el año 2022, el videojuego de realidad virtual Sword Art Online es lanzado.',
      },
    ];
  }

  /**
   * Fetch Currently Releasing Anime (Animes en Emisión) using AniList GraphQL
   */
  static async getLatestReleasing(): Promise<AnimeItem[]> {
    const query = `
      query {
        Page(page: 1, perPage: 15) {
          media(status: RELEASING, type: ANIME, sort: POPULARITY_DESC) {
            id
            title {
              romaji
              english
            }
            coverImage {
              extraLarge
              large
            }
            bannerImage
          }
        }
      }
    `;

    try {
      const response = await axios.post(
        CONFIG.ANILIST_API_URL,
        { query },
        { timeout: 5000 }
      );

      const mediaList = response.data?.data?.Page?.media || [];
      if (mediaList.length > 0) {
        return mediaList.map((item: any) => {
          // Prioritize Romaji title to match JKAnime/native Japanese slugs
          const titleStr = item.title?.romaji || item.title?.english || 'Anime en Emisión';
          const slugStr = titleStr
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-|-$/g, '');

          return {
            id: slugStr,
            title: titleStr,
            slug: slugStr,
            image: item.coverImage?.extraLarge || item.coverImage?.large || 'https://cdn.animeav1.com/covers/222.jpg',
            backdrop: item.bannerImage || null,
            type: 'EN EMISIÓN',
            provider: 'AniList Releasing',
          };
        });
      }
    } catch (e) {
      console.error('Error fetching releasing anime from AniList:', e);
    }

    // Fallback: Scrape releasing episodes directly from JKAnime homepage
    try {
      const { data: html } = await axios.get(CONFIG.JKANIME_BASE_URL, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 4500,
      });
      const $ = cheerio.load(html);
      const items: AnimeItem[] = [];

      $('a.box').each((_, el) => {
        const href = $(el).attr('href') || '';
        const title = $(el).find('h2, h3, .title').text().trim() || cleanTitle('', href);
        const img = $(el).find('img').attr('src') || 'https://cdn.animeav1.com/covers/222.jpg';
        const parts = href.replace(/\/$/, '').split('/');
        if (parts.length >= 4) {
          const slug = parts[parts.length - 2] || parts[parts.length - 1];
          if (
            slug &&
            !['buscar', 'animes', 'directorio', 'genero', 'emision', 'usuario', 'perfil', 'editar-perfil', 'login', 'registro'].includes(slug.toLowerCase()) &&
            !slug.includes('perfil') &&
            !slug.includes('user') &&
            !items.some((i) => i.slug === slug)
          ) {
            items.push({
              id: slug,
              title: cleanTitle(title, slug),
              slug: slug,
              image: img,
              backdrop: null,
              type: 'EN EMISIÓN',
              provider: 'JKAnime',
            });
          }
        }
      });

      if (items.length > 0) return items.slice(0, 15);
    } catch (e) {
      console.error('JKAnime homepage fallback error:', e);
    }

    return [];
  }

  static async searchAnime(query: string): Promise<AnimeItem[]> {
    if (!query || query.trim().length === 0) return [];
    const cleanQ = query.replace(/[:!?#,._-]/g, ' ').replace(/\s+/g, ' ').trim();
    const cacheKey = `search_jka_${cleanQ.toLowerCase()}`;
    const cached = apiCache.get<AnimeItem[]>(cacheKey);
    if (cached) return cached;

    // 1. Direct Search: Scrape JKAnime search page directly (100% official JKAnime slugs)
    try {
      const searchUrl = `${CONFIG.JKANIME_BASE_URL}/buscar/${encodeURIComponent(cleanQ)}/`;
      const { data: html } = await axios.get(searchUrl, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 4500,
      });
      const $ = cheerio.load(html);
      const items: AnimeItem[] = [];

      $('.anime__item').each((_, el) => {
        const a = $(el).find('a').first();
        const href = a.attr('href') || '';
        if (href.includes('jkanime.net/')) {
          const parts = href.replace(/\/$/, '').split('/');
          const slug = parts[parts.length - 1];
          const systemPaths = ['buscar', 'animes', 'directorio', 'genero', 'emision', 'usuario', 'perfil', 'editar-perfil', 'login', 'registro'];

          if (slug && !systemPaths.includes(slug.toLowerCase()) && !items.some((i) => i.slug === slug)) {
            const picDiv = $(el).find('.anime__item__pic, .set-bg').first();
            let img = picDiv.attr('data-setbg') || picDiv.find('img').attr('src') || '';
            if (!img) {
              img = `https://cdn.jkdesa.com/assets/images/animes/image/${slug}.jpg`;
            }

            const titleText = $(el).find('h5, .title, .tituloblanco, h2, h3').text().trim() || cleanTitle('', slug);

            items.push({
              id: slug,
              title: cleanTitle(titleText, slug),
              slug: slug,
              image: img,
              backdrop: null,
              type: 'TV Anime',
              provider: 'JKAnime',
            });
          }
        }
      });

      if (items.length > 0) {
        apiCache.set(cacheKey, items, 1800); // 30-min cache
        return items;
      }
    } catch (e) {
      console.error('JKAnime direct search error:', e);
    }

    // 2. Fallback: Try AnimeVerse search if JKAnime search returns 0 results
    try {
      const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/search?q=${encodeURIComponent(cleanQ)}`;
      const { data } = await axios.get(url, { timeout: 4500 });

      if (data && data.success && Array.isArray(data.data?.results) && data.data.results.length > 0) {
        const items = data.data.results.map((item: any) => {
          const rawSlug = item.slug || item.id || item.title;
          const slug = cleanSlug(rawSlug);
          const title = cleanTitle(item.title, slug);

          return {
            id: slug,
            title: title,
            slug: slug,
            image: item.image || `https://cdn.jkdesa.com/assets/images/animes/image/${slug}.jpg`,
            backdrop: item.backdrop || null,
            type: item.type || 'TV Anime',
            provider: item.provider || 'AnimeVerse',
          };
        });
        apiCache.set(cacheKey, items, 1800);
        return items;
      }
    } catch (e) {
      console.warn('AnimeVerse search fallback timed out/failed');
    }

    return [];
  }

  /**
   * Get Catalog by Genre
   */
  static async getCatalog(genre?: string, page: number = 1): Promise<AnimeItem[]> {
    try {
      const genreParam = genre ? encodeURIComponent(genre) : 'Accion';
      const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/catalog?genre=${genreParam}`;
      const { data } = await axios.get(url, { timeout: 6000 });

      if (data && data.success && data.data?.results) {
        return data.data.results.map((item: any) => {
          const rawSlug = item.slug || item.id || item.title;
          const slug = cleanSlug(rawSlug);
          const title = cleanTitle(item.title, slug);

          return {
            id: slug,
            title: title,
            slug: slug,
            image: item.image,
            type: item.type || 'Anime',
            provider: item.provider || 'Catalog',
          };
        });
      }
    } catch (e) {
      console.error('Catalog error:', e);
    }
    return [];
  }

  /**
   * Universal automatic slug resolver for JKAnime
   */
  static async resolveJkSlug(slugInput: string): Promise<string> {
    let slug = cleanSlug(slugInput);
    slug = slug.replace(/-(episode|episodio)-\d+$/i, '').replace(/\/\d+$/, '');

    // 1. Check if direct slug exists on JKAnime
    try {
      await axios.head(`${CONFIG.JKANIME_BASE_URL}/${slug}/`, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 1500,
      });
      return slug;
    } catch {}

    // 2. Try common JKAnime slug variations (e.g. -tv, -ii, -iii, -2024, -season-2)
    const variations = [
      `${slug}-tv`,
      `${slug}-2024`,
      `${slug}-ii`,
      `${slug}-iii`,
      `${slug}-season-2`,
      `${slug}-season-3`,
      slug.replace(/-20\d\d$/, ''),
      slug.replace(/-season-\d+$/, ''),
      slug.replace(/-2$/, '-ii').replace(/-3$/, '-iii').replace(/-4$/, '-iv')
    ];

    for (const v of variations) {
      if (v !== slug) {
        try {
          await axios.head(`${CONFIG.JKANIME_BASE_URL}/${v}/`, {
            headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
            timeout: 1200,
          });
          return v;
        } catch {}
      }
    }

    // 3. Fallback to scraping JKAnime search results with strict system path filters
    try {
      const cleanTitleText = slug.replace(/-/g, ' ');
      const words = cleanTitleText.split(' ').filter((w) => w.length >= 1);
      const query = words.slice(0, 3).join('_');
      const searchUrl = `${CONFIG.JKANIME_BASE_URL}/buscar/${encodeURIComponent(query)}/`;
      const { data: html } = await axios.get(searchUrl, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 3000,
      });
      const $ = cheerio.load(html);
      const candidates: string[] = [];
      const systemPaths = [
        'buscar', 'animes', 'directorio', 'genero', 'emision', 'usuario', 'top',
        'letra', 'ajax', 'assets', 'assets3', 'perfil', 'editar-perfil', 'login',
        'registro', 'logout', 'privacidad', 'dmca', 'contacto', 'horario', 'politicas',
        'politica-de-privacidad', 'terminos-y-condiciones', 'panel', 'home', 'dash',
        'comentarios', 'solicitudes', 'listas', 'guardado', 'historial', 'notificaciones',
        'estrenos', 'comunidad', 'aplicacion', '$me', 'reset', 'google', 'discord',
        'facebook', 'twitter', 'instagram', 'auth', 'password', 'user'
      ];

      $('.anime__item a, .portada a, .box a, .portadas a, .list a, .anime_pic a').each((_: number, el: any) => {
        const href = $(el).attr('href') || '';
        if (href.includes('jkanime.net/')) {
          const parts = href.replace(/\/$/, '').split('/');
          const candidate = parts[parts.length - 1];
          if (
            candidate &&
            /^[a-z0-9-]+$/i.test(candidate) &&
            !systemPaths.includes(candidate.toLowerCase()) &&
            !href.includes('/dash/') &&
            !href.includes('/buscar/') &&
            !candidate.includes('perfil') &&
            !candidate.includes('user') &&
            !candidate.includes('login')
          ) {
            if (!candidates.includes(candidate)) {
              candidates.push(candidate);
            }
          }
        }
      });

      if (candidates.length > 0) {
        const cleanWords = slug.toLowerCase().replace(/[^a-z0-9]+/g, ' ').split(' ').filter((w: string) => w.length >= 1);

        // First check if any candidate matches ALL key words of original slug
        if (cleanWords.length > 0) {
          const exactWordMatch = candidates.find((c: string) => {
            const cClean = c.toLowerCase().replace(/[^a-z0-9]+/g, ' ');
            return cleanWords.every((word: string) => cClean.includes(word));
          });
          if (exactWordMatch) return exactWordMatch;
        }

        // Select candidate with maximum overlapping words
        let bestCandidate = candidates[0];
        let maxOverlap = 0;
        for (const c of candidates) {
          const cClean = c.toLowerCase().replace(/[^a-z0-9]+/g, ' ');
          const overlap = cleanWords.filter((w: string) => cClean.includes(w)).length;
          if (overlap > maxOverlap) {
            maxOverlap = overlap;
            bestCandidate = c;
          }
        }

        return bestCandidate;
      }
    } catch {
      // ignore
    }

    return slug;
  }

  /**
   * Helper to fetch real native episode thumbnail preview directly from JKAnime episode page
   */
  static async fetchJkEpisodeStill(slug: string, epNum: number): Promise<string | null> {
    try {
      const url = `${CONFIG.JKANIME_BASE_URL}/${slug}/${epNum}/`;
      const { data: html } = await axios.get(url, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 2500,
      });
      const m = html.match(/(https:\/\/cdn\.jkdesa\.com\/assets\/images\/animes\/video\/image(?:_thumb)?\/[^"'\s>]+)/);
      if (m && m[1]) {
        return m[1];
      }
    } catch {
      // ignore
    }
    return null;
  }

  /**
   * Get Anime Detail, Spanish Synopsis, Episode Stills and Metadata
   */
  static async getAnimeDetails(slugInput: string): Promise<AnimeDetail> {
    const slug = await ScraperService.resolveJkSlug(slugInput);
    const cleanTitleText = slug.replace(/-/g, ' ');

    const jkDataPromise = (async () => {
      try {
        const url = `${CONFIG.JKANIME_BASE_URL}/${slug}/`;
        const { data: html } = await axios.get(url, {
          headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
          timeout: 4000,
        });
        const $ = cheerio.load(html);

        let epCount = 0;
        $('li').each((_, el) => {
          const text = $(el).text().trim();
          if (text.includes('Episodios:')) {
            const numMatch = text.match(/Episodios:\s*(\d+)/i);
            if (numMatch && numMatch[1] && numMatch[1] !== '0') {
              epCount = parseInt(numMatch[1], 10);
            }
          }
        });

        // For long-running ongoing series (like One Piece) where "Episodios: 0" is printed,
        // extract max episode number from episode links on the page (e.g. 1171)
        if (epCount === 0) {
          const epMatches = html.match(new RegExp(`jkanime\\.net\\/${slug}\\/(\\d+)\\/?`, 'g'));
          if (epMatches) {
            for (const mStr of epMatches) {
              const numM = mStr.match(/\/(\d+)\/?$/);
              if (numM && numM[1]) {
                const parsed = parseInt(numM[1], 10);
                if (parsed > epCount) epCount = parsed;
              }
            }
          }
        }

        let seasonCover: string | null = null;
        const imgTag = $('.anime_pic img, img[src*="assets/images/animes/image/"]').first().attr('src');
        if (imgTag) {
          seasonCover = imgTag;
        }

        let seasonTitle: string | null = null;
        const pageTitle = $('title').text().trim();
        if (pageTitle && pageTitle.includes(' - anime ')) {
          seasonTitle = pageTitle.split(' - anime ')[0].trim();
        } else if (pageTitle && pageTitle.includes(' online JkAnime')) {
          seasonTitle = pageTitle.split(' online JkAnime')[0].split(' - ')[0].trim();
        }

        let seasonSynopsis: string | null = null;
        $('.sinopsis p, .sinop p, .sinopsis, .sinop, div.sinopsis, p').each((_, el) => {
          const t = $(el).text().trim();
          if (
            t.length > 40 &&
            !seasonSynopsis &&
            !t.includes('JKAnime') &&
            !t.includes('Votar') &&
            !t.includes('redes sociales') &&
            !t.includes('Sesi') &&
            !t.includes('cuenta') &&
            !t.includes('alojado')
          ) {
            seasonSynopsis = t;
          }
        });

        // Fallback: Scrape AnimeFLV directly for native Spanish synopsis if JKAnime paragraph is missing
        if (!seasonSynopsis) {
          try {
            const flvUrl = `https://animeflv.net/anime/${slug}`;
            const flvRes = await axios.get(flvUrl, {
              headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
              timeout: 3000,
            });
            const $flv = cheerio.load(flvRes.data);
            const flvText = $flv('div.Description p').text().trim() || $flv('.sinopsis').text().trim();
            if (flvText && flvText.length > 30) {
              seasonSynopsis = flvText;
            }
          } catch {
            // ignore
          }
        }

        return { epCount, seasonCover, seasonSynopsis, seasonTitle };
      } catch (e) {
        return { epCount: 0, seasonCover: null, seasonSynopsis: null, seasonTitle: null };
      }
    })();

    const [spanishMeta, meta, kitsuMeta, jkMeta] = await Promise.all([
      TMDBService.getSpanishMetadata(cleanTitleText),
      TMDBService.getAniListMetadata(cleanTitleText),
      TMDBService.getKitsuMetadata(cleanTitleText),
      jkDataPromise,
    ]);

    // Sanitize spanishMeta to prevent loose TMDB mismatches (e.g. Nihon Animator Mihonichi)
    const isInvalidSynopsis = spanishMeta?.synopsis ? (spanishMeta.synopsis.includes('Japan Anima') || spanishMeta.synopsis.includes('Exhibition')) : false;
    const validSpanishMeta = (spanishMeta && !isInvalidSynopsis) ? spanishMeta : null;

    const isMovie = meta?.format === 'MOVIE' || cleanTitleText.toLowerCase().includes('movie') || cleanTitleText.toLowerCase().includes('pelicula') || cleanTitleText.toLowerCase().includes('película');
    const mediaType = isMovie ? 'PELÍCULA' : (meta?.format === 'SPECIAL' || meta?.format === 'OVA' ? 'ESPECIAL' : 'SERIE');

    const totalEpisodes = isMovie
      ? 1
      : (jkMeta.epCount > 0
          ? jkMeta.epCount
          : (meta?.episodes && meta.episodes > 0 ? meta.episodes : 12));

    const stills = validSpanishMeta?.episodeStills || {};
    const seasonFallbackImage = jkMeta.seasonCover || meta?.bannerImage || meta?.coverImage?.extraLarge || validSpanishMeta?.backdrop || validSpanishMeta?.image || 'https://cdn.animeav1.com/covers/222.jpg';

    const isSequel = /(?:season|part|2nd|3rd|4th|ii|iii|iv)/i.test(slug);
    const isLongRunning = totalEpisodes > 50 || slug.includes('one-piece') || slug.includes('naruto') || slug.includes('detective-conan') || slug.includes('dragon-ball');

    // Concurrently fetch real native episode preview stills directly from JKAnime episode pages
    const epCountToFetch = isMovie ? 1 : Math.min(totalEpisodes, 25);
    const jkStillsPromises = Array.from({ length: epCountToFetch }, (_, i) =>
      ScraperService.fetchJkEpisodeStill(slug, i + 1)
    );
    const jkStills = await Promise.all(jkStillsPromises);

    const episodes: EpisodeItem[] = [];

    if (isMovie) {
      episodes.push({
        id: `${slug}-movie`,
        number: 1,
        title: 'Película Completa',
        image: jkStills[0] || seasonFallbackImage,
        duration: '1 h 45 min',
        overview: `Disfruta de la película completa de ${cleanTitle(cleanTitleText, slug)} en calidad Full HD.`,
        slug: `${slug}/1`,
      });
    } else {
      const isReleasingStatus = meta?.status === 'RELEASING';
      for (let i = 0; i < totalEpisodes; i++) {
        const epNum = i + 1;
        const jkStill = jkStills[i];

        // Stop adding ghost episodes if this episode does not exist yet on JKAnime for short releasing shows
        if (i > 0 && !jkStill && (isSequel || isReleasingStatus || jkMeta.epCount === 0) && !isLongRunning) {
          break;
        }

        const stillImg = jkStill || seasonFallbackImage;
        episodes.push({
          id: `${slug}-episode-${epNum}`,
          number: epNum,
          title: `Episodio ${epNum}`,
          image: stillImg,
          duration: '24 min',
          overview: `Capítulo ${epNum} de ${cleanTitle(cleanTitleText, slug)}`,
          slug: `${slug}/${epNum}`,
        });
      }
    }

    let rawSynopsis = jkMeta.seasonSynopsis || null;

    if (!rawSynopsis && validSpanishMeta?.synopsis) {
      rawSynopsis = validSpanishMeta.synopsis;
    }

    if (rawSynopsis && rawSynopsis.length < 120 && validSpanishMeta?.synopsis && rawSynopsis !== validSpanishMeta.synopsis) {
      rawSynopsis = `${rawSynopsis}\n\n${validSpanishMeta.synopsis}`;
    }

    if (!rawSynopsis && meta?.description) {
      const translated = await TMDBService.translateToSpanish(meta.description);
      rawSynopsis = translated || meta.description.replace(/<[^>]*>?/gm, '');
    }

    const finalSynopsis = rawSynopsis || `${cleanTitle(cleanTitleText, slug)} es una aclamada serie de animación japonesa con increíbles batallas e historia envolvente.`;

    const rawStatus = meta?.status || 'FINISHED';
    const formattedStatus = rawStatus === 'RELEASING' ? '🟢 EN EMISIÓN' : '🔴 FINALIZADO';

    const baseTitle = jkMeta.seasonTitle || cleanTitle(cleanTitleText, slug);
    const displayTitle = baseTitle
      .replace(/([a-zA-Z])(\d+)/g, '$1 $2')
      .replace(/Season(\d+)/i, 'Season $1')
      .replace(/\s{2,}/g, ' ')
    const rawEnglishTitle = meta?.title?.english || null;
    const formattedEnglishTitle = rawEnglishTitle
      ? rawEnglishTitle
          .replace(/([a-zA-Z])(\d+)/g, '$1 $2')
          .replace(/Season(\d+)/i, 'Season $1')
          .replace(/\s{2,}/g, ' ')
          .trim()
      : null;

    return {
      id: slug,
      title: displayTitle,
      englishTitle: formattedEnglishTitle,
      japaneseTitle: meta?.title?.native || '',
      slug: slug,
      synopsis: finalSynopsis,
      image: meta?.coverImage?.extraLarge || validSpanishMeta?.image || jkMeta.seasonCover || 'https://cdn.animeav1.com/covers/222.jpg',
      backdrop: validSpanishMeta?.backdrop || kitsuMeta || meta?.bannerImage || jkMeta.seasonCover || meta?.coverImage?.extraLarge || null,
      genres: meta?.genres || ['Acción', 'Aventura', 'Fantasía'],
      status: formattedStatus,
      score: validSpanishMeta?.score || (meta?.averageScore ? meta.averageScore / 10 : 8.5),
      year: validSpanishMeta?.year || 2024,
      type: mediaType,
      episodes: episodes,
    };
  }

  /**
   * Scrape Real Video Stream Links for Specified Anime & Episode with Smart Slug Resolution
   */
  static async getStreamSources(episodeSlug: string, backendBase: string = 'https://apk-animetv-backend.onrender.com'): Promise<WatchResponse> {
    const cacheKey = `stream_v13_${episodeSlug}`;
    const cached = apiCache.get<WatchResponse>(cacheKey);
    if (cached) {
      return cached;
    }

    const parts = episodeSlug.split('/');
    let rawAnimeSlug = parts[0] || 'anime';
    let epNum = parseInt(parts[1] || '1', 10);

    if (rawAnimeSlug.includes('-episode-')) {
      const m = rawAnimeSlug.match(/^(.*?)-episode-(\d+)$/i);
      if (m) {
        rawAnimeSlug = m[1];
        if (isNaN(epNum) || epNum === 1) epNum = parseInt(m[2], 10);
      }
    }

    const animeSlug = cleanSlug(rawAnimeSlug);

    const servers: StreamSource[] = [];

    const DEFAULT_HEADERS = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
      'Accept-Language': 'es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7',
    };

    const extractJkServers = async (targetSlug: string, targetEp: number): Promise<StreamSource[]> => {
      const results: StreamSource[] = [];
      try {
        const epUrl = `https://jkanime.net/${targetSlug}/${targetEp}/`;
        const { data: epHtml } = await axios.get(epUrl, {
          headers: DEFAULT_HEADERS,
          timeout: 4500,
        });

        const playerUrls = epHtml.match(/https?:\/\/jkanime\.net\/jkplayer\/[^"'\s<>]+/g) || [];
        for (let i = 0; i < Math.min(playerUrls.length, 6); i++) {
          const playerUrl = playerUrls[i];
          try {
            const { data: playerHtml } = await axios.get(playerUrl, {
              headers: {
                ...DEFAULT_HEADERS,
                Referer: epUrl,
              },
              timeout: 3500,
            });

            const m3u8Match = playerHtml.match(/(https?:\/\/[^\s"'<>]+\.m3u8[^\s"'<>]*)/i);
            const mp4Match = playerHtml.match(/url:\s*['"](https?:\/\/[^'"]+\.mp4[^'"]*)['"]/i) || playerHtml.match(/(https?:\/\/jkplayers\.com\/stream\/[^\s"'<>]+)/i);

            if (m3u8Match && m3u8Match[1]) {
              const proxyUrl = `${backendBase}/api/v1/proxy/m3u8?url=${encodeURIComponent(m3u8Match[1])}`;
              if (!results.some(s => s.url === proxyUrl)) {
                results.push({
                  server: `JKAnime HLS ${results.length + 1}`,
                  title: `Servidor Principal ${results.length + 1} (HLS)`,
                  url: proxyUrl,
                  format: 'hls',
                  audio: 'sub',
                  isDirect: true,
                });
              }
            } else if (mp4Match && mp4Match[1]) {
              const rawMp4 = mp4Match[1];
              const proxyUrl = `${backendBase}/api/v1/proxy/video?url=${encodeURIComponent(rawMp4)}`;
              if (!results.some(s => s.url === proxyUrl)) {
                results.push({
                  server: `JKAnime MP4 Direct`,
                  title: `Servidor Principal MP4 Direct`,
                  url: proxyUrl,
                  format: 'mp4',
                  audio: 'sub',
                  isDirect: true,
                });
              }
            }
          } catch {
            // continue
          }
        }
      } catch {
        // continue
      }
      return results;
    };

    const resolveEmbedToDirectStream = async (embedUrl: string): Promise<string | null> => {
      try {
        if (embedUrl.includes('filemoon')) {
          const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 3500 });
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
            if (m3u8) return m3u8[1];
          }
        }
        if (embedUrl.includes('mp4upload')) {
          const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 3500 });
          const mp4Match = html.match(/src:\s*["'](https?:\/\/[^"']+\.mp4[^"']*)["']/i) || html.match(/(https?:\/\/[^\s"'<>]+\.mp4[^\s"'<>]*)/i);
          if (mp4Match && mp4Match[1]) return mp4Match[1];
        }
        if (embedUrl.includes('ok.ru')) {
          const secureUrl = embedUrl.replace('http://', 'https://');
          const { data: html } = await axios.get(secureUrl, { headers: DEFAULT_HEADERS, timeout: 3500 });
          const optionsAttr = cheerio.load(html)('div[data-options]').attr('data-options');
          if (optionsAttr) {
            const decoded = optionsAttr.replace(/&quot;/g, '"').replace(/&amp;/g, '&');
            const json = JSON.parse(decoded);
            const flashvars = JSON.parse(json.flashvars.metadata);
            if (flashvars.videos && flashvars.videos.length > 0) {
              const preferred = flashvars.videos.find((v: any) => v.name === 'hd') || flashvars.videos.find((v: any) => v.name === 'sd') || flashvars.videos[0];
              if (preferred && preferred.url) return preferred.url;
            }
          }
        }
      } catch {
        // ignore
      }
      return null;
    };

    const extractSecondaryProvider = async (targetSlug: string, targetEp: number): Promise<StreamSource[]> => {
      const secServers: StreamSource[] = [];

      const parsePage = async (url: string) => {
        try {
          const { data: html } = await axios.get(url, {
            headers: DEFAULT_HEADERS,
            timeout: 4500,
          });
          const $ = cheerio.load(html);

          const elements = $('button[data-player], a[data-player]').toArray();
          for (const el of elements) {
            const sName = $(el).text().trim() || 'Servidor';
            const b64 = $(el).attr('data-player');
            if (b64) {
              try {
                const decodedUrl = Buffer.from(b64, 'base64').toString('utf-8');
                const cleanName = sName.toUpperCase();

                const SUPPORTED_HOSTS = ['yourupload', 'mp4upload'];
                const isSupportedProxy = SUPPORTED_HOSTS.some(h => decodedUrl.toLowerCase().includes(h));

                // Check for REAL file extensions, not substrings of domain names
                // e.g. '.mp4upload.com' should NOT match as a .mp4 file
                const hasRealM3u8 = /\.m3u8(\?|#|$)/i.test(decodedUrl);
                const hasRealMp4 = /\.mp4(\?|#|$)/i.test(decodedUrl);

                if (hasRealM3u8 || hasRealMp4) {
                  // Direct playable stream — no proxy needed
                  const proxyUrl = `${backendBase}/api/v1/proxy/video?url=${encodeURIComponent(decodedUrl)}`;
                  secServers.push({
                    server: `AnimeFLV / ${cleanName}`,
                    title: `Servidor AnimeFLV ${targetEp} (${cleanName})`,
                    url: proxyUrl,
                    format: hasRealM3u8 ? 'hls' : 'mp4',
                    audio: 'sub',
                    isDirect: true,
                  });
                } else if (isSupportedProxy) {
                  // Embed page — route through proxy extractor to get real video URL
                  const proxyUrl = `${backendBase}/api/v1/proxy/video?url=${encodeURIComponent(decodedUrl)}`;
                  secServers.push({
                    server: `AnimeFLV / ${cleanName}`,
                    title: `Servidor AnimeFLV ${targetEp} (${cleanName})`,
                    url: proxyUrl,
                    format: 'mp4',
                    audio: 'sub',
                    isDirect: true,
                  });
                }
                // else: unsupported host — skip to avoid black screen
              } catch {
                // ignore
              }
            }
          }
        } catch {
          // ignore
        }
      };

      // 1. Direct slug try
      await parsePage(`https://monoschinos2.com/ver/${targetSlug}-episodio-${targetEp}`);

      // 2. Candidate search fallback if direct slug yielded 0 servers
      if (secServers.length === 0) {
        try {
          const cleanQuery = targetSlug.replace(/-/g, ' ');
          const searchUrl = `https://monoschinos2.com/buscar?q=${encodeURIComponent(cleanQuery)}`;
          const { data: searchHtml } = await axios.get(searchUrl, {
            headers: DEFAULT_HEADERS,
            timeout: 4000,
          });
          const $ = cheerio.load(searchHtml);
          const candidateSlugs: string[] = [];

          $('a').each((_, el) => {
            const href = $(el).attr('href') || '';
            if (href.includes('/anime/') || href.includes('/ver/')) {
              const cleaned = href.replace(/https?:\/\/[^\/]+/g, '').replace('/anime/', '').replace('/ver/', '').replace(/-episodio-\d+$/g, '');
              if (cleaned && !candidateSlugs.includes(cleaned)) {
                candidateSlugs.push(cleaned);
              }
            }
          });

          for (const cand of candidateSlugs.slice(0, 3)) {
            await parsePage(`https://monoschinos2.com/ver/${cand}-episodio-${targetEp}`);
            if (secServers.length > 0) break;
          }
        } catch {
          // ignore
        }
      }

      return secServers;
    };

    // Run JKAnime and AnimeFLV/Secondary scrapers in parallel
    const [jkResult, secResult] = await Promise.allSettled([
      extractJkServers(animeSlug, epNum),
      extractSecondaryProvider(animeSlug, epNum),
    ]);

    let jkServers: StreamSource[] = [];
    if (jkResult.status === 'fulfilled') {
      jkServers = jkResult.value;
    }

    // If direct JKAnime slug failed, search by title keyword
    if (jkServers.length === 0) {
      const cleanTitleText = animeSlug.replace(/-/g, ' ');
      try {
        const searchUrl = `${CONFIG.JKANIME_BASE_URL}/buscar/${encodeURIComponent(cleanTitleText)}/1/`;
        const { data: searchHtml } = await axios.get(searchUrl, {
          headers: DEFAULT_HEADERS,
          timeout: 3500,
        });
        const $ = cheerio.load(searchHtml);
        const candidateSlugs: string[] = [];

        $('a').each((_, el) => {
          const href = $(el).attr('href') || '';
          if (href.includes('jkanime.net/') && href.split('/').filter(Boolean).length === 4) {
            const pathParts = href.split('/').filter(Boolean);
            const foundSlug = pathParts[pathParts.length - 1];
            if (foundSlug && !['buscar', 'api', 'auth'].includes(foundSlug)) {
              candidateSlugs.push(foundSlug);
            }
          }
        });

        for (const candidate of candidateSlugs.slice(0, 3)) {
          const foundList = await extractJkServers(candidate, epNum);
          if (foundList.length > 0) {
            jkServers = foundList;
            break;
          }
        }
      } catch {
        // ignore
      }
    }

    if (jkServers.length > 0) {
      servers.push(...jkServers);
    }

    // Add secondary provider (AnimeFLV) servers
    if (secResult.status === 'fulfilled' && secResult.value.length > 0) {
      servers.push(...secResult.value);
    }

    // Fallback if no servers found anywhere
    if (servers.length === 0) {
      servers.push({
        server: 'AnimeTV HLS CDN',
        title: `Servidor HLS HD ${epNum}`,
        url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
        format: 'hls',
        audio: 'sub',
        isDirect: true,
      });
    }

    const response: WatchResponse = {
      animeTitle: animeSlug,
      episodeNumber: epNum,
      servers: servers,
    };

    if (servers.length > 0 && !servers[0].url.includes('mux.dev')) {
      apiCache.set(cacheKey, response, 7200); // 2-hour TTL cache
    }

    return response;
  }
}
