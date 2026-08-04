export interface RecentEpisodeItem {
  id: string;
  slug: string;
  title: string;
  episodeNumber: number;
  image: string;
  backdrop?: string | null;
  synopsis?: string | null;
}

export interface AnimeItem {
  id: string;
  title: string;
  slug: string;
  image: string;
  backdrop?: string | null;
  type?: string;
  score?: number | null;
  status?: string | null;
  year?: number | null;
  synopsis?: string | null;
  provider?: string;
}

export interface EpisodeItem {
  id: string;
  number: number;
  title: string;
  image?: string | null;
  duration?: string | null;
  overview?: string | null;
  slug: string;
}

export interface AnimeDetail {
  id: string;
  title: string;
  slug: string;
  englishTitle?: string | null;
  japaneseTitle?: string;
  synopsis: string;
  image: string;
  backdrop: string | null;
  genres: string[];
  status: string;
  score: number | null;
  year: number | null;
  type: string;
  episodes: EpisodeItem[];
}

export interface StreamSource {
  server: string;
  title: string;
  url: string;
  format?: 'hls' | 'mp4' | 'iframe';
  audio?: 'sub' | 'latino' | 'castellano';
  isDirect?: boolean;
}

export interface WatchResponse {
  animeTitle: string;
  episodeNumber: number;
  servers: StreamSource[];
}

export interface CatalogResponse {
  page: number;
  hasMore: boolean;
  results: AnimeItem[];
}
