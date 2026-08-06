import axios from 'axios';
import { CONFIG } from '../config/constants';

export interface AniListMedia {
  id: number;
  title: {
    romaji: string;
    english: string;
    native: string;
  };
  format: string;
  episodes: number | null;
  status: string | null;
  bannerImage: string | null;
  coverImage: {
    extraLarge: string;
    large: string;
  };
  description: string;
  genres: string[];
  averageScore: number;
}

export interface EnrichedMetadata {
  synopsis: string | null;
  backdrop: string | null;
  image: string | null;
  score: number | null;
  year: number | null;
  episodeStills: Record<string, string>;
}

export class TMDBService {


  /**
   * Fetch metadata enriched with 100% Spanish synopsis & episode stills from TMDB / AnimeVerse
   */
  static async getSpanishMetadata(title: string | string[]): Promise<EnrichedMetadata | null> {
    if (!title) return null;

    const inputTitles = Array.isArray(title) ? title : [title];
    const candidates: string[] = [];

    // 1. Full original titles FIRST (e.g. "Assassination Classroom the Movie: Our Time")
    for (const t of inputTitles) {
      if (!t || typeof t !== 'string') continue;
      const trimmed = t.trim();
      if (trimmed && !candidates.includes(trimmed)) {
        candidates.push(trimmed);
      }
    }

    // 2. Cleaned full titles without punctuation (e.g. "Assassination Classroom the Movie Our Time")
    for (const t of inputTitles) {
      if (!t || typeof t !== 'string') continue;
      if (t.includes('//') || t.includes(':') || t.includes('-')) {
        const clean = t.replace(/\/\//g, ' ').replace(/:/g, ' ').replace(/-/g, ' ').replace(/\s+/g, ' ').trim();
        if (clean && !candidates.includes(clean)) {
          candidates.push(clean);
        }
      }
    }

    // 4. Root title candidates (strip season/arc suffixes to find main series in TMDB)
    for (const t of inputTitles) {
      if (!t || typeof t !== 'string') continue;
      const root = t
        .replace(/\s+(?:2nd|3rd|4th|5th|6th|7th|8th|9th)\s+Season.*/i, '')
        .replace(/\s+Season\s+\d+.*/i, '')
        .replace(/\s+Part\s+\d+.*/i, '')
        .replace(/:\s+.*$/i, '')
        .replace(/\s+-\s+.*$/i, '')
        .trim();
      if (root && root.length > 3 && !candidates.includes(root)) {
        candidates.push(root);
      }
    }

    const fetchPromises = candidates.map(async (queryTitle) => {
      try {
        const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/enrich?title=${encodeURIComponent(queryTitle)}`;
        const { data } = await axios.get(url, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept': 'application/json',
          },
          timeout: 7000,
        });

        if (data) {
          const details = data.details || data.tmdb || {};
          const overview = details.overview || null;
          const backdropPath = data.anilist?.bannerImage ? data.anilist.bannerImage : (details.backdrop_path ? `${CONFIG.TMDB_IMAGE_BASE_BACKDROP}${details.backdrop_path}` : null);
          const posterPath = details.poster_path ? `${CONFIG.TMDB_IMAGE_BASE_POSTER}${details.poster_path}` : (data.anilist?.coverImage?.large || data.anilist?.coverImage?.extraLarge || null);
          const voteAverage = details.vote_average ? Math.round(details.vote_average * 10) / 10 : (data.anilist?.averageScore ? Math.round(data.anilist.averageScore / 10 * 10) / 10 : null);
          const year = details.first_air_date ? parseInt(details.first_air_date.split('-')[0], 10) : (data.anilist?.seasonYear || null);
          const episodeStills: Record<string, string> = data.episodeStills || {};

          if (backdropPath || overview) {
            return {
              synopsis: overview,
              backdrop: backdropPath,
              image: posterPath,
              score: voteAverage,
              year: year,
              episodeStills: episodeStills,
            };
          }
        }
      } catch (err: any) {
        console.warn(`[TMDBService] Enrich failed for "${queryTitle}":`, err?.message || err);
      }
      throw new Error('No valid metadata found');
    });

    const results = await Promise.allSettled(fetchPromises);
    for (const result of results) {
      if (result.status === 'fulfilled' && result.value) {
        return result.value;
      }
    }

    // Fallback: Query AniList for official 16:9 anime bannerImage if TMDB returned no backdrop
    try {
      const aniList = await this.getAniListMetadata(title);
      if (aniList && aniList.bannerImage) {
        return {
          synopsis: aniList.description ? await this.translateToSpanish(aniList.description) : null,
          backdrop: aniList.bannerImage,
          image: aniList.coverImage?.extraLarge || aniList.coverImage?.large || null,
          score: aniList.averageScore ? Math.round((aniList.averageScore / 10) * 10) / 10 : null,
          year: null,
          episodeStills: {},
        };
      }
    } catch (_e) {}

    return null;
  }

  /**
   * Fetch AniList media info (episodes count, genres, native title)
   */
  static async getAniListMetadata(title: string | string[]): Promise<Partial<AniListMedia> | null> {
    const titles = Array.isArray(title) ? title : [title];
    const query = `
      query ($search: String) {
        Media (search: $search, type: ANIME) {
          id
          format
          title {
            romaji
            english
            native
          }
          episodes
          status
          bannerImage
          coverImage {
            extraLarge
            large
          }
          description
          genres
          averageScore
        }
      }
    `;

    for (const t of titles) {
      if (!t) continue;
      try {
        const response = await axios.post(
          CONFIG.ANILIST_API_URL,
          {
            query,
            variables: { search: t },
          },
          { timeout: 4000 }
        );

        const media = response.data?.data?.Media;
        if (media) return media;
      } catch {
        // Try next title candidate
      }
    }

    return null;
  }

  /**
   * Fetch Kitsu cover image banner
   */
  static async getKitsuMetadata(title: string): Promise<string | null> {
    try {
      const url = `https://kitsu.io/api/edge/anime?filter[text]=${encodeURIComponent(title)}`;
      const { data } = await axios.get(url, {
        headers: {
          'Accept': 'application/vnd.api+json',
          'Content-Type': 'application/vnd.api+json',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        },
        timeout: 4000,
      });
      if (data && Array.isArray(data.data) && data.data.length > 0) {
        const coverImage = data.data[0].attributes?.coverImage;
        return coverImage?.original || coverImage?.large || coverImage?.small || null;
      }
    } catch {
      // ignore
    }
    return null;
  }

  /**
   * Translate English text to Spanish using Google Translate API
   */
  static async translateToSpanish(text: string): Promise<string | null> {
    if (!text) return null;
    try {
      const cleanText = text.replace(/<[^>]*>?/gm, '').trim();
      if (!cleanText) return null;
      const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t&q=${encodeURIComponent(cleanText)}`;
      const res = await axios.get(url, { timeout: 4000 });
      if (res.data && res.data[0]) {
        const translated = res.data[0].map((item: any) => item[0]).join('');
        if (translated && translated.trim().length > 0) {
          return translated.trim();
        }
      }
    } catch {
      // ignore
    }
    return null;
  }
}
