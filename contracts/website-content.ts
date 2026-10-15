export type Locale = 'en' | 'vi';
export interface LocalizedText { en: string; vi: string }
export interface GalleryImage {
  id: string;
  src: string;
  alt: LocalizedText;
  caption: LocalizedText;
}
export interface ArticleImages {
  /** Legacy cover URL; retained independently of the additional images. */
  image: string;
  imageAlt?: LocalizedText | null;
  imageCaption?: LocalizedText | null;
  gallery?: GalleryImage[] | null;
}
export interface Entity {
  id: string;
  title: LocalizedText;
  description: LocalizedText;
  visible?: boolean;
}
export interface ResearchArea extends Entity, ArticleImages {
  labId?: number | null;
  icon: 'earth' | 'cpu' | 'radio' | 'network';
  topics: LocalizedText[];
}
export type MemberRole = 'Steering Member' | 'Core Member' | 'PhD student' | 'MSc student' | 'Undergraduate' | 'Guest'
  | 'Faculty' | 'Researcher' | 'Alumni';
export interface Member extends Entity {
  userId?: number | null;
  role: MemberRole | '';
  initials: string;
  area: string;
  email: string;
  color: string;
  image?: string;
  scholarProfile?: ScholarProfile | null;
}
export interface ScholarMetrics {
  citations: number | null;
  hIndex: number | null;
  i10Index: number | null;
  sinceYear: number | null;
  recentCitations: number | null;
  recentHIndex: number | null;
  recentI10Index: number | null;
}
export interface ScholarPublication {
  title: string;
  authors: string;
  venue: string;
  year: number | null;
  citations: number | null;
  url: string;
}
export interface ScholarProfile {
  url: string;
  affiliation: LocalizedText;
  interests: LocalizedText[];
  homepageUrl?: string | null;
  retrievedOn: string;
  metrics?: ScholarMetrics | null;
  publications: ScholarPublication[];
}
export type PublicationType = 'Journal' | 'Conference' | 'Book' | 'Preprint';
export type ResourceKind = 'DOI' | 'PDF' | 'Code';
export interface ArticleSource {
  sourceName?: string | null;
  sourceUrl?: string | null;
}
export interface Publication extends Entity, ArticleSource {
  year: number | null;
  type: PublicationType | '';
  authors: string[];
  area: string;
  venue: LocalizedText;
  project: string;
  resources: ResourceKind[];
  links?: Partial<Record<ResourceKind, string>>;
  bodyHtml?: LocalizedText | null;
}
export interface Project extends Entity, ArticleSource, ArticleImages {
  area: string;
  category: LocalizedText;
  status: 'Ongoing' | 'Completed' | '';
  period: string;
  lead: string;
  objectives: LocalizedText[];
  bodyHtml?: LocalizedText | null;
}
export interface NewsItem extends Entity, ArticleSource, ArticleImages {
  category: 'News' | 'Seminar' | 'Achievement' | 'Event' | '';
  date: string;
  location: LocalizedText;
  body: LocalizedText[];
  bodyHtml?: LocalizedText | null;
}
export interface LabData {
  research: ResearchArea[];
  members: Member[];
  publications: Publication[];
  projects: Project[];
  news: NewsItem[];
}
export type CollectionName = keyof LabData;
export interface SiteProfile {
  name: string;
  shortName: string;
  email: string;
  coordinates: string;
  locationLabel: string;
  showDemoNotice: boolean;
  summary: LocalizedText;
}
export interface WebsiteContent {
  profile: SiteProfile;
  data: LabData;
  copy: Record<string, LocalizedText>;
  media: Record<string, string>;
  pages?: WebsitePage[];
  contentLibrary?: WebsiteSection[] | null;
}
export type PageTemplate = 'home' | 'about' | 'research' | 'people' | 'publications' | 'projects' | 'products' | 'news' | 'join' | 'contact' | 'custom';
export interface WebsiteSection extends Entity, ArticleImages {
  eyebrow: LocalizedText;
  body: LocalizedText[];
  bodyHtml?: LocalizedText | null;
  presentation: 'standard' | 'featured' | 'split';
  linkLabel: LocalizedText;
  linkUrl: string;
}
export interface WebsitePage extends Entity {
  slug: string;
  navigationTitle: LocalizedText;
  template: PageTemplate;
  showInNavigation: boolean;
  sections: WebsiteSection[];
  sectionIds?: string[] | null;
}
export interface WebsiteState {
  revision: number;
  draft: WebsiteContent | null;
  published: WebsiteContent | null;
  publishedAt: string | null;
}
export interface WebsiteSaveRequest { revision: number; content: WebsiteContent }
export interface PublishedWebsiteContent { content: WebsiteContent | null; publishedAt: string | null }
