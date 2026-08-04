import axios from 'axios';
import { CONFIG } from '../config/constants';

export interface AniListMedia {
  id: number;
  title: {
    romaji: string | null;
    english: string | null;
    native: string | null;
  };
  synonyms: string[];
  format: string;
  episodes: number | null;
  status: string | null;
  bannerImage: string | null;
  coverImage: {
    extraLarge: string | null;
    large: string | null;
  };
  description: string | null;
  genres: string[];
  averageScore: number | null;
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
   * Generates smart prioritized search candidates from complex anime titles
   */
  private static buildSearchCandidates(title: string): string[] {
    const candidates = new Set<string>();
    if (!title) return [];

    const rawTitle = title.trim();

    // 1. Subtitle after ':' (e.g. "Sword Art Online: Unanswered//butterfly" -> "Unanswered//butterfly")
    if (rawTitle.includes(':')) {
      const afterColon = rawTitle.split(':').slice(1).join(':').trim();
      if (afterColon) candidates.add(afterColon);
    }

    // 2. Subtitle or title with slashes replaced with space or slash
    if (rawTitle.includes('//')) {
      candidates.add(rawTitle.replace(/\/\//g, ' / '));
      candidates.add(rawTitle.replace(/\/\//g, ' '));
    }

    // 3. Cleaned subtitle without slashes
    if (rawTitle.includes(':') && rawTitle.includes('//')) {
      const afterColon = rawTitle.split(':').slice(1).join(':').trim();
      if (afterColon) candidates.add(afterColon.replace(/\/\//g, ' ').trim());
    }

    // 4. Cleaned full title without punctuation
    const cleanTitle = rawTitle
      .replace(/[\/\/:_\-~]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    if (cleanTitle) candidates.add(cleanTitle);

    // 5. Full original title
    candidates.add(rawTitle);

    // 6. Base franchise title before ':' (e.g. "Sword Art Online")
    if (rawTitle.includes(':')) {
      const mainFranchise = rawTitle.split(':')[0].trim();
      if (mainFranchise) candidates.add(mainFranchise);
    }

    return Array.from(candidates);
  }

  /**
   * Fetch metadata enriched with 100% Spanish synopsis & episode stills from TMDB / AnimeVerse
   */
  static async getSpanishMetadata(title: string): Promise<EnrichedMetadata | null> {
    if (!title) return null;

    const initialCandidates = this.buildSearchCandidates(title);

    // 1. Try TMDB / AnimeVerse with initial candidate waterfall
    for (const queryTitle of initialCandidates) {
      try {
        const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/enrich?title=${encodeURIComponent(queryTitle)}`;
        const { data } = await axios.get(url, { timeout: 4000 });

        if (data) {
          const details = data.details || data.tmdb || {};
          const overview = details.overview || null;
          const backdropPath = details.backdrop_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.backdrop_path}` : null;
          const posterPath = details.poster_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.poster_path}` : null;
          const voteAverage = details.vote_average ? Math.round(details.vote_average * 10) / 10 : null;
          const year = details.first_air_date ? parseInt(details.first_air_date.split('-')[0], 10) : null;
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
      } catch {
        // Try next candidate
      }
    }

    // 2. Multi-Source Title Expansion: Query AniList to retrieve English, Romaji, Native, and Synonyms
    let foundAniListMedia: Partial<AniListMedia> | null = null;
    const expandedCandidates = new Set<string>();

    for (const queryTitle of initialCandidates) {
      try {
        const aniList = await this.getAniListMetadata(queryTitle);
        if (aniList) {
          if (!foundAniListMedia) foundAniListMedia = aniList;

          if (aniList.title?.romaji) expandedCandidates.add(aniList.title.romaji);
          if (aniList.title?.english) expandedCandidates.add(aniList.title.english);
          if (aniList.title?.native) expandedCandidates.add(aniList.title.native);
          if (Array.isArray(aniList.synonyms)) {
            aniList.synonyms.forEach(syn => {
              if (syn && syn.trim().length > 1) expandedCandidates.add(syn.trim());
            });
          }
          if (expandedCandidates.size > 0) break;
        }
      } catch (_e) {}
    }

    // 3. Second-pass: Try TMDB / AnimeVerse using all expanded titles from AniList (Romaji, English, Synonyms)
    for (const expandedTitle of expandedCandidates) {
      const candidatesForExpanded = this.buildSearchCandidates(expandedTitle);
      for (const cand of candidatesForExpanded) {
        try {
          const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/enrich?title=${encodeURIComponent(cand)}`;
          const { data } = await axios.get(url, { timeout: 4000 });

          if (data) {
            const details = data.details || data.tmdb || {};
            const overview = details.overview || null;
            const backdropPath = details.backdrop_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.backdrop_path}` : null;
            const posterPath = details.poster_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.poster_path}` : null;
            const voteAverage = details.vote_average ? Math.round(details.vote_average * 10) / 10 : null;
            const year = details.first_air_date ? parseInt(details.first_air_date.split('-')[0], 10) : null;
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
        } catch {}
      }
    }

    // 4. Fallback: Return AniList 16:9 bannerImage & translated synopsis if TMDB returned no backdrop
    if (foundAniListMedia && foundAniListMedia.bannerImage) {
      return {
        synopsis: foundAniListMedia.description ? await this.translateToSpanish(foundAniListMedia.description) : null,
        backdrop: foundAniListMedia.bannerImage,
        image: foundAniListMedia.coverImage?.extraLarge || foundAniListMedia.coverImage?.large || null,
        score: foundAniListMedia.averageScore ? Math.round((foundAniListMedia.averageScore / 10) * 10) / 10 : null,
        year: null,
        episodeStills: {},
      };
    }

    return null;
  }

  /**
   * Fetch AniList media info (episodes count, genres, native title, synonyms)
   */
  static async getAniListMetadata(title: string): Promise<Partial<AniListMedia> | null> {
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
          synonyms
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

    try {
      const response = await axios.post(
        CONFIG.ANILIST_API_URL,
        {
          query,
          variables: { search: title },
        },
        { timeout: 4000 }
      );

      return response.data?.data?.Media || null;
    } catch {
      return null;
    }
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
